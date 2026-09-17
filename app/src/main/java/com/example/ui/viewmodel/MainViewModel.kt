package com.example.ui.viewmodel

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.TerminalLine
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.repository.AppRepository
import com.example.data.usb.LineEnding
import com.example.data.usb.SerialConnectionState
import com.example.data.usb.SerialMessage
import com.example.data.usb.UploadResult
import com.example.data.usb.UsbBoardInfo
import com.example.ui.editor.AutocompleteHelper
import com.example.ui.editor.AutocompleteItem
import com.example.ui.editor.UndoRedoManager
import com.example.compiler.TerminalLineType
import com.example.data.model.IdeWorkflowState
import com.example.upload.UploadService
import com.example.upload.UploadStageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    HOME("Home"),
    EDITOR("Editor"),
    BOARDS("Boards"),
    UPLOAD("Upload"),
    SERIAL_MONITOR("Serial"),
    PROJECTS("Projects"),
    LIBRARIES("Libraries"),
    SETTINGS("Settings")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AppRepository(application.applicationContext)
    val uploadService = UploadService(repository.usbHardwareManager)

    // Workflow State
    val workflowState: StateFlow<IdeWorkflowState> = repository.workflowState
    val lastAttachedDevice: StateFlow<UsbBoardInfo?> = repository.usbHardwareManager.lastAttachedDevice

    fun dismissAttachedBanner() {
        repository.usbHardwareManager.dismissAttachedBanner()
    }

    // Active Navigation Screen
    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Editor State
    private val _editorValue = MutableStateFlow(TextFieldValue())
    val editorValue: StateFlow<TextFieldValue> = _editorValue.asStateFlow()

    private val undoRedoManager = UndoRedoManager()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Search & Replace
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _matchCount = MutableStateFlow(0)
    val matchCount: StateFlow<Int> = _matchCount.asStateFlow()

    // Autocomplete
    private val _autocompleteSuggestions = MutableStateFlow<List<AutocompleteItem>>(emptyList())
    val autocompleteSuggestions: StateFlow<List<AutocompleteItem>> = _autocompleteSuggestions.asStateFlow()

    // Serial Monitor State
    private val _serialInput = MutableStateFlow("")
    val serialInput: StateFlow<String> = _serialInput.asStateFlow()

    private val _selectedBaudRate = MutableStateFlow(115200)
    val selectedBaudRate: StateFlow<Int> = _selectedBaudRate.asStateFlow()

    private val _selectedLineEnding = MutableStateFlow(LineEnding.BOTH)
    val selectedLineEnding: StateFlow<LineEnding> = _selectedLineEnding.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val _showTimestamps = MutableStateFlow(true)
    val showTimestamps: StateFlow<Boolean> = _showTimestamps.asStateFlow()

    private val _hexMode = MutableStateFlow(false)
    val hexMode: StateFlow<Boolean> = _hexMode.asStateFlow()

    // Projects, Boards, Libraries from Repository
    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allBoards: StateFlow<List<BoardEntity>> = repository.allBoards.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allLibraries: StateFlow<List<LibraryEntity>> = repository.allLibraries.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val settings: StateFlow<Map<String, String>> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap()
    )

    val currentProjectId: StateFlow<Long?> = repository.currentProjectId
    val currentProjectFiles: StateFlow<List<ProjectFileEntity>> = repository.currentProjectFiles
    val activeFileIndex: StateFlow<Int> = repository.activeFileIndex
    val selectedBoard: StateFlow<BoardEntity?> = repository.selectedBoard

    val isCompiling: StateFlow<Boolean> = repository.isCompiling
    val compileProgress: StateFlow<Float> = repository.compileProgress
    val terminalLogs: StateFlow<List<TerminalLine>> = repository.terminalLogs
    val isTerminalExpanded: StateFlow<Boolean> = repository.isTerminalExpanded

    val connectedUsbDevices: StateFlow<List<UsbBoardInfo>> = repository.connectedUsbDevices
    val serialConnectionState: StateFlow<SerialConnectionState> = repository.serialConnectionState
    val serialMessages: StateFlow<List<SerialMessage>> = repository.serialMessages

    // Upload Service Screen State
    private val _selectedUsbDevice = MutableStateFlow<UsbBoardInfo?>(null)
    val selectedUsbDevice: StateFlow<UsbBoardInfo?> = _selectedUsbDevice.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _uploadStage = MutableStateFlow("")
    val uploadStage: StateFlow<String> = _uploadStage.asStateFlow()

    private val _uploadLogs = MutableStateFlow<List<TerminalLine>>(emptyList())
    val uploadLogs: StateFlow<List<TerminalLine>> = _uploadLogs.asStateFlow()

    // Active file being edited tracking
    private var lastActiveFileId: Long? = null

    init {
        // Sync editor value when active file changes
        viewModelScope.launch {
            repository.currentProjectFiles.collect { files ->
                val index = repository.activeFileIndex.value
                if (index in files.indices) {
                    val file = files[index]
                    if (file.id != lastActiveFileId) {
                        lastActiveFileId = file.id
                        _editorValue.value = TextFieldValue(
                            text = file.content,
                            selection = TextRange(0)
                        )
                        updateUndoRedoStatus()
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.activeFileIndex.collect { index ->
                val files = repository.currentProjectFiles.value
                if (index in files.indices) {
                    val file = files[index]
                    if (file.id != lastActiveFileId) {
                        lastActiveFileId = file.id
                        _editorValue.value = TextFieldValue(
                            text = file.content,
                            selection = TextRange(0)
                        )
                        updateUndoRedoStatus()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.usbHardwareManager.cleanup()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Editor Actions
    fun onEditorValueChange(newValue: TextFieldValue) {
        val oldValue = _editorValue.value
        if (oldValue.text != newValue.text) {
            undoRedoManager.recordState(oldValue)
            updateUndoRedoStatus()

            // Check for autocomplete suggestions
            _autocompleteSuggestions.value = AutocompleteHelper.getSuggestions(
                newValue.text,
                newValue.selection.end
            )

            // Auto-save active file
            val files = repository.currentProjectFiles.value
            val idx = repository.activeFileIndex.value
            if (idx in files.indices) {
                viewModelScope.launch {
                    repository.saveFileContent(files[idx].id, newValue.text)
                }
            }
        } else {
            // Cursor movement, re-evaluate autocomplete
            _autocompleteSuggestions.value = AutocompleteHelper.getSuggestions(
                newValue.text,
                newValue.selection.end
            )
        }

        _editorValue.value = newValue
        updateMatchCount()
    }

    fun applyAutocomplete(item: AutocompleteItem) {
        val updated = AutocompleteHelper.applySuggestion(_editorValue.value, item)
        onEditorValueChange(updated)
        _autocompleteSuggestions.value = emptyList()
    }

    fun insertTextAtCursor(insertStr: String) {
        val current = _editorValue.value
        val text = current.text
        val start = current.selection.start
        val end = current.selection.end

        val newText = text.substring(0, start) + insertStr + text.substring(end)
        val newCursor = start + insertStr.length
        onEditorValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )
    }

    fun undo() {
        val prev = undoRedoManager.undo(_editorValue.value)
        if (prev != null) {
            _editorValue.value = prev
            updateUndoRedoStatus()
            val files = repository.currentProjectFiles.value
            val idx = repository.activeFileIndex.value
            if (idx in files.indices) {
                viewModelScope.launch {
                    repository.saveFileContent(files[idx].id, prev.text)
                }
            }
        }
    }

    fun redo() {
        val next = undoRedoManager.redo(_editorValue.value)
        if (next != null) {
            _editorValue.value = next
            updateUndoRedoStatus()
            val files = repository.currentProjectFiles.value
            val idx = repository.activeFileIndex.value
            if (idx in files.indices) {
                viewModelScope.launch {
                    repository.saveFileContent(files[idx].id, next.text)
                }
            }
        }
    }

    private fun updateUndoRedoStatus() {
        _canUndo.value = undoRedoManager.canUndo()
        _canRedo.value = undoRedoManager.canRedo()
    }

    // Search & Replace
    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchQuery.value = ""
            _replaceQuery.value = ""
            _matchCount.value = 0
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        updateMatchCount()
    }

    fun onReplaceQueryChange(query: String) {
        _replaceQuery.value = query
    }

    private fun updateMatchCount() {
        val q = _searchQuery.value
        if (q.isEmpty()) {
            _matchCount.value = 0
            return
        }
        val text = _editorValue.value.text
        var count = 0
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf(q, idx, ignoreCase = true)
            if (found >= 0) {
                count++
                idx = found + q.length
            } else break
        }
        _matchCount.value = count
    }

    fun findNext() {
        val q = _searchQuery.value
        if (q.isEmpty()) return
        val text = _editorValue.value.text
        val currentIdx = _editorValue.value.selection.end
        var found = text.indexOf(q, currentIdx, ignoreCase = true)
        if (found < 0) {
            // Wrap around
            found = text.indexOf(q, 0, ignoreCase = true)
        }
        if (found >= 0) {
            _editorValue.value = _editorValue.value.copy(
                selection = TextRange(found, found + q.length)
            )
        }
    }

    fun replaceCurrent() {
        val q = _searchQuery.value
        val r = _replaceQuery.value
        if (q.isEmpty()) return
        val current = _editorValue.value
        val selStart = current.selection.start
        val selEnd = current.selection.end

        if (selEnd > selStart && current.text.substring(selStart, selEnd).equals(q, ignoreCase = true)) {
            val newText = current.text.substring(0, selStart) + r + current.text.substring(selEnd)
            onEditorValueChange(
                TextFieldValue(newText, TextRange(selStart + r.length))
            )
        } else {
            findNext()
        }
    }

    fun replaceAll() {
        val q = _searchQuery.value
        val r = _replaceQuery.value
        if (q.isEmpty()) return
        val text = _editorValue.value.text
        val newText = text.replace(q, r, ignoreCase = true)
        onEditorValueChange(TextFieldValue(newText, TextRange(0)))
    }

    // Projects & Files
    fun selectProject(projectId: Long) {
        repository.selectProject(projectId)
        navigateTo(Screen.EDITOR)
    }

    fun createProject(name: String, description: String, boardId: String) {
        viewModelScope.launch {
            val pId = repository.createNewProject(name, description, boardId)
            repository.selectProject(pId)
            navigateTo(Screen.EDITOR)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun renameProject(projectId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newName)
        }
    }

    fun addFileToCurrentProject(fileName: String) {
        viewModelScope.launch {
            repository.addNewFileToCurrentProject(fileName)
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameFile(fileId, newName)
        }
    }

    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            repository.deleteFile(fileId)
        }
    }

    fun selectActiveFile(index: Int) {
        repository.selectActiveFileIndex(index)
    }

    // Boards & Libraries
    fun selectBoard(board: BoardEntity) {
        repository.selectBoard(board)
    }

    fun toggleBoardInstall(board: BoardEntity) {
        viewModelScope.launch {
            repository.toggleBoardInstalled(board.id, !board.isInstalled)
        }
    }

    fun toggleLibraryInstall(library: LibraryEntity) {
        viewModelScope.launch {
            repository.toggleLibraryInstalled(library.id, !library.isInstalled)
        }
    }

    fun includeLibraryInSketch(library: LibraryEntity) {
        viewModelScope.launch {
            repository.includeLibraryInActiveFile(library)
            navigateTo(Screen.EDITOR)
        }
    }

    // Build / Compiler
    fun compile() {
        repository.compileCurrentSketch()
    }

    fun toggleTerminal() {
        repository.toggleTerminalExpanded()
    }

    fun clearTerminal() {
        repository.clearTerminal()
    }

    // Upload Firmware & Service
    fun selectUsbDevice(deviceInfo: UsbBoardInfo) {
        _selectedUsbDevice.value = deviceInfo
    }

    val hasCompiledBinary: Boolean
        get() = repository.lastCompiledBinary != null

    fun loadSampleBlinkFirmware(isEsp32: Boolean): Int {
        val size = repository.loadSampleBlinkFirmware(isEsp32)
        appendUploadLog(TerminalLine(TerminalLineType.SUCCESS, "Loaded sample Blink test firmware ($size bytes). Ready for hardware upload."))
        return size
    }

    fun clearCompiledBinary() {
        repository.clearCompiledBinary()
        appendUploadLog(TerminalLine(TerminalLineType.INFO, "Cleared compiled firmware binary."))
    }

    fun upload(targetDevice: UsbDevice?) {
        val hex = repository.lastCompiledBinary
        if (hex == null || hex.isEmpty()) {
            // Requirement 4: Upload must NOT start if compilation has not succeeded!
            appendUploadLog(TerminalLine(TerminalLineType.WARNING, "Cannot upload: Sketch must be compiled first! Compiling sketch now..."))
            repository.addTerminalLine(TerminalLine(TerminalLineType.WARNING, "--- Compilation required before upload ---"))
            compile()
            return
        }
        startUpload(targetDevice)
    }

    fun startUpload(targetDevice: UsbDevice?) {
        val board = repository.selectedBoard.value
        val hex = repository.lastCompiledBinary
        val dev = targetDevice ?: _selectedUsbDevice.value?.device ?: connectedUsbDevices.value.firstOrNull()?.device

        if (hex == null || hex.isEmpty()) {
            _uploadStage.value = "Compilation Required"
            appendUploadLog(TerminalLine(TerminalLineType.ERROR, "Upload blocked: No compiled firmware binary found. You must compile the sketch first."))
            repository.toggleTerminalExpanded()
            return
        }

        _isUploading.value = true
        _uploadProgress.value = 0.05f
        _uploadStage.value = "Starting Upload"
        _uploadLogs.value = listOf(TerminalLine(TerminalLineType.INFO, "Starting firmware upload via UploadService..."))
        repository.toggleTerminalExpanded()

        viewModelScope.launch {
            uploadService.performUpload(
                device = dev,
                targetBoard = board,
                compiledHexBytes = hex,
                onTerminalLog = { line ->
                    appendUploadLog(line)
                    repository.addTerminalLine(line)
                }
            ).collect { stage ->
                when (stage) {
                    is UploadStageState.Idle -> {}
                    is UploadStageState.InProgress -> {
                        _uploadStage.value = stage.stage
                        _uploadProgress.value = stage.progress
                        appendUploadLog(TerminalLine(TerminalLineType.INFO, "[${stage.stage}] ${stage.logLine}"))
                    }
                    is UploadStageState.Completed -> {
                        _isUploading.value = false
                        _uploadProgress.value = if (stage.success) 1.0f else 0f
                        _uploadStage.value = if (stage.success) "Success" else "Failed"
                        val type = if (stage.success) TerminalLineType.SUCCESS else TerminalLineType.ERROR
                        appendUploadLog(TerminalLine(type, stage.message))
                        if (stage.fullLog.isNotEmpty()) {
                            appendUploadLog(TerminalLine(TerminalLineType.STDOUT, stage.fullLog))
                        }
                    }
                }
            }
        }
    }

    private fun appendUploadLog(line: TerminalLine) {
        val current = _uploadLogs.value
        _uploadLogs.value = if (current.size > 200) current.drop(current.size - 199) + line else current + line
    }

    fun createNewSketch(name: String, boardId: String = "arduino_uno") {
        viewModelScope.launch {
            val pId = repository.createNewProject(name, "Arduino Sketch", boardId)
            repository.selectProject(pId)
            navigateTo(Screen.EDITOR)
        }
    }

    fun importSketch(name: String, content: String, boardId: String = "arduino_uno") {
        viewModelScope.launch {
            val pId = repository.importSketchProject(name, content, boardId)
            repository.selectProject(pId)
            navigateTo(Screen.EDITOR)
        }
    }

    // Serial Monitor
    fun onSerialInputChange(text: String) {
        _serialInput.value = text
    }

    fun setBaudRate(rate: Int) {
        _selectedBaudRate.value = rate
    }

    fun setLineEnding(ending: LineEnding) {
        _selectedLineEnding.value = ending
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun toggleTimestamps() {
        _showTimestamps.value = !_showTimestamps.value
    }

    fun toggleHexMode() {
        _hexMode.value = !_hexMode.value
    }

    fun connectUsb(device: UsbDevice) {
        repository.usbHardwareManager.connect(device, _selectedBaudRate.value)
    }

    fun disconnectUsb() {
        repository.usbHardwareManager.disconnect()
    }

    fun requestUsbPermission(device: UsbDevice) {
        repository.usbHardwareManager.requestPermission(device)
    }

    fun refreshUsbDevices() {
        repository.usbHardwareManager.scanDevices()
    }

    fun sendSerial() {
        val text = _serialInput.value
        if (text.isNotEmpty()) {
            val success = repository.usbHardwareManager.send(text, _selectedLineEnding.value)
            if (success) {
                _serialInput.value = ""
            }
        }
    }

    fun clearSerialLog() {
        repository.usbHardwareManager.clearMessages()
    }

    // Settings
    fun updateSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.updateSetting(key, value)
        }
    }
}
