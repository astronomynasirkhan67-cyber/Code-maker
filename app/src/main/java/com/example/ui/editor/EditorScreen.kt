package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProjectFileEntity
import com.example.ui.theme.ArduinoAccentOrange
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight

@Composable
fun EditorScreen(
    files: List<ProjectFileEntity>,
    activeFileIndex: Int,
    editorValue: TextFieldValue,
    fontSizeSp: Int,
    showLineNumbers: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isSearchVisible: Boolean,
    searchQuery: String,
    replaceQuery: String,
    matchCount: Int,
    autocompleteSuggestions: List<AutocompleteItem>,
    onEditorValueChange: (TextFieldValue) -> Unit,
    onActiveFileSelect: (Int) -> Unit,
    onAddFileClick: (String) -> Unit,
    onRenameFile: (fileId: Long, newName: String) -> Unit,
    onDeleteFile: (fileId: Long) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertSymbol: (String) -> Unit,
    onApplyAutocomplete: (AutocompleteItem) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var fileForMenu by remember { mutableStateOf<ProjectFileEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameFileName by remember { mutableStateOf("") }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Files Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (files.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = activeFileIndex.coerceIn(0, files.size - 1),
                    modifier = Modifier.weight(1f),
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicator = { tabPositions ->
                        if (activeFileIndex in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeFileIndex]),
                                color = ArduinoTealLight,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    files.forEachIndexed { index, file ->
                        val isSelected = index == activeFileIndex
                        Tab(
                            selected = isSelected,
                            onClick = { onActiveFileSelect(index) },
                            modifier = Modifier.testTag("file_tab_${file.name}"),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ArduinoTealLight else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    if (!file.isMain) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "File Options",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { fileForMenu = file },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // New File Button (+)
            IconButton(
                onClick = {
                    newFileName = ""
                    showNewFileDialog = true
                },
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("add_file_tab_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab / File",
                    tint = ArduinoTealLight
                )
            }
        }

        // File Context Menu (Rename / Delete)
        DropdownMenu(
            expanded = fileForMenu != null,
            onDismissRequest = { fileForMenu = null }
        ) {
            DropdownMenuItem(
                text = { Text("Rename file") },
                onClick = {
                    renameFileName = fileForMenu?.name ?: ""
                    showRenameDialog = true
                    fileForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete file", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    fileForMenu?.let { onDeleteFile(it.id) }
                    fileForMenu = null
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }

        // 2. Search & Replace Panel (Expandable)
        AnimatedVisibility(visible = isSearchVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Find in file...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            textStyle = TextStyle(fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArduinoTealLight
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$matchCount match${if (matchCount == 1) "" else "es"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        IconButton(onClick = onFindNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next Match", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onCloseSearch, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = onReplaceQueryChange,
                            placeholder = { Text("Replace with...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            textStyle = TextStyle(fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArduinoTealLight
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onReplaceCurrent,
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTealDark)
                        ) {
                            Text("Replace", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = onReplaceAll,
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                        ) {
                            Text("All", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 3. Autocomplete Suggestions Bar
        AnimatedVisibility(visible = autocompleteSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                autocompleteSuggestions.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArduinoTealLight.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onApplyAutocomplete(item) }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = item.trigger,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = ArduinoAccentOrange
                                )
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Code Editor Main Canvas (Line Numbers Gutter + Code Input)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                val lineCount = remember(editorValue.text) {
                    editorValue.text.count { it == '\n' } + 1
                }

                // Line numbers gutter
                if (showLineNumbers) {
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp + 6).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Gutter separator line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }

                // Horizontal scrollable code text field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
                ) {
                    BasicTextField(
                        value = editorValue,
                        onValueChange = onEditorValueChange,
                        visualTransformation = ArduinoSyntaxHighlighter.visualTransformation,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp + 6).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(ArduinoTealLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 600.dp)
                            .testTag("code_editor_input")
                    )
                }
            }
        }

        // 5. Quick Symbols & Keyboard Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Undo button
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // Redo button
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // Tab Indent
            SymbolChip(symbol = "Tab", onClick = { onInsertSymbol("  ") })

            // Quick coding symbols
            val symbols = listOf("{", "}", "(", ")", ";", "=", "\"", "#", "<", ">", "+", "-", "*", "/", "&", "|", "!", "[", "]", ",")
            symbols.forEach { sym ->
                SymbolChip(symbol = sym, onClick = { onInsertSymbol(sym) })
            }
        }
    }

    // Dialog: Create New File Tab
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Add Source File") },
            text = {
                Column {
                    Text("Enter filename (e.g. config.h, helper.cpp):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("my_module.h") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            onAddFileClick(newFileName)
                            showNewFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Rename File
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                Column {
                    Text("Enter new file name:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameFileName,
                        onValueChange = { renameFileName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeFile = files.getOrNull(activeFileIndex)
                        if (activeFile != null && renameFileName.isNotBlank()) {
                            onRenameFile(activeFile.id, renameFileName)
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArduinoTeal)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SymbolChip(
    symbol: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
