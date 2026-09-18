package com.example.ui.upload

import android.hardware.usb.UsbDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.local.entity.BoardEntity
import com.example.data.model.IdeWorkflowState
import com.example.data.usb.UsbBoardInfo
import com.example.firmware.FirmwarePackage
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

@OptIn(ExperimentalLayoutApi::class)
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
    onNavigateToEditor: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToBoards: (() -> Unit)? = null,
    onLoadManualFirmware: ((FirmwarePackage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isConsoleExpanded by remember { mutableStateOf(false) }
    var showManualFlashDialog by remember { mutableStateOf(false) }

    val isTargetEsp32 = selectedBoard?.mcu?.contains("esp32", ignoreCase = true) == true ||
            selectedBoard?.fqbn?.contains("esp32", ignoreCase = true) == true

    val hasUsb = connectedDevices.isNotEmpty()
    val activeDevice = selectedDevice ?: connectedDevices.firstOrNull()

    // State-aware button logic
    val isCompiling = workflowState == IdeWorkflowState.COMPILING
    val canCompile = !isCompiling && !isUploading
    val hasFirmware = hasCompiledBinary || activeFirmwarePackage != null
    val canUpload = !isCompiling && !isUploading && hasFirmware && hasUsb && (activeDevice?.hasPermission == true)

    val logsListState = rememberLazyListState()
    LaunchedEffect(uploadOutputLogs.size) {
        if (uploadOutputLogs.isNotEmpty()) {
            logsListState.animateScrollToItem(uploadOutputLogs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Firmware Upload",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Compile, verify, and flash sketches over USB OTG",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshDevices,
                        modifier = Modifier.testTag("refresh_usb_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan USB Devices", tint = ArduinoTealLight)
                    }
                }
            }

            // 1. Target Board Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Board",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            OutlinedButton(
                                onClick = onChangeBoardClick,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("change_board_button")
                            ) {
                                Text("Change Board", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ArduinoTeal.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = ArduinoTealLight, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = selectedBoard?.name ?: "No Board Selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = selectedBoard?.fqbn ?: "esp32:esp32:esp32",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ArduinoTealLight,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. USB OTG Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "USB OTG",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Button(
                                onClick = onRefreshDevices,
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("scan_usb_button")
                            ) {
                                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan USB", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (connectedDevices.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ArduinoAccentYellow)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Disconnected",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = ArduinoAccentYellow)
                                    )
                                    Text(
                                        text = "No USB device detected. Connect ESP32 via USB OTG cable.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    )
                                }
                            }
                        } else {
                            connectedDevices.forEach { devInfo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (devInfo.hasPermission) ArduinoAccentGreen else ArduinoAccentOrange)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${devInfo.chipType.label} (VID: 0x${Integer.toHexString(devInfo.vendorId).uppercase()})",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = if (devInfo.hasPermission) "Connected & Permission Granted" else "Permission Required",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (devInfo.hasPermission) ArduinoAccentGreen else ArduinoAccentOrange,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }

                                    if (!devInfo.hasPermission) {
                                        Button(
                                            onClick = { onRequestPermission(devInfo.device) },
                                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentOrange),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Grant Permission", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Specific Error Differentiation Banners
            when (workflowState) {
                IdeWorkflowState.SYNTAX_ERROR -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ArduinoAccentRed.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ArduinoAccentRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Compilation Halted: Syntax Error", fontWeight = FontWeight.Bold, color = ArduinoAccentRed)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Static syntax verification found errors in the sketch code. Review terminal logs for file and line details.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onNavigateToEditor?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Go to Editor to Fix Errors", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                IdeWorkflowState.COMPILER_MISSING -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ArduinoAccentYellow.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = ArduinoAccentYellow, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Compiler Toolchain Not Configured", fontWeight = FontWeight.Bold, color = ArduinoAccentYellow)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To produce genuine ESP32 binaries (.bin), configure an Arduino CLI Build Server URL in Settings, or use Test Blink for immediate USB OTG testing.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onNavigateToSettings?.invoke() },
                                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Settings", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onLoadSampleBlink(true) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Load Test Blink", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                IdeWorkflowState.CORE_MISSING -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ArduinoAccentRed.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ArduinoAccentRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Platform Core Missing", fontWeight = FontWeight.Bold, color = ArduinoAccentRed)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "The target board's platform core is not installed. Open Boards Manager to download and install it.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onNavigateToBoards?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.DeveloperBoard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Boards Manager", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                else -> {}
            }

            // 4. Build & Compile Action Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Build",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onVerifyClick,
                            enabled = canCompile,
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("compile_button")
                        ) {
                            if (isCompiling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compiling...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compile Sketch", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Firmware Status & Flash Upload Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Firmware Flash",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when {
                                    isUploading -> ArduinoTeal.copy(alpha = 0.15f)
                                    workflowState == IdeWorkflowState.UPLOAD_SUCCESS -> ArduinoAccentGreen.copy(alpha = 0.15f)
                                    hasFirmware -> ArduinoAccentGreen.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when {
                                        isUploading -> "Flashing: $uploadStage"
                                        workflowState == IdeWorkflowState.UPLOAD_SUCCESS -> "Flash Succeeded"
                                        hasFirmware -> "Ready to Flash"
                                        else -> "Compilation Required"
                                    },
                                    color = when {
                                        isUploading -> ArduinoTealLight
                                        workflowState == IdeWorkflowState.UPLOAD_SUCCESS -> ArduinoAccentGreen
                                        hasFirmware -> ArduinoAccentGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (isUploading) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = ArduinoAccentGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$uploadStage (${(uploadProgress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onStartUpload(activeDevice?.device) },
                                enabled = canUpload,
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_flash_button")
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Flash Board", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showManualFlashDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("manual_flash_button")
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Manual Flash", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 6. Test Blink Card (Precompiled Genuine ESP32 Firmware)
            item {
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
                            Column {
                                Text(
                                    text = "Test Blink Firmware",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Board: ESP32 Dev Module • Firmware: Precompiled (4 segments)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                )
                            }

                            Button(
                                onClick = { onLoadSampleBlink(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = ArduinoTealLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("load_test_blink_button")
                            ) {
                                Text("Load Test Blink", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 7. Firmware Artifacts Breakdown Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Firmware Package",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            if (activeFirmwarePackage != null && onExportFirmwareZip != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = onExportFirmwareZip,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export ZIP", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (activeFirmwarePackage == null) {
                            Text(
                                text = "No firmware generated yet. Compile your sketch or load Test Blink to inspect binary segments.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        } else {
                            Text(
                                text = "Total Size: ${activeFirmwarePackage.formattedTotalSize} (${activeFirmwarePackage.binaries.size} partitions)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = ArduinoTealLight)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            activeFirmwarePackage.binaries.forEach { bin ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bin.filename,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Offset: ${bin.flashAddress} • SHA: ${bin.sha256.take(12)}...",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${bin.size} B",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Console & Log Viewer (Collapsible)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = TerminalBackground)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isConsoleExpanded = !isConsoleExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Console Logs (${uploadOutputLogs.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            IconButton(
                                onClick = { isConsoleExpanded = !isConsoleExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (isConsoleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }

                        if (isConsoleExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                state = logsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                items(uploadOutputLogs) { line ->
                                    Text(
                                        text = line.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = when (line.type) {
                                            TerminalLineType.ERROR -> TerminalError
                                            TerminalLineType.WARNING -> TerminalWarning
                                            TerminalLineType.SUCCESS -> TerminalSuccess
                                            TerminalLineType.INFO -> TerminalInfo
                                            else -> TerminalText
                                        },
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Manual Flash Confirmation Dialog
    if (showManualFlashDialog) {
        ManualFlashDialog(
            activeFirmwarePackage = activeFirmwarePackage,
            onConfirmFlash = { pkg ->
                onLoadManualFirmware?.invoke(pkg)
                showManualFlashDialog = false
                onStartUpload(activeDevice?.device)
            },
            onDismiss = { showManualFlashDialog = false }
        )
    }
}

@Composable
private fun ManualFlashDialog(
    activeFirmwarePackage: FirmwarePackage?,
    onConfirmFlash: (FirmwarePackage) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Firmware Flash") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Verify firmware partition offsets and SHA-256 before writing to ESP32 flash memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (activeFirmwarePackage != null) {
                    Text(
                        text = "Board: ${activeFirmwarePackage.boardName} (${activeFirmwarePackage.formattedTotalSize})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ArduinoTealLight)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(activeFirmwarePackage.binaries) { b ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "• ${b.filename} → ${b.flashAddress}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "  Size: ${b.size} B | SHA: ${b.sha256.take(16)}...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No active firmware package loaded. Tap 'Load Test Blink' first to load precompiled ESP32 Blink firmware.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArduinoAccentYellow
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activeFirmwarePackage != null) {
                        onConfirmFlash(activeFirmwarePackage)
                    }
                },
                enabled = activeFirmwarePackage != null,
                colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentGreen)
            ) {
                Text("Confirm & Flash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
