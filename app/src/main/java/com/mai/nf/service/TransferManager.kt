package com.mai.nf.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.mai.nf.client.ClientFactory
import com.mai.nf.data.ServerRepository
import com.mai.nf.data.TransferQueueRepository
import com.mai.nf.model.Protocol
import com.mai.nf.model.RemoteFile
import com.mai.nf.model.ServerProfile
import com.mai.nf.model.TransferItem
import com.mai.nf.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class TransferManager(
    private val context: Context,
    val queueRepository: TransferQueueRepository,
    val serverRepository: ServerRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelledIds = ConcurrentHashMap<String, Boolean>()

    fun downloadFile(profile: ServerProfile, remoteFile: RemoteFile): String {
        val downloadDir = getDownloadDirectory()
        val destFile = File(downloadDir, remoteFile.name)
        val partFile = File(downloadDir, "${remoteFile.name}.part")

        val item = TransferItem(
            serverProfileId = profile.id,
            serverName = profile.name,
            protocol = profile.protocol,
            fileName = remoteFile.name,
            remotePath = remoteFile.path,
            localUri = Uri.fromFile(destFile).toString(),
            localFilePath = destFile.absolutePath,
            isUpload = false,
            totalBytes = remoteFile.size,
            status = TransferStatus.QUEUED
        )

        queueRepository.enqueue(item)
        startServiceIfForeground()

        val job = scope.launch {
            executeDownload(item, profile, partFile, destFile)
        }
        activeJobs[item.id] = job
        return item.id
    }

    fun uploadFile(
        profile: ServerProfile,
        localUri: Uri,
        fileName: String,
        remoteDir: String,
        fileSize: Long
    ): String {
        val remotePath = if (remoteDir.endsWith("/")) "$remoteDir$fileName" else "$remoteDir/$fileName"

        val item = TransferItem(
            serverProfileId = profile.id,
            serverName = profile.name,
            protocol = profile.protocol,
            fileName = fileName,
            remotePath = remotePath,
            localUri = localUri.toString(),
            localFilePath = localUri.path ?: "",
            isUpload = true,
            totalBytes = fileSize,
            status = TransferStatus.QUEUED
        )

        queueRepository.enqueue(item)
        startServiceIfForeground()

        val job = scope.launch {
            executeUpload(item, profile, localUri)
        }
        activeJobs[item.id] = job
        return item.id
    }

    fun cancelTransfer(id: String) {
        cancelledIds[id] = true
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        queueRepository.markCancelled(id)
    }

    fun retryTransfer(id: String) {
        val item = queueRepository.transfers.value.find { it.id == id } ?: return
        val profile = serverRepository.servers.value.find { it.id == item.serverProfileId } ?: return

        cancelledIds.remove(id)
        queueRepository.retry(id)

        if (item.isUpload) {
            val uri = Uri.parse(item.localUri)
            val job = scope.launch {
                executeUpload(item, profile, uri)
            }
            activeJobs[item.id] = job
        } else {
            val destFile = File(item.localFilePath)
            val partFile = File(destFile.parentFile, "${destFile.name}.part")
            val job = scope.launch {
                executeDownload(item, profile, partFile, destFile)
            }
            activeJobs[item.id] = job
        }
        startServiceIfForeground()
    }

    private suspend fun executeDownload(
        item: TransferItem,
        profile: ServerProfile,
        partFile: File,
        destFile: File
    ) {
        var lastTransferred = 0L
        var lastTime = System.currentTimeMillis()
        val client = ClientFactory.createClient(profile)

        try {
            queueRepository.updateStatus(item.id, TransferStatus.IN_PROGRESS)

            val connectRes = client.connect()
            if (connectRes.isFailure) {
                val err = connectRes.exceptionOrNull()?.message ?: "Failed to connect to ${profile.host}"
                queueRepository.markFailed(item.id, err)
                return
            }

            if (partFile.exists()) partFile.delete()

            FileOutputStream(partFile).use { outStream ->
                val result = client.downloadFile(
                    remotePath = item.remotePath,
                    destStream = outStream,
                    fileSize = item.totalBytes,
                    onProgress = { transferred, total ->
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 400) {
                            val bytesDelta = transferred - lastTransferred
                            val speed = if (elapsed > 0) (bytesDelta * 1000) / elapsed else 0L
                            lastTransferred = transferred
                            lastTime = now
                            queueRepository.updateProgress(item.id, transferred, total, speed)
                        }
                    },
                    isCancelled = { cancelledIds[item.id] == true }
                )

                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    if (cancelledIds[item.id] == true || ex is InterruptedException) {
                        queueRepository.markCancelled(item.id)
                    } else {
                        queueRepository.markFailed(item.id, ex?.message ?: "Download failed")
                    }
                    if (partFile.exists()) partFile.delete()
                    return
                }
            }

            if (destFile.exists()) destFile.delete()
            val renamed = partFile.renameTo(destFile)
            if (!renamed) {
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
            }

            queueRepository.markCompleted(item.id)
        } catch (e: Exception) {
            if (cancelledIds[item.id] == true || e is InterruptedException) {
                queueRepository.markCancelled(item.id)
            } else {
                queueRepository.markFailed(item.id, e.message ?: "Transfer error: ${e.javaClass.simpleName}")
            }
            if (partFile.exists()) partFile.delete()
        } finally {
            client.close()
            activeJobs.remove(item.id)
            cancelledIds.remove(item.id)
        }
    }

    private suspend fun executeUpload(
        item: TransferItem,
        profile: ServerProfile,
        localUri: Uri
    ) {
        var lastTransferred = 0L
        var lastTime = System.currentTimeMillis()
        val client = ClientFactory.createClient(profile)

        try {
            queueRepository.updateStatus(item.id, TransferStatus.IN_PROGRESS)

            val inStream = context.contentResolver.openInputStream(localUri)
                ?: throw IllegalStateException("Cannot open local file: $localUri")

            val connectRes = client.connect()
            if (connectRes.isFailure) {
                inStream.close()
                val err = connectRes.exceptionOrNull()?.message ?: "Failed to connect to ${profile.host}"
                queueRepository.markFailed(item.id, err)
                return
            }

            inStream.use { stream ->
                val result = client.uploadFile(
                    sourceStream = stream,
                    remotePath = item.remotePath,
                    totalBytes = item.totalBytes,
                    onProgress = { transferred, total ->
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 400) {
                            val bytesDelta = transferred - lastTransferred
                            val speed = if (elapsed > 0) (bytesDelta * 1000) / elapsed else 0L
                            lastTransferred = transferred
                            lastTime = now
                            queueRepository.updateProgress(item.id, transferred, total, speed)
                        }
                    },
                    isCancelled = { cancelledIds[item.id] == true }
                )

                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    if (cancelledIds[item.id] == true || ex is InterruptedException) {
                        queueRepository.markCancelled(item.id)
                    } else {
                        queueRepository.markFailed(item.id, ex?.message ?: "Upload failed")
                    }
                    return
                }
            }

            queueRepository.markCompleted(item.id)
        } catch (e: Exception) {
            if (cancelledIds[item.id] == true || e is InterruptedException) {
                queueRepository.markCancelled(item.id)
            } else {
                queueRepository.markFailed(item.id, e.message ?: "Upload error: ${e.javaClass.simpleName}")
            }
        } finally {
            client.close()
            activeJobs.remove(item.id)
            cancelledIds.remove(item.id)
        }
    }

    private fun getDownloadDirectory(): File {
        val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val maiDir = File(pubDir, "MaiNF")
        if (!maiDir.exists()) {
            maiDir.mkdirs()
        }
        return if (maiDir.canWrite()) maiDir else (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
    }

    private fun startServiceIfForeground() {
        try {
            val intent = Intent(context, TransferService::class.java)
            context.startService(intent)
        } catch (_: Exception) {}
    }
}
