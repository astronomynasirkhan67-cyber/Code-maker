package com.example.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

/**
 * Categorization of USB-to-Serial converter chips commonly found on ESP32 and Arduino boards.
 */
enum class UsbChipType(val label: String, val isEsp32Common: Boolean) {
    CP210X("Silicon Labs CP210x USB to UART Bridge", true),
    CH340("WCH CH340 / CH341 USB to Serial Bridge", true),
    CH9102("WCH CH9102 USB to Serial Bridge", true),
    FTDI("FTDI USB Serial Converter", true),
    ESPRESSIF_CDC("Espressif Native USB CDC / JTAG", true),
    ARDUINO_CDC("Arduino USB CDC-ACM Serial", false),
    GENERIC_CDC("Standard USB CDC-ACM Serial", false),
    UNKNOWN("USB Serial Device", false);

    companion object {
        fun fromVidPid(vid: Int, pid: Int): UsbChipType {
            return when (vid) {
                0x10C4 -> CP210X // Silicon Labs (CP2102, CP2104, CP2102N)
                0x1A86 -> {
                    when (pid) {
                        0x55D4 -> CH9102 // Common on newer ESP32 boards
                        else -> CH340   // 0x7523, 0x5523, 0x7522, etc.
                    }
                }
                0x0403 -> FTDI   // FTDI (0x6001, 0x6010, 0x6014, 0x6015)
                0x303A -> ESPRESSIF_CDC // Espressif native USB (ESP32-S2, ESP32-S3, ESP32-C3)
                0x2341, 0x2A03 -> ARDUINO_CDC // Arduino LLC & Arduino Srl
                0x2E8A -> GENERIC_CDC // Raspberry Pi Pico
                0x067B -> GENERIC_CDC // Prolific PL2303
                else -> UNKNOWN
            }
        }

        fun detect(device: UsbDevice): UsbChipType {
            val vid = device.vendorId
            val pid = device.productId

            val byId = fromVidPid(vid, pid)
            if (byId != UNKNOWN) {
                return byId
            }

            // Inspect USB interface classes
            var hasCdc = false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                    iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
                ) {
                    hasCdc = true
                    break
                }
            }
            return if (hasCdc) GENERIC_CDC else UNKNOWN
        }
    }
}
