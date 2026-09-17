package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.boards.BoardsManagerScreen
import com.example.ui.components.IdeTopAppBar
import com.example.ui.components.TerminalPanel
import com.example.ui.editor.EditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.libraries.LibraryManagerScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.serial.SerialMonitorScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.upload.UploadScreen
import com.example.ui.theme.ArduinoAccentGreen
import com.example.ui.theme.ArduinoTeal
import com.example.ui.theme.ArduinoTealDark
import com.example.ui.theme.ArduinoTealLight
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val currentProjectFiles by viewModel.currentProjectFiles.collectAsState()
    val activeFileIndex by viewModel.activeFileIndex.collectAsState()
    val selectedBoard by viewModel.selectedBoard.collectAsState()
    val allBoards by viewModel.allBoards.collectAsState()
    val allLibraries by viewModel.allLibraries.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val editorValue by viewModel.editorValue.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val matchCount by viewModel.matchCount.collectAsState()
    val autocompleteSuggestions by viewModel.autocompleteSuggestions.collectAsState()

    val isCompiling by viewModel.isCompiling.collectAsState()
    val compileProgress by viewModel.compileProgress.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val isTerminalExpanded by viewModel.isTerminalExpanded.collectAsState()

    val connectedUsbDevices by viewModel.connectedUsbDevices.collectAsState()
    val serialConnectionState by viewModel.serialConnectionState.collectAsState()
    val serialMessages by viewModel.serialMessages.collectAsState()
    val serialInput by viewModel.serialInput.collectAsState()
    val selectedBaudRate by viewModel.selectedBaudRate.collectAsState()
    val selectedLineEnding by viewModel.selectedLineEnding.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val showTimestamps by viewModel.showTimestamps.collectAsState()
    val hexMode by viewModel.hexMode.collectAsState()

    val selectedUsbDevice by viewModel.selectedUsbDevice.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val uploadStage by viewModel.uploadStage.collectAsState()
    val uploadLogs by viewModel.uploadLogs.collectAsState()

    val activeProject = allProjects.find { it.id == currentProjectId }
    val projectName = activeProject?.name ?: "Mobile Arduino IDE"

    val fontSizeSp = settings["editor_font_size"]?.toIntOrNull() ?: 14
    val showLineNumbers = (settings["editor_line_numbers"] ?: "true").toBoolean()

    val navItems = listOf(
        Screen.HOME to Icons.Default.Home,
        Screen.EDITOR to Icons.Default.Code,
        Screen.BOARDS to Icons.Default.DeveloperBoard,
        Screen.UPLOAD to Icons.Default.Upload,
        Screen.SERIAL_MONITOR to Icons.Default.SettingsInputAntenna,
        Screen.SETTINGS to Icons.Default.Settings
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
            IdeTopAppBar(
                projectName = projectName,
                selectedBoard = selectedBoard,
                connectedUsbDevices = connectedUsbDevices,
                isCompiling = isCompiling,
                compileProgress = compileProgress,
                isTerminalExpanded = isTerminalExpanded,
                onVerifyClick = { viewModel.compile() },
                onUploadClick = { viewModel.navigateTo(Screen.UPLOAD) },
                onSerialMonitorClick = { viewModel.navigateTo(Screen.SERIAL_MONITOR) },
                onToggleTerminal = { viewModel.toggleTerminal() },
                onSelectBoardClick = { viewModel.navigateTo(Screen.BOARDS) },
                onToggleSearch = { viewModel.toggleSearch() }
            )
        },
        bottomBar = {
            if (!isExpandedWidth) {
                Column {
                    // Terminal panel docked above bottom navigation
                    TerminalPanel(
                        logs = terminalLogs,
                        isExpanded = isTerminalExpanded,
                        onToggleExpand = { viewModel.toggleTerminal() },
                        onClear = { viewModel.clearTerminal() }
                    )

                    NavigationBar(
                        containerColor = ArduinoTealDark,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ) {
                        navItems.forEach { (screen, icon) ->
                            val isSelected = currentScreen == screen
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.navigateTo(screen) },
                                icon = { Icon(icon, contentDescription = screen.title) },
                                label = { Text(screen.title, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ArduinoTealDark,
                                    selectedTextColor = androidx.compose.ui.graphics.Color.White,
                                    indicatorColor = ArduinoTealLight,
                                    unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Adaptive Side NavigationRail for Tablets & Foldables
            if (isExpandedWidth) {
                NavigationRail(
                    containerColor = ArduinoTealDark,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) {
                    navItems.forEach { (screen, icon) ->
                        val isSelected = currentScreen == screen
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateTo(screen) },
                            icon = { Icon(icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ArduinoTealDark,
                                selectedTextColor = androidx.compose.ui.graphics.Color.White,
                                indicatorColor = ArduinoTealLight,
                                unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            // Screen Content
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            projects = allProjects,
                            currentProjectId = currentProjectId,
                            availableBoards = allBoards,
                            onSelectProject = { viewModel.selectProject(it) },
                            onCreateNewSketch = { name, boardId -> viewModel.createNewSketch(name, boardId) },
                            onImportSketch = { name, code, boardId -> viewModel.importSketch(name, code, boardId) },
                            onOpenAllProjects = { viewModel.navigateTo(Screen.PROJECTS) },
                            onOpenSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                            onNavigateWorkflowStep = { step ->
                                when (step) {
                                    "sketch" -> viewModel.navigateTo(Screen.EDITOR)
                                    "board" -> viewModel.navigateTo(Screen.BOARDS)
                                    "verify" -> {
                                        viewModel.compile()
                                        viewModel.navigateTo(Screen.EDITOR)
                                    }
                                    "upload" -> viewModel.navigateTo(Screen.UPLOAD)
                                    "serial" -> viewModel.navigateTo(Screen.SERIAL_MONITOR)
                                }
                            }
                        )

                        Screen.UPLOAD -> UploadScreen(
                            selectedBoard = selectedBoard,
                            connectedDevices = connectedUsbDevices,
                            selectedDevice = selectedUsbDevice,
                            isUploading = isUploading,
                            uploadProgress = uploadProgress,
                            uploadStage = uploadStage,
                            uploadOutputLogs = uploadLogs,
                            onSelectDevice = { viewModel.selectUsbDevice(it) },
                            onRequestPermission = { viewModel.requestUsbPermission(it) },
                            onRefreshDevices = { viewModel.refreshUsbDevices() },
                            onStartUpload = { viewModel.startUpload(it) },
                            onChangeBoardClick = { viewModel.navigateTo(Screen.BOARDS) },
                            onOpenSerialMonitor = { viewModel.navigateTo(Screen.SERIAL_MONITOR) }
                        )

                        Screen.EDITOR -> EditorScreen(
                            files = currentProjectFiles,
                            activeFileIndex = activeFileIndex,
                            editorValue = editorValue,
                            fontSizeSp = fontSizeSp,
                            showLineNumbers = showLineNumbers,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            isSearchVisible = isSearchVisible,
                            searchQuery = searchQuery,
                            replaceQuery = replaceQuery,
                            matchCount = matchCount,
                            autocompleteSuggestions = autocompleteSuggestions,
                            onEditorValueChange = { viewModel.onEditorValueChange(it) },
                            onActiveFileSelect = { viewModel.selectActiveFile(it) },
                            onAddFileClick = { viewModel.addFileToCurrentProject(it) },
                            onRenameFile = { id, name -> viewModel.renameFile(id, name) },
                            onDeleteFile = { viewModel.deleteFile(it) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onInsertSymbol = { viewModel.insertTextAtCursor(it) },
                            onApplyAutocomplete = { viewModel.applyAutocomplete(it) },
                            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                            onReplaceQueryChange = { viewModel.onReplaceQueryChange(it) },
                            onFindNext = { viewModel.findNext() },
                            onReplaceCurrent = { viewModel.replaceCurrent() },
                            onReplaceAll = { viewModel.replaceAll() },
                            onCloseSearch = { viewModel.toggleSearch() }
                        )

                        Screen.PROJECTS -> ProjectsScreen(
                            projects = allProjects,
                            currentProjectId = currentProjectId,
                            availableBoards = allBoards,
                            onSelectProject = { viewModel.selectProject(it) },
                            onCreateProject = { name, desc, boardId -> viewModel.createProject(name, desc, boardId) },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            onRenameProject = { id, name -> viewModel.renameProject(id, name) }
                        )

                        Screen.BOARDS -> BoardsManagerScreen(
                            boards = allBoards,
                            selectedBoard = selectedBoard,
                            onSelectBoard = { viewModel.selectBoard(it) },
                            onToggleInstall = { viewModel.toggleBoardInstall(it) }
                        )

                        Screen.LIBRARIES -> LibraryManagerScreen(
                            libraries = allLibraries,
                            onToggleInstall = { viewModel.toggleLibraryInstall(it) },
                            onIncludeInSketch = { viewModel.includeLibraryInSketch(it) }
                        )

                        Screen.SERIAL_MONITOR -> SerialMonitorScreen(
                            connectedDevices = connectedUsbDevices,
                            connectionState = serialConnectionState,
                            messages = serialMessages,
                            inputText = serialInput,
                            selectedBaudRate = selectedBaudRate,
                            selectedLineEnding = selectedLineEnding,
                            autoScroll = autoScroll,
                            showTimestamps = showTimestamps,
                            hexMode = hexMode,
                            onInputChange = { viewModel.onSerialInputChange(it) },
                            onSendMessage = { viewModel.sendSerial() },
                            onBaudRateSelect = { viewModel.setBaudRate(it) },
                            onLineEndingSelect = { viewModel.setLineEnding(it) },
                            onToggleAutoScroll = { viewModel.toggleAutoScroll() },
                            onToggleTimestamps = { viewModel.toggleTimestamps() },
                            onToggleHexMode = { viewModel.toggleHexMode() },
                            onConnectDevice = { viewModel.connectUsb(it) },
                            onDisconnectDevice = { viewModel.disconnectUsb() },
                            onRequestPermission = { viewModel.requestUsbPermission(it) },
                            onRefreshDevices = { viewModel.refreshUsbDevices() },
                            onClearMessages = { viewModel.clearSerialLog() }
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onUpdateSetting = { k, v -> viewModel.updateSetting(k, v) }
                        )
                    }
                }

                // If expanded width (tablet), show terminal at bottom of content area
                if (isExpandedWidth) {
                    TerminalPanel(
                        logs = terminalLogs,
                        isExpanded = isTerminalExpanded,
                        onToggleExpand = { viewModel.toggleTerminal() },
                        onClear = { viewModel.clearTerminal() }
                    )
                }
            }
        }
    }
}
}
