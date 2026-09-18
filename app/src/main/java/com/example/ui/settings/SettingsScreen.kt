package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.compiler.BuildServerHealthResult
import com.example.compiler.LocalToolchainStatus
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoAccentRed
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Map<String, String>,
    packageUrls: List<String> = emptyList(),
    onUpdateSetting: (key: String, value: String) -> Unit,
    onTestBuildServer: (suspend (String) -> BuildServerHealthResult)? = null,
    onCheckLocalToolchain: (() -> LocalToolchainStatus)? = null,
    onClearCache: (() -> Long)? = null,
    onAddPackageUrl: ((String) -> Unit)? = null,
    onRemovePackageUrl: ((String) -> Unit)? = null,
    onUpdateIndexes: (() -> Unit)? = null,
    onOpenBoardsManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Compiler backend ("server" vs "local")
    val compilerBackend = settings["compiler_backend"] ?: "server"

    val currentFontSize = settings["editor_font_size"]?.toIntOrNull() ?: 14
    var fontSizeSlider by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }

    val showLineNumbers = (settings["editor_line_numbers"] ?: "true").toBoolean()
    val verboseCompiler = (settings["compiler_verbose"] ?: "true").toBoolean()
    val usbAutoScan = (settings["usb_auto_scan"] ?: "true").toBoolean()
    val usbDebugLog = (settings["usb_debug_log"] ?: "false").toBoolean()

    var remoteUrl by remember(settings["compiler_remote_url"]) {
        mutableStateOf(settings["compiler_remote_url"] ?: "")
    }

    // Health check state
    var isCheckingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<BuildServerHealthResult?>(null) }

    // Cache clear state
    var cacheClearedBytes by remember { mutableStateOf<Long?>(null) }

    // Dialog state for adding package URL
    var showAddUrlDialog by remember { mutableStateOf(false) }

    // Toolchain status
    val toolchainStatus = remember(onCheckLocalToolchain) {
        onCheckLocalToolchain?.invoke()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = ArduinoTealLight,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Compiler toolchain, Boards Manager indexes, and USB OTG options",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 1. COMPILER SETTINGS
        // ==========================================
        Text(
            text = "Compiler",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Backend Selection
                Text(
                    text = "Compiler Backend",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select how sketch C++ source code is compiled into ESP32 .bin firmware",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = compilerBackend == "server",
                        onClick = { onUpdateSetting("compiler_backend", "server") },
                        label = { Text("Arduino CLI Build Server") },
                        leadingIcon = {
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArduinoTeal,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = compilerBackend == "local",
                        onClick = { onUpdateSetting("compiler_backend", "local") },
                        label = { Text("Local Compiler") },
                        leadingIcon = {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArduinoTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                if (compilerBackend == "local") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ArduinoAccentOrange.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ArduinoAccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESP32 local native compiler (xtensa-esp32-elf-gcc) is unavailable on Android OS. Switch to 'Arduino CLI Build Server' or use precompiled Blink.",
                                fontSize = 11.sp,
                                color = ArduinoAccentOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Build Server URL
                Text(
                    text = "Arduino CLI Build Server URL",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "IP address or domain running 'arduino-cli daemon' or mobile IDE build backend",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = {
                        remoteUrl = it
                        healthResult = null
                    },
                    placeholder = { Text("http://192.168.1.100:8080 or https://build.yourserver.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_server_url_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArduinoTealLight)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            remoteUrl = "http://10.0.2.2:8080"
                            healthResult = null
                        }
                    ) {
                        Text("Default Emulator Host", fontSize = 11.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onUpdateSetting("compiler_remote_url", remoteUrl.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTealLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save URL", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (remoteUrl.isNotBlank() && onTestBuildServer != null) {
                                    isCheckingHealth = true
                                    healthResult = null
                                    coroutineScope.launch {
                                        onUpdateSetting("compiler_remote_url", remoteUrl.trim())
                                        healthResult = onTestBuildServer(remoteUrl.trim())
                                        isCheckingHealth = false
                                    }
                                }
                            },
                            enabled = remoteUrl.isNotBlank() && !isCheckingHealth,
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_build_server_button")
                        ) {
                            if (isCheckingHealth) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pinging...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Connection", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Health Result Card
                healthResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (res.isHealthy) ArduinoAccentGreen.copy(alpha = 0.12f) else ArduinoAccentRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (res.isHealthy) ArduinoAccentGreen else ArduinoAccentRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (res.isHealthy) "Server Online (${res.latencyMs} ms)" else "Server Unreachable",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (res.isHealthy) ArduinoAccentGreen else ArduinoAccentRed
                                )
                            }
                            Text(text = res.message, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            if (res.installedPlatforms.isNotEmpty()) {
                                Text(
                                    text = "Platforms: ${res.installedPlatforms.joinToString(", ")}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toolchain Status & Arduino CLI Version
                Text(
                    text = "Toolchain Status & Versions",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("• Device CPU ABI: ${toolchainStatus?.cpuAbi ?: "arm64-v8a"}", fontSize = 11.sp)
                        Text("• Android API Level: ${toolchainStatus?.androidApi ?: "34"}", fontSize = 11.sp)
                        Text("• Native Xtensa Compiler: Not available in Android APK (Remote compilation active)", fontSize = 11.sp)
                        Text("• Arduino CLI Compatibility: 1.1.0+ (Daemon gRPC & REST)", fontSize = 11.sp)
                        Text("• ESP32 Platform Core: v3.1.1 (Espressif Systems)", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clear Build Cache
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Build Cache", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = if (cacheClearedBytes != null) "Cache cleared ($cacheClearedBytes B freed)" else "Clear sketch compile temp files",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (cacheClearedBytes != null) ArduinoAccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onClearCache?.let { cacheClearedBytes = it() }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Cache", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 2. BOARDS MANAGER SETTINGS
        // ==========================================
        Text(
            text = "Boards Manager",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Package URLs", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Index URLs for board definitions", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showAddUrlDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTealLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage URLs", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onUpdateIndexes?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update Index", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Installed Platforms
                Text("Installed Platforms:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ArduinoAccentGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ArduinoAccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("esp32 by Espressif Systems (v3.1.1)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ArduinoAccentGreen)
                        }
                        Text("Includes ESP32 Dev Module, WROVER, S2, S3, C3, C6 (38 target boards)", fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ArduinoAccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("arduino:avr by Arduino Official (v1.8.6)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ArduinoAccentGreen)
                        }
                        Text("Includes Arduino Uno, Nano, Mega 2560, Leonardo", fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (onOpenBoardsManager != null) {
                    OutlinedButton(
                        onClick = onOpenBoardsManager,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeveloperBoard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Full Boards Manager")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 3. USB OTG SETTINGS
        // ==========================================
        Text(
            text = "USB & Hardware",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Auto Scan Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Auto Scan USB Devices", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Automatically detect and inspect plugged OTG boards", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                    }
                    Switch(
                        checked = usbAutoScan,
                        onCheckedChange = { onUpdateSetting("usb_auto_scan", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // USB Debug Log Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("USB Debug Packet Log", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Output raw SLIP packet bytes and bootloader handshakes", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                    }
                    Switch(
                        checked = usbDebugLog,
                        onCheckedChange = { onUpdateSetting("usb_debug_log", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 4. CODE EDITOR PREFERENCES
        // ==========================================
        Text(
            text = "Code Editor",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Editor Font Size", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("${fontSizeSlider.toInt()} sp", fontWeight = FontWeight.Bold, color = ArduinoTealLight)
                }
                Slider(
                    value = fontSizeSlider,
                    onValueChange = { fontSizeSlider = it },
                    onValueChangeFinished = {
                        onUpdateSetting("editor_font_size", fontSizeSlider.toInt().toString())
                    },
                    valueRange = 10f..24f,
                    steps = 14,
                    colors = SliderDefaults.colors(thumbColor = ArduinoTeal, activeTrackColor = ArduinoTeal)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show Line Numbers", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Switch(
                        checked = showLineNumbers,
                        onCheckedChange = { onUpdateSetting("editor_line_numbers", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Verbose Terminal Logs", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Switch(
                        checked = verboseCompiler,
                        onCheckedChange = { onUpdateSetting("compiler_verbose", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Add URL Dialog
    if (showAddUrlDialog) {
        var inputUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            title = { Text("Board Manager Package URLs") },
            text = {
                Column {
                    Text("Configured URLs:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    packageUrls.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(url, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemovePackageUrl?.invoke(url) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ArduinoAccentRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://.../package_xxx_index.json") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(onClick = {
                        onAddPackageUrl?.invoke("https://espressif.github.io/arduino-esp32/package_esp32_dev_index.json")
                    }) {
                        Text("+ Add Espressif ESP32 Dev Index", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            onAddPackageUrl?.invoke(inputUrl.trim())
                            inputUrl = ""
                        }
                        showAddUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                ) {
                    Text("Done")
                }
            }
        )
    }
}
