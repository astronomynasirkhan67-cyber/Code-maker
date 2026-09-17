package com.example.data.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.example.data.usb.UsbChipType
import com.example.data.usb.UsbSerialPort
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Android driver for Silicon Labs CP210x USB-to-UART bridges (CP2102, CP2104, CP2102N).
 * Widely used on ESP32 DevKit, NodeMCU, and various development boards.
 */
class Cp210xSerialPort(
    override val device: UsbDevice
) : UsbSerialPort {

    override val portName: String = device.deviceName
    override val chipType: UsbChipType = UsbChipType.CP210X

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null

    companion object {
        private const val REQ_TYPE_WRITE = 0x41
        private const val CP210X_IFC_ENABLE = 0x00
        private const val CP210X_SET_BAUDRATE = 0x1E
        private const val CP210X_SET_LINE_CTL = 0x03
        private const val CP210X_SET_MHS = 0x07
        private const val CP210X_PURGE = 0x12
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

            // Find bulk IN and bulk OUT endpoints
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

            // Enable UART interface
            val res = connection.controlTransfer(
                REQ_TYPE_WRITE,
                CP210X_IFC_ENABLE,
                0x0001,
                iface.id,
                null,
                0,
                1000
            )
            if (res < 0) {
                close()
                return false
            }

            // Default 115200 8N1
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
                connection?.controlTransfer(
                    REQ_TYPE_WRITE,
                    CP210X_IFC_ENABLE,
                    0x0000,
                    iface.id,
                    null,
                    0,
                    500
                )
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
        val iface = usbInterface ?: return false

        try {
            // Set baud rate (4-byte LE integer)
            val baudBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(baudRate).array()
            conn.controlTransfer(REQ_TYPE_WRITE, CP210X_SET_BAUDRATE, 0, iface.id, baudBytes, 4, 1000)

            // Line control: 8 data bits, no parity, 1 stop bit = 0x0800
            val lineCtl = when (dataBits) {
                5 -> 0x0500
                6 -> 0x0600
                7 -> 0x0700
                else -> 0x0800
            }
            conn.controlTransfer(REQ_TYPE_WRITE, CP210X_SET_LINE_CTL, lineCtl, iface.id, null, 0, 1000)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val iface = usbInterface ?: return false

        return try {
            // Mask: bit 8 (DTR mask 0x0100), bit 9 (RTS mask 0x0200) -> 0x0300
            // Value: bit 0 (DTR value 0x0001), bit 1 (RTS value 0x0002)
            var mhs = 0x0300
            if (dtr) mhs = mhs or 0x0001
            if (rts) mhs = mhs or 0x0002

            val res = conn.controlTransfer(REQ_TYPE_WRITE, CP210X_SET_MHS, mhs, iface.id, null, 0, 500)
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
        val conn = connection ?: return false
        val iface = usbInterface ?: return false
        return try {
            var flags = 0
            if (purgeTx) flags = flags or 0x0005
            if (purgeRx) flags = flags or 0x000A
            conn.controlTransfer(REQ_TYPE_WRITE, CP210X_PURGE, flags, iface.id, null, 0, 500) >= 0
        } catch (e: Exception) {
            false
        }
    }
}
