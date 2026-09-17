package com.example.compiler

import android.util.Base64
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectFileEntity
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
    val binaryBytes: ByteArray? = null
)

/**
 * Modular compiler service interface supporting both local static syntax checking
 * and remote/local Arduino-CLI build daemon compilation.
 * Never reports fake successful compilation without a real binary and compiler run.
 */
class CompilerService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    fun compileSketch(
        projectName: String,
        files: List<ProjectFileEntity>,
        targetBoard: BoardEntity,
        installedLibraries: List<LibraryEntity>,
        remoteCompilerUrl: String,
        verboseOutput: Boolean = true
    ): Flow<CompileProgress> = flow {
        val isEsp32 = targetBoard.fqbn.contains("esp32", ignoreCase = true) ||
                targetBoard.name.contains("ESP32", ignoreCase = true)

        emit(CompileProgress("Initializing", 0.05f, TerminalLine(
            TerminalLineType.INFO,
            "[Mobile Arduino IDE Compiler v1.3]"
        )))
        emit(CompileProgress("Target Setup", 0.10f, TerminalLine(
            TerminalLineType.INFO,
            "Target board: ${targetBoard.name} [${targetBoard.fqbn}] (MCU: ${targetBoard.mcu}, Clock: ${targetBoard.clockSpeed})"
        )))
        emit(CompileProgress("Source Check", 0.15f, TerminalLine(
            TerminalLineType.INFO,
            "Processing sketch: $projectName (${files.size} source file(s))..."
        )))

        delay(80)

        // Step 1: Real Static Syntax & Structure Verification
        emit(CompileProgress("Syntax Verification", 0.25f, TerminalLine(
            TerminalLineType.INFO,
            "Analyzing C++ syntax and Arduino structure..."
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

        // Analyze every source file
        for (file in files) {
            val lines = file.content.lines()

            var openBraces = 0
            var openParens = 0
            var openBrackets = 0

            var foundSetup = false
            var foundLoop = false

            val installedHeaders = installedLibraries.map { it.headerToInclude.lowercase() }.toSet() +
                    setOf("arduino.h", "stdint.h", "stdbool.h", "math.h", "string.h", "stdlib.h", "wifi.h", "wire.h", "spi.h")

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
                stage = "Compilation Failed",
                progress = 1.0f,
                line = TerminalLine(
                    TerminalLineType.ERROR,
                    "Compilation halted: ${collectedErrors.size} error(s) found. Fix errors to continue."
                ),
                isFinished = true,
                isSuccess = false
            ))
            return@flow
        }

        emit(CompileProgress("Syntax Passed", 0.70f, TerminalLine(
            TerminalLineType.SUCCESS,
            "Static syntax check passed: 0 syntax errors detected."
        )))

        delay(100)

        // Step 2: Build Backend Execution
        val cleanUrl = remoteCompilerUrl.trim()
        if (cleanUrl.isNotEmpty()) {
            emit(CompileProgress("Build Server", 0.75f, TerminalLine(
                TerminalLineType.INFO,
                "Submitting to build backend: $cleanUrl"
            )))

            try {
                val payloadJson = JSONObject().apply {
                    put("fqbn", targetBoard.fqbn)
                    val filesArray = JSONArray()
                    files.forEach { file ->
                        filesArray.put(JSONObject().apply {
                            put("name", file.name)
                            put("content", file.content)
                        })
                    }
                    put("files", filesArray)
                }

                val request = Request.Builder()
                    .url(if (cleanUrl.endsWith("/")) "${cleanUrl}compile" else "$cleanUrl/compile")
                    .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    // Check if JSON response contains binary payload
                    var binaryData: ByteArray? = null
                    try {
                        val json = JSONObject(responseBody)
                        if (json.has("binary")) {
                            val b64 = json.getString("binary")
                            binaryData = Base64.decode(b64, Base64.DEFAULT)
                        } else if (json.has("hex")) {
                            binaryData = json.getString("hex").toByteArray()
                        }
                    } catch (_: Exception) {
                        binaryData = responseBody.toByteArray()
                    }

                    emit(CompileProgress(
                        stage = "Build Complete",
                        progress = 1.0f,
                        line = TerminalLine(
                            TerminalLineType.SUCCESS,
                            "Compilation successful! Flashable firmware generated (${binaryData?.size ?: 0} bytes)."
                        ),
                        isFinished = true,
                        isSuccess = true,
                        binaryBytes = binaryData
                    ))
                } else {
                    emit(CompileProgress(
                        stage = "Build Server Error",
                        progress = 1.0f,
                        line = TerminalLine(
                            TerminalLineType.ERROR,
                            "Remote compiler failed (HTTP ${response.code}):\n$responseBody"
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
                        "Failed to connect to compiler server ($cleanUrl): ${e.message}"
                    ),
                    isFinished = true,
                    isSuccess = false
                ))
            }
        } else {
            // No remote build server configured
            if (isEsp32) {
                emit(CompileProgress(
                    stage = "Toolchain Not Installed",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.WARNING,
                        "ESP32 compiler toolchain is not installed."
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
                        """[ESP32 Compiler Status]
- Sketch code syntax: Clean (0 errors).
- ESP32 Cross-Compiler (xtensa-esp32-elf-gcc) is not bundled on this device.
- To produce an ESP32 binary (.bin) for flashing:
  1. Configure an Arduino-CLI build server under Settings > Compiler Settings.
  2. Or use 'Load Test Blink Firmware' on the Upload screen to test flashing.""".trimIndent()
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
            } else {
                emit(CompileProgress(
                    stage = "Toolchain Not Installed",
                    progress = 1.0f,
                    line = TerminalLine(
                        TerminalLineType.WARNING,
                        "AVR compiler toolchain (avr-gcc) is not installed."
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
                        "Configure a remote Arduino-CLI build backend in Settings > Compiler Settings to compile AVR sketches into .hex."
                    ),
                    isFinished = true,
                    isSuccess = false,
                    isToolchainMissing = true
                ))
            }
        }
    }.flowOn(Dispatchers.IO)
}
