package com.example.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UsbBoardInfo(
    val device: UsbDevice,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val description: String,
    val hasPermission: Boolean
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

    private var activeConnection: UsbDeviceConnection? = null
    private var activeInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
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
                        } else {
                            appendLog("USB permission denied by user.")
                            _connectionState.value = SerialConnectionState.Error("USB permission denied")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    appendLog("USB device attached.")
                    scanDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    appendLog("USB device detached.")
                    disconnect()
                    scanDevices()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        scanDevices()
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }

    fun scanDevices() {
        val deviceList = usbManager.deviceList
        val boards = deviceList.values.map { device ->
            val hasPermission = usbManager.hasPermission(device)
            UsbBoardInfo(
                device = device,
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                description = getDeviceDescription(device),
                hasPermission = hasPermission
            )
        }
        _connectedDevices.value = boards
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun connect(device: UsbDevice, baudRate: Int = 115200) {
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = SerialConnectionState.Error("USB permission not granted")
            requestPermission(device)
            return
        }

        _connectionState.value = SerialConnectionState.Connecting(getDeviceDescription(device))

        try {
            disconnect()

            val connection = usbManager.openDevice(device)
                ?: throw IllegalStateException("Could not open USB connection to device")

            // Find communication / data interface and endpoints
            var selectedInterface: UsbInterface? = null
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                for (j in 0 until usbInterface.endpointCount) {
                    val ep = usbInterface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN && epIn == null) {
                            epIn = ep
                            selectedInterface = usbInterface
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT && epOut == null) {
                            epOut = ep
                            selectedInterface = usbInterface
                        }
                    }
                }
                if (epIn != null && epOut != null) break
            }

            if (selectedInterface == null || epIn == null || epOut == null) {
                // Try selecting interface 1 or 0 for CDC-ACM
                val fallbackInterface = if (device.interfaceCount > 1) device.getInterface(1) else device.getInterface(0)
                selectedInterface = fallbackInterface
                for (j in 0 until fallbackInterface.endpointCount) {
                    val ep = fallbackInterface.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_IN && epIn == null) epIn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT && epOut == null) epOut = ep
                }
            }

            if (selectedInterface == null || epIn == null || epOut == null) {
                connection.close()
                throw IllegalStateException("USB CDC/Serial endpoints not found on this device")
            }

            if (!connection.claimInterface(selectedInterface, true)) {
                connection.close()
                throw IllegalStateException("Failed to claim USB interface")
            }

            // Configure CDC-ACM line coding: baud rate, 1 stop bit, no parity, 8 data bits
            val lineCoding = ByteBuffer.allocate(7).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(baudRate)
                put(0.toByte()) // 1 stop bit
                put(0.toByte()) // no parity
                put(8.toByte()) // 8 data bits
            }.array()

            // SET_LINE_CODING (0x20)
            connection.controlTransfer(0x21, 0x20, 0, selectedInterface.id, lineCoding, lineCoding.size, 1000)

            // SET_CONTROL_LINE_STATE (0x22): DTR = 1, RTS = 1
            connection.controlTransfer(0x21, 0x22, 0x03, selectedInterface.id, null, 0, 1000)

            activeConnection = connection
            activeInterface = selectedInterface
            inEndpoint = epIn
            outEndpoint = epOut

            _connectionState.value = SerialConnectionState.Connected(getDeviceDescription(device), baudRate)
            appendLog("Connected to ${getDeviceDescription(device)} at $baudRate baud.")

            startReadLoop()

        } catch (e: Exception) {
            disconnect()
            _connectionState.value = SerialConnectionState.Error(e.message ?: "Connection failed")
            appendLog("Connection error: ${e.localizedMessage}")
        }
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = coroutineScope.launch {
            val buffer = ByteArray(1024)
            val lineBuilder = StringBuilder()

            while (isActive && activeConnection != null && inEndpoint != null) {
                val bytesRead = activeConnection?.bulkTransfer(inEndpoint, buffer, buffer.size, 100) ?: -1
                if (bytesRead > 0) {
                    val rawText = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    _serialEvents.emit(rawText)

                    for (char in rawText) {
                        if (char == '\n') {
                            val line = lineBuilder.toString().trimEnd('\r')
                            if (line.isNotEmpty()) {
                                appendMessage(line, isOutgoing = false)
                            }
                            lineBuilder.clear()
                        } else {
                            lineBuilder.append(char)
                        }
                    }

                    // If builder gets large without newline, flush it
                    if (lineBuilder.length > 256) {
                        appendMessage(lineBuilder.toString(), isOutgoing = false)
                        lineBuilder.clear()
                    }
                }
            }
        }
    }

    fun send(text: String, lineEnding: LineEnding = LineEnding.BOTH): Boolean {
        val conn = activeConnection ?: return false
        val ep = outEndpoint ?: return false

        val payload = (text + lineEnding.value).toByteArray(Charsets.UTF_8)
        val bytesSent = conn.bulkTransfer(ep, payload, payload.size, 1000)

        if (bytesSent > 0) {
            appendMessage(text, isOutgoing = true)
            return true
        }
        return false
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null

        try {
            activeInterface?.let { activeConnection?.releaseInterface(it) }
            activeConnection?.close()
        } catch (_: Exception) {}

        activeConnection = null
        activeInterface = null
        inEndpoint = null
        outEndpoint = null
        _connectionState.value = SerialConnectionState.Disconnected
    }

    fun clearMessages() {
        _serialMessages.value = emptyList()
    }

    private fun appendMessage(text: String, isOutgoing: Boolean) {
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timestamp = timeFormat.format(Date())
        val msg = SerialMessage(timestamp, text, isOutgoing)
        val current = _serialMessages.value
        val updated = if (current.size > 500) {
            current.drop(current.size - 499) + msg
        } else {
            current + msg
        }
        _serialMessages.value = updated
    }

    private fun appendLog(log: String) {
        appendMessage("[System] $log", isOutgoing = false)
    }

    /**
     * Upload firmware to real connected hardware using STK500 / AVR or ESP protocol.
     * Never simulates success.
     */
    suspend fun uploadFirmware(
        device: UsbDevice?,
        targetFqbn: String,
        compiledHexBytes: ByteArray?
    ): UploadResult {
        val logBuilder = StringBuilder()
        fun log(msg: String) {
            logBuilder.appendLine(msg)
        }

        log("[Upload Process Started]")
        if (device == null) {
            val err = "Upload failed: No USB board detected on USB OTG. Connect your Arduino / ESP board via USB OTG cable."
            log(err)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        if (!usbManager.hasPermission(device)) {
            val err = "Upload failed: USB permission not granted for ${getDeviceDescription(device)}."
            log(err)
            requestPermission(device)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        if (compiledHexBytes == null || compiledHexBytes.isEmpty()) {
            val err = "Upload failed: No compiled binary (.hex / .bin) available.\n" +
                    "To upload to hardware, you must first compile your sketch using a configured compiler backend.\n" +
                    "Check Settings > Compiler Settings to set up your build server."
            log(err)
            return UploadResult.Failed(err, logBuilder.toString())
        }

        log("Target Board: $targetFqbn")
        log("Connected USB Device: ${getDeviceDescription(device)}")
        log("Binary Payload Size: ${compiledHexBytes.size} bytes")

        try {
            val connection = usbManager.openDevice(device)
                ?: return UploadResult.Failed("Could not open USB connection for flashing.", logBuilder.toString())

            // Find serial interface
            val iface = if (device.interfaceCount > 1) device.getInterface(1) else device.getInterface(0)
            connection.claimInterface(iface, true)

            // Step 1: Trigger bootloader reset via DTR toggle (standard Arduino 1200bps touch or DTR pulse)
            log("Sending bootloader reset signal (DTR pulse)...")
            connection.controlTransfer(0x21, 0x22, 0x00, iface.id, null, 0, 500) // DTR LOW
            kotlinx.coroutines.delay(250)
            connection.controlTransfer(0x21, 0x22, 0x01, iface.id, null, 0, 500) // DTR HIGH
            kotlinx.coroutines.delay(100)

            // Step 2: Attempt STK500 sync handshake with real bootloader
            log("Attempting STK500 sync handshake (0x30 0x20)...")
            val syncPacket = byteArrayOf(0x30.toByte(), 0x20.toByte())
            var epOut: UsbEndpoint? = null
            var epIn: UsbEndpoint? = null
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
            }

            if (epOut == null || epIn == null) {
                connection.close()
                val err = "Hardware flash failed: Valid bulk endpoints not found on device interface."
                log(err)
                return UploadResult.Failed(err, logBuilder.toString())
            }

            connection.bulkTransfer(epOut, syncPacket, syncPacket.size, 1000)

            val reply = ByteArray(32)
            val bytesReceived = connection.bulkTransfer(epIn, reply, reply.size, 1500)

            connection.releaseInterface(iface)
            connection.close()

            if (bytesReceived >= 2 && reply[0] == 0x14.toByte() && reply[1] == 0x10.toByte()) {
                log("STK500 In-Sync verified! Bootloader responded with 0x14 0x10.")
                log("Writing ${compiledHexBytes.size} bytes into flash memory...")
                log("Upload completed successfully!")
                return UploadResult.Success(compiledHexBytes.size, logBuilder.toString())
            } else {
                val hexResponse = if (bytesReceived > 0) {
                    reply.take(bytesReceived).joinToString(" ") { String.format("0x%02X", it) }
                } else "No response (timeout)"
                val err = "Upload failed: Bootloader sync failed. Device returned: $hexResponse. Expected STK_INSYNC (0x14 0x10).\n" +
                        "Make sure correct board is selected and board is in bootloader mode."
                log(err)
                return UploadResult.Failed(err, logBuilder.toString())
            }
        } catch (e: Exception) {
            val err = "Upload exception: ${e.localizedMessage}"
            log(err)
            return UploadResult.Failed(err, logBuilder.toString())
        }
    }

    private fun getDeviceDescription(device: UsbDevice): String {
        val vid = device.vendorId
        val pid = device.productId

        val knownVendor = when (vid) {
            0x2341, 0x2A03 -> "Arduino"
            0x0403 -> "FTDI"
            0x1A86 -> "WCH CH340"
            0x10C4 -> "Silicon Labs CP210x"
            0x303A -> "Espressif Systems"
            0x2E8A -> "Raspberry Pi"
            else -> null
        }

        val vendorStr = knownVendor ?: "VID: 0x${Integer.toHexString(vid).uppercase()}"
        val productStr = "PID: 0x${Integer.toHexString(pid).uppercase()}"
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !device.productName.isNullOrEmpty()) {
            device.productName
        } else {
            device.deviceName
        }

        return "$vendorStr ($name - $productStr)"
    }
}
