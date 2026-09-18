package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.ArduinoAccentOrange
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

    val workflowState by viewModel.workflowState.collectAsState()
    val lastAttachedDevice by viewModel.lastAttachedDevice.collectAsState()
    val activeFirmwarePackage by viewModel.activeFirmwarePackage.collectAsState()
    val boardPackageUrls by viewModel.boardPackageUrls.collectAsState(emptyList())
    val esp32Platform by viewModel.esp32Platform.collectAsState()
    val coreInstallProgress by viewModel.coreInstallProgress.collectAsState()
    val context = LocalContext.current

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
                // USB Attachment Detection Banner
                lastAttachedDevice?.let { attached ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = ArduinoTealDark,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = ArduinoAccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "USB Device Connected: ${attached.chipType.label}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "${attached.deviceName} (VID: 0x${Integer.toHexString(attached.vendorId).uppercase()})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!attached.hasPermission) {
                                    Button(
                                        onClick = { viewModel.requestUsbPermission(attached.device) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoAccentOrange),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("Grant", fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.selectUsbDevice(attached)
                                            viewModel.navigateTo(Screen.UPLOAD)
                                            viewModel.dismissAttachedBanner()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ArduinoTealLight),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("Upload", fontSize = 11.sp)
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.dismissAttachedBanner() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

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
                            workflowState = workflowState,
                            hasCompiledBinary = viewModel.hasCompiledBinary,
                            activeFirmwarePackage = activeFirmwarePackage,
                            isUploading = isUploading,
                            uploadProgress = uploadProgress,
                            uploadStage = uploadStage,
                            uploadOutputLogs = uploadLogs,
                            onSelectDevice = { viewModel.selectUsbDevice(it) },
                            onRequestPermission = { viewModel.requestUsbPermission(it) },
                            onRefreshDevices = { viewModel.refreshUsbDevices() },
                            onVerifyClick = { viewModel.compile() },
                            onLoadSampleBlink = { viewModel.loadSampleBlinkFirmware(it) },
                            onStartUpload = { viewModel.startUpload(it) },
                            onChangeBoardClick = { viewModel.navigateTo(Screen.BOARDS) },
                            onOpenSerialMonitor = { viewModel.navigateTo(Screen.SERIAL_MONITOR) },
                            onExportFirmwareZip = {
                                val zipFile = viewModel.exportFirmwareZip()
                                if (zipFile != null) {
                                    val shareIntent = viewModel.shareFirmwareZip(zipFile)
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ESP32 Firmware Package"))
                                }
                            },
                            onNavigateToEditor = { viewModel.navigateTo(Screen.EDITOR) },
                            onNavigateToSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                            onNavigateToBoards = { viewModel.navigateTo(Screen.BOARDS) },
                            onLoadManualFirmware = { viewModel.setCustomFirmwarePackage(it) }
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
                            esp32Platform = esp32Platform,
                            packageUrls = boardPackageUrls,
                            installProgress = coreInstallProgress,
                            onSelectBoard = { viewModel.selectBoard(it) },
                            onToggleInstall = { viewModel.toggleBoardInstall(it) },
                            onInstallEsp32Core = { viewModel.installEsp32Core(it) },
                            onUninstallEsp32Core = { viewModel.uninstallEsp32Core() },
                            onUpdateIndexes = { viewModel.updateBoardIndexes() },
                            onAddPackageUrl = { viewModel.addBoardPackageUrl(it) },
                            onRemovePackageUrl = { viewModel.removeBoardPackageUrl(it) }
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
                            packageUrls = boardPackageUrls,
                            onUpdateSetting = { k, v -> viewModel.updateSetting(k, v) },
                            onTestBuildServer = { url -> viewModel.checkBuildServerHealth(url) },
                            onCheckLocalToolchain = { viewModel.checkLocalToolchain() },
                            onClearCache = { viewModel.clearBuildCache() },
                            onAddPackageUrl = { viewModel.addBoardPackageUrl(it) },
                            onRemovePackageUrl = { viewModel.removeBoardPackageUrl(it) },
                            onUpdateIndexes = { viewModel.updateBoardIndexes() },
                            onOpenBoardsManager = { viewModel.navigateTo(Screen.BOARDS) }
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
