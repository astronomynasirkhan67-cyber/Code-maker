package com.example.data.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.example.data.usb.UsbChipType
import com.example.data.usb.UsbSerialPort

/**
 * Native Android driver for standard USB CDC-ACM serial devices.
 * Supports Espressif Native USB (ESP32-S2, ESP32-S3, ESP32-C3), Arduino Uno R3, Mega, Leonardo, RP2040.
 */
class CdcAcmSerialPort(
    override val device: UsbDevice
) : UsbSerialPort {

    override val portName: String = device.deviceName
    override val chipType: UsbChipType = UsbChipType.detect(device)

    private var connection: UsbDeviceConnection? = null
    private var controlInterface: UsbInterface? = null
    private var dataInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null

    companion object {
        private const val USB_RECIP_INTERFACE = 0x01
        private const val USB_RT_ACM = UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE
        private const val SET_LINE_CODING = 0x20
        private const val SET_CONTROL_LINE_STATE = 0x22
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        try {
            this.connection = connection
            if (device.interfaceCount == 0) return false

            var ctrlIface: UsbInterface? = null
            var dataIface: UsbInterface? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                when (iface.interfaceClass) {
                    UsbConstants.USB_CLASS_COMM -> ctrlIface = iface
                    UsbConstants.USB_CLASS_CDC_DATA -> dataIface = iface
                }
            }

            // Fallbacks if classes are vendor-specific
            if (ctrlIface == null && device.interfaceCount > 0) {
                ctrlIface = device.getInterface(0)
            }
            if (dataIface == null) {
                dataIface = if (device.interfaceCount > 1) device.getInterface(1) else ctrlIface
            }

            this.controlInterface = ctrlIface
            this.dataInterface = dataIface ?: return false

            if (ctrlIface != null && !connection.claimInterface(ctrlIface, true)) {
                return false
            }
            if (dataIface != ctrlIface && !connection.claimInterface(dataIface, true)) {
                ctrlIface?.let { connection.releaseInterface(it) }
                return false
            }

            // Find bulk IN and bulk OUT endpoints on data interface
            for (i in 0 until dataIface.endpointCount) {
                val ep = dataIface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        readEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        writeEndpoint = ep
                    }
                }
            }

            // If not found on data interface, try control interface
            if ((readEndpoint == null || writeEndpoint == null) && ctrlIface != null) {
                for (i in 0 until ctrlIface.endpointCount) {
                    val ep = ctrlIface.getEndpoint(i)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN && readEndpoint == null) {
                            readEndpoint = ep
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT && writeEndpoint == null) {
                            writeEndpoint = ep
                        }
                    }
                }
            }

            if (readEndpoint == null || writeEndpoint == null) {
                close()
                return false
            }

            setParameters(115200, 8, 1, 0)
            setDtrRts(dtr = true, rts = true)
            return true
        } catch (e: Exception) {
            close()
            return false
        }
    }

    override fun close() {
        try {
            dataInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
            if (controlInterface != dataInterface) {
                controlInterface?.let { iface ->
                    connection?.releaseInterface(iface)
                }
            }
        } catch (_: Exception) {}
        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        controlInterface = null
        dataInterface = null
        readEndpoint = null
        writeEndpoint = null
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        val conn = connection ?: return false
        val iface = controlInterface ?: return false

        try {
            val lineCoding = ByteArray(7)
            lineCoding[0] = (baudRate and 0xFF).toByte()
            lineCoding[1] = ((baudRate shr 8) and 0xFF).toByte()
            lineCoding[2] = ((baudRate shr 16) and 0xFF).toByte()
            lineCoding[3] = ((baudRate shr 24) and 0xFF).toByte()
            lineCoding[4] = (stopBits - 1).toByte() // 0 = 1 stop bit
            lineCoding[5] = parity.toByte()        // 0 = none
            lineCoding[6] = dataBits.toByte()      // 8 data bits

            val res = conn.controlTransfer(USB_RT_ACM, SET_LINE_CODING, 0, iface.id, lineCoding, 7, 1000)
            return res >= 0
        } catch (e: Exception) {
            return false
        }
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val iface = controlInterface ?: return false

        return try {
            val value = (if (dtr) 1 else 0) or (if (rts) 2 else 0)
            val res = conn.controlTransfer(USB_RT_ACM, SET_CONTROL_LINE_STATE, value, iface.id, null, 0, 500)
            res >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = readEndpoint ?: return -1

        return try {
            val len = conn.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
            if (len < 0) 0 else len
        } catch (e: Exception) {
            -1
        }
    }

    override fun write(data: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = writeEndpoint ?: return -1

        return try {
            val res = conn.bulkTransfer(ep, data, data.size, timeoutMs)
            if (res < 0) -1 else res
        } catch (e: Exception) {
            -1
        }
    }

    override fun isOpen(): Boolean {
        return connection != null && readEndpoint != null && writeEndpoint != null
    }

    override fun purgeHwBuffers(purgeRx: Boolean, purgeTx: Boolean): Boolean {
        return true
    }
}
