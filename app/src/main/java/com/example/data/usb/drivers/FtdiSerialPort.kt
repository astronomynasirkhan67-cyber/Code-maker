package com.example.data.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.example.data.usb.UsbChipType
import com.example.data.usb.UsbSerialPort

/**
 * Native Android driver for FTDI USB-to-UART converters (FT232R, FT2232, etc.).
 */
class FtdiSerialPort(
    override val device: UsbDevice
) : UsbSerialPort {

    override val portName: String = device.deviceName
    override val chipType: UsbChipType = UsbChipType.FTDI

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null

    companion object {
        private const val REQ_TYPE_WRITE = 0x40
        private const val SIO_RESET = 0
        private const val SIO_MODEM_CTRL = 1
        private const val SIO_SET_BAUD_RATE = 3
        private const val SIO_SET_DATA = 4
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

            // Reset FTDI port
            connection.controlTransfer(REQ_TYPE_WRITE, SIO_RESET, 0, 0, null, 0, 1000)
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
            val divisor = when (baudRate) {
                9600 -> 0x0138
                19200 -> 0x009C
                38400 -> 0x004E
                57600 -> 0x0034
                115200 -> 0x001A
                230400 -> 0x000D
                else -> 0x001A
            }
            conn.controlTransfer(REQ_TYPE_WRITE, SIO_SET_BAUD_RATE, divisor, 0, null, 0, 1000)

            // 8 data bits, no parity, 1 stop bit = 8
            conn.controlTransfer(REQ_TYPE_WRITE, SIO_SET_DATA, dataBits, 0, null, 0, 1000)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false

        return try {
            val dtrFlag = if (dtr) 0x0101 else 0x0100
            val rtsFlag = if (rts) 0x0202 else 0x0200
            val res = conn.controlTransfer(REQ_TYPE_WRITE, SIO_MODEM_CTRL, dtrFlag or rtsFlag, 0, null, 0, 500)
            res >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = readEndpoint ?: return -1

        return try {
            // FTDI chips include a 2-byte header with status flags
            val rawBuf = ByteArray(buffer.size + 2)
            val len = conn.bulkTransfer(ep, rawBuf, rawBuf.size, timeoutMs)
            if (len <= 2) {
                0
            } else {
                val payloadLen = len - 2
                System.arraycopy(rawBuf, 2, buffer, 0, payloadLen)
                payloadLen
            }
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
        return try {
            if (purgeRx) conn.controlTransfer(REQ_TYPE_WRITE, SIO_RESET, 1, 0, null, 0, 500)
            if (purgeTx) conn.controlTransfer(REQ_TYPE_WRITE, SIO_RESET, 2, 0, null, 0, 500)
            true
        } catch (e: Exception) {
            false
        }
    }
}
