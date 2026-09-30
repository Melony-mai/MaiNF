package com.mai.nf.client

import com.mai.nf.model.RemoteFile
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface RemoteFileClient : Closeable {
    suspend fun connect(): Result<Unit>
    suspend fun isConnected(): Boolean
    suspend fun listFiles(remotePath: String): Result<List<RemoteFile>>
    suspend fun downloadFile(
        remotePath: String,
        destStream: OutputStream,
        fileSize: Long,
        onProgress: (transferred: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<Unit>
    suspend fun uploadFile(
        sourceStream: InputStream,
        remotePath: String,
        totalBytes: Long,
        onProgress: (transferred: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<Unit>
    suspend fun createDirectory(remotePath: String): Result<Unit>
    suspend fun deleteFile(remotePath: String): Result<Unit>
    suspend fun deleteDirectory(remotePath: String): Result<Unit>
    suspend fun rename(fromPath: String, toPath: String): Result<Unit>
    suspend fun disconnect()
}
