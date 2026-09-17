package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BoardDao
import com.example.data.local.dao.LibraryDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.SettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProjectEntity::class,
        ProjectFileEntity::class,
        BoardEntity::class,
        LibraryEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun boardDao(): BoardDao
    abstract fun libraryDao(): LibraryDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arduino_ide_db"
                ).addCallback(DatabaseCallback(context.applicationContext))
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getInstance(context)
                seedDatabase(database)
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            // 1. Seed Boards
            val defaultBoards = listOf(
                BoardEntity(
                    id = "arduino_uno",
                    name = "Arduino Uno",
                    fqbn = "arduino:avr:uno",
                    arch = "AVR",
                    mcu = "ATmega328P",
                    clockSpeed = "16 MHz",
                    flashSize = "32 KB",
                    ramSize = "2 KB",
                    isInstalled = true,
                    description = "The classic Arduino microcontroller board based on the ATmega328P. Ideal for learning and prototyping.",
                    category = "Arduino Official",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x2341:0x0043,0x2341:0x0001,0x1A86:0x7523,0x0403:0x6001"
                ),
                BoardEntity(
                    id = "arduino_nano",
                    name = "Arduino Nano",
                    fqbn = "arduino:avr:nano",
                    arch = "AVR",
                    mcu = "ATmega328P",
                    clockSpeed = "16 MHz",
                    flashSize = "32 KB",
                    ramSize = "2 KB",
                    isInstalled = true,
                    description = "Small, complete, and breadboard-friendly board based on the ATmega328P with mini/micro USB.",
                    category = "Arduino Official",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x0403:0x6001,0x1A86:0x7523,0x2341:0x0042"
                ),
                BoardEntity(
                    id = "arduino_mega",
                    name = "Arduino Mega 2560",
                    fqbn = "arduino:avr:mega",
                    arch = "AVR",
                    mcu = "ATmega2560",
                    clockSpeed = "16 MHz",
                    flashSize = "256 KB",
                    ramSize = "8 KB",
                    isInstalled = true,
                    description = "Microcontroller board designed for complex projects. 54 digital I/O pins, 16 analog inputs, and 4 UARTs.",
                    category = "Arduino Official",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x2341:0x0042,0x2341:0x0010,0x1A86:0x7523"
                ),
                BoardEntity(
                    id = "esp32_dev",
                    name = "ESP32 Dev Module",
                    fqbn = "esp32:esp32:esp32",
                    arch = "Xtensa LX6",
                    mcu = "ESP32-D0WDQ6",
                    clockSpeed = "240 MHz",
                    flashSize = "4 MB",
                    ramSize = "520 KB",
                    isInstalled = true,
                    description = "High-performance dual-core MCU with integrated Wi-Fi and Bluetooth Low Energy (BLE) for IoT devices.",
                    category = "Espressif",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x10C4:0xEA60,0x1A86:0x7523,0x303A:0x1001"
                ),
                BoardEntity(
                    id = "esp8266_nodemcu",
                    name = "NodeMCU 1.0 (ESP-12E)",
                    fqbn = "esp8266:esp8266:nodemcuv2",
                    arch = "Tensilica",
                    mcu = "ESP8266",
                    clockSpeed = "80 MHz",
                    flashSize = "4 MB",
                    ramSize = "80 KB",
                    isInstalled = true,
                    description = "Affordable Wi-Fi microchip board widely used for Internet of Things and smart home automation projects.",
                    category = "Espressif",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x10C4:0xEA60,0x1A86:0x7523"
                ),
                BoardEntity(
                    id = "rpi_pico",
                    name = "Raspberry Pi Pico",
                    fqbn = "rp2040:rp2040:pico",
                    arch = "ARM Cortex-M0+",
                    mcu = "RP2040",
                    clockSpeed = "133 MHz",
                    flashSize = "2 MB",
                    ramSize = "264 KB",
                    isInstalled = false,
                    description = "Dual-core ARM Cortex-M0+ microcontroller with flexible clock, 8 programmable I/O state machines, and micro-USB.",
                    category = "Raspberry Pi",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x2E8A:0x000A,0x2E8A:0x0003"
                ),
                BoardEntity(
                    id = "arduino_leonardo",
                    name = "Arduino Leonardo",
                    fqbn = "arduino:avr:leonardo",
                    arch = "AVR",
                    mcu = "ATmega32U4",
                    clockSpeed = "16 MHz",
                    flashSize = "32 KB",
                    ramSize = "2.5 KB",
                    isInstalled = false,
                    description = "Board with built-in USB communication, allowing the Leonardo to act as a native mouse or keyboard HID.",
                    category = "Arduino Official",
                    defaultBaudRate = 115200,
                    vidPidPairs = "0x2341:0x8036,0x2341:0x0036"
                )
            )
            db.boardDao().insertBoards(defaultBoards)

            // 2. Seed Libraries
            val defaultLibraries = listOf(
                LibraryEntity(
                    id = "adafruit_gfx",
                    name = "Adafruit GFX Library",
                    version = "1.11.9",
                    author = "Adafruit",
                    description = "Core graphics library for all Adafruit displays, providing a common set of graphics primitives (points, lines, circles, text).",
                    category = "Display",
                    isInstalled = true,
                    headerToInclude = "Adafruit_GFX.h",
                    dependencies = "Adafruit BusIO"
                ),
                LibraryEntity(
                    id = "liquidcrystal_i2c",
                    name = "LiquidCrystal I2C",
                    version = "1.1.2",
                    author = "Frank de Brabander",
                    description = "Allows control of 16x2 and 20x4 HD44780 LCD displays via PCF8574 I2C adapter with only two signal wires.",
                    category = "Display",
                    isInstalled = true,
                    headerToInclude = "LiquidCrystal_I2C.h",
                    dependencies = "Wire"
                ),
                LibraryEntity(
                    id = "dht_sensor",
                    name = "DHT sensor library",
                    version = "1.4.6",
                    author = "Adafruit",
                    description = "Arduino library for reading DHT11, DHT22, and AM2302 digital temperature and relative humidity sensors.",
                    category = "Sensors",
                    isInstalled = true,
                    headerToInclude = "DHT.h",
                    dependencies = "Adafruit Unified Sensor"
                ),
                LibraryEntity(
                    id = "wire_lib",
                    name = "Wire (I2C)",
                    version = "1.0.0",
                    author = "Arduino",
                    description = "Built-in Two-Wire Interface (I2C / TWI) library for communicating with sensors, OLEDs, and other I2C peripheral devices.",
                    category = "Communication",
                    isInstalled = true,
                    headerToInclude = "Wire.h"
                ),
                LibraryEntity(
                    id = "spi_lib",
                    name = "SPI",
                    version = "1.0.0",
                    author = "Arduino",
                    description = "Built-in Serial Peripheral Interface (SPI) library for synchronous high-speed communication with microcontrollers and peripherals.",
                    category = "Communication",
                    isInstalled = true,
                    headerToInclude = "SPI.h"
                ),
                LibraryEntity(
                    id = "servo_lib",
                    name = "Servo",
                    version = "1.2.1",
                    author = "Michael Margolis",
                    description = "Allows Arduino boards to control hobby RC servomotors using PWM signals on digital pins.",
                    category = "Actuators",
                    isInstalled = true,
                    headerToInclude = "Servo.h"
                ),
                LibraryEntity(
                    id = "arduino_json",
                    name = "ArduinoJson",
                    version = "7.0.4",
                    author = "Benoit Blanchon",
                    description = "High-performance, memory-efficient JSON library specifically designed for embedded C++ and microcontrollers.",
                    category = "Data Processing",
                    isInstalled = true,
                    headerToInclude = "ArduinoJson.h"
                ),
                LibraryEntity(
                    id = "fastled_lib",
                    name = "FastLED",
                    version = "3.6.0",
                    author = "Daniel Garcia",
                    description = "Feature-packed, multi-platform library for driving addressable RGB LED strips like WS2812B, NeoPixels, and APA102.",
                    category = "LEDs",
                    isInstalled = false,
                    headerToInclude = "FastLED.h"
                ),
                LibraryEntity(
                    id = "pubsubclient",
                    name = "PubSubClient",
                    version = "2.8.0",
                    author = "Nick O'Leary",
                    description = "Simple MQTT client for sending and receiving telemetry messages with cloud IoT brokers and home automation gateways.",
                    category = "Communication",
                    isInstalled = false,
                    headerToInclude = "PubSubClient.h"
                ),
                LibraryEntity(
                    id = "wifimanager",
                    name = "WiFiManager",
                    version = "2.0.16",
                    author = "tzapu",
                    description = "ESP8266 and ESP32 web configuration portal for easily entering Wi-Fi credentials on first boot without hardcoding.",
                    category = "Networking",
                    isInstalled = false,
                    headerToInclude = "WiFiManager.h"
                )
            )
            db.libraryDao().insertLibraries(defaultLibraries)

            // 3. Seed Starter Projects
            val blinkId = db.projectDao().insertProject(
                ProjectEntity(
                    name = "Blink",
                    description = "Standard starter sketch to toggle the onboard LED on and off repeatedly every second.",
                    selectedBoardId = "arduino_uno"
                )
            )
            db.projectDao().insertFile(
                ProjectFileEntity(
                    projectId = blinkId,
                    name = "Blink.ino",
                    isMain = true,
                    content = """/*
  Blink
  Turns an LED on for one second, then off for one second, repeatedly.
  Most Arduinos have an on-board LED you can control. On the UNO, MEGA
  and ZERO it is attached to digital pin 13.
*/

const int ledPin = LED_BUILTIN; // Pin 13 on most boards

void setup() {
  // Initialize digital pin as an output.
  pinMode(ledPin, OUTPUT);
  Serial.begin(115200);
  Serial.println("Mobile Arduino IDE - Blink Starting...");
}

void loop() {
  digitalWrite(ledPin, HIGH);   // Turn the LED on
  Serial.println("LED Status: ON");
  delay(1000);                  // Wait for 1000 millisecond(s)
  
  digitalWrite(ledPin, LOW);    // Turn the LED off
  Serial.println("LED Status: OFF");
  delay(1000);                  // Wait for 1000 millisecond(s)
}
"""
                )
            )

            val analogId = db.projectDao().insertProject(
                ProjectEntity(
                    name = "AnalogReadSerial",
                    description = "Reads an analog input pin, prints the result to the Serial Monitor.",
                    selectedBoardId = "arduino_uno"
                )
            )
            db.projectDao().insertFile(
                ProjectFileEntity(
                    projectId = analogId,
                    name = "AnalogReadSerial.ino",
                    isMain = true,
                    content = """/*
  AnalogReadSerial
  Reads an analog input on pin 0, prints the result to the Serial Monitor.
  Graphical representation is available using Serial Plotter (Tools > Serial Plotter).
*/

const int sensorPin = A0;

void setup() {
  // Initialize serial communication at 115200 bits per second:
  Serial.begin(115200);
  Serial.println("--- Analog Sensor Stream Initialized ---");
}

void loop() {
  // Read the input on analog pin 0:
  int sensorValue = analogRead(sensorPin);
  float voltage = sensorValue * (5.0 / 1023.0);
  
  // Print out the value you read:
  Serial.print("Raw ADC: ");
  Serial.print(sensorValue);
  Serial.print(" | Voltage: ");
  Serial.print(voltage, 2);
  Serial.println(" V");
  
  delay(250); // Delay between reads for stability
}
"""
                )
            )

            val espId = db.projectDao().insertProject(
                ProjectEntity(
                    name = "ESP32_WiFi_Scanner",
                    description = "Scans available Wi-Fi networks and prints their SSID, RSSI, and encryption type.",
                    selectedBoardId = "esp32_dev"
                )
            )
            db.projectDao().insertFile(
                ProjectFileEntity(
                    projectId = espId,
                    name = "ESP32_WiFi_Scanner.ino",
                    isMain = true,
                    content = """/*
  ESP32 WiFi Scanner
  Scans for 2.4GHz Wi-Fi networks in range and outputs details to Serial.
*/

#include <WiFi.h>

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Set WiFi to station mode and disconnect from AP if previously connected
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);

  Serial.println("ESP32 WiFi Scanner Ready");
}

void loop() {
  Serial.println("\nStarting network scan...");

  int n = WiFi.scanNetworks();
  Serial.println("Scan complete!");

  if (n == 0) {
    Serial.println("No networks found.");
  } else {
    Serial.print(n);
    Serial.println(" networks found:");
    for (int i = 0; i < n; ++i) {
      Serial.print(i + 1);
      Serial.print(": ");
      Serial.print(WiFi.SSID(i));
      Serial.print(" (RSSI: ");
      Serial.print(WiFi.RSSI(i));
      Serial.println(" dBm)");
      delay(10);
    }
  }

  // Wait 5 seconds before next scan
  delay(5000);
}
"""
                )
            )

            val i2cId = db.projectDao().insertProject(
                ProjectEntity(
                    name = "I2C_Bus_Scanner",
                    description = "Scans the I2C bus (SDA/SCL) and prints 7-bit addresses of detected devices.",
                    selectedBoardId = "arduino_uno"
                )
            )
            db.projectDao().insertFile(
                ProjectFileEntity(
                    projectId = i2cId,
                    name = "I2C_Bus_Scanner.ino",
                    isMain = true,
                    content = """/*
  I2C Bus Scanner
  Scans all standard 7-bit addresses (0x01 to 0x7E) to locate connected
  I2C peripheral devices (e.g. OLED, RTC, Gyroscope, LCD).
*/

#include <Wire.h>

void setup() {
  Wire.begin();
  Serial.begin(115200);
  while (!Serial); // Wait for serial monitor connection
  Serial.println("\n--- I2C Scanner Active ---");
}

void loop() {
  byte error, address;
  int nDevices = 0;

  Serial.println("Scanning I2C bus...");

  for (address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("I2C device found at address 0x");
      if (address < 16) Serial.print("0");
      Serial.print(address, HEX);
      Serial.println(" !");
      nDevices++;
    } else if (error == 4) {
      Serial.print("Unknown error at address 0x");
      if (address < 16) Serial.print("0");
      Serial.println(address, HEX);
    }
  }

  if (nDevices == 0) {
    Serial.println("No I2C devices found\n");
  } else {
    Serial.println("Scan done\n");
  }

  delay(5000); // Wait 5 seconds for next scan
}
"""
                )
            )

            // 4. Seed Default Settings
            db.settingDao().setSetting(SettingEntity("editor_font_size", "14"))
            db.settingDao().setSetting(SettingEntity("editor_tab_size", "2"))
            db.settingDao().setSetting(SettingEntity("editor_line_numbers", "true"))
            db.settingDao().setSetting(SettingEntity("editor_auto_indent", "true"))
            db.settingDao().setSetting(SettingEntity("editor_auto_save", "true"))
            db.settingDao().setSetting(SettingEntity("serial_default_baud", "115200"))
            db.settingDao().setSetting(SettingEntity("serial_timestamps", "true"))
            db.settingDao().setSetting(SettingEntity("serial_line_ending", "NL_CR"))
            db.settingDao().setSetting(SettingEntity("compiler_remote_url", ""))
            db.settingDao().setSetting(SettingEntity("compiler_verbose", "true"))
        }
    }
}
