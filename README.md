# Mobile Arduino IDE

A full-featured, offline-first Android application inspired by the workflow of Arduino IDE, crafted with **Kotlin**, **Jetpack Compose**, and **Material Design 3**.

---

## Architecture & Features

### 1. Code Editor
- **Syntax Highlighting**: Real-time syntax coloring for C/C++ keywords, Arduino standard functions (`pinMode`, `digitalWrite`, `Serial`), types, numbers, strings, and comments.
- **Line Numbers & Auto-Indentation**: Responsive line numbering and cursor-aware indentation.
- **Undo / Redo History**: Complete state stack for text modifications.
- **Search & Replace**: Case-aware search with match counter, Find Next, Replace, and Replace All.
- **Code Autocomplete**: Keyword and function suggestions.
- **Multi-File Projects**: Create, edit, rename, and delete `.ino`, `.cpp`, and `.h` tabs within projects.
- **Quick Symbol Toolbar**: One-tap access to programming symbols (`{`, `}`, `(`, `)`, `;`, `=`, `<`, `>`, `#`).

### 2. Boards Manager
- Built-in board profiles:
  - **Arduino Uno** (`arduino:avr:uno`, ATmega328P @ 16 MHz)
  - **Arduino Nano** (`arduino:avr:nano`, ATmega328P @ 16 MHz)
  - **Arduino Mega 2560** (`arduino:avr:mega`, ATmega2560 @ 16 MHz)
  - **ESP32 Dev Module** (`esp32:esp32:esp32`, Xtensa Dual-Core 240 MHz)
  - **NodeMCU 1.0 / ESP8266** (`esp8266:esp8266:nodemcuv2`, Tensilica L106 @ 80 MHz)
- Search, filter, and install/uninstall board definitions.
- Displays FQBN, CPU architecture, clock rate, and pin flash configurations.

### 3. Library Manager
- Search and manage standard Arduino libraries (Wire, SPI, Servo, LiquidCrystal, DHT Sensor, FastLED, Adafruit GFX, etc.).
- One-tap `#include <library.h>` injection into the active sketch.
- Offline library database with dependency tracking and installation status.

### 4. Build & Compiler Service
- **Local Static Verification**: Parses code structure, unclosed braces/parentheses, missing `#include` files, and ensures standard `void setup()` and `void loop()` entry points are defined.
- **Remote / Local Build Service Interface**: Connect to an external `arduino-cli` HTTP compiler daemon to generate real machine binaries (`.hex` / `.bin`).
- **Interactive Terminal**: Monospaced terminal output with color-coded info, warning, error, and success lines.

### 5. USB-OTG Hardware & Firmware Upload
- **Android USB Host Support**: Direct communication via USB OTG adapter cables.
- **Supported USB Serial ICs**:
  - CDC-ACM (Native USB on Arduino Leonardo, Due, Mega, ESP32-S2/S3)
  - FTDI (FT232R, FT2232)
  - CH340 / CH341 (common on Nano and clones)
  - CP210x (Silicon Labs CP2102/CP2104 on ESP32/ESP8266 boards)
- **UploadService Abstraction**:
  - DTR reset pulse generation for microcontroller bootloader synchronization.
  - STK500 protocol handshake (`0x30 0x20` sync verification).
  - Memory block flashing and verification.
  - Truthful status reporting (never simulates successful uploads).

### 6. Serial Monitor
- Real-time bidirectional UART communication.
- Selectable baud rates (300 to 2,000,000 baud; default 115200).
- Configurable line endings (None, Newline `\n`, Carriage Return `\r`, Both `\r\n`).
- Hexadecimal viewing mode for binary debugging.
- Timestamp toggle, auto-scrolling terminal, and clear buffer controls.

---

## Workflow

```
Home Screen ──> Create / Open Sketch ──> Code Editor ──> Select Board ──> Select Port ──> Verify / Compile ──> Upload Firmware ──> Serial Monitor
```

---

## How to Push to GitHub & Build the APK

### Step 1: Initialize Git and Push to GitHub

From the root of your project, run:

```bash
git init
git add .
git commit -m "Initial commit: Mobile Arduino IDE complete project"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```

### Step 2: Automated APK Build with GitHub Actions

This repository includes `.github/workflows/build-apk.yml`. As soon as you push to `main` (or trigger manually via **Workflow Dispatch**), GitHub Actions will:
1. Check out the project code.
2. Set up JDK 21 (Temurin) and Android SDK.
3. Restore the debug keystore.
4. Execute `./gradlew assembleDebug`.
5. Upload the compiled APK as an artifact.

### Step 3: Download the APK

1. Go to your repository on GitHub (`https://github.com/<username>/<repo>`).
2. Click the **Actions** tab at the top.
3. Click the latest workflow run (e.g. `Build Android Debug APK`).
4. Scroll down to the **Artifacts** section at the bottom of the page.
5. Click **`mobile-arduino-ide-debug`** to download the ZIP file containing your installable `app-debug.apk`.

---

## Local Building with Android Studio or CLI

### Using CLI
```bash
chmod +x gradlew
./gradlew assembleDebug
```
The generated APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Using Android Studio
1. Open Android Studio.
2. Select **Open** and select this directory.
3. Wait for Gradle sync to finish.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)** or click **Run** on a connected device.

---

## Technology Stack
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean MVVM with Coroutines & StateFlow
- **Database**: Room Persistence Library with KSP
- **Hardware**: Android `android.hardware.usb.UsbManager` and custom CDC/STK500 driver layer
