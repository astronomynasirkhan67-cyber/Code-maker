package com.example.ui.serial

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.usb.LineEnding
import com.example.data.usb.SerialConnectionState
import com.example.data.usb.SerialMessage
import com.example.data.usb.UsbBoardInfo
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoAccentRed
import com.example.ui.theme.ArduinoAccentYellow
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealLight
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalInfo
import com.example.ui.theme.TerminalSuccess
import com.example.ui.theme.TerminalText

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SerialMonitorScreen(
    connectedDevices: List<UsbBoardInfo>,
    connectionState: SerialConnectionState,
    messages: List<SerialMessage>,
    inputText: String,
    selectedBaudRate: Int,
    selectedLineEnding: LineEnding,
    autoScroll: Boolean,
    showTimestamps: Boolean,
    hexMode: Boolean,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onBaudRateSelect: (Int) -> Unit,
    onLineEndingSelect: (LineEnding) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleTimestamps: () -> Unit,
    onToggleHexMode: () -> Unit,
    onConnectDevice: (UsbDevice) -> Unit,
    onDisconnectDevice: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    var baudMenuExpanded by remember { mutableStateOf(false) }
    var lineEndingMenuExpanded by remember { mutableStateOf(false) }

    val baudRates = listOf(9600, 19200, 38400, 57600, 115200)

    LaunchedEffect(messages.size, autoScroll) {
        if (autoScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = null,
                    tint = ArduinoTealLight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Serial Monitor",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Real-time USB OTG communication with hardware board",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            IconButton(onClick = onRefreshDevices) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan USB Devices")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardware USB Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isConnected = connectionState is SerialConnectionState.Connected
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) ArduinoAccentGreen else if (connectedDevices.isNotEmpty()) ArduinoAccentYellow else ArduinoAccentRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionState) {
                                is SerialConnectionState.Connected -> "Connected to ${connectionState.deviceName}"
                                is SerialConnectionState.Connecting -> "Connecting to ${connectionState.deviceName}..."
                                is SerialConnectionState.Error -> "Connection Error: ${connectionState.message}"
                                SerialConnectionState.Disconnected -> if (connectedDevices.isNotEmpty()) "${connectedDevices.size} USB device(s) found" else "No USB OTG device detected"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (connectionState is SerialConnectionState.Connected) {
                        Button(
                            onClick = onDisconnectDevice,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Disconnect", fontSize = 11.sp)
                        }
                    }
                }

                // If devices found and not connected, show connect options
                if (connectionState !is SerialConnectionState.Connected && connectedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    connectedDevices.forEach { devInfo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = devInfo.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "VID: 0x${Integer.toHexString(devInfo.vendorId).uppercase()} | PID: 0x${Integer.toHexString(devInfo.productId).uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            if (!devInfo.hasPermission) {
                                Button(
                                    onClick = { onRequestPermission(devInfo.device) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentOrange)
                                ) {
                                    Text("Grant USB", fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = { onConnectDevice(devInfo.device) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                                ) {
                                    Text("Open Port", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else if (connectedDevices.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect an Arduino, ESP32, or RP2040 board using a USB OTG adapter to send and receive real hardware telemetry.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Toolbar: Baud Rate, Line Ending, Options
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Baud Rate selector
            Box {
                OutlinedButton(onClick = { baudMenuExpanded = true }) {
                    Text("$selectedBaudRate baud", fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = baudMenuExpanded,
                    onDismissRequest = { baudMenuExpanded = false }
                ) {
                    baudRates.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("$rate baud") },
                            onClick = {
                                onBaudRateSelect(rate)
                                baudMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Line Ending selector
            Box {
                OutlinedButton(onClick = { lineEndingMenuExpanded = true }) {
                    Text(selectedLineEnding.label, fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = lineEndingMenuExpanded,
                    onDismissRequest = { lineEndingMenuExpanded = false }
                ) {
                    LineEnding.values().forEach { ending ->
                        DropdownMenuItem(
                            text = { Text(ending.label) },
                            onClick = {
                                onLineEndingSelect(ending)
                                lineEndingMenuExpanded = false
                            }
                        )
                    }
                }
            }

            FilterChip(
                selected = autoScroll,
                onClick = onToggleAutoScroll,
                label = { Text("Autoscroll", fontSize = 11.sp) }
            )

            FilterChip(
                selected = showTimestamps,
                onClick = onToggleTimestamps,
                label = { Text("Timestamps", fontSize = 11.sp) }
            )

            IconButton(onClick = onClearMessages, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Serial Console Output Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = TerminalBackground,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Serial Monitor Output Empty",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                        Text(
                            text = "Incoming serial data from hardware will stream here automatically.",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.3f))
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(messages) { msg ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            if (showTimestamps) {
                                Text(
                                    text = "[${msg.timestamp}] ",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                )
                            }
                            Text(
                                text = if (msg.isOutgoing) ">> ${msg.text}" else msg.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (msg.isOutgoing) ArduinoAccentOrange else TerminalText
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Serial Send Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Send serial command (e.g. AT or '1')...", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("serial_input_field"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArduinoTealLight
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSendMessage,
                enabled = inputText.isNotEmpty() && connectionState is SerialConnectionState.Connected,
                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                modifier = Modifier.testTag("serial_send_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
