package com.mai.nf.model

import java.util.UUID

enum class TransferStatus {
    QUEUED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val serverProfileId: String,
    val serverName: String,
    val protocol: Protocol,
    val fileName: String,
    val remotePath: String,
    val localUri: String,
    val localFilePath: String = "",
    val isUpload: Boolean,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedSpeed: String
        get() {
            if (speedBytesPerSec <= 0) return "--"
            val kb = speedBytesPerSec / 1024.0
            if (kb < 1024) return String.format("%.1f KB/s", kb)
            val mb = kb / 1024.0
            return String.format("%.1f MB/s", mb)
        }

    val formattedTransferred: String
        get() {
            val transMb = transferredBytes / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return if (totalBytes > 0) {
                String.format("%.2f / %.2f MB", transMb, totalMb)
            } else {
                String.format("%.2f MB", transMb)
            }
        }
}
