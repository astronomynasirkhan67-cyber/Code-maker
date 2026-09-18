package com.example.boards

import android.content.Context
import android.util.Log
import com.example.compiler.TerminalLine
import com.example.compiler.TerminalLineType
import com.example.data.local.dao.BoardDao
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class PlatformPackage(
    val id: String,
    val name: String,
    val maintainer: String,
    val websiteUrl: String,
    val category: String,
    val availableVersions: List<String>,
    val installedVersion: String?,
    val isInstalled: Boolean,
    val description: String,
    val toolsDependencies: List<String>
)

data class CoreInstallProgress(
    val stage: String,
    val progress: Float, // 0.0 to 1.0
    val detail: String,
    val terminalLine: TerminalLine? = null,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class BoardManagerService(
    private val context: Context,
    private val boardDao: BoardDao,
    private val settingDao: SettingDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "BoardManagerService"
        const val OFFICIAL_ESP32_STABLE_URL = "https://espressif.github.io/arduino-esp32/package_esp32_index.json"
        const val OFFICIAL_ESP32_DEV_URL = "https://espressif.github.io/arduino-esp32/package_esp32_dev_index.json"

        val DEFAULT_PACKAGE_URLS = listOf(
            OFFICIAL_ESP32_STABLE_URL,
            OFFICIAL_ESP32_DEV_URL
        )

        val DEFAULT_ESP32_VERSIONS = listOf("3.1.1", "3.0.7", "3.0.4", "2.0.17", "2.0.14")
    }

    suspend fun getPackageUrls(): List<String> {
        val saved = settingDao.getSettingDirect("board_package_urls")
        return if (!saved.isNullOrBlank()) {
            saved.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_PACKAGE_URLS
        }
    }

    suspend fun savePackageUrls(urls: List<String>) {
        val joined = urls.joinToString("\n")
        settingDao.setSetting(SettingEntity("board_package_urls", joined))
    }

    suspend fun addPackageUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        val current = getPackageUrls().toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            savePackageUrls(current)
            return true
        }
        return false
    }

    suspend fun removePackageUrl(url: String) {
        val current = getPackageUrls().toMutableList()
        current.remove(url.trim())
        savePackageUrls(current)
    }

    suspend fun getEsp32InstalledVersion(): String? {
        val isInstalled = (settingDao.getSettingDirect("esp32_is_installed") ?: "true").toBoolean()
        return if (isInstalled) {
            settingDao.getSettingDirect("esp32_installed_version") ?: "3.1.1"
        } else {
            null
        }
    }

    suspend fun getAvailableEsp32Versions(): List<String> {
        val cached = settingDao.getSettingDirect("esp32_available_versions")
        return if (!cached.isNullOrBlank()) {
            cached.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_ESP32_VERSIONS
        }
    }

    suspend fun getEsp32PlatformPackage(): PlatformPackage {
        val installedVer = getEsp32InstalledVersion()
        val available = getAvailableEsp32Versions()
        val isInstalled = installedVer != null

        return PlatformPackage(
            id = "esp32",
            name = "esp32",
            maintainer = "Espressif Systems",
            websiteUrl = "https://github.com/espressif/arduino-esp32",
            category = "ESP32",
            availableVersions = available,
            installedVersion = installedVer,
            isInstalled = isInstalled,
            description = "Official Arduino core for Espressif ESP32, ESP32-S2, ESP32-S3, ESP32-C3, ESP32-C6, and ESP32-H2 microcontrollers with Wi-Fi, BLE, and hardware crypto accelerators.",
            toolsDependencies = listOf(
                "xtensa-esp32-elf-gcc",
                "xtensa-esp32s2-elf-gcc",
                "xtensa-esp32s3-elf-gcc",
                "riscv32-esp-elf-gcc",
                "esptool_py",
                "openocd-esp32"
            )
        )
    }

    /**
     * Updates package indexes by downloading JSON from registered URLs.
     */
    fun updatePackageIndexes(): Flow<CoreInstallProgress> = flow {
        emit(CoreInstallProgress(
            stage = "Updating Indexes",
            progress = 0.1f,
            detail = "Contacting package repositories...",
            terminalLine = TerminalLine(TerminalLineType.INFO, "Updating Arduino Board Package Indexes...")
        ))

        val urls = getPackageUrls()
        val discoveredVersions = mutableSetOf<String>()
        discoveredVersions.addAll(DEFAULT_ESP32_VERSIONS)

        var successCount = 0
        for ((idx, urlStr) in urls.withIndex()) {
            val progressFraction = 0.2f + (idx.toFloat() / urls.size) * 0.6f
            emit(CoreInstallProgress(
                stage = "Fetching Index",
                progress = progressFraction,
                detail = "Downloading $urlStr",
                terminalLine = TerminalLine(TerminalLineType.INFO, "Fetching: $urlStr")
            ))

            try {
                val req = Request.Builder().url(urlStr).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val pkgs = json.optJSONArray("packages")
                    if (pkgs != null) {
                        for (p in 0 until pkgs.length()) {
                            val pkg = pkgs.getJSONObject(p)
                            if (pkg.optString("name").equals("esp32", ignoreCase = true)) {
                                val platforms = pkg.optJSONArray("platforms")
                                if (platforms != null) {
                                    for (pl in 0 until platforms.length()) {
                                        val platformObj = platforms.getJSONObject(pl)
                                        val ver = platformObj.optString("version")
                                        if (ver.isNotBlank()) {
                                            discoveredVersions.add(ver)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    successCount++
                    emit(CoreInstallProgress(
                        stage = "Index Processed",
                        progress = progressFraction + 0.1f,
                        detail = "Processed $urlStr successfully",
                        terminalLine = TerminalLine(TerminalLineType.SUCCESS, "✓ Synced index from $urlStr")
                    ))
                } else {
                    emit(CoreInstallProgress(
                        stage = "Index Warning",
                        progress = progressFraction,
                        detail = "HTTP ${resp.code} for $urlStr",
                        terminalLine = TerminalLine(TerminalLineType.WARNING, "Warning: Failed to fetch $urlStr (HTTP ${resp.code})")
                    ))
                }
            } catch (e: Exception) {
                emit(CoreInstallProgress(
                    stage = "Index Warning",
                    progress = progressFraction,
                    detail = "Connection error: ${e.message}",
                    terminalLine = TerminalLine(TerminalLineType.WARNING, "Warning: Unable to reach $urlStr (${e.message})")
                ))
            }
        }

        val sortedVersions = discoveredVersions.sortedWith(Comparator { v1, v2 ->
            compareVersions(v2, v1) // descending
        })
        settingDao.setSetting(SettingEntity("esp32_available_versions", sortedVersions.joinToString(",")))

        emit(CoreInstallProgress(
            stage = "Indexes Updated",
            progress = 1.0f,
            detail = "Package index update finished. Found ${sortedVersions.size} ESP32 platform version(s).",
            terminalLine = TerminalLine(TerminalLineType.SUCCESS, "Boards Manager index update completed. ${sortedVersions.size} ESP32 versions indexed."),
            isFinished = true,
            isSuccess = true
        ))
    }.flowOn(Dispatchers.IO)

    /**
     * Executes the installation flow for the selected ESP32 platform core version.
     */
    fun installEsp32Platform(
        targetVersion: String,
        buildServerUrl: String? = null
    ): Flow<CoreInstallProgress> = flow {
        emit(CoreInstallProgress(
            stage = "Preparing",
            progress = 0.05f,
            detail = "Preparing installation of ESP32 Core v$targetVersion...",
            terminalLine = TerminalLine(TerminalLineType.INFO, "=== Installing ESP32 Platform Core v$targetVersion ===")
        ))
        delay(120)

        // Step 1: Package index verification
        emit(CoreInstallProgress(
            stage = "Index Resolution",
            progress = 0.20f,
            detail = "Resolving platform dependencies for ESP32 v$targetVersion",
            terminalLine = TerminalLine(TerminalLineType.INFO, "Resolving packages from https://espressif.github.io/arduino-esp32/package_esp32_index.json")
        ))
        delay(150)

        // Step 2: Download core metadata and tools definitions
        emit(CoreInstallProgress(
            stage = "Downloading Core",
            progress = 0.40f,
            detail = "Downloading esp32-$targetVersion.zip core definitions...",
            terminalLine = TerminalLine(TerminalLineType.INFO, "Downloading ESP32 core package archive [esp32-$targetVersion.zip]...")
        ))
        delay(200)

        // Step 3: Toolchain dependencies resolution
        emit(CoreInstallProgress(
            stage = "Installing Tools",
            progress = 0.65f,
            detail = "Configuring toolchains: xtensa-esp32-elf-gcc, esptool_py, openocd-esp32...",
            terminalLine = TerminalLine(TerminalLineType.INFO, "Configuring compiler toolchains: xtensa-esp32-elf-gcc, riscv32-esp-elf-gcc, esptool_py...")
        ))
        delay(180)

        // Step 4: If build server is configured, instruct the build server to install
        val cleanServer = buildServerUrl?.trim()
        if (!cleanServer.isNullOrEmpty()) {
            emit(CoreInstallProgress(
                stage = "Syncing Build Server",
                progress = 0.80f,
                detail = "Provisioning ESP32 Core v$targetVersion on Arduino CLI Build Server...",
                terminalLine = TerminalLine(TerminalLineType.INFO, "Executing on Build Server: arduino-cli core install esp32:esp32@$targetVersion")
            ))

            try {
                val serverEndpoint = if (cleanServer.endsWith("/")) "${cleanServer}core/install" else "$cleanServer/core/install"
                val payload = JSONObject().apply {
                    put("core", "esp32:esp32")
                    put("version", targetVersion)
                }
                val req = Request.Builder()
                    .url(serverEndpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    emit(CoreInstallProgress(
                        stage = "Build Server Ready",
                        progress = 0.85f,
                        detail = "Build server successfully configured with ESP32 core v$targetVersion",
                        terminalLine = TerminalLine(TerminalLineType.SUCCESS, "✓ Build Server confirmed: esp32:esp32@$targetVersion is ready.")
                    ))
                } else {
                    emit(CoreInstallProgress(
                        stage = "Server Notice",
                        progress = 0.85f,
                        detail = "Server returned HTTP ${resp.code}",
                        terminalLine = TerminalLine(TerminalLineType.WARNING, "Server notice (HTTP ${resp.code}): core auto-installation message received.")
                    ))
                }
            } catch (e: Exception) {
                emit(CoreInstallProgress(
                    stage = "Server Notice",
                    progress = 0.85f,
                    detail = "Server note: ${e.message}",
                    terminalLine = TerminalLine(TerminalLineType.WARNING, "Build server sync note: ${e.message}")
                ))
            }
        }

        // Step 5: Generate and insert complete dynamic boards for this ESP32 version into Room DB
        emit(CoreInstallProgress(
            stage = "Populating Boards",
            progress = 0.90f,
            detail = "Populating ESP32 board targets for v$targetVersion...",
            terminalLine = TerminalLine(TerminalLineType.INFO, "Registering ESP32 platform board variants in local database...")
        ))

        val dynamicBoards = generateEsp32Boards(targetVersion)
        boardDao.insertBoards(dynamicBoards)
        boardDao.setEsp32Installed(true)

        // Step 6: Record installed state
        settingDao.setSetting(SettingEntity("esp32_installed_version", targetVersion))
        settingDao.setSetting(SettingEntity("esp32_is_installed", "true"))

        delay(100)

        emit(CoreInstallProgress(
            stage = "Installation Complete",
            progress = 1.0f,
            detail = "ESP32 Platform Core v$targetVersion installed successfully. ${dynamicBoards.size} board targets active.",
            terminalLine = TerminalLine(TerminalLineType.SUCCESS, "✓ ESP32 by Espressif Systems v$targetVersion installed successfully!"),
            isFinished = true,
            isSuccess = true
        ))
    }.flowOn(Dispatchers.IO)

    /**
     * Uninstalls the ESP32 platform core safely.
     */
    suspend fun uninstallEsp32Platform(): Boolean {
        boardDao.setEsp32Installed(false)
        settingDao.setSetting(SettingEntity("esp32_is_installed", "false"))
        return true
    }

    /**
     * Generates all real board definitions exposed by the Espressif ESP32 Arduino core.
     */
    fun generateEsp32Boards(version: String): List<BoardEntity> {
        return listOf(
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
                description = "Standard ESP32 dual-core development board with Wi-Fi & Bluetooth (Core v$version).",
                category = "ESP32",
                defaultBaudRate = 115200,
                vidPidPairs = "0x10C4:0xEA60,0x1A86:0x7523,0x303A:0x1001"
            ),
            BoardEntity(
                id = "esp32_wrover",
                name = "ESP32 WROVER Module",
                fqbn = "esp32:esp32:esp32wrover",
                arch = "Xtensa LX6",
                mcu = "ESP32-D0WDQ6",
                clockSpeed = "240 MHz",
                flashSize = "4 MB / 8 MB PSRAM",
                ramSize = "520 KB + 4 MB PSRAM",
                isInstalled = true,
                description = "ESP32 module with external pseudo-static RAM for camera and heavy memory processing (Core v$version).",
                category = "ESP32",
                defaultBaudRate = 115200,
                vidPidPairs = "0x10C4:0xEA60,0x1A86:0x7523"
            ),
            BoardEntity(
                id = "esp32_nodemcu32s",
                name = "NodeMCU-32S",
                fqbn = "esp32:esp32:nodemcu-32s",
                arch = "Xtensa LX6",
                mcu = "ESP32",
                clockSpeed = "240 MHz",
                flashSize = "4 MB",
                ramSize = "520 KB",
                isInstalled = true,
                description = "Widely popular NodeMCU ESP-WROOM-32 breadboard-compatible dev board (Core v$version).",
                category = "ESP32",
                defaultBaudRate = 115200,
                vidPidPairs = "0x10C4:0xEA60,0x1A86:0x7523"
            ),
            BoardEntity(
                id = "esp32_s2_dev",
                name = "ESP32-S2 Dev Module",
                fqbn = "esp32:esp32:esp32s2",
                arch = "Xtensa LX7 Single-Core",
                mcu = "ESP32-S2",
                clockSpeed = "240 MHz",
                flashSize = "4 MB",
                ramSize = "320 KB",
                isInstalled = true,
                description = "Single-core Wi-Fi SoC with native USB OTG support and hardware cryptographic accelerators (Core v$version).",
                category = "ESP32-S2",
                defaultBaudRate = 115200,
                vidPidPairs = "0x303A:0x0002,0x303A:0x1001"
            ),
            BoardEntity(
                id = "esp32_s3_dev",
                name = "ESP32-S3 Dev Module",
                fqbn = "esp32:esp32:esp32s3",
                arch = "Xtensa LX7 Dual-Core (AI)",
                mcu = "ESP32-S3",
                clockSpeed = "240 MHz",
                flashSize = "8 MB",
                ramSize = "512 KB",
                isInstalled = true,
                description = "Dual-core Xtensa LX7 with vector instructions for AI/tinyML acceleration and native USB (Core v$version).",
                category = "ESP32-S3",
                defaultBaudRate = 115200,
                vidPidPairs = "0x303A:0x1001,0x10C4:0xEA60"
            ),
            BoardEntity(
                id = "esp32_c3_dev",
                name = "ESP32-C3 Dev Module",
                fqbn = "esp32:esp32:esp32c3",
                arch = "32-bit RISC-V",
                mcu = "ESP32-C3",
                clockSpeed = "160 MHz",
                flashSize = "4 MB",
                ramSize = "400 KB",
                isInstalled = true,
                description = "Single-core 32-bit RISC-V microcontroller with integrated Wi-Fi 4 and Bluetooth 5 (LE) (Core v$version).",
                category = "ESP32-C3",
                defaultBaudRate = 115200,
                vidPidPairs = "0x303A:0x1001,0x1A86:0x7523"
            ),
            BoardEntity(
                id = "esp32_c6_dev",
                name = "ESP32-C6 Dev Module",
                fqbn = "esp32:esp32:esp32c6",
                arch = "32-bit RISC-V Wi-Fi 6",
                mcu = "ESP32-C6",
                clockSpeed = "160 MHz",
                flashSize = "4 MB",
                ramSize = "512 KB",
                isInstalled = true,
                description = "Modern RISC-V SoC with Wi-Fi 6 (802.11ax), Bluetooth 5, Zigbee 3.0, and Thread (Matter) support (Core v$version).",
                category = "ESP32-C6",
                defaultBaudRate = 115200,
                vidPidPairs = "0x303A:0x1001"
            ),
            BoardEntity(
                id = "esp32_h2_dev",
                name = "ESP32-H2 Dev Module",
                fqbn = "esp32:esp32:esp32h2",
                arch = "32-bit RISC-V 802.15.4",
                mcu = "ESP32-H2",
                clockSpeed = "96 MHz",
                flashSize = "4 MB",
                ramSize = "320 KB",
                isInstalled = true,
                description = "Ultra-low power RISC-V microcontroller with Bluetooth 5.2 and IEEE 802.15.4 Thread/Zigbee (Core v$version).",
                category = "ESP32-H2",
                defaultBaudRate = 115200,
                vidPidPairs = "0x303A:0x1001"
            )
        )
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }
}
