package com.mai.nf.client

import com.mai.nf.model.Protocol
import com.mai.nf.model.RemoteFile
import com.mai.nf.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.InputStream
import java.io.OutputStream

class FtpClientImpl(private val profile: ServerProfile) : RemoteFileClient {

    private var ftpClient: FTPClient? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnect()

            val client = if (profile.protocol == Protocol.FTPS) {
                FTPSClient(false).apply {
                    // Accept all certificates for custom/self-signed home servers if needed
                    trustManager = org.apache.commons.net.util.TrustManagerUtils.getAcceptAllTrustManager()
                }
            } else {
                FTPClient()
            }

            client.connectTimeout = 15000
            client.defaultTimeout = 15000
            client.setDataTimeout(java.time.Duration.ofMillis(30000))
            client.controlEncoding = "UTF-8"

            client.connect(profile.host, profile.port)
            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect()
                throw IllegalStateException("FTP server refused connection with code $reply: ${client.replyString}")
            }

            val user = if (profile.isAnonymous) "anonymous" else profile.username
            val pass = if (profile.isAnonymous) "anonymous@mainf.app" else profile.password

            val loggedIn = client.login(user, pass)
            if (!loggedIn) {
                client.disconnect()
                throw IllegalStateException("Authentication failed for user: $user")
            }

            client.setFileType(FTP.BINARY_FILE_TYPE)
            if (profile.passiveMode) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }

            if (profile.protocol == Protocol.FTPS && client is FTPSClient) {
                client.execPROT("P") // Secure data channel
            }

            ftpClient = client
            Unit
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext false
        try {
            client.isConnected && client.sendNoOp()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listFiles(remotePath: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val client = ftpClient!!
            val path = if (remotePath.isBlank()) "/" else remotePath
            val files = client.listFiles(path)
            
            files?.mapNotNull { f ->
                val name = f.name
                if (name == "." || name == "..") return@mapNotNull null
                val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                val time = f.timestamp?.timeInMillis ?: 0L
                RemoteFile(
                    name = name,
                    path = fullPath,
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) 0L else f.size,
                    lastModified = time,
                    permissions = f.rawListing ?: ""
                )
            }?.sortedWith(
                compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: emptyList()
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
            val client = ftpClient!!
            val inputStream = client.retrieveFileStream(remotePath)
                ?: throw IllegalStateException("Failed to open remote file: ${client.replyString}")

            try {
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalTransferred = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        throw InterruptedException("Download cancelled by user")
                    }
                    destStream.write(buffer, 0, bytesRead)
                    totalTransferred += bytesRead
                    onProgress(totalTransferred, fileSize)
                }
                destStream.flush()
            } finally {
                inputStream.close()
                client.completePendingCommand()
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
            val client = ftpClient!!
            val outputStream = client.storeFileStream(remotePath)
                ?: throw IllegalStateException("Failed to open remote destination: ${client.replyString}")

            try {
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalTransferred = 0L

                while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        throw InterruptedException("Upload cancelled by user")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    totalTransferred += bytesRead
                    onProgress(totalTransferred, totalBytes)
                }
                outputStream.flush()
            } finally {
                outputStream.close()
                client.completePendingCommand()
            }
            Unit
        }
    }

    override suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val success = ftpClient!!.makeDirectory(remotePath)
            if (!success) {
                throw IllegalStateException("Failed to create folder: ${ftpClient!!.replyString}")
            }
            Unit
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val success = ftpClient!!.deleteFile(remotePath)
            if (!success) {
                throw IllegalStateException("Failed to delete file: ${ftpClient!!.replyString}")
            }
            Unit
        }
    }

    override suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val success = ftpClient!!.removeDirectory(remotePath)
            if (!success) {
                throw IllegalStateException("Failed to delete directory: ${ftpClient!!.replyString}")
            }
            Unit
        }
    }

    override suspend fun rename(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConnected()
            val success = ftpClient!!.rename(fromPath, toPath)
            if (!success) {
                throw IllegalStateException("Failed to rename: ${ftpClient!!.replyString}")
            }
            Unit
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            ftpClient?.let {
                if (it.isConnected) {
                    it.logout()
                    it.disconnect()
                }
            }
        } catch (_: Exception) {
        } finally {
            ftpClient = null
        }
    }

    override fun close() {
        try {
            ftpClient?.let {
                if (it.isConnected) it.disconnect()
            }
        } catch (_: Exception) {}
        ftpClient = null
    }

    private suspend fun ensureConnected() {
        if (!isConnected()) {
            val result = connect()
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("Unable to reconnect to FTP server")
            }
        }
    }
}
