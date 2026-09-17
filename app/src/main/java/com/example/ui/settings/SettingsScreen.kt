package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealLight

@Composable
fun SettingsScreen(
    settings: Map<String, String>,
    onUpdateSetting: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val currentFontSize = settings["editor_font_size"]?.toIntOrNull() ?: 14
    var fontSizeSlider by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }

    val showLineNumbers = (settings["editor_line_numbers"] ?: "true").toBoolean()
    val verboseCompiler = (settings["compiler_verbose"] ?: "true").toBoolean()

    var remoteUrl by remember(settings["compiler_remote_url"]) {
        mutableStateOf(settings["compiler_remote_url"] ?: "")
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
                            text = "Connect a local network or cloud arduino-cli daemon to generate binary hex files",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    placeholder = { Text("e.g. http://192.168.1.50:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArduinoTealLight)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onUpdateSetting("compiler_remote_url", remoteUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Server URL")
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
                        Text("Print full build stages and warnings in terminal", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    Switch(
                        checked = verboseCompiler,
                        onCheckedChange = { onUpdateSetting("compiler_verbose", it.toString()) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ArduinoTeal)
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
                            "• The app will prompt for Android USB host permission when a compatible microcontroller (Uno, Nano, Mega, ESP32, RP2040, CH340, CP2102) is detected.\n" +
                            "• STK500 bootloader syncing and CDC-ACM serial streaming operate directly over the USB port.",
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
                    text = "Version 1.2.0 • Architecture: Android Native Kotlin & Jetpack Compose\n" +
                            "Features: Monospace C++ Syntax Engine, STK500 Bootloader, USB Serial Engine, Local Room Database.",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
