package com.google.android.sensing.upload

import java.time.OffsetDateTime

open class SyncUploadProgress {
    val timestamp: OffsetDateTime = OffsetDateTime.now()

    data class Started(val totalRequests: Int): SyncUploadProgress()

    data class InProgress(
        val totalRequests: Int,
        val completedRequests: Int,
        val currentTotalBytes: Long,
        val currentCompletedBytes: Long,
    ): SyncUploadProgress()

    data class Completed(val totalCompletedRequests: Int,): SyncUploadProgress()

    data class Failed(val exceptions: Exception): SyncUploadProgress()
}