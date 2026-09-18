package com.example.data.model

/**
 * Strict workflow and hardware states for Mobile Arduino IDE.
 * Accurately reflects device connection, permissions, compilation, and upload stages.
 */
enum class IdeWorkflowState(val title: String, val description: String) {
    NO_USB_DEVICE(
        title = "No USB Device",
        description = "Connect an ESP32 or Arduino board via USB OTG adapter."
    ),
    USB_DEVICE_DETECTED(
        title = "USB Device Detected",
        description = "USB hardware detected on the OTG bus."
    ),
    USB_PERMISSION_REQUIRED(
        title = "USB Permission Required",
        description = "Android requires explicit user permission to access the USB serial device."
    ),
    USB_PERMISSION_GRANTED(
        title = "USB Permission Granted",
        description = "USB serial device authorized for hardware communication."
    ),
    BOARD_SELECTED(
        title = "Board Selected",
        description = "Target board model has been configured."
    ),
    SYNTAX_ERROR(
        title = "Syntax Error",
        description = "Syntax errors detected during sketch code validation."
    ),
    COMPILER_MISSING(
        title = "ESP32 Compiler Toolchain Not Installed",
        description = "ESP32 compiler toolchain is not available. Configure an Arduino CLI build server in Settings or install the core."
    ),
    CORE_MISSING(
        title = "ESP32 Platform Core Not Installed",
        description = "Target board platform core is not installed. Open Boards Manager to install it."
    ),
    COMPILING(
        title = "Compiling",
        description = "Processing and compiling sketch source code."
    ),
    COMPILE_FAILED(
        title = "Compilation Failed",
        description = "Toolchain compiler failed to build the sketch binaries."
    ),
    COMPILE_SUCCESS(
        title = "Compilation Successful",
        description = "Sketch compiled successfully and flashable binary is ready."
    ),
    FIRMWARE_READY(
        title = "Firmware Ready",
        description = "Firmware binaries are verified and ready to flash."
    ),
    UPLOADING(
        title = "Uploading Firmware",
        description = "Flashing firmware to microcontroller via USB OTG."
    ),
    UPLOAD_FAILED(
        title = "USB Upload Failed",
        description = "Failed to sync or flash firmware to microcontroller."
    ),
    UPLOAD_SUCCESS(
        title = "USB Upload Successful",
        description = "Firmware written and verified on microcontroller."
    ),
    USB_DISCONNECTED(
        title = "USB Disconnected",
        description = "The USB device was disconnected from the OTG port."
    )
}
