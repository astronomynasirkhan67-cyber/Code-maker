package com.example.ui.components

import android.hardware.usb.UsbDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.local.entity.BoardEntity
import com.example.data.usb.UsbBoardInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeTopAppBar(
    projectName: String,
    selectedBoard: BoardEntity?,
    connectedUsbDevices: List<UsbBoardInfo>,
    isCompiling: Boolean,
    compileProgress: Float,
    isTerminalExpanded: Boolean,
    onVerifyClick: () -> Unit,
    onUploadClick: () -> Unit,
    onSerialMonitorClick: () -> Unit,
    onToggleTerminal: () -> Unit,
    onSelectBoardClick: () -> Unit,
    onToggleSearch: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Board & Port Selector Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onSelectBoardClick() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasUsb = connectedUsbDevices.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (hasUsb) ArduinoAccentGreen else ArduinoAccentYellow)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedBoard?.name ?: "Select Board"} | ${if (hasUsb) "USB Connected" else "No USB"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ArduinoTealDark,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            actions = {
                // Verify / Compile Action
                IconButton(
                    onClick = onVerifyClick,
                    modifier = Modifier.testTag("verify_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Verify and Compile Code",
                        tint = if (isCompiling) ArduinoAccentYellow else Color.White
                    )
                }

                // Upload Action
                IconButton(
                    onClick = onUploadClick,
                    modifier = Modifier.testTag("upload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Upload to Board",
                        tint = Color.White
                    )
                }

                // Search Action
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier.testTag("search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search and Replace"
                    )
                }

                // Terminal Toggle
                IconButton(
                    onClick = onToggleTerminal,
                    modifier = Modifier.testTag("terminal_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Toggle Terminal Panel",
                        tint = if (isTerminalExpanded) ArduinoAccentGreen else Color.White
                    )
                }

                // Overflow Menu
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Serial Monitor") },
                        onClick = {
                            showMenu = false
                            onSerialMonitorClick()
                        },
                        leadingIcon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Change Board...") },
                        onClick = {
                            showMenu = false
                            onSelectBoardClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                }
            }
        )

        // Compilation progress bar
        AnimatedVisibility(visible = isCompiling) {
            LinearProgressIndicator(
                progress = { compileProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ArduinoAccentGreen,
                trackColor = ArduinoTealDark
            )
        }
    }
}

@Composable
fun TerminalPanel(
    logs: List<TerminalLine>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Auto-scroll when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalBackground,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleExpand() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = ArduinoTealLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Output Console (${logs.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TerminalText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand/Collapse Terminal",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy All
                    IconButton(
                        onClick = {
                            val allText = logs.joinToString("\n") { it.text }
                            clipboardManager.setText(AnnotatedString(allText))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Console Output",
                            tint = TerminalText,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Clear
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Console",
                            tint = TerminalText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Expandable Log Area
            AnimatedVisibility(visible = isExpanded) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    items(logs) { line ->
                        val textColor = when (line.type) {
                            TerminalLineType.INFO -> TerminalInfo
                            TerminalLineType.STDOUT -> TerminalText
                            TerminalLineType.WARNING -> TerminalWarning
                            TerminalLineType.ERROR -> TerminalError
                            TerminalLineType.SUCCESS -> TerminalSuccess
                        }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = textColor,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
