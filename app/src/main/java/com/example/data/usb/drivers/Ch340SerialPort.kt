package com.example.data.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.example.data.usb.UsbChipType
import com.example.data.usb.UsbSerialPort

/**
 * Native Android driver for WCH CH340 / CH341 / CH9102 USB-to-UART bridges.
 * Ubiquitous on inexpensive ESP32 boards, NodeMCU, and Arduino clones.
 */
class Ch340SerialPort(
    override val device: UsbDevice
) : UsbSerialPort {

    override val portName: String = device.deviceName
    override val chipType: UsbChipType = if (device.productId == 0x55D4) UsbChipType.CH9102 else UsbChipType.CH340

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null

    companion object {
        private const val REQ_TYPE_WRITE = 0x40
        private const val REQ_TYPE_READ = 0xC0
        private const val CMD_READ_REG = 0x95
        private const val CMD_WRITE_REG = 0x9A
        private const val CMD_MODEM_CTRL = 0xA4
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        try {
            this.connection = connection
            if (device.interfaceCount == 0) return false

            val iface = device.getInterface(0)
            if (!connection.claimInterface(iface, true)) {
                return false
            }
            this.usbInterface = iface

            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        readEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        writeEndpoint = ep
                    }
                }
            }

            if (readEndpoint == null || writeEndpoint == null) {
                close()
                return false
            }

            // CH340 initialization handshake sequence
            val buf = ByteArray(2)
            connection.controlTransfer(REQ_TYPE_READ, CMD_READ_REG, 0, 0, buf, 2, 1000)
            connection.controlTransfer(REQ_TYPE_WRITE, 0xA1, 0, 0, null, 0, 1000)
            connection.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x1312, 0xD982, null, 0, 1000)
            connection.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x0F2C, 0x0004, null, 0, 1000)
            connection.controlTransfer(REQ_TYPE_READ, CMD_READ_REG, 0x2518, 0, buf, 2, 1000)
            connection.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x2518, 0x0050, null, 0, 1000)

            // Configure default 115200 8N1
            setParameters(115200, 8, 1, 0)
            setDtrRts(dtr = false, rts = false)
            return true
        } catch (e: Exception) {
            close()
            return false
        }
    }

    override fun close() {
        try {
            usbInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
        } catch (_: Exception) {}
        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        usbInterface = null
        readEndpoint = null
        writeEndpoint = null
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        val conn = connection ?: return false

        try {
            val (factor, divisor) = when (baudRate) {
                9600 -> 0xB282 to 0x0007
                19200 -> 0xD982 to 0x0007
                38400 -> 0x6483 to 0x0007
                57600 -> 0x9883 to 0x0007
                115200 -> 0xCC83 to 0x0007
                230400 -> 0xE683 to 0x0007
                460800 -> 0xF383 to 0x0007
                921600 -> 0xF387 to 0x0007
                else -> 0xCC83 to 0x0007 // Default 115200
            }

            conn.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x1312, factor, null, 0, 1000)
            conn.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x0F2C, divisor, null, 0, 1000)
            conn.controlTransfer(REQ_TYPE_WRITE, CMD_WRITE_REG, 0x2518, 0x00C3, null, 0, 1000)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false

        return try {
            // In CH340 modem control:
            // DTR = bit 5 (0x20, active low)
            // RTS = bit 6 (0x40, active low)
            var controlVal = 0
            if (!dtr) controlVal = controlVal or 0x20
            if (!rts) controlVal = controlVal or 0x40

            val res = conn.controlTransfer(REQ_TYPE_WRITE, CMD_MODEM_CTRL, controlVal, 0, null, 0, 500)
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
        return connection != null && usbInterface != null && readEndpoint != null && writeEndpoint != null
    }

    override fun purgeHwBuffers(purgeRx: Boolean, purgeTx: Boolean): Boolean {
        return true
    }
}
