package com.mai.nf.data

import com.mai.nf.model.TransferItem
import com.mai.nf.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferQueueRepository {

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    fun enqueue(item: TransferItem) {
        _transfers.value = _transfers.value + item
    }

    fun updateStatus(id: String, status: TransferStatus, errorMessage: String? = null) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(status = status, errorMessage = errorMessage ?: item.errorMessage)
            } else item
        }
    }

    fun updateProgress(id: String, transferred: Long, total: Long, speed: Long) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                val totalBytes = if (total > 0) total else item.totalBytes
                item.copy(
                    transferredBytes = transferred,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speed,
                    status = TransferStatus.IN_PROGRESS
                )
            } else item
        }
    }

    fun markCompleted(id: String) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = TransferStatus.COMPLETED,
                    transferredBytes = item.totalBytes.coerceAtLeast(item.transferredBytes),
                    speedBytesPerSec = 0
                )
            } else item
        }
    }

    fun markFailed(id: String, error: String) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = error,
                    speedBytesPerSec = 0
                )
            } else item
        }
    }

    fun markCancelled(id: String) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = TransferStatus.CANCELLED,
                    speedBytesPerSec = 0
                )
            } else item
        }
    }

    fun remove(id: String) {
        _transfers.value = _transfers.value.filterNot { it.id == id }
    }

    fun clearCompleted() {
        _transfers.value = _transfers.value.filter {
            it.status == TransferStatus.QUEUED || it.status == TransferStatus.IN_PROGRESS
        }
    }

    fun retry(id: String) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = TransferStatus.QUEUED,
                    transferredBytes = 0L,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
            } else item
        }
    }
}
