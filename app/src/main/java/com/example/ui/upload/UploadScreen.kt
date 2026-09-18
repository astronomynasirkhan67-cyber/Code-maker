package com.example.ui.upload

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Memory
import com.example.firmware.FirmwarePackage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.local.entity.BoardEntity
import com.example.data.model.IdeWorkflowState
import com.example.data.usb.UsbBoardInfo
import com.example.data.usb.UsbChipType
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoAccentRed
import com.example.ui.theme.ArduinoAccentYellow
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalError
import com.example.ui.theme.TerminalInfo
import com.example.ui.theme.TerminalSuccess
import com.example.ui.theme.TerminalText
import com.example.ui.theme.TerminalWarning

@Composable
fun UploadScreen(
    selectedBoard: BoardEntity?,
    connectedDevices: List<UsbBoardInfo>,
    selectedDevice: UsbBoardInfo?,
    workflowState: IdeWorkflowState,
    hasCompiledBinary: Boolean,
    activeFirmwarePackage: FirmwarePackage? = null,
    isUploading: Boolean,
    uploadProgress: Float,
    uploadStage: String,
    uploadOutputLogs: List<TerminalLine>,
    onSelectDevice: (UsbBoardInfo) -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onVerifyClick: () -> Unit,
    onLoadSampleBlink: (Boolean) -> Unit,
    onStartUpload: (UsbDevice?) -> Unit,
    onChangeBoardClick: () -> Unit,
    onOpenSerialMonitor: () -> Unit,
    onExportFirmwareZip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uploadOutputLogs.size) {
        if (uploadOutputLogs.isNotEmpty()) {
            listState.animateScrollToItem(uploadOutputLogs.size - 1)
        }
    }

    val isTargetEsp32 = selectedBoard?.mcu?.contains("esp32", ignoreCase = true) == true ||
            selectedBoard?.fqbn?.contains("esp32", ignoreCase = true) == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = ArduinoTealLight,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Firmware Upload",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Flash firmware directly over USB OTG to ESP32 / Arduino",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.testTag("refresh_usb_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan USB Devices")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Workflow State Tracker Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (workflowState) {
                    IdeWorkflowState.UPLOAD_SUCCESS -> ArduinoAccentGreen.copy(alpha = 0.15f)
                    IdeWorkflowState.UPLOAD_FAILED, IdeWorkflowState.COMPILE_FAILED -> ArduinoAccentRed.copy(alpha = 0.15f)
                    IdeWorkflowState.UPLOADING, IdeWorkflowState.COMPILING -> ArduinoTeal.copy(alpha = 0.15f)
                    IdeWorkflowState.USB_PERMISSION_REQUIRED -> ArduinoAccentOrange.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (workflowState) {
                        IdeWorkflowState.UPLOAD_SUCCESS -> ArduinoAccentGreen
                        IdeWorkflowState.UPLOAD_FAILED, IdeWorkflowState.COMPILE_FAILED -> ArduinoAccentRed
                        IdeWorkflowState.UPLOADING, IdeWorkflowState.COMPILING -> ArduinoTealLight
                        IdeWorkflowState.USB_PERMISSION_REQUIRED -> ArduinoAccentOrange
                        else -> ArduinoAccentYellow
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = workflowState.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = statusColor)
                        )
                        Text(
                            text = workflowState.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Target Board Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ArduinoTeal.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = ArduinoTealLight)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedBoard?.name ?: "No Board Selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "FQBN: ${selectedBoard?.fqbn ?: "None"} • MCU: ${selectedBoard?.mcu ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onChangeBoardClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. USB Device & Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = ArduinoTealLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "USB OTG Port & Device",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Connection Status Pill
                        val isConnected = connectedDevices.isNotEmpty()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isConnected) ArduinoAccentGreen.copy(alpha = 0.15f) else ArduinoAccentRed.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) ArduinoAccentGreen else ArduinoAccentRed)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isConnected) "${connectedDevices.size} DEVICE(S)" else "DISCONNECTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isConnected) ArduinoAccentGreen else ArduinoAccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (connectedDevices.isEmpty()) {
                        Text(
                            text = "No USB microcontroller detected. Plug in your ESP32 or Arduino via a USB-C or OTG adapter cable and tap Scan.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            connectedDevices.forEach { devInfo ->
                                val isSelected = selectedDevice?.device?.deviceId == devInfo.device.deviceId ||
                                        (selectedDevice == null && connectedDevices.first() == devInfo)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) ArduinoTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectDevice(devInfo) }
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) ArduinoTealLight else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = devInfo.description,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "Chip: ${devInfo.chipType.label} • Port: ${devInfo.deviceName}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = ArduinoTealLight,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp
                                                )
                                            )
                                            Text(
                                                text = "VID: 0x${Integer.toHexString(devInfo.vendorId).uppercase()} • PID: 0x${Integer.toHexString(devInfo.productId).uppercase()}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            )
                                        }

                                        if (!devInfo.hasPermission) {
                                            Button(
                                                onClick = { onRequestPermission(devInfo.device) },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentOrange)
                                            ) {
                                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Grant Permission", fontSize = 11.sp)
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ArduinoAccentGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Authorized",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = ArduinoAccentGreen, fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Firmware Compilation Status & Test Helpers
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val hasFirmware = hasCompiledBinary || activeFirmwarePackage != null
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasFirmware) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasFirmware) ArduinoAccentGreen else ArduinoAccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeFirmwarePackage != null) "ESP32 Firmware Package Ready"
                                else if (hasCompiledBinary) "Firmware Binary Ready"
                                else "Compilation Required",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasFirmware) ArduinoAccentGreen else ArduinoAccentOrange
                                )
                            )
                        }

                        // Fast helper buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onLoadSampleBlink(isTargetEsp32) },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Load Test Blink", fontSize = 11.sp)
                            }

                            Button(
                                onClick = onVerifyClick,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify/Compile", fontSize = 11.sp)
                            }
                        }
                    }

                    if (activeFirmwarePackage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ArduinoTeal.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = ArduinoTealLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${activeFirmwarePackage.targetChip} • ${activeFirmwarePackage.formattedTotalSize} (${activeFirmwarePackage.binaries.size} segments)",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = ArduinoTealLight
                                            )
                                        )
                                    }

                                    if (onExportFirmwareZip != null) {
                                        OutlinedButton(
                                            onClick = onExportFirmwareZip,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export ZIP", fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                activeFirmwarePackage.binaries.forEach { seg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${seg.flashAddress}: ${seg.filename}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                        Text(
                                            text = "${seg.size} bytes",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!hasCompiledBinary) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Upload is gated: You must compile your sketch first to generate a valid binary, or tap 'Load Test Blink' to test flasher hardware immediately.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        )
                    }
                }
            }

            // ESP32 Manual Bootloader Hint if ESP32 selected
            if (isTargetEsp32) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ArduinoTealLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ESP32 Tip: If auto-reset fails, hold BOOT (IO0), tap EN (RST) once, release BOOT, then tap Upload.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Upload Progress Indicator
            if (isUploading || uploadProgress > 0f) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uploadStage.ifEmpty { "Uploading..." },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = ArduinoTealLight)
                        )
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ArduinoTealLight,
                        trackColor = ArduinoTealDark
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5. Upload Terminal Output
            Text(
                text = "Upload Output & Handshake Logs",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalBackground)
                    .padding(10.dp)
            ) {
                if (uploadOutputLogs.isEmpty()) {
                    Text(
                        text = "Ready to upload. Ensure your board is connected via USB OTG and press 'Upload Firmware'.\n\nSupports ESP32 ROM bootloader (SLIP protocol) and Arduino AVR STK500v1.",
                        color = TerminalText.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uploadOutputLogs) { log ->
                            val color = when (log.type) {
                                TerminalLineType.ERROR -> TerminalError
                                TerminalLineType.WARNING -> TerminalWarning
                                TerminalLineType.SUCCESS -> TerminalSuccess
                                TerminalLineType.INFO -> TerminalInfo
                                TerminalLineType.STDOUT -> TerminalText
                            }
                            Text(
                                text = log.text,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Action Buttons: Upload & Serial Monitor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val activeDev = selectedDevice?.device ?: connectedDevices.firstOrNull()?.device
                        onStartUpload(activeDev)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_upload_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                    enabled = !isUploading
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isUploading) "Uploading..." else "Upload Firmware")
                }

                OutlinedButton(
                    onClick = onOpenSerialMonitor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_serial_monitor_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, tint = ArduinoTealLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Serial Monitor")
                }
            }
        }
    }
}
