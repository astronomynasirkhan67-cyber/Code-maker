package com.example.data.usb

import android.hardware.usb.UsbDevice
import com.example.data.usb.drivers.CdcAcmSerialPort
import com.example.data.usb.drivers.Ch340SerialPort
import com.example.data.usb.drivers.Cp210xSerialPort
import com.example.data.usb.drivers.FtdiSerialPort

/**
 * Factory for creating the appropriate hardware driver for a connected USB-to-Serial device.
 */
object UsbSerialPortFactory {
    fun createPort(device: UsbDevice): UsbSerialPort {
        val chipType = UsbChipType.detect(device)
        return when (chipType) {
            UsbChipType.CP210X -> Cp210xSerialPort(device)
            UsbChipType.CH340, UsbChipType.CH9102 -> Ch340SerialPort(device)
            UsbChipType.FTDI -> FtdiSerialPort(device)
            UsbChipType.ESPRESSIF_CDC,
            UsbChipType.ARDUINO_CDC,
            UsbChipType.GENERIC_CDC,
            UsbChipType.UNKNOWN -> CdcAcmSerialPort(device)
        }
    }
}
