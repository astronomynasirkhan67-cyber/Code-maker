package com.example.upload

import android.hardware.usb.UsbDevice
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.local.entity.BoardEntity
import com.example.data.usb.UploadResult as UsbUploadResult
import com.example.data.usb.UsbHardwareManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

sealed class UploadStageState {
    data object Idle : UploadStageState()
    data class InProgress(
        val stage: String,
        val progress: Float,
        val logLine: String
    ) : UploadStageState()
    data class Completed(
        val success: Boolean,
        val message: String,
        val fullLog: String
    ) : UploadStageState()
}

/**
 * Coordinates firmware upload workflow between UI, hardware USB host layer,
 * and board-specific flashing protocols (ESP32 and AVR).
 */
class UploadService(
    private val usbHardwareManager: UsbHardwareManager
) {
    fun performUpload(
        device: UsbDevice?,
        targetBoard: BoardEntity?,
        compiledHexBytes: ByteArray?,
        onTerminalLog: (TerminalLine) -> Unit = {}
    ): Flow<UploadStageState> = flow {
        emit(UploadStageState.InProgress(
            stage = "Initializing Hardware",
            progress = 0.05f,
            logLine = "[Upload Service Initiated]"
        ))

        delay(80)

        if (targetBoard == null) {
            emit(UploadStageState.Completed(
                success = false,
                message = "Target board is not selected. Please select your board in Boards Manager.",
                fullLog = "Error: Target board configuration is null."
            ))
            return@flow
        }

        emit(UploadStageState.InProgress(
            stage = "Target Board: ${targetBoard.name}",
            progress = 0.10f,
            logLine = "Target: ${targetBoard.name} (${targetBoard.fqbn})"
        ))

        if (device == null) {
            emit(UploadStageState.Completed(
                success = false,
                message = "No USB device detected. Please connect your ESP32 or Arduino board using a USB OTG cable.",
                fullLog = """[Hardware Check Failed]
No active USB serial peripheral detected on Android USB Host.
Requirements for USB upload:
1. Connect board to phone/tablet via a USB OTG adapter.
2. Grant USB Host permissions when prompted.
3. Verify the USB cable supports data transfer."""
            ))
            return@flow
        }

        if (compiledHexBytes == null || compiledHexBytes.isEmpty()) {
            emit(UploadStageState.Completed(
                success = false,
                message = "No compiled binary available. Please compile your sketch first.",
                fullLog = "Error: Sketch must be compiled before uploading."
            ))
            return@flow
        }

        val progressChannel = Channel<Pair<Float, String>>(Channel.UNLIMITED)

        coroutineScope {
            val uploadJob = launch {
                val result = usbHardwareManager.uploadFirmware(
                    device = device,
                    targetFqbn = targetBoard.fqbn,
                    compiledBytes = compiledHexBytes,
                    onProgress = { p, s ->
                        progressChannel.trySend(p to s)
                    },
                    onTerminalLog = { line ->
                        onTerminalLog(line)
                        progressChannel.trySend(-1f to line.text)
                    }
                )
                progressChannel.close(UploadResultException(result))
            }

            try {
                for ((progress, stage) in progressChannel) {
                    if (progress >= 0f) {
                        emit(UploadStageState.InProgress(
                            stage = stage,
                            progress = progress,
                            logLine = stage
                        ))
                    } else {
                        emit(UploadStageState.InProgress(
                            stage = "Flashing",
                            progress = 0.5f,
                            logLine = stage
                        ))
                    }
                }
            } catch (e: UploadResultException) {
                when (val res = e.result) {
                    is UsbUploadResult.Success -> {
                        emit(UploadStageState.Completed(
                            success = true,
                            message = "Firmware uploaded successfully (${res.bytesUploaded} bytes written).",
                            fullLog = res.log
                        ))
                    }
                    is UsbUploadResult.Failed -> {
                        emit(UploadStageState.Completed(
                            success = false,
                            message = res.error,
                            fullLog = res.log
                        ))
                    }
                }
            } catch (e: Exception) {
                emit(UploadStageState.Completed(
                    success = false,
                    message = e.message ?: "Upload aborted",
                    fullLog = "Exception: ${e.message}"
                ))
            }
        }
    }

    private class UploadResultException(val result: UsbUploadResult) : Exception()
}
