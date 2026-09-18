package com.example.firmware

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FirmwareManager(private val context: Context) {

    companion object {
        private const val TAG = "FirmwareManager"
    }

    /**
     * Loads the genuine precompiled ESP32 Blink firmware package from assets.
     */
    fun loadPrecompiledEsp32Blink(): FirmwarePackage? {
        return try {
            val assetManager = context.assets
            val manifestContent = assetManager.open("esp32_blink/flash_manifest.json").bufferedReader().use { it.readText() }
            val json = JSONObject(manifestContent)

            val boardName = json.optString("board", "ESP32 Dev Module")
            val fqbn = json.optString("fqbn", "esp32:esp32:esp32")
            val coreVersion = json.optString("coreVersion", "3.1.1")
            val flashMode = json.optString("flashMode", "DIO")
            val flashFreq = json.optString("flashFreq", "80MHz")
            val flashSize = json.optString("flashSize", "4MB")
            val buildTimestamp = json.optLong("buildTimestamp", System.currentTimeMillis())

            val binariesArray = json.getJSONArray("binaries")
            val binariesList = mutableListOf<FirmwareBinary>()

            for (i in 0 until binariesArray.length()) {
                val item = binariesArray.getJSONObject(i)
                val filename = item.getString("filename")
                val flashAddress = item.getString("flashAddress")
                val flashOffset = item.optInt("flashOffset", 0x10000)

                val bytes = assetManager.open("esp32_blink/$filename").use { it.readBytes() }
                val sha256 = item.optString("sha256", FirmwareBinary.calculateSha256(bytes))

                binariesList.add(
                    FirmwareBinary(
                        filename = filename,
                        flashAddress = flashAddress,
                        flashOffset = flashOffset,
                        size = bytes.size,
                        sha256 = sha256,
                        data = bytes
                    )
                )
            }

            FirmwarePackage(
                boardName = boardName,
                fqbn = fqbn,
                coreVersion = coreVersion,
                flashMode = flashMode,
                flashFreq = flashFreq,
                flashSize = flashSize,
                buildTimestamp = buildTimestamp,
                binaries = binariesList,
                buildLogs = listOf(
                    "Loaded precompiled ESP32 Arduino Blink asset bundle.",
                    "Board: $boardName ($fqbn)",
                    "Total binary package size: ${binariesList.sumOf { it.size }} bytes."
                ),
                isPrecompiled = true,
                backendSource = "Precompiled Blink Asset"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load precompiled ESP32 Blink asset package", e)
            null
        }
    }

    /**
     * Creates a full ZIP archive containing the binaries, flash_manifest.json, and build_info.txt.
     */
    fun exportToZip(pkg: FirmwarePackage): File {
        val outDir = File(context.cacheDir, "firmware").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(pkg.buildTimestamp))
        val zipFile = File(outDir, "ESP32_Firmware_$dateStr.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Write each binary
            for (bin in pkg.binaries) {
                val entry = ZipEntry("ESP32_Firmware/${bin.filename}")
                zos.putNextEntry(entry)
                zos.write(bin.data)
                zos.closeEntry()
            }

            // 2. Generate flash_manifest.json
            val manifestJson = JSONObject().apply {
                put("board", pkg.boardName)
                put("fqbn", pkg.fqbn)
                put("coreVersion", pkg.coreVersion)
                put("flashMode", pkg.flashMode)
                put("flashFreq", pkg.flashFreq)
                put("flashSize", pkg.flashSize)
                put("buildTimestamp", pkg.buildTimestamp)
                put("backendSource", pkg.backendSource)

                val arr = JSONArray()
                for (b in pkg.binaries) {
                    arr.put(JSONObject().apply {
                        put("filename", b.filename)
                        put("flashAddress", b.flashAddress)
                        put("flashOffset", b.flashOffset)
                        put("size", b.size)
                        put("sha256", b.sha256)
                    })
                }
                put("binaries", arr)
            }

            val manifestEntry = ZipEntry("ESP32_Firmware/flash_manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Generate human-readable build_info.txt
            val buildInfoText = buildString {
                appendLine("=================================================================")
                appendLine("ESP32 Arduino Firmware Package")
                appendLine("Generated by Mobile Arduino IDE")
                appendLine("=================================================================")
                appendLine("Target Board:     ${pkg.boardName}")
                appendLine("Target FQBN:      ${pkg.fqbn}")
                appendLine("Core Version:     ${pkg.coreVersion}")
                appendLine("Flash Mode:       ${pkg.flashMode} @ ${pkg.flashFreq}")
                appendLine("Flash Size:       ${pkg.flashSize}")
                appendLine("Backend Source:   ${pkg.backendSource}")
                appendLine("Build Timestamp:  ${Date(pkg.buildTimestamp)}")
                appendLine("Total Size:       ${pkg.formattedTotalSize} (${pkg.totalSizeBytes} bytes)")
                appendLine()
                appendLine("Binary Flash Map:")
                for (b in pkg.binaries) {
                    appendLine("  - Address: ${b.flashAddress.padEnd(8)} | File: ${b.filename.padEnd(18)} | Size: ${b.size.toString().padStart(7)} bytes | SHA256: ${b.sha256}")
                }
                appendLine()
                appendLine("Flashing Command (esptool):")
                val cmdParts = pkg.binaries.sortedBy { it.flashOffset }.joinToString(" ") { "${it.flashAddress} ${it.filename}" }
                appendLine("  esptool.py --chip esp32 write_flash -z --flash_mode ${pkg.flashMode.lowercase()} --flash_freq ${pkg.flashFreq.lowercase()} $cmdParts")
                appendLine("=================================================================")
            }

            val infoEntry = ZipEntry("ESP32_Firmware/build_info.txt")
            zos.putNextEntry(infoEntry)
            zos.write(buildInfoText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return zipFile
    }

    /**
     * Shares the exported firmware ZIP file via standard Android share sheet.
     */
    fun shareFirmwareZip(zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ESP32 Firmware Package: ${zipFile.name}")
            putExtra(Intent.EXTRA_TEXT, "Exported ESP32 Firmware binaries and flash manifest from Mobile Arduino IDE.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
