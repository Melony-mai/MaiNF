package com.mai.nf.client

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.mai.nf.model.RemoteFile
import com.mai.nf.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbClientImpl(private val profile: ServerProfile) : RemoteFileClient {

    private var smbClient: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnect()

            val config = SmbConfig.builder()
                .withTimeout(15000, TimeUnit.MILLISECONDS)
                .withSoTimeout(30000, TimeUnit.MILLISECONDS)
                .withMultiProtocolNegotiate(true)
                .build()

            val client = SMBClient(config)
            val conn = client.connect(profile.host, profile.port)

            val authContext = if (profile.isAnonymous) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    profile.username,
                    profile.password.toCharArray(),
                    profile.domain.ifBlank { null }
                )
            }

            val sess = conn.authenticate(authContext)
            
            val shareToConnect = profile.shareName.trim().trim('/', '\\')
            if (shareToConnect.isBlank()) {
                throw IllegalArgumentException("SMB share name is required (e.g. 'shared' or 'public')")
            }

            val share = sess.connectShare(shareToConnect) as? DiskShare
                ?: throw IllegalStateException("Share '$shareToConnect' is not a disk share")

            smbClient = client
            connection = conn
            session = sess
            diskShare = share
            Unit
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        connection?.isConnected == true && diskShare?.isConnected == true
    }

    override suspend fun listFiles(remotePath: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)

            val files: List<FileIdBothDirectoryInformation> = share.list(smbPath)

            files.mapNotNull { f ->
                val name = f.fileName
                if (name == "." || name == "..") return@mapNotNull null
                val isDir = (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                val fullPath = if (remotePath.isBlank() || remotePath == "/") {
                    "/$name"
                } else {
                    "${remotePath.trimEnd('/')}/$name"
                }
                val time = f.changeTime?.toEpochMillis() ?: 0L
                RemoteFile(
                    name = name,
                    path = fullPath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else f.endOfFile,
                    lastModified = time,
                    permissions = if (isDir) "d" else "-"
                )
            }.sortedWith(
                compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }
            )
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        destStream: OutputStream,
        fileSize: Long,
        onProgress: (transferred: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)

            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            try {
                file.inputStream.use { inStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalTransferred = 0L

                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw InterruptedException("Download cancelled by user")
                        }
                        destStream.write(buffer, 0, bytesRead)
                        totalTransferred += bytesRead
                        onProgress(totalTransferred, fileSize)
                    }
                    destStream.flush()
                }
            } finally {
                file.close()
            }
            Unit
        }
    }

    override suspend fun uploadFile(
        sourceStream: InputStream,
        remotePath: String,
        totalBytes: Long,
        onProgress: (transferred: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)

            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )

            try {
                file.outputStream.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalTransferred = 0L

                    while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw InterruptedException("Upload cancelled by user")
                        }
                        outStream.write(buffer, 0, bytesRead)
                        totalTransferred += bytesRead
                        onProgress(totalTransferred, totalBytes)
                    }
                    outStream.flush()
                }
            } finally {
                file.close()
            }
            Unit
        }
    }

    override suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)
            share.mkdir(smbPath)
            Unit
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)
            share.rm(smbPath)
            Unit
        }
    }

    override suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbPath = normalizeSmbPath(remotePath)
            share.rmdir(smbPath, true)
            Unit
        }
    }

    override suspend fun rename(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val share = diskShare!!
            val smbFrom = normalizeSmbPath(fromPath)
            val smbTo = normalizeSmbPath(toPath)

            val file = share.openFile(
                smbFrom,
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            try {
                file.rename(smbTo, true)
            } finally {
                file.close()
            }
            Unit
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            diskShare?.close()
        } catch (_: Exception) {}
        try {
            session?.close()
        } catch (_: Exception) {}
        try {
            connection?.close()
        } catch (_: Exception) {}
        try {
            smbClient?.close()
        } catch (_: Exception) {}

        diskShare = null
        session = null
        connection = null
        smbClient = null
    }

    override fun close() {
        try {
            diskShare?.close()
            session?.close()
            connection?.close()
            smbClient?.close()
        } catch (_: Exception) {}
    }

    private suspend fun ensureConnected() {
        if (!isConnected()) {
            val result = connect()
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("Unable to reconnect to SMB server")
            }
        }
    }

    private fun normalizeSmbPath(path: String): String {
        val trimmed = path.trim().replace('/', '\\').trim('\\')
        return trimmed
    }
}
