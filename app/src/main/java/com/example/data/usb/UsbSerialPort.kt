package com.example.data.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

/**
 * Common interface for USB-to-Serial converter chips (CP210x, CH340, FTDI, CDC-ACM).
 * Provides robust communication, baud rate configuration, DTR/RTS pin control for bootloaders,
 * and safe read/write operations without app crashes.
 */
interface UsbSerialPort {
    val device: UsbDevice
    val portName: String
    val chipType: UsbChipType

    /**
     * Opens the device connection, claims necessary USB interfaces, and initializes UART registers.
     * Returns true if open succeeded, false otherwise.
     */
    fun open(connection: UsbDeviceConnection): Boolean

    /**
     * Closes the connection and releases claimed interfaces.
     */
    fun close()

    /**
     * Configures UART parameters (baud rate, data bits, stop bits, parity).
     */
    fun setParameters(baudRate: Int, dataBits: Int = 8, stopBits: Int = 1, parity: Int = 0): Boolean

    /**
     * Sets DTR (Data Terminal Ready) and RTS (Request To Send) control lines.
     * Essential for triggering ESP32 auto-bootloader mode.
     */
    fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean

    /**
     * Reads incoming serial bytes into [buffer].
     * Returns number of bytes read, or 0 if timeout/no data, or -1 on disconnection/error.
     */
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    /**
     * Writes serial bytes to the device.
     * Returns number of bytes written, or -1 on error.
     */
    fun write(data: ByteArray, timeoutMs: Int): Int

    /**
     * Returns true if the port is currently open and valid.
     */
    fun isOpen(): Boolean

    /**
     * Flushes buffers if supported.
     */
    fun purgeHwBuffers(purgeRx: Boolean = true, purgeTx: Boolean = true): Boolean
}
