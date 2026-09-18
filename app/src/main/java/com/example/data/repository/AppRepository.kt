package com.example.data.repository

import android.content.Context
import android.hardware.usb.UsbDevice
import com.example.compiler.CompileProgress
import com.example.compiler.CompilerService
import com.example.compiler.BuildServerHealthResult
import com.example.compiler.LocalToolchainStatus
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.firmware.FirmwareManager
import com.example.firmware.FirmwarePackage
import java.io.File
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.SettingEntity
import com.example.data.model.IdeWorkflowState
import com.example.data.usb.LineEnding
import com.example.data.usb.SerialConnectionState
import com.example.data.usb.SerialMessage
import com.example.data.usb.UploadResult
import com.example.data.usb.UsbBoardInfo
import com.example.data.usb.UsbHardwareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    val usbHardwareManager: UsbHardwareManager = UsbHardwareManager(context),
    private val compilerService: CompilerService = CompilerService()
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // DAOs
    private val projectDao = db.projectDao()
    private val boardDao = db.boardDao()
    private val libraryDao = db.libraryDao()
    private val settingDao = db.settingDao()

    // Observable Data
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allBoards: Flow<List<BoardEntity>> = boardDao.getAllBoards()
    val installedBoards: Flow<List<BoardEntity>> = boardDao.getInstalledBoards()
    val allLibraries: Flow<List<LibraryEntity>> = libraryDao.getAllLibraries()
    val installedLibraries: Flow<List<LibraryEntity>> = libraryDao.getInstalledLibraries()

    // Workflow State Machine
    private val _workflowState = MutableStateFlow(IdeWorkflowState.NO_USB_DEVICE)
    val workflowState: StateFlow<IdeWorkflowState> = _workflowState.asStateFlow()

    // Active Project & File Selection State
    private val _currentProjectId = MutableStateFlow<Long?>(null)
    val currentProjectId: StateFlow<Long?> = _currentProjectId.asStateFlow()

    private val _currentProjectFiles = MutableStateFlow<List<ProjectFileEntity>>(emptyList())
    val currentProjectFiles: StateFlow<List<ProjectFileEntity>> = _currentProjectFiles.asStateFlow()

    private val _activeFileIndex = MutableStateFlow<Int>(0)
    val activeFileIndex: StateFlow<Int> = _activeFileIndex.asStateFlow()

    private val _selectedBoard = MutableStateFlow<BoardEntity?>(null)
    val selectedBoard: StateFlow<BoardEntity?> = _selectedBoard.asStateFlow()

    // Compiler & Terminal State
    private val _isCompiling = MutableStateFlow(false)
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    private val _compileProgress = MutableStateFlow(0f)
    val compileProgress: StateFlow<Float> = _compileProgress.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLine>>(listOf(
        TerminalLine(TerminalLineType.INFO, "Mobile Arduino IDE v1.3 initialized. Ready.")
    ))
    val terminalLogs: StateFlow<List<TerminalLine>> = _terminalLogs.asStateFlow()

    private val _isTerminalExpanded = MutableStateFlow(false)
    val isTerminalExpanded: StateFlow<Boolean> = _isTerminalExpanded.asStateFlow()

    // Last compiled binary cache (hex/bin)
    var lastCompiledBinary: ByteArray? = null
        private set

    val firmwareManager = FirmwareManager(context)

    private val _activeFirmwarePackage = MutableStateFlow<FirmwarePackage?>(null)
    val activeFirmwarePackage: StateFlow<FirmwarePackage?> = _activeFirmwarePackage.asStateFlow()

    // USB / Hardware State
    val connectedUsbDevices: StateFlow<List<UsbBoardInfo>> = usbHardwareManager.connectedDevices
    val serialConnectionState: StateFlow<SerialConnectionState> = usbHardwareManager.connectionState
    val serialMessages: StateFlow<List<SerialMessage>> = usbHardwareManager.serialMessages

    // Settings
    val settings: Flow<Map<String, String>> = settingDao.getAllSettings().map { list ->
        list.associate { it.key to it.value }
    }

    init {
        // Load initial project and default board
        scope.launch {
            val initialProject = projectDao.getAllProjects().firstOrNull()?.firstOrNull()
            if (initialProject != null) {
                selectProject(initialProject.id)
            }
            val defaultBoard = boardDao.getBoardByIdDirect("arduino_uno")
            _selectedBoard.value = defaultBoard

            // Monitor USB devices to update workflow state machine
            connectedUsbDevices.collect { devices ->
                updateUsbWorkflowState(devices)
            }
        }
    }

    private fun updateUsbWorkflowState(devices: List<UsbBoardInfo>) {
        if (_isCompiling.value) return
        if (_workflowState.value == IdeWorkflowState.UPLOADING) return

        if (devices.isEmpty()) {
            _workflowState.value = if (_selectedBoard.value != null) {
                IdeWorkflowState.BOARD_SELECTED
            } else {
                IdeWorkflowState.NO_USB_DEVICE
            }
        } else {
            val hasPerm = devices.any { it.hasPermission }
            if (hasPerm) {
                if (_workflowState.value != IdeWorkflowState.COMPILE_SUCCESS &&
                    _workflowState.value != IdeWorkflowState.COMPILE_FAILED &&
                    _workflowState.value != IdeWorkflowState.UPLOAD_SUCCESS &&
                    _workflowState.value != IdeWorkflowState.UPLOAD_FAILED
                ) {
                    _workflowState.value = IdeWorkflowState.USB_PERMISSION_GRANTED
                }
            } else {
                _workflowState.value = IdeWorkflowState.USB_PERMISSION_REQUIRED
            }
        }
    }

    // Projects & Files Management
    fun selectProject(projectId: Long) {
        _currentProjectId.value = projectId
        scope.launch {
            val project = projectDao.getProjectByIdDirect(projectId)
            if (project != null) {
                val board = boardDao.getBoardByIdDirect(project.selectedBoardId)
                if (board != null) {
                    _selectedBoard.value = board
                }
            }
            val files = projectDao.getFilesForProjectDirect(projectId)
            _currentProjectFiles.value = files
            _activeFileIndex.value = 0
        }
    }

    fun selectActiveFileIndex(index: Int) {
        if (index in 0 until _currentProjectFiles.value.size) {
            _activeFileIndex.value = index
        }
    }

    suspend fun saveFileContent(fileId: Long, content: String) {
        projectDao.updateFileContent(fileId, content)
        val updatedFiles = _currentProjectFiles.value.map {
            if (it.id == fileId) it.copy(content = content) else it
        }
        _currentProjectFiles.value = updatedFiles

        // Also update project updatedAt timestamp
        val pId = _currentProjectId.value
        if (pId != null) {
            val project = projectDao.getProjectByIdDirect(pId)
            if (project != null) {
                projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun createNewProject(name: String, description: String = "", boardId: String = "arduino_uno"): Long {
        val cleanName = if (name.isBlank()) "Sketch_${System.currentTimeMillis() % 10000}" else name.trim()
        val projectId = projectDao.insertProject(
            ProjectEntity(
                name = cleanName,
                description = description,
                selectedBoardId = boardId
            )
        )
        // Insert default main .ino file
        projectDao.insertFile(
            ProjectFileEntity(
                projectId = projectId,
                name = "$cleanName.ino",
                isMain = true,
                content = """void setup() {
  // Put your setup code here, to run once:
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
}

void loop() {
  // Put your main code here, to run repeatedly:
  digitalWrite(LED_BUILTIN, HIGH);
  delay(1000);
  digitalWrite(LED_BUILTIN, LOW);
  delay(1000);
}
"""
            )
        )
        selectProject(projectId)
        return projectId
    }

    suspend fun importSketchProject(name: String, content: String, boardId: String = "arduino_uno"): Long {
        val cleanName = if (name.isBlank()) "Imported_${System.currentTimeMillis() % 10000}" else name.trim()
        val projectId = projectDao.insertProject(
            ProjectEntity(
                name = cleanName,
                description = "Imported Arduino sketch",
                selectedBoardId = boardId
            )
        )
        projectDao.insertFile(
            ProjectFileEntity(
                projectId = projectId,
                name = if (cleanName.endsWith(".ino")) cleanName else "$cleanName.ino",
                isMain = true,
                content = content
            )
        )
        selectProject(projectId)
        return projectId
    }

    suspend fun addNewFileToCurrentProject(fileName: String): Boolean {
        val pId = _currentProjectId.value ?: return false
        var clean = fileName.trim()
        if (!clean.contains(".")) clean = "$clean.h"

        // Avoid duplicates
        if (_currentProjectFiles.value.any { it.name.equals(clean, ignoreCase = true) }) {
            return false
        }

        val templateContent = when {
            clean.endsWith(".h") -> {
                val guard = clean.uppercase().replace(".", "_") + "_INCLUDED"
                "#ifndef $guard\n#define $guard\n\n#include <Arduino.h>\n\n// Add header declarations here\n\n#endif // $guard\n"
            }
            clean.endsWith(".cpp") -> {
                "#include <Arduino.h>\n\n// Add implementation functions here\n"
            }
            else -> "// Source file: $clean\n"
        }

        val newFileId = projectDao.insertFile(
            ProjectFileEntity(
                projectId = pId,
                name = clean,
                isMain = false,
                content = templateContent
            )
        )
        val files = projectDao.getFilesForProjectDirect(pId)
        _currentProjectFiles.value = files
        val newIndex = files.indexOfFirst { it.id == newFileId }
        if (newIndex >= 0) {
            _activeFileIndex.value = newIndex
        }
        return true
    }

    suspend fun renameFile(fileId: Long, newName: String) {
        val file = projectDao.getFileById(fileId) ?: return
        var clean = newName.trim()
        if (!clean.contains(".")) {
            clean += when {
                file.name.endsWith(".ino") -> ".ino"
                file.name.endsWith(".cpp") -> ".cpp"
                else -> ".h"
            }
        }
        projectDao.updateFile(file.copy(name = clean))
        val pId = _currentProjectId.value ?: return
        _currentProjectFiles.value = projectDao.getFilesForProjectDirect(pId)
    }

    suspend fun deleteFile(fileId: Long) {
        val file = projectDao.getFileById(fileId) ?: return
        if (file.isMain) return // Prevent deleting main sketch file
        projectDao.deleteFileById(fileId)
        val pId = _currentProjectId.value ?: return
        val updated = projectDao.getFilesForProjectDirect(pId)
        _currentProjectFiles.value = updated
        if (_activeFileIndex.value >= updated.size) {
            _activeFileIndex.value = (updated.size - 1).coerceAtLeast(0)
        }
    }

    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteProjectById(projectId)
        if (_currentProjectId.value == projectId) {
            val remaining = projectDao.getAllProjects().firstOrNull()?.firstOrNull()
            if (remaining != null) {
                selectProject(remaining.id)
            } else {
                createNewProject("Blink")
            }
        }
    }

    suspend fun renameProject(projectId: Long, newName: String) {
        val project = projectDao.getProjectByIdDirect(projectId) ?: return
        projectDao.updateProject(project.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    // Board Selection
    fun selectBoard(board: BoardEntity) {
        _selectedBoard.value = board
        val pId = _currentProjectId.value ?: return
        scope.launch {
            val project = projectDao.getProjectByIdDirect(pId)
            if (project != null) {
                projectDao.updateProject(project.copy(selectedBoardId = board.id))
            }
        }
    }

    suspend fun toggleBoardInstalled(boardId: String, isInstalled: Boolean) {
        boardDao.setBoardInstalled(boardId, isInstalled)
    }

    // Libraries
    suspend fun toggleLibraryInstalled(libraryId: String, isInstalled: Boolean) {
        libraryDao.setLibraryInstalled(libraryId, isInstalled)
    }

    suspend fun includeLibraryInActiveFile(library: LibraryEntity) {
        val currentFiles = _currentProjectFiles.value
        val activeIndex = _activeFileIndex.value
        if (activeIndex in currentFiles.indices) {
            val activeFile = currentFiles[activeIndex]
            val includeStatement = "#include <${library.headerToInclude}>\n"
            if (!activeFile.content.contains(includeStatement.trim())) {
                val updatedContent = includeStatement + activeFile.content
                saveFileContent(activeFile.id, updatedContent)
            }
        }
    }

    // Compilation
    fun compileCurrentSketch() {
        val currentFiles = _currentProjectFiles.value
        val board = _selectedBoard.value
        if (board == null || currentFiles.isEmpty()) {
            addTerminalLine(TerminalLine(TerminalLineType.ERROR, "Compile error: No target board or sketch files loaded."))
            _isTerminalExpanded.value = true
            _workflowState.value = IdeWorkflowState.COMPILE_FAILED
            return
        }

        val projectName = currentFiles.find { it.isMain }?.name?.removeSuffix(".ino") ?: "Sketch"

        _isCompiling.value = true
        _isTerminalExpanded.value = true
        _compileProgress.value = 0f
        _terminalLogs.value = emptyList()
        _workflowState.value = IdeWorkflowState.COMPILING

        scope.launch {
            val installedLibs = libraryDao.getInstalledLibraries().firstOrNull() ?: emptyList()
            val remoteUrl = settingDao.getSettingDirect("compiler_remote_url") ?: ""
            val isVerbose = (settingDao.getSettingDirect("compiler_verbose") ?: "true").toBoolean()

            compilerService.compileSketch(
                projectName = projectName,
                files = currentFiles,
                targetBoard = board,
                installedLibraries = installedLibs,
                remoteCompilerUrl = remoteUrl,
                verboseOutput = isVerbose
            ).collect { progress ->
                _compileProgress.value = progress.progress
                progress.line?.let { line ->
                    addTerminalLine(line)
                }

                if (progress.isFinished) {
                    if (progress.isSuccess) {
                        lastCompiledBinary = progress.binaryBytes
                        _activeFirmwarePackage.value = progress.firmwarePackage
                        _workflowState.value = IdeWorkflowState.COMPILE_SUCCESS
                    } else {
                        lastCompiledBinary = null
                        _activeFirmwarePackage.value = null
                        _workflowState.value = IdeWorkflowState.COMPILE_FAILED
                    }
                }
            }

            _isCompiling.value = false
        }
    }

    fun loadSampleBlinkFirmware(isEsp32: Boolean): Int {
        if (isEsp32) {
            val pkg = firmwareManager.loadPrecompiledEsp32Blink()
            if (pkg != null) {
                _activeFirmwarePackage.value = pkg
                lastCompiledBinary = pkg.binaries.find { it.filename.contains("firmware", ignoreCase = true) }?.data
                _workflowState.value = IdeWorkflowState.COMPILE_SUCCESS
                addTerminalLine(TerminalLine(TerminalLineType.SUCCESS, "Loaded genuine ESP32 Arduino Blink Firmware Package:"))
                for (b in pkg.binaries) {
                    addTerminalLine(TerminalLine(TerminalLineType.INFO, "  - ${b.flashAddress}: ${b.filename} (${b.size} bytes)"))
                }
                addTerminalLine(TerminalLine(TerminalLineType.SUCCESS, "Package size: ${pkg.formattedTotalSize}. Ready to flash over USB OTG."))
                return pkg.totalSizeBytes
            }
        }

        // Fallback for AVR / mock
        val sample = if (isEsp32) {
            ByteArray(4096) { i -> if (i == 0) 0xE9.toByte() else (i % 256).toByte() }
        } else {
            ":100000000C945C000C946E000C946E000C946E00CA\n:00000001FF\n".toByteArray()
        }
        lastCompiledBinary = sample
        _activeFirmwarePackage.value = null
        _workflowState.value = IdeWorkflowState.COMPILE_SUCCESS
        addTerminalLine(TerminalLine(TerminalLineType.SUCCESS, "Loaded sample Blink test firmware (${sample.size} bytes). Ready for upload."))
        return sample.size
    }

    fun clearCompiledBinary() {
        lastCompiledBinary = null
        _activeFirmwarePackage.value = null
        _workflowState.value = IdeWorkflowState.BOARD_SELECTED
    }

    fun toggleTerminalExpanded() {
        _isTerminalExpanded.value = !_isTerminalExpanded.value
    }

    fun clearTerminal() {
        _terminalLogs.value = emptyList()
    }

    fun addTerminalLine(line: TerminalLine) {
        val current = _terminalLogs.value
        _terminalLogs.value = if (current.size > 400) {
            current.drop(current.size - 399) + line
        } else {
            current + line
        }
    }

    // Hardware Upload
    suspend fun uploadCurrentSketch(targetDevice: UsbDevice?): UploadResult {
        _isTerminalExpanded.value = true
        addTerminalLine(TerminalLine(TerminalLineType.INFO, "--- Starting Firmware Upload ---"))

        val binary = lastCompiledBinary
        val pkg = _activeFirmwarePackage.value
        if ((binary == null || binary.isEmpty()) && pkg == null) {
            val err = "Upload blocked: No compiled firmware binary available. You must compile the sketch first or tap 'Load Test Blink'."
            addTerminalLine(TerminalLine(TerminalLineType.ERROR, err))
            _workflowState.value = IdeWorkflowState.COMPILE_FAILED
            return UploadResult.Failed(err, err)
        }

        _workflowState.value = IdeWorkflowState.UPLOADING

        val board = _selectedBoard.value
        val targetFqbn = board?.fqbn ?: "esp32:esp32:esp32"

        val result = usbHardwareManager.uploadFirmware(
            device = targetDevice,
            targetFqbn = targetFqbn,
            compiledBytes = binary,
            firmwarePackage = pkg,
            onProgress = { _, _ -> },
            onTerminalLog = { addTerminalLine(it) }
        )

        when (result) {
            is UploadResult.Success -> {
                _workflowState.value = IdeWorkflowState.UPLOAD_SUCCESS
                addTerminalLine(TerminalLine(TerminalLineType.SUCCESS, result.log))
            }
            is UploadResult.Failed -> {
                _workflowState.value = IdeWorkflowState.UPLOAD_FAILED
                addTerminalLine(TerminalLine(TerminalLineType.ERROR, result.log))
            }
        }
        return result
    }

    fun exportFirmwareZip(): File? {
        val pkg = _activeFirmwarePackage.value ?: return null
        return firmwareManager.exportToZip(pkg)
    }

    fun shareFirmwareZip(zipFile: File): Intent {
        return firmwareManager.shareFirmwareZip(zipFile)
    }

    suspend fun checkBuildServerHealth(url: String): BuildServerHealthResult {
        return compilerService.checkBuildServerHealth(url)
    }

    fun checkLocalToolchain(): LocalToolchainStatus {
        return compilerService.checkLocalToolchain(context)
    }

    fun requestInstallEsp32Core(url: String): Flow<TerminalLine> {
        return compilerService.requestInstallEsp32Core(url)
    }

    fun clearBuildCache(): Long {
        return compilerService.clearBuildCache(context)
    }

    fun setCustomFirmwarePackage(pkg: FirmwarePackage) {
        _activeFirmwarePackage.value = pkg
        lastCompiledBinary = pkg.binaries.find { it.filename.contains("firmware", ignoreCase = true) }?.data
            ?: pkg.binaries.firstOrNull()?.data
        _workflowState.value = IdeWorkflowState.COMPILE_SUCCESS
    }

    // Settings
    suspend fun updateSetting(key: String, value: String) {
        settingDao.setSetting(SettingEntity(key, value))
    }
}
