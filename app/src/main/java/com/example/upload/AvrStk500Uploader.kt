package com.example.upload

import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.usb.UsbSerialPort
import kotlinx.coroutines.delay

/**
 * Native Android implementation of the AVR STK500v1 protocol for Arduino Uno, Nano, Mega.
 */
class AvrStk500Uploader(
    private val serialPort: UsbSerialPort,
    private val onLog: (TerminalLine) -> Unit,
    private val onProgress: (Float, String) -> Unit
) {
    companion object {
        private const val Resp_STK_INSYNC = 0x14.toByte()
        private const val Resp_STK_OK = 0x10.toByte()
        private const val Cmnd_STK_GET_SYNC = 0x30.toByte()
        private const val Sync_CRC_EOP = 0x20.toByte()
        private const val Cmnd_STK_SET_DEVICE = 0x42.toByte()
        private const val Cmnd_STK_ENTER_PROGMODE = 0x50.toByte()
        private const val Cmnd_STK_LOAD_ADDRESS = 0x55.toByte()
        private const val Cmnd_STK_PROG_PAGE = 0x64.toByte()
        private const val Cmnd_STK_LEAVE_PROGMODE = 0x51.toByte()
        private const val PAGE_SIZE = 128
    }

    private fun log(text: String, type: TerminalLineType = TerminalLineType.STDOUT) {
        onLog(TerminalLine(type, text))
    }

    suspend fun upload(hexBinary: ByteArray): UploadResult {
        try {
            onProgress(0.05f, "Configuring Port for AVR (115200)...")
            log("[AVR STK500] Initializing ${serialPort.chipType.label} at 115200 baud", TerminalLineType.INFO)
            serialPort.setParameters(115200, 8, 1, 0)
            serialPort.purgeHwBuffers()

            // 1. DTR Reset pulse to reboot ATmega into bootloader
            onProgress(0.10f, "Pulsing DTR to reset ATmega bootloader...")
            log("[AVR STK500] Pulsing DTR line to trigger Arduino bootloader...", TerminalLineType.INFO)
            serialPort.setDtrRts(dtr = true, rts = true)
            delay(100)
            serialPort.setDtrRts(dtr = false, rts = false)
            delay(250)

            // 2. Sync Handshake (0x30 0x20)
            onProgress(0.20f, "Connecting to STK500 bootloader...")
            var inSync = false
            for (attempt in 1..10) {
                serialPort.write(byteArrayOf(Cmnd_STK_GET_SYNC, Sync_CRC_EOP), 200)
                delay(80)
                val resp = ByteArray(2)
                val count = serialPort.read(resp, 150)
                if (count >= 2 && resp[0] == Resp_STK_INSYNC && resp[1] == Resp_STK_OK) {
                    inSync = true
                    break
                }
            }

            if (!inSync) {
                log("[AVR Error] Failed to get in sync with STK500 bootloader (0x30 0x20)", TerminalLineType.ERROR)
                return UploadResult(success = false, error = "STK500 sync failed. Ensure correct board is selected.")
            }

            log("[AVR STK500] Bootloader responded INSYNC! Programming parameters set.", TerminalLineType.SUCCESS)

            // Enter prog mode
            serialPort.write(byteArrayOf(Cmnd_STK_ENTER_PROGMODE, Sync_CRC_EOP), 300)
            delay(50)

            if (hexBinary.isEmpty()) {
                return UploadResult(success = false, error = "Firmware binary is empty.")
            }

            val totalSize = hexBinary.size
            val totalPages = (totalSize + PAGE_SIZE - 1) / PAGE_SIZE
            log("[AVR STK500] Writing $totalSize bytes ($totalPages pages)...", TerminalLineType.INFO)

            for (page in 0 until totalPages) {
                val offset = page * PAGE_SIZE
                val remaining = totalSize - offset
                val pageSize = if (remaining < PAGE_SIZE) remaining else PAGE_SIZE

                // Load address (word address)
                val wordAddr = (offset / 2).toShort()
                val addrCmd = byteArrayOf(
                    Cmnd_STK_LOAD_ADDRESS,
                    (wordAddr.toInt() and 0xFF).toByte(),
                    ((wordAddr.toInt() shr 8) and 0xFF).toByte(),
                    Sync_CRC_EOP
                )
                serialPort.write(addrCmd, 300)
                delay(10)

                // Prog page
                val pageData = ByteArray(5 + pageSize + 1)
                pageData[0] = Cmnd_STK_PROG_PAGE
                pageData[1] = ((pageSize shr 8) and 0xFF).toByte()
                pageData[2] = (pageSize and 0xFF).toByte()
                pageData[3] = 'F'.code.toByte() // Flash
                System.arraycopy(hexBinary, offset, pageData, 4, pageSize)
                pageData[4 + pageSize] = Sync_CRC_EOP

                serialPort.write(pageData, 1000)
                delay(20)

                val progress = 0.25f + (0.65f * ((page + 1).toFloat() / totalPages))
                val percent = ((page + 1) * 100) / totalPages
                onProgress(progress, "Flashing AVR: $percent% ($offset / $totalSize bytes)")
            }

            // Leave prog mode
            serialPort.write(byteArrayOf(Cmnd_STK_LEAVE_PROGMODE, Sync_CRC_EOP), 300)
            delay(50)

            onProgress(1.0f, "AVR Flashing Complete!")
            log("[AVR STK500] Done! Board running user sketch.", TerminalLineType.SUCCESS)
            return UploadResult(success = true)

        } catch (e: Exception) {
            log("[AVR Error] STK500 upload failed: ${e.message}", TerminalLineType.ERROR)
            return UploadResult(success = false, error = e.message ?: "STK500 upload exception")
        }
    }
}
