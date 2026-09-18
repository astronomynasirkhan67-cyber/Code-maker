package com.example.firmware

import java.security.MessageDigest

data class FirmwareBinary(
    val filename: String,
    val flashAddress: String, // e.g. "0x1000"
    val flashOffset: Int,     // e.g. 0x1000
    val size: Int,
    val sha256: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FirmwareBinary
        if (filename != other.filename) return false
        if (flashOffset != other.flashOffset) return false
        if (size != other.size) return false
        if (sha256 != other.sha256) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + flashOffset
        result = 31 * result + size
        result = 31 * result + sha256.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        fun calculateSha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun create(filename: String, flashAddress: String, data: ByteArray): FirmwareBinary {
            val offset = try {
                if (flashAddress.startsWith("0x", ignoreCase = true)) {
                    flashAddress.substring(2).toInt(16)
                } else {
                    flashAddress.toInt()
                }
            } catch (e: Exception) {
                0x10000
            }
            return FirmwareBinary(
                filename = filename,
                flashAddress = flashAddress,
                flashOffset = offset,
                size = data.size,
                sha256 = calculateSha256(data),
                data = data
            )
        }
    }
}

data class FirmwarePackage(
    val boardName: String,
    val fqbn: String,
    val coreVersion: String = "3.1.1",
    val flashMode: String = "DIO",
    val flashFreq: String = "80MHz",
    val flashSize: String = "4MB",
    val buildTimestamp: Long = System.currentTimeMillis(),
    val binaries: List<FirmwareBinary> = emptyList(),
    val buildLogs: List<String> = emptyList(),
    val isPrecompiled: Boolean = false,
    val backendSource: String = "Arduino CLI Build Server" // "Local Compiler", "Arduino CLI Build Server", or "Precompiled Asset"
) {
    val totalSizeBytes: Int get() = binaries.sumOf { it.size }

    val targetChip: String
        get() = if (fqbn.contains("esp32", ignoreCase = true)) "ESP32"
        else if (fqbn.contains("avr", ignoreCase = true)) "AVR"
        else boardName

    val formattedTotalSize: String
        get() {
            val kb = totalSizeBytes / 1024.0
            return if (kb > 1024) {
                "%.2f MB".format(kb / 1024.0)
            } else {
                "%.1f KB".format(kb)
            }
        }

    fun getBinaryForOffset(offset: Int): FirmwareBinary? {
        return binaries.find { it.flashOffset == offset }
    }

    /**
     * Converts to sequential flash segments ordered by offset.
     */
    fun toFlashSegments(): List<FlashSegment> {
        return binaries.sortedBy { it.flashOffset }.map {
            FlashSegment(it.filename, it.flashOffset, it.data)
        }
    }
}

data class FlashSegment(
    val filename: String,
    val flashOffset: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlashSegment
        if (filename != other.filename) return false
        if (flashOffset != other.flashOffset) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + flashOffset
        result = 31 * result + data.contentHashCode()
        return result
    }
}
