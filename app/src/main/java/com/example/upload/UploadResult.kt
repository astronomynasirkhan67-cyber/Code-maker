package com.example.upload

/**
 * Result returned by board-specific hardware flashers.
 */
data class UploadResult(
    val success: Boolean,
    val error: String? = null
)
