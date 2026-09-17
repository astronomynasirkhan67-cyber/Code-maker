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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
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
import com.example.data.usb.UsbBoardInfo
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoAccentRed
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
    isUploading: Boolean,
    uploadProgress: Float,
    uploadStage: String,
    uploadOutputLogs: List<TerminalLine>,
    onSelectDevice: (UsbBoardInfo) -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onStartUpload: (UsbDevice?) -> Unit,
    onChangeBoardClick: () -> Unit,
    onOpenSerialMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uploadOutputLogs.size) {
        if (uploadOutputLogs.isNotEmpty()) {
            listState.animateScrollToItem(uploadOutputLogs.size - 1)
        }
    }

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
                            text = "Flash compiled sketch to microcontroller via USB OTG",
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

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Target Board Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                            modifier = Modifier.size(40.dp)
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
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 2. USB Device & Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = ArduinoTealLight, modifier = Modifier.size(20.dp))
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                    text = if (isConnected) "DEVICE DETECTED" else "DISCONNECTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isConnected) ArduinoAccentGreen else ArduinoAccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (connectedDevices.isEmpty()) {
                        Text(
                            text = "No USB microcontroller detected. Please plug in your Arduino / ESP board via a USB-C or OTG adapter cable and press Scan.",
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
                                                text = "Port: ${devInfo.deviceName} • VID: 0x${Integer.toHexString(devInfo.vendorId).uppercase()} • PID: 0x${Integer.toHexString(devInfo.productId).uppercase()}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Upload Progress Indicator
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Upload Terminal Output
            Text(
                text = "Upload Output & Handshake Logs",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.height(6.dp))

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
                        text = "Ready to upload. Ensure your board is connected via USB OTG and press 'Upload Firmware'.\n\nThe upload service performs genuine STK500 / AVR or ESP protocol sync before flashing.",
                        color = TerminalText.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
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

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Action Buttons: Upload & Serial Monitor
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
