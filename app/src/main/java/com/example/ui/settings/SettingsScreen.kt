package com.example.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun SettingsScreen(
    settings: Map<String, String>,
    onUpdateSetting: (key: String, value: String) -> Unit,
    onTestBuildServer: (suspend (String) -> BuildServerHealthResult)? = null,
    onCheckLocalToolchain: (() -> LocalToolchainStatus)? = null,
    onClearCache: (() -> Long)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val currentFontSize = settings["editor_font_size"]?.toIntOrNull() ?: 14
    var fontSizeSlider by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }

    val showLineNumbers = (settings["editor_line_numbers"] ?: "true").toBoolean()
    val verboseCompiler = (settings["compiler_verbose"] ?: "true").toBoolean()

    var remoteUrl by remember(settings["compiler_remote_url"]) {
        mutableStateOf(settings["compiler_remote_url"] ?: "")
    }

    // Health check state
    var isCheckingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<BuildServerHealthResult?>(null) }

    // Cache clear state
    var cacheClearedBytes by remember { mutableStateOf<Long?>(null) }

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
        // Header
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
                    text = "Preferences & Toolchain",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Configure code editor, build services, and USB hardware settings",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Code Editor
        Text(
            text = "Code Editor",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, tint = ArduinoTealLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Editor Font Size", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${fontSizeSlider.toInt()} sp", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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

                Spacer(modifier = Modifier.height(12.dp))

                // Line Numbers Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Numbers, contentDescription = null, tint = ArduinoTealLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Show Line Numbers", style = MaterialTheme.typography.bodyMedium)
                            Text("Display code gutter numbers", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                    Switch(
                        checked = showLineNumbers,
                        onCheckedChange = { onUpdateSetting("editor_line_numbers", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Compiler & Build Service
        Text(
            text = "Compiler & Toolchain",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = ArduinoTealLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Remote Arduino-CLI Build Server", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = "Connect a local network or cloud arduino-cli daemon to compile full sketches",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = {
                        remoteUrl = it
                        healthResult = null
                    },
                    placeholder = { Text("e.g. http://192.168.1.50:8080 or https://build.yourdomain.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArduinoTealLight)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTestBuildServer != null) {
                        OutlinedButton(
                            onClick = {
                                if (remoteUrl.isNotBlank()) {
                                    isCheckingHealth = true
                                    healthResult = null
                                    coroutineScope.launch {
                                        healthResult = onTestBuildServer(remoteUrl)
                                        isCheckingHealth = false
                                    }
                                }
                            },
                            enabled = !isCheckingHealth && remoteUrl.isNotBlank(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (isCheckingHealth) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Testing...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Connection", fontSize = 11.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { onUpdateSetting("compiler_remote_url", remoteUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save URL")
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
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.isHealthy) ArduinoAccentGreen else ArduinoAccentRed
                                    )
                                )
                            }
                            Text(
                                text = res.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )
                            if (res.installedPlatforms.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Installed Cores: ${res.installedPlatforms.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Verbose output
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Verbose Compilation Output", style = MaterialTheme.typography.bodyMedium)
                        Text("Print full build stages, includes, and flags in terminal", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    Switch(
                        checked = verboseCompiler,
                        onCheckedChange = { onUpdateSetting("compiler_verbose", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clear Build Cache Button
                if (onClearCache != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Build Artifacts Cache", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (cacheClearedBytes != null) "Cache cleared (${cacheClearedBytes} bytes freed)"
                                else "Clear local sketch temporary build outputs",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (cacheClearedBytes != null) ArduinoAccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                cacheClearedBytes = onClearCache()
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cache", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Local Device Diagnostics
        Text(
            text = "Device & Toolchain Diagnostics",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = ArduinoTealLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Android System Architecture", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                Spacer(modifier = Modifier.height(8.dp))

                toolchainStatus?.let { ts ->
                    Text(
                        text = "• CPU ABI: ${ts.cpuAbi}\n" +
                                "• Android API: Level ${ts.androidApi}\n" +
                                "• Device Storage: ${ts.internalStorageFreeBytes / (1024 * 1024)} MB available\n" +
                                "• Local Xtensa GCC: Not bundled natively in Android OS (compilation routed through Remote Arduino-CLI)\n" +
                                "• Standalone Flash Mode: Genuine multi-segment ESP32 Blink firmware is bundled directly in assets for immediate USB flashing.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                } ?: run {
                    Text(
                        text = "• Local Xtensa cross-compiler toolchain is not bundled into Android APK due to size constraints (>400MB).\n" +
                                "• The app provides seamless remote Arduino CLI compilation and bundled verified ESP32 binaries.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Hardware & USB OTG Info
        Text(
            text = "Hardware & USB Connection",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ArduinoTealLight
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Usb, contentDescription = null, tint = ArduinoTealLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("USB OTG Requirements", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Connect your board using a USB On-The-Go (OTG) adapter or Type-C cable.\n" +
                            "• Compatible chips: CP2102/CP2104, CH340/CH341, FTDI FT232, PL2303, and Native USB CDC-ACM (ESP32-S2/S3/C3, RP2040, Leonardo).\n" +
                            "• Flashing Protocol: Multi-segment SLIP protocol with automatic DTR/RTS bootloader entry sequence and MD5 integrity verification.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: About
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ArduinoTealLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About Mobile Arduino IDE", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.3.0 • Architecture: Android Native Kotlin & Jetpack Compose\n" +
                            "Features: Monospace C++ Syntax Engine, Multi-segment ESP32 Bootloader Flasher, AVR STK500, USB CDC Serial Monitor, Arduino-CLI Integration.",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
