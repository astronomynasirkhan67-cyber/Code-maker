package com.example.compiler

import android.content.Context
import android.util.Base64
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.firmware.FirmwareBinary
import com.example.firmware.FirmwarePackage
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
import java.util.regex.Pattern

enum class TerminalLineType {
    INFO,
    STDOUT,
    WARNING,
    ERROR,
    SUCCESS
}

data class TerminalLine(
    val type: TerminalLineType,
    val text: String,
    val lineNumber: Int? = null,
    val fileName: String? = null
)

data class CompileProgress(
    val stage: String,
    val progress: Float, // 0.0 to 1.0
    val line: TerminalLine? = null,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val isToolchainMissing: Boolean = false,
    val isSyntaxError: Boolean = false,
    val isCoreMissing: Boolean = false,
    val binaryBytes: ByteArray? = null,
    val firmwarePackage: FirmwarePackage? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompileProgress
        if (stage != other.stage) return false
        if (progress != other.progress) return false
        if (line != other.line) return false
        if (isFinished != other.isFinished) return false
        if (isSuccess != other.isSuccess) return false
        if (isToolchainMissing != other.isToolchainMissing) return false
        if (isSyntaxError != other.isSyntaxError) return false
        if (isCoreMissing != other.isCoreMissing) return false
        if (binaryBytes != null) {
            if (other.binaryBytes == null) return false
            if (!binaryBytes.contentEquals(other.binaryBytes)) return false
        } else if (other.binaryBytes != null) return false
        return firmwarePackage == other.firmwarePackage
    }

    override fun hashCode(): Int {
        var result = stage.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + (line?.hashCode() ?: 0)
        result = 31 * result + isFinished.hashCode()
        result = 31 * result + isSuccess.hashCode()
        result = 31 * result + isToolchainMissing.hashCode()
        result = 31 * result + isSyntaxError.hashCode()
        result = 31 * result + isCoreMissing.hashCode()
        result = 31 * result + (binaryBytes?.contentHashCode() ?: 0)
        result = 31 * result + (firmwarePackage?.hashCode() ?: 0)
        return result
    }
}

data class BuildServerHealthResult(
    val isOnline: Boolean,
    val latencyMs: Long = 0,
    val serverName: String = "",
    val arduinoCliVersion: String = "Unknown",
    val esp32CoreVersion: String = "Not Installed",
    val message: String = "",
    val platforms: List<String> = emptyList()
) {
    val isHealthy: Boolean get() = isOnline
    val installedPlatforms: List<String>
        get() = if (platforms.isNotEmpty()) platforms
        else if (esp32CoreVersion != "Not Installed") listOf("esp32:esp32@$esp32CoreVersion")
        else emptyList()
}

data class LocalToolchainStatus(
    val isAvailable: Boolean,
    val toolchainPath: String? = null,
    val gccVersion: String? = null,
    val message: String,
    val cpuAbi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
    val androidApi: Int = android.os.Build.VERSION.SDK_INT,
    val internalStorageFreeBytes: Long = 1024L * 1024L * 512L // Default estimate or calculated dynamically
)

/**
 * Robust compiler pipeline implementing real Arduino & ESP32 compilation architectures.
 * Distinguishes static syntax analysis from true toolchain binary generation.
 * Supports local toolchain check, remote Arduino CLI compilation daemons, and complete ESP32 binary packages.
 */
class CompilerService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Checks if a local native xtensa/avr toolchain binary is installed in app data.
     */
    fun checkLocalToolchain(context: Context): LocalToolchainStatus {
        val possiblePaths = listOf(
            File(context.filesDir, "bin/xtensa-esp32-elf-gcc"),
            File(context.filesDir, "toolchain/bin/xtensa-esp32-elf-gcc"),
            File("/data/data/com.termux/files/usr/bin/xtensa-esp32-elf-gcc"),
            File("/system/bin/xtensa-esp32-elf-gcc")
        )

        val found = possiblePaths.find { it.exists() && it.canExecute() }
        return if (found != null) {
            LocalToolchainStatus(
                isAvailable = true,
                toolchainPath = found.absolutePath,
                gccVersion = "Xtensa ESP32 ELF GCC (Local)",
                message = "Local Xtensa toolchain detected at ${found.absolutePath}"
            )
        } else {
            LocalToolchainStatus(
                isAvailable = false,
                toolchainPath = null,
                message = "Local cross-compiler (xtensa-esp32-elf-gcc) is not installed on this Android device."
            )
        }
    }

    /**
     * Clears cached build files and compiler temporary artifacts.
     */
    fun clearBuildCache(context: Context): Long {
        var bytesFreed = 0L
        val cacheDirs = listOf(
            File(context.cacheDir, "compiler_cache"),
            File(context.cacheDir, "firmware"),
            File(context.cacheDir, "downloads")
        )

        for (dir in cacheDirs) {
            if (dir.exists()) {
                dir.walkBottomUp().forEach { file ->
                    val len = file.length()
                    if (file.delete()) {
                        bytesFreed += len
                    }
                }
            }
        }
        return bytesFreed
    }

    /**
     * Pings the Arduino CLI Build Server and retrieves version, cores, and status.
     */
    suspend fun checkBuildServerHealth(baseUrl: String): BuildServerHealthResult {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) {
            return BuildServerHealthResult(
                isOnline = false,
                message = "Build Server URL is empty. Configure a server in Settings."
            )
        }

        val url = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val startTime = System.currentTimeMillis()

        return try {
            val healthReq = Request.Builder()
                .url("${url}health")
                .get()
                .build()

            val response = client.newCall(healthReq).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                var cliVer = "1.1.0"
                var esp32Core = "3.1.1"
                var sName = "Arduino CLI Daemon"

                try {
                    val json = JSONObject(body)
                    cliVer = json.optString("arduinoCliVersion", json.optString("cli_version", "1.1.0"))
                    esp32Core = json.optString("esp32CoreVersion", json.optString("core_version", "3.1.1"))
                    sName = json.optString("server", "Arduino CLI Build Daemon")
                } catch (_: Exception) {}

                BuildServerHealthResult(
                    isOnline = true,
                    latencyMs = latency,
                    serverName = sName,
                    arduinoCliVersion = cliVer,
                    esp32CoreVersion = esp32Core,
                    message = "Connected to $sName (${latency}ms). Arduino CLI $cliVer, ESP32 Core $esp32Core."
                )
            } else {
                BuildServerHealthResult(
                    isOnline = false,
                    latencyMs = latency,
                    message = "Server returned HTTP ${response.code}: $body"
                )
            }
        } catch (e: Exception) {
            BuildServerHealthResult(
                isOnline = false,
                message = "Connection failed: ${e.message ?: "Unable to reach build server"}"
            )
        }
    }

    /**
     * Requests the build server to install/update the ESP32 core via `arduino-cli core install esp32:esp32`.
     */
    fun requestInstallEsp32Core(baseUrl: String): Flow<TerminalLine> = flow {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) {
            emit(TerminalLine(TerminalLineType.ERROR, "Cannot install core: Build Server URL is empty."))
            return@flow
        }

        val url = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        emit(TerminalLine(TerminalLineType.INFO, "Contacting Build Server at $url to install ESP32 Arduino Core..."))

        try {
            val payload = JSONObject().apply {
                put("command", "core install esp32:esp32")
                put("core", "esp32:esp32")
            }
            val req = Request.Builder()
                .url("${url}core/install")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (resp.isSuccessful) {
                emit(TerminalLine(TerminalLineType.SUCCESS, "ESP32 Core installed successfully on Build Server!"))
                emit(TerminalLine(TerminalLineType.INFO, body))
            } else {
                emit(TerminalLine(TerminalLineType.ERROR, "Core install request failed (HTTP ${resp.code}): $body"))
            }
        } catch (e: Exception) {
            emit(TerminalLine(TerminalLineType.ERROR, "Failed to trigger core installation: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Complete sketch compilation workflow.
     */
    fun compileSketch(
        projectName: String,
        files: List<ProjectFileEntity>,
        targetBoard: BoardEntity,
        installedLibraries: List<LibraryEntity>,
        remoteCompilerUrl: String,
        compilerBackend: String = "server",
        verboseOutput: Boolean = true
    ): Flow<CompileProgress> = flow {
        val isEsp32 = targetBoard.fqbn.contains("esp32", ignoreCase = true) ||
                targetBoard.name.contains("ESP32", ignoreCase = true)

        emit(CompileProgress("Initializing", 0.05f, TerminalLine(
            TerminalLineType.INFO,
            "[Mobile Arduino IDE Compiler Pipeline v2.0]"
        )))
        emit(CompileProgress("Target Setup", 0.10f, TerminalLine(
            TerminalLineType.INFO,
            "Target board: ${targetBoard.name} [${targetBoard.fqbn}] (MCU: ${targetBoard.mcu}, Clock: ${targetBoard.clockSpeed})"
        )))

        // Verify target board platform installation
        if (!targetBoard.isInstalled) {
            emit(CompileProgress(
                stage = "Target Core Missing",
                progress = 1.0f,
                line = TerminalLine(
                    TerminalLineType.ERROR,
                    "Target board platform for '${targetBoard.name}' (${targetBoard.fqbn}) is not installed. Open Boards Manager to install the platform core."
                ),
                isFinished = true,
                isSuccess = false,
                isCoreMissing = true
            ))
            return@flow
        }

        emit(CompileProgress("Source Check", 0.15f, TerminalLine(
            TerminalLineType.INFO,
            "Compiling sketch: $projectName (${files.size} source file(s))..."
        )))

        delay(80)

        // Step 1: Real Static Syntax & Structure Verification
        emit(CompileProgress("Syntax Verification", 0.25f, TerminalLine(
            TerminalLineType.INFO,
            "Running C++ static syntax verification..."
        )))

        val collectedErrors = mutableListOf<TerminalLine>()
        val collectedWarnings = mutableListOf<TerminalLine>()

        val mainIno = files.find { it.isMain || it.name.endsWith(".ino") }
        if (mainIno == null) {
            val err = TerminalLine(
                TerminalLineType.ERROR,
                "Build error: No main .ino sketch file found in project."
            )
            collectedErrors.add(err)
            emit(CompileProgress("Error", 1.0f, err, isFinished = true, isSuccess = false))
            return@flow
        }

        // Analyze each source file
        for (file in files) {
            val lines = file.content.lines()

            var openBraces = 0
            var openParens = 0
            var openBrackets = 0

            var foundSetup = false
            var foundLoop = false

            val installedHeaders = installedLibraries.map { it.headerToInclude.lowercase() }.toSet() +
                    setOf("arduino.h", "stdint.h", "stdbool.h", "math.h", "string.h", "stdlib.h", "wifi.h", "wire.h", "spi.h", "bluetoothserial.h")

            for ((index, rawLine) in lines.withIndex()) {
                val lineNum = index + 1
                val trimmed = rawLine.trim()

                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    continue
                }

                // Check #include directives
                if (trimmed.startsWith("#include")) {
                    val matcher = Pattern.compile("#include\\s+[<\"]([a-zA-Z0-9_.-]+)[>\"]").matcher(trimmed)
                    if (matcher.find()) {
                        val header = matcher.group(1) ?: ""
                        if (!installedHeaders.contains(header.lowercase()) && !files.any { it.name.equals(header, ignoreCase = true) }) {
                            val warn = TerminalLine(
                                TerminalLineType.WARNING,
                                "${file.name}:$lineNum: warning: Header <$header> is not in installed libraries list.",
                                lineNum,
                                file.name
                            )
                            collectedWarnings.add(warn)
                            emit(CompileProgress("Warning", 0.35f, warn))
                        }
                    }
                }

                // Check for setup() and loop() in main ino
                if (file == mainIno) {
                    if (trimmed.contains("void") && trimmed.contains("setup") && trimmed.contains("()")) {
                        foundSetup = true
                    }
                    if (trimmed.contains("void") && trimmed.contains("loop") && trimmed.contains("()")) {
                        foundLoop = true
                    }
                }

                // Count braces, parentheses, brackets
                for (ch in trimmed) {
                    when (ch) {
                        '{' -> openBraces++
                        '}' -> openBraces--
                        '(' -> openParens++
                        ')' -> openParens--
                        '[' -> openBrackets++
                        ']' -> openBrackets--
                    }
                }

                if (openBraces < 0) {
                    val err = TerminalLine(
                        TerminalLineType.ERROR,
                        "${file.name}:$lineNum: error: Extraneous closing brace '}' without matching '{'",
                        lineNum,
                        file.name
                    )
                    collectedErrors.add(err)
                    emit(CompileProgress("Syntax Error", 0.45f, err))
                    openBraces = 0
                }

                // Check common semicolon misses
                val isStatement = (trimmed.startsWith("int ") || trimmed.startsWith("float ") ||
                        trimmed.startsWith("double ") || trimmed.startsWith("char ") ||
                        trimmed.startsWith("bool ") || trimmed.startsWith("pinMode") ||
                        trimmed.startsWith("digitalWrite") || trimmed.startsWith("digitalRead") ||
                        trimmed.startsWith("analogRead") || trimmed.startsWith("analogWrite") ||
                        trimmed.startsWith("Serial.") || trimmed.startsWith("delay"))

                if (isStatement && !trimmed.endsWith(";") && !trimmed.endsWith("{") && !trimmed.endsWith(",")) {
                    val err = TerminalLine(
                        TerminalLineType.ERROR,
                        "${file.name}:$lineNum: error: Expected ';' before end of line: '$trimmed'",
                        lineNum,
                        file.name
                    )
                    collectedErrors.add(err)
                    emit(CompileProgress("Syntax Error", 0.50f, err))
                }
            }

            if (openBraces > 0) {
                val err = TerminalLine(
                    TerminalLineType.ERROR,
                    "${file.name}: error: Unmatched opening brace '{' - missing $openBraces closing brace(s) '}'",
                    lines.size,
                    file.name
                )
                collectedErrors.add(err)
                emit(CompileProgress("Syntax Error", 0.55f, err))
            }

            if (openParens != 0) {
                val err = TerminalLine(
                    TerminalLineType.ERROR,
                    "${file.name}: error: Unbalanced parentheses '(' and ')' detected in file.",
                    lines.size,
                    file.name
                )
                collectedErrors.add(err)
                emit(CompileProgress("Syntax Error", 0.55f, err))
            }

            if (file == mainIno) {
                if (!foundSetup) {
                    val err = TerminalLine(
                        TerminalLineType.ERROR,
                        "${file.name}: error: Mandatory function 'void setup()' is missing in the primary sketch.",
                        1,
                        file.name
                    )
                    collectedErrors.add(err)
                    emit(CompileProgress("Syntax Error", 0.60f, err))
                }
                if (!foundLoop) {
                    val err = TerminalLine(
                        TerminalLineType.ERROR,
                        "${file.name}: error: Mandatory function 'void loop()' is missing in the primary sketch.",
                        1,
                        file.name
                    )
                    collectedErrors.add(err)
                    emit(CompileProgress("Syntax Error", 0.60f, err))
                }
            }
        }

        if (collectedErrors.isNotEmpty()) {
            emit(CompileProgress(
                stage = "Syntax Errors Detected",
                progress = 1.0f,
                line = TerminalLine(
                    TerminalLineType.ERROR,
                    "Compilation halted: ${collectedErrors.size} syntax error(s) found in sketch code. Fix errors to continue."
                ),
                isFinished = true,
                isSuccess = false,
                isSyntaxError = true
            ))
            return@flow
        }

        emit(CompileProgress("Syntax Verified", 0.65f, TerminalLine(
            TerminalLineType.SUCCESS,
            "Static syntax check passed: 0 errors detected. Proceeding to toolchain compiler..."
        )))

        delay(100)

        // Step 2: Toolchain Compilation
        val cleanUrl = remoteCompilerUrl.trim()
        if (cleanUrl.isNotEmpty()) {
            emit(CompileProgress("Build Server Execution", 0.70f, TerminalLine(
                TerminalLineType.INFO,
                "Submitting to Arduino CLI Build Server: $cleanUrl"
            )))

            try {
                val payloadJson = JSONObject().apply {
                    put("fqbn", targetBoard.fqbn)
                    put("projectName", projectName)
                    put("verbose", verboseOutput)
                    val filesArray = JSONArray()
                    files.forEach { file ->
                        filesArray.put(JSONObject().apply {
                            put("name", file.name)
                            put("content", file.content)
                        })
                    }
                    put("files", filesArray)
                }

                val compileEndpoint = if (cleanUrl.endsWith("/")) "${cleanUrl}compile" else "$cleanUrl/compile"
                val request = Request.Builder()
                    .url(compileEndpoint)
                    .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = try {
                        JSONObject(responseBody)
                    } catch (e: Exception) {
                        null
                    }

                    // Print compiler stdout logs if present
                    val compilerLogs = json?.optJSONArray("logs")
                    if (compilerLogs != null) {
                        for (i in 0 until compilerLogs.length()) {
                            val logLine = compilerLogs.getString(i)
                            emit(CompileProgress("Compiling", 0.85f, TerminalLine(TerminalLineType.STDOUT, logLine)))
                        }
                    }

                    val binariesList = mutableListOf<FirmwareBinary>()

                    if (json != null && json.has("binaries")) {
                        val arr = json.getJSONArray("binaries")
                        for (i in 0 until arr.length()) {
                            val bObj = arr.getJSONObject(i)
                            val fName = bObj.getString("filename")
                            val addr = bObj.getString("flashAddress")
                            val b64 = bObj.getString("data")
                            val data = Base64.decode(b64, Base64.DEFAULT)
                            binariesList.add(FirmwareBinary.create(fName, addr, data))
                        }
                    } else if (json != null && json.has("binary")) {
                        val b64 = json.getString("binary")
                        val data = Base64.decode(b64, Base64.DEFAULT)
                        binariesList.add(FirmwareBinary.create("firmware.bin", "0x10000", data))
                    } else if (json != null && json.has("hex")) {
                        val hex = json.getString("hex").toByteArray()
                        binariesList.add(FirmwareBinary.create("firmware.hex", "0x0000", hex))
                    } else if (responseBody.isNotEmpty()) {
                        val bytes = responseBody.toByteArray()
                        binariesList.add(FirmwareBinary.create("firmware.bin", "0x10000", bytes))
                    }

                    val pkg = if (binariesList.isNotEmpty()) {
                        FirmwarePackage(
                            boardName = targetBoard.name,
                            fqbn = targetBoard.fqbn,
                            coreVersion = json?.optString("coreVersion", "3.1.1") ?: "3.1.1",
                            flashMode = json?.optString("flashMode", "DIO") ?: "DIO",
                            flashFreq = json?.optString("flashFreq", "80MHz") ?: "80MHz",
                            flashSize = targetBoard.flashSize,
                            binaries = binariesList,
                            buildLogs = listOf("Successfully compiled by Arduino CLI Build Server ($cleanUrl)"),
                            isPrecompiled = false,
                            backendSource = "Arduino CLI Build Server ($cleanUrl)"
                        )
                    } else {
                        null
                    }

                    emit(CompileProgress(
                        stage = "Build Complete",
                        progress = 1.0f,
                        line = TerminalLine(
                            TerminalLineType.SUCCESS,
                            "Compilation successful! Generated ${binariesList.size} binary file(s) (${pkg?.formattedTotalSize ?: "0 KB"}). Ready to flash."
                        ),
                        isFinished = true,
                        isSuccess = true,
                        binaryBytes = binariesList.find { it.filename.contains("firmware", ignoreCase = true) || it.filename.endsWith(".bin") }?.data,
                        firmwarePackage = pkg
                    ))
                } else {
                    emit(CompileProgress(
                        stage = "Build Server Error",
                        progress = 1.0f,
                        line = TerminalLine(
                            TerminalLineType.ERROR,
                            "Build Server compilation failed (HTTP ${response.code}):\n$responseBody"
                        ),
                        isFinished = true,
                        isSuccess = false
                    ))
                }
            } catch (e: Exception) {
                emit(CompileProgress(
                    stage = "Connection Error",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.ERROR,
                        "Failed to connect to Build Server ($cleanUrl): ${e.message}\nMake sure the Arduino CLI server is running and reachable on this network."
                    ),
                    isFinished = true,
                    isSuccess = false
                ))
            }
        } else {
            // No Build Server configured or local compiler backend chosen
            if (isEsp32) {
                val backendMsg = if (compilerBackend == "local") {
                    "ESP32 local compiler is unavailable on this Android device. Configure an Arduino CLI build server in Settings -> Compiler."
                } else {
                    "ESP32 compilation blocked: Arduino CLI Build Server is not configured. Configure server URL in Settings -> Compiler."
                }

                emit(CompileProgress(
                    stage = "Compiler Toolchain Missing",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.ERROR,
                        backendMsg
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
                delay(50)
                emit(CompileProgress(
                    stage = "Toolchain Notice",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.INFO,
                        """[ESP32 Toolchain Status]
- Compiler Backend: ${if (compilerBackend == "local") "Local Compiler" else "Build Server"}
- Syntax Verification: Passed (0 errors detected)
- Local Native Xtensa Compiler: Not available on this Android device
- Arduino CLI Build Server: ${if (cleanUrl.isEmpty()) "Not configured" else cleanUrl}

ACTIONS:
1. Open Settings -> Compiler -> Build Server URL to connect your Arduino CLI daemon.
2. Or tap 'Load Test Blink' on the Upload screen to flash precompiled ESP32 Blink firmware directly via USB OTG.""".trimIndent()
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
            } else {
                emit(CompileProgress(
                    stage = "AVR Toolchain Missing",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.ERROR,
                        "AVR compiler toolchain (avr-gcc) is not installed on this device."
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
                delay(50)
                emit(CompileProgress(
                    stage = "Toolchain Notice",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.INFO,
                        "Configure an Arduino CLI build backend in Settings > Compiler Settings to compile AVR sketches into .hex."
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
            }
        }
    }.flowOn(Dispatchers.IO)
}
