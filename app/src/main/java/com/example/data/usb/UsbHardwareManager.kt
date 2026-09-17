package com.example.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.upload.AvrStk500Uploader
import com.example.upload.Esp32Uploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UsbBoardInfo(
    val device: UsbDevice,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val description: String,
    val hasPermission: Boolean,
    val chipType: UsbChipType = UsbChipType.detect(device),
    val isEsp32CommonChip: Boolean = chipType.isEsp32Common
)

sealed class SerialConnectionState {
    data object Disconnected : SerialConnectionState()
    data class Connecting(val deviceName: String) : SerialConnectionState()
    data class Connected(val deviceName: String, val baudRate: Int) : SerialConnectionState()
    data class Error(val message: String) : SerialConnectionState()
}

data class SerialMessage(
    val timestamp: String,
    val text: String,
    val isOutgoing: Boolean = false
)

enum class LineEnding(val label: String, val value: String) {
    NONE("No line ending", ""),
    NEWLINE("Newline (\\n)", "\n"),
    CARRIAGE_RETURN("Carriage return (\\r)", "\r"),
    BOTH("Both NL & CR (\\r\\n)", "\r\n")
}

sealed class UploadResult {
    data class Success(val bytesUploaded: Int, val log: String) : UploadResult()
    data class Failed(val error: String, val log: String) : UploadResult()
}

class UsbHardwareManager(private val context: Context) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<UsbBoardInfo>>(emptyList())
    val connectedDevices: StateFlow<List<UsbBoardInfo>> = _connectedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<SerialConnectionState>(SerialConnectionState.Disconnected)
    val connectionState: StateFlow<SerialConnectionState> = _connectionState.asStateFlow()

    private val _serialMessages = MutableStateFlow<List<SerialMessage>>(emptyList())
    val serialMessages: StateFlow<List<SerialMessage>> = _serialMessages.asStateFlow()

    private val _serialEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val serialEvents: SharedFlow<String> = _serialEvents.asSharedFlow()

    // Last attached device notice for banner
    private val _lastAttachedDevice = MutableStateFlow<UsbBoardInfo?>(null)
    val lastAttachedDevice: StateFlow<UsbBoardInfo?> = _lastAttachedDevice.asStateFlow()

    private var activePort: UsbSerialPort? = null
    private var readJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.mobilearduinoide.USB_PERMISSION"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        scanDevices()

                        if (granted && device != null) {
                            appendLog("USB permission granted for ${getDeviceDescription(device)}")
                            _lastAttachedDevice.value = _connectedDevices.value.find { it.device.deviceId == device.deviceId }
                        } else {
                            appendLog("USB permission denied by user.")
                            _connectionState.value = SerialConnectionState.Error("USB permission denied by user")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    appendLog("USB device attached.")
                    scanDevices()
                    val latest = _connectedDevices.value.firstOrNull()
                    _lastAttachedDevice.value = latest
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    appendLog("USB device detached.")
                    if (activePort?.device?.deviceId == device?.deviceId) {
                        disconnect()
                    }
                    scanDevices()
                    if (_lastAttachedDevice.value?.device?.deviceId == device?.deviceId) {
                        _lastAttachedDevice.value = null
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
        } catch (_: Exception) {}
        scanDevices()
    }

    fun dismissAttachedBanner() {
        _lastAttachedDevice.value = null
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }

    fun scanDevices() {
        try {
            val deviceList = usbManager.deviceList
            val boards = deviceList.values.map { device ->
                val hasPermission = usbManager.hasPermission(device)
                val chipType = UsbChipType.detect(device)
                UsbBoardInfo(
                    device = device,
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    description = getDeviceDescription(device),
                    hasPermission = hasPermission,
                    chipType = chipType,
                    isEsp32CommonChip = chipType.isEsp32Common
                )
            }
            _connectedDevices.value = boards
        } catch (e: Exception) {
            _connectedDevices.value = emptyList()
        }
    }

    fun requestPermission(device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            appendLog("Error requesting USB permission: ${e.message}")
        }
    }

    fun connect(device: UsbDevice, baudRate: Int = 115200) {
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = SerialConnectionState.Error("USB permission not granted")
            requestPermission(device)
            return
        }

        val desc = getDeviceDescription(device)
        _connectionState.value = SerialConnectionState.Connecting(desc)

        try {
            disconnect()

            val connection = usbManager.openDevice(device)
                ?: throw IllegalStateException("Could not open USB connection to device")

            val port = UsbSerialPortFactory.createPort(device)
            if (!port.open(connection)) {
                connection.close()
                throw IllegalStateException("Failed to initialize USB serial port registers for ${port.chipType.label}")
            }

            port.setParameters(baudRate, 8, 1, 0)
            activePort = port

            _connectionState.value = SerialConnectionState.Connected(desc, baudRate)
            appendLog("Connected to $desc at $baudRate baud.")

            startReadLoop()
        } catch (e: Exception) {
            _connectionState.value = SerialConnectionState.Error(e.message ?: "Connection failed")
            appendLog("USB Connection error: ${e.message}")
            disconnect()
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null

        try {
            activePort?.close()
        } catch (_: Exception) {}
        activePort = null

        if (_connectionState.value !is SerialConnectionState.Disconnected) {
            _connectionState.value = SerialConnectionState.Disconnected
            appendLog("USB Serial Disconnected.")
        }
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = coroutineScope.launch {
            val buffer = ByteArray(1024)
            val lineBuffer = StringBuilder()

            while (isActive) {
                val port = activePort
                if (port == null || !port.isOpen()) {
                    break
                }

                try {
                    val bytesRead = port.read(buffer, 200)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        _serialEvents.emit(text)

                        lineBuffer.append(text)
                        var nlIndex = lineBuffer.indexOf('\n')
                        while (nlIndex != -1) {
                            val line = lineBuffer.substring(0, nlIndex).trimEnd('\r')
                            if (line.isNotEmpty()) {
                                appendLog(line, isOutgoing = false)
                            }
                            lineBuffer.delete(0, nlIndex + 1)
                            nlIndex = lineBuffer.indexOf('\n')
                        }
                    } else if (bytesRead < 0) {
                        // Error or device disconnected
                        appendLog("USB Serial read stream closed.")
                        disconnect()
                        break
                    }
                } catch (e: Exception) {
                    appendLog("Serial read exception: ${e.message}")
                    disconnect()
                    break
                }
            }
        }
    }

    fun sendData(data: ByteArray): Boolean {
        val port = activePort ?: return false
        return try {
            val res = port.write(data, 1000)
            res >= 0
        } catch (e: Exception) {
            false
        }
    }

    fun sendString(text: String, lineEnding: LineEnding = LineEnding.NEWLINE): Boolean {
        val payload = text + lineEnding.value
        val success = sendData(payload.toByteArray(Charsets.UTF_8))
        if (success) {
            appendLog(text, isOutgoing = true)
        }
        return success
    }

    fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        return activePort?.setDtrRts(dtr, rts) ?: false
    }

    private fun appendLog(text: String, isOutgoing: Boolean = false) {
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val msg = SerialMessage(
            timestamp = timeFormat.format(Date()),
            text = text,
            isOutgoing = isOutgoing
        )
        val current = _serialMessages.value
        _serialMessages.value = if (current.size > 500) {
            current.drop(current.size - 499) + msg
        } else {
            current + msg
        }
    }

    fun send(text: String, lineEnding: LineEnding = LineEnding.NEWLINE): Boolean {
        return sendString(text, lineEnding)
    }

    fun clearMessages() {
        clearLog()
    }

    fun clearLog() {
        _serialMessages.value = emptyList()
    }

    /**
     * Executes robust firmware flashing without crashing.
     * Supports ESP32 ROM bootloader protocol and AVR STK500 protocol.
     */
    suspend fun uploadFirmware(
        device: UsbDevice?,
        targetFqbn: String,
        compiledBytes: ByteArray?,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onTerminalLog: (TerminalLine) -> Unit = {}
    ): UploadResult {
        val logBuilder = StringBuilder()
        fun log(msg: String, type: TerminalLineType = TerminalLineType.STDOUT) {
            logBuilder.appendLine(msg)
            onTerminalLog(TerminalLine(type = type, text = msg))
        }

        log("[Upload Process Started]")
        if (device == null) {
            val err = "Upload failed: No USB board detected on USB OTG port."
            log(err, TerminalLineType.ERROR)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        if (!usbManager.hasPermission(device)) {
            val err = "Upload failed: USB permission not granted for ${getDeviceDescription(device)}."
            log(err, TerminalLineType.ERROR)
            requestPermission(device)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        if (compiledBytes == null || compiledBytes.isEmpty()) {
            val err = "Upload failed: No compiled binary (.bin / .hex) available.\n" +
                    "You must first compile the sketch and verify zero errors before uploading."
            log(err, TerminalLineType.ERROR)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        // Pause serial monitor reading during upload
        val wasConnected = activePort != null
        disconnect()
        delay(100)

        var port: UsbSerialPort? = null
        return try {
            val connection = usbManager.openDevice(device)
                ?: return UploadResult.Failed("Could not open USB connection for flashing.", logBuilder.toString())

            port = UsbSerialPortFactory.createPort(device)
            if (!port.open(connection)) {
                connection.close()
                return UploadResult.Failed("Failed to claim USB interface and initialize port registers.", logBuilder.toString())
            }

            val isEsp = targetFqbn.contains("esp32", ignoreCase = true) || targetFqbn.contains("esp8266", ignoreCase = true)

            val uploadResult = if (isEsp) {
                log("Detected Espressif target ($targetFqbn). Launching ESP32 Bootloader Uploader...", TerminalLineType.INFO)
                val uploader = Esp32Uploader(
                    serialPort = port,
                    onLog = { onTerminalLog(it) },
                    onProgress = { p, s -> onProgress(p, s) }
                )
                uploader.upload(compiledBytes)
            } else {
                log("Detected AVR / Arduino target ($targetFqbn). Launching AVR STK500 Uploader...", TerminalLineType.INFO)
                val uploader = AvrStk500Uploader(
                    serialPort = port,
                    onLog = { onTerminalLog(it) },
                    onProgress = { p, s -> onProgress(p, s) }
                )
                uploader.upload(compiledBytes)
            }

            if (uploadResult.success) {
                UploadResult.Success(compiledBytes.size, logBuilder.toString())
            } else {
                UploadResult.Failed(uploadResult.error ?: "Upload failed", logBuilder.toString())
            }

        } catch (e: Exception) {
            val err = "Upload exception: ${e.message}"
            log(err, TerminalLineType.ERROR)
            UploadResult.Failed(err, logBuilder.toString())
        } finally {
            try {
                port?.close()
            } catch (_: Exception) {}
        }
    }

    fun getDeviceDescription(device: UsbDevice): String {
        val chipType = UsbChipType.detect(device)
        val vidHex = "0x${Integer.toHexString(device.vendorId).uppercase()}"
        val pidHex = "0x${Integer.toHexString(device.productId).uppercase()}"
        return "${chipType.label} (VID: $vidHex, PID: $pidHex)"
    }
}
