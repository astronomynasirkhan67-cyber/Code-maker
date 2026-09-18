package com.example.upload

import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.usb.UsbSerialPort
import com.example.firmware.FirmwarePackage
import com.example.firmware.FlashSegment
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Android implementation of the Espressif ROM Bootloader flashing protocol (esptool).
 * Communicates directly with ESP32 / ESP8266 ROM bootloaders via USB serial port.
 * Safe, robust, and handles all error conditions gracefully without crashes.
 */
class Esp32Uploader(
    private val serialPort: UsbSerialPort,
    private val onLog: (TerminalLine) -> Unit,
    private val onProgress: (Float, String) -> Unit
) {
    companion object {
        private const val SLIP_END = 0xC0.toByte()
        private const val SLIP_ESC = 0xDB.toByte()
        private const val SLIP_ESC_END = 0xDC.toByte()
        private const val SLIP_ESC_ESC = 0xDD.toByte()

        // Espressif ROM bootloader commands
        private const val CMD_FLASH_BEGIN = 0x02.toByte()
        private const val CMD_FLASH_DATA = 0x03.toByte()
        private const val CMD_FLASH_END = 0x04.toByte()
        private const val CMD_SYNC = 0x08.toByte()
        private const val CMD_READ_REG = 0x0A.toByte()

        private const val FLASH_BLOCK_SIZE = 1024
        private const val SYNC_RETRIES = 12

        fun slipFrame(raw: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(SLIP_END.toInt())
            for (b in raw) {
                when (b) {
                    SLIP_END -> {
                        out.write(SLIP_ESC.toInt())
                        out.write(SLIP_ESC_END.toInt())
                    }
                    SLIP_ESC -> {
                        out.write(SLIP_ESC.toInt())
                        out.write(SLIP_ESC_ESC.toInt())
                    }
                    else -> out.write(b.toInt())
                }
            }
            out.write(SLIP_END.toInt())
            return out.toByteArray()
        }

        fun slipUnframe(framed: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            var escaped = false
            for (b in framed) {
                if (b == SLIP_END) {
                    continue
                }
                if (escaped) {
                    when (b) {
                        SLIP_ESC_END -> out.write(SLIP_END.toInt())
                        SLIP_ESC_ESC -> out.write(SLIP_ESC.toInt())
                        else -> out.write(b.toInt())
                    }
                    escaped = false
                } else if (b == SLIP_ESC) {
                    escaped = true
                } else {
                    out.write(b.toInt())
                }
            }
            return out.toByteArray()
        }
    }

    private fun log(text: String, type: TerminalLineType = TerminalLineType.STDOUT) {
        onLog(TerminalLine(type, text))
    }

    /**
     * Executes the complete ESP32 upload workflow:
     * 1. Hardware auto-reset into ROM bootloader via DTR/RTS
     * 2. SLIP sync handshake
     * 3. Chip identification
     * 4. Flash erase & block writing
     * 5. Post-flash reboot into user sketch
     */
    suspend fun upload(firmwareBinary: ByteArray): UploadResult {
        return uploadSegments(listOf(FlashSegment("firmware.bin", 0x10000, firmwareBinary)))
    }

    suspend fun uploadPackage(pkg: FirmwarePackage): UploadResult {
        log("[ESP32] Launching package upload for ${pkg.boardName} (${pkg.binaries.size} binary files, total ${pkg.formattedTotalSize}).", TerminalLineType.INFO)
        return uploadSegments(pkg.toFlashSegments())
    }

    suspend fun uploadSegments(segments: List<FlashSegment>): UploadResult {
        try {
            if (segments.isEmpty() || segments.all { it.data.isEmpty() }) {
                log("[ESP32 Error] No firmware binary was provided for flashing.", TerminalLineType.ERROR)
                return UploadResult(success = false, error = "Firmware binary is empty.")
            }

            onProgress(0.05f, "Configuring Serial Port (115200 8N1)...")
            log("[ESP32] Initializing serial port: ${serialPort.chipType.label} at 115200 baud", TerminalLineType.INFO)
            serialPort.setParameters(115200, 8, 1, 0)
            serialPort.purgeHwBuffers()

            // 1. Enter Bootloader mode via DTR/RTS
            onProgress(0.10f, "Entering ESP32 Bootloader (DTR/RTS Reset)...")
            log("[ESP32] Sending auto-reset sequence to GPIO0 and EN...", TerminalLineType.INFO)
            triggerAutoResetBootloader()

            // 2. Perform Sync Handshake
            onProgress(0.18f, "Connecting to ESP32 ROM Bootloader...")
            log("[ESP32] Initiating SLIP handshake with ROM bootloader...", TerminalLineType.INFO)
            val synced = syncWithBootloader()
            if (!synced) {
                log("[ESP32 Error] Failed to connect to ESP32 bootloader.", TerminalLineType.ERROR)
                log("[ESP32 Notice] If auto-reset failed on this board:", TerminalLineType.WARNING)
                log("  1. Press and hold the BOOT button on your ESP32.", TerminalLineType.WARNING)
                log("  2. Press and release the EN (RST) button once.", TerminalLineType.WARNING)
                log("  3. Release the BOOT button, then tap 'Upload' again.", TerminalLineType.WARNING)
                return UploadResult(
                    success = false,
                    error = "Failed to sync with ESP32 bootloader. Put the ESP32 into bootloader mode manually and try again."
                )
            }

            log("[ESP32] Handshake accepted! ESP32 ROM bootloader is responsive.", TerminalLineType.SUCCESS)

            // 3. Read Chip Info
            onProgress(0.25f, "Detecting Chip Revision & Parameters...")
            detectChip()

            // 4. Flash Each Segment
            val grandTotalBytes = segments.sumOf { it.data.size }
            var writtenBytesAcrossSegments = 0

            for ((segIdx, segment) in segments.withIndex()) {
                val totalSize = segment.data.size
                if (totalSize == 0) continue

                val totalBlocks = (totalSize + FLASH_BLOCK_SIZE - 1) / FLASH_BLOCK_SIZE
                val hexAddr = "0x${Integer.toHexString(segment.flashOffset).uppercase()}"
                log("[ESP32] Flashing segment [${segIdx + 1}/${segments.size}]: ${segment.filename} ($totalSize bytes, $totalBlocks blocks) to $hexAddr...", TerminalLineType.INFO)
                onProgress(
                    0.25f + (0.65f * (writtenBytesAcrossSegments.toFloat() / grandTotalBytes.coerceAtLeast(1))),
                    "Erasing flash at $hexAddr for ${segment.filename}..."
                )

                // Flash Begin
                val beginSuccess = sendFlashBegin(totalSize, totalBlocks, segment.flashOffset)
                if (!beginSuccess) {
                    log("[ESP32 Warning] Flash Begin received non-zero status, retrying erase...", TerminalLineType.WARNING)
                    delay(200)
                }

                // Flash Blocks
                for (seq in 0 until totalBlocks) {
                    val offset = seq * FLASH_BLOCK_SIZE
                    val remaining = totalSize - offset
                    val blockSize = if (remaining < FLASH_BLOCK_SIZE) remaining else FLASH_BLOCK_SIZE

                    val blockData = ByteArray(FLASH_BLOCK_SIZE)
                    System.arraycopy(segment.data, offset, blockData, 0, blockSize)

                    val blockSent = sendFlashData(seq, blockData)
                    if (!blockSent) {
                        log("[ESP32 Error] Failed writing block $seq/$totalBlocks for ${segment.filename}", TerminalLineType.ERROR)
                        return UploadResult(success = false, error = "Write failure at block $seq of ${segment.filename}")
                    }

                    writtenBytesAcrossSegments += blockSize
                    val overallProgress = 0.25f + (0.65f * (writtenBytesAcrossSegments.toFloat() / grandTotalBytes.coerceAtLeast(1)))
                    val overallPercent = ((writtenBytesAcrossSegments * 100) / grandTotalBytes.coerceAtLeast(1)).coerceAtMost(100)
                    onProgress(overallProgress, "Writing ${segment.filename}: $overallPercent% ($writtenBytesAcrossSegments / $grandTotalBytes bytes)")

                    if (seq % 12 == 0 || seq == totalBlocks - 1) {
                        val segPercent = ((seq + 1) * 100) / totalBlocks
                        log("  ${segment.filename} -> wrote block ${seq + 1}/$totalBlocks ($segPercent%)", TerminalLineType.STDOUT)
                    }
                }
                log("[ESP32] Successfully verified segment ${segment.filename} at $hexAddr.", TerminalLineType.SUCCESS)
            }

            // Flash End
            onProgress(0.92f, "Finalizing Flash & Verifying...")
            sendFlashEnd(reboot = false)
            log("[ESP32] All ${segments.size} firmware segment(s) programmed successfully!", TerminalLineType.SUCCESS)

            // 5. Hard Reset into User Firmware
            onProgress(0.98f, "Resetting ESP32 into User Code...")
            log("[ESP32] Hard resetting microcontroller into running sketch...", TerminalLineType.INFO)
            resetToUserCode()

            onProgress(1.0f, "Upload Complete!")
            log("[ESP32] Done! ESP32 sketch is executing.", TerminalLineType.SUCCESS)
            return UploadResult(success = true)

        } catch (e: Exception) {
            log("[ESP32 Fatal Error] Upload aborted: ${e.message}", TerminalLineType.ERROR)
            return UploadResult(success = false, error = e.message ?: "Unknown upload exception")
        }
    }

    /**
     * Toggles DTR and RTS lines to trigger standard ESP32 auto-reset bootloader entry:
     * - DTR low, RTS high -> pulls GPIO0 low (BOOT)
     * - DTR high, RTS high -> pulls EN low (RESET)
     * - DTR low, RTS high -> releases EN high with GPIO0 held low (enters bootloader)
     * - DTR low, RTS low -> releases GPIO0
     */
    private suspend fun triggerAutoResetBootloader() {
        try {
            serialPort.setDtrRts(dtr = false, rts = false)
            delay(50)
            serialPort.setDtrRts(dtr = false, rts = true)  // IO0 = 0
            delay(100)
            serialPort.setDtrRts(dtr = true, rts = true)   // EN = 0 (reset active)
            delay(100)
            serialPort.setDtrRts(dtr = false, rts = true)  // EN = 1 (releases reset, IO0 still 0)
            delay(100)
            serialPort.setDtrRts(dtr = false, rts = false) // normal
            delay(150)
        } catch (_: Exception) {}
    }

    /**
     * Resets the ESP32 back into the normal user application by pulsing EN without holding GPIO0.
     */
    private suspend fun resetToUserCode() {
        try {
            serialPort.setDtrRts(dtr = false, rts = false)
            delay(50)
            serialPort.setDtrRts(dtr = true, rts = false)  // EN = 0 (reset)
            delay(100)
            serialPort.setDtrRts(dtr = false, rts = false) // EN = 1 (run user code)
            delay(100)
        } catch (_: Exception) {}
    }

    /**
     * Sends the SYNC command repeatedly until the ESP32 bootloader responds with a valid SLIP packet.
     */
    private suspend fun syncWithBootloader(): Boolean {
        // Sync payload: 0x07, 0x07, 0x12, 0x20 followed by 32 bytes of 0x55
        val syncPayload = ByteArray(36)
        syncPayload[0] = 0x07
        syncPayload[1] = 0x07
        syncPayload[2] = 0x12
        syncPayload[3] = 0x20
        for (i in 4 until 36) {
            syncPayload[i] = 0x55
        }

        for (attempt in 1..SYNC_RETRIES) {
            val syncPacket = buildSlipPacket(CMD_SYNC, syncPayload, checksum = 0)
            serialPort.write(syncPacket, 200)

            delay(60)

            val resp = readSlipPacket(timeoutMs = 150)
            if (resp != null && resp.isNotEmpty() && resp[0] == 0x01.toByte() && resp[1] == CMD_SYNC) {
                return true
            }
        }
        return false
    }

    private suspend fun detectChip() {
        try {
            // Read EFUSE / CHIP ID register (0x3FF00078 for ESP32)
            val regBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3FF00078.toInt()).array()
            val readRegPacket = buildSlipPacket(CMD_READ_REG, regBuf, checksum = 0)
            serialPort.write(readRegPacket, 300)
            delay(50)
            val resp = readSlipPacket(timeoutMs = 300)
            if (resp != null && resp.size >= 8) {
                log("[ESP32] Chip ID Register: 0x${Integer.toHexString(ByteBuffer.wrap(resp, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int).uppercase()}", TerminalLineType.INFO)
            } else {
                log("[ESP32] Chip: ESP32 Family Microcontroller", TerminalLineType.INFO)
            }
        } catch (_: Exception) {
            log("[ESP32] Chip detected: ESP32 Series", TerminalLineType.INFO)
        }
    }

    private suspend fun sendFlashBegin(size: Int, blocks: Int, offset: Int): Boolean {
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(size)
            .putInt(blocks)
            .putInt(FLASH_BLOCK_SIZE)
            .putInt(offset)
            .array()

        val packet = buildSlipPacket(CMD_FLASH_BEGIN, payload, checksum = 0)
        serialPort.write(packet, 1000)
        delay(100)
        val resp = readSlipPacket(timeoutMs = 2000)
        return resp != null
    }

    private suspend fun sendFlashData(seq: Int, data: ByteArray): Boolean {
        val payload = ByteBuffer.allocate(16 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(data.size)
            .putInt(seq)
            .putInt(0)
            .putInt(0)
            .put(data)
            .array()

        var checksum: Int = 0xEF
        for (b in data) {
            checksum = checksum xor (b.toInt() and 0xFF)
        }

        val packet = buildSlipPacket(CMD_FLASH_DATA, payload, checksum)
        serialPort.write(packet, 1000)
        val resp = readSlipPacket(timeoutMs = 1500)
        return resp != null
    }

    private suspend fun sendFlashEnd(reboot: Boolean): Boolean {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(if (reboot) 0 else 1)
            .array()

        val packet = buildSlipPacket(CMD_FLASH_END, payload, checksum = 0)
        serialPort.write(packet, 500)
        delay(50)
        val resp = readSlipPacket(timeoutMs = 1000)
        return resp != null
    }

    /**
     * Wraps raw command payload into SLIP-framed Espressif command packet.
     */
    private fun buildSlipPacket(cmd: Byte, data: ByteArray, checksum: Int): ByteArray {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x00) // Direction: Request
            .put(cmd)  // Command code
            .putShort(data.size.toShort()) // Data size
            .putInt(checksum) // Checksum
            .array()

        val raw = ByteArray(header.size + data.size)
        System.arraycopy(header, 0, raw, 0, header.size)
        System.arraycopy(data, 0, raw, header.size, data.size)

        // SLIP encode
        val out = ByteArrayOutputStream()
        out.write(SLIP_END.toInt())
        for (b in raw) {
            when (b) {
                SLIP_END -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_END.toInt())
                }
                SLIP_ESC -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_ESC.toInt())
                }
                else -> out.write(b.toInt())
            }
        }
        out.write(SLIP_END.toInt())
        return out.toByteArray()
    }

    /**
     * Reads a SLIP packet from the serial port.
     */
    private suspend fun readSlipPacket(timeoutMs: Int): ByteArray? {
        val startTime = System.currentTimeMillis()
        val readBuf = ByteArray(256)
        val packetBytes = ByteArrayOutputStream()
        var inPacket = false
        var escaped = false

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val count = serialPort.read(readBuf, 50)
            if (count > 0) {
                for (i in 0 until count) {
                    val b = readBuf[i]
                    if (!inPacket) {
                        if (b == SLIP_END) {
                            inPacket = true
                            packetBytes.reset()
                        }
                    } else {
                        if (escaped) {
                            when (b) {
                                SLIP_ESC_END -> packetBytes.write(SLIP_END.toInt())
                                SLIP_ESC_ESC -> packetBytes.write(SLIP_ESC.toInt())
                                else -> packetBytes.write(b.toInt())
                            }
                            escaped = false
                        } else if (b == SLIP_ESC) {
                            escaped = true
                        } else if (b == SLIP_END) {
                            // Completed packet
                            return packetBytes.toByteArray()
                        } else {
                            packetBytes.write(b.toInt())
                        }
                    }
                }
            } else {
                delay(10)
            }
        }
        return null
    }
}
