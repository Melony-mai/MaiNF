package com.mai.nf.server.smb

import com.mai.nf.server.model.HostServerConfig
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SmbServer(
    private val config: HostServerConfig,
    private val localIpProvider: () -> String,
    private val onLog: (String) -> Unit,
    private val onClientCountChanged: (Int) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val activeClients = AtomicInteger(0)
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()

    val isServerRunning: Boolean
        get() = isRunning

    fun start() {
        if (isRunning) return
        val rootDir = File(config.sharedFolderPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        try {
            val server = ServerSocket(config.smbPort)
            serverSocket = server
            isRunning = true
            onLog("SMB Server started on port ${config.smbPort}")

            scope.launch {
                while (isActive && isRunning) {
                    try {
                        val client = server.accept()
                        clientSockets.add(client)
                        val count = activeClients.incrementAndGet()
                        onClientCountChanged(count)
                        val clientIp = client.inetAddress.hostAddress ?: "Unknown"
                        onLog("SMB client connected from $clientIp")

                        launch {
                            handleClient(client, rootDir)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
            onLog("Failed to start SMB server: ${e.message}")
            throw e
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (s in clientSockets) {
            try { s.close() } catch (_: Exception) {}
        }
        clientSockets.clear()
        activeClients.set(0)
        onClientCountChanged(0)
        onLog("SMB Server stopped")
    }

    private suspend fun handleClient(socket: Socket, rootDir: File) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
        val openFiles = ConcurrentHashMap<Long, FileHandle>()
        val handleIdGen = AtomicLong(100)

        try {
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            while (isRunning && !socket.isClosed) {
                // Read 4-byte NetBIOS direct TCP transport header
                val b0 = inStream.read()
                if (b0 == -1) break
                val b1 = inStream.read()
                val b2 = inStream.read()
                val b3 = inStream.read()
                if (b1 == -1 || b2 == -1 || b3 == -1) break

                val packetLength = ((b1 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b3 and 0xFF)
                if (packetLength <= 0 || packetLength > 10 * 1024 * 1024) break

                val packetData = ByteArray(packetLength)
                inStream.readFully(packetData)

                val response = processSmbPacket(packetData, rootDir, openFiles, handleIdGen, clientIp)
                if (response != null) {
                    val respLen = response.size
                    outStream.writeByte(0x00)
                    outStream.writeByte((respLen shr 16) and 0xFF)
                    outStream.writeByte((respLen shr 8) and 0xFF)
                    outStream.writeByte(respLen and 0xFF)
                    outStream.write(response)
                    outStream.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            for (h in openFiles.values) {
                try { h.close() } catch (_: Exception) {}
            }
            openFiles.clear()
            try { socket.close() } catch (_: Exception) {}
            clientSockets.remove(socket)
            val count = activeClients.decrementAndGet()
            onClientCountChanged(count.coerceAtLeast(0))
            onLog("SMB client disconnected: $clientIp")
        }
    }

    private fun processSmbPacket(
        data: ByteArray,
        rootDir: File,
        openFiles: ConcurrentHashMap<Long, FileHandle>,
        handleGen: AtomicLong,
        clientIp: String
    ): ByteArray? {
        if (data.size < 64) return null

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Check SMB2 magic "\u00feSMB" (0xFE, 'S', 'M', 'B')
        val magic0 = bb.get(0)
        val magic1 = bb.get(1)
        val magic2 = bb.get(2)
        val magic3 = bb.get(3)

        // If client sends legacy SMB1 negotiate request, respond with SMB2 dialect
        if (magic0 == 0xFF.toByte() && magic1 == 'S'.toByte() && magic2 == 'M'.toByte() && magic3 == 'B'.toByte()) {
            return buildSmb1NegotiateResponse()
        }

        if (magic0 != 0xFE.toByte() || magic1 != 'S'.toByte() || magic2 != 'M'.toByte() || magic3 != 'B'.toByte()) {
            return null
        }

        val creditCharge = bb.getShort(6)
        val status = bb.getInt(8)
        val command = bb.getShort(12).toInt() and 0xFFFF
        val creditsRequested = bb.getShort(14).toInt() and 0xFFFF
        val flags = bb.getInt(16)
        val messageId = bb.getLong(24)
        val asyncId = bb.getLong(32)
        val sessionId = bb.getLong(40)

        val creditResponse = (creditsRequested.coerceAtLeast(1) + 32).toShort()

        fun buildHeader(statusVal: Int, bodySize: Int): ByteBuffer {
            val totalSize = 64 + bodySize
            val resp = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
            resp.put(0xFE.toByte())
            resp.put('S'.toByte())
            resp.put('M'.toByte())
            resp.put('B'.toByte())
            resp.putShort(64) // Header size
            resp.putShort(creditCharge)
            resp.putInt(statusVal) // Status
            resp.putShort(command.toShort())
            resp.putShort(creditResponse)
            resp.putInt(flags or 0x00000001) // SERVER_TO_REDIR flag
            resp.putInt(0) // Next command
            resp.putLong(messageId)
            resp.putInt(0) // Process ID
            resp.putInt(0) // Tree ID
            resp.putLong(if (sessionId != 0L) sessionId else 1L)
            resp.putLong(0L) // Signature
            resp.putLong(0L)
            return resp
        }

        when (command) {
            0x0000 -> { // SMB2_NEGOTIATE
                val resp = buildHeader(0, 65)
                resp.putShort(65) // Struct size
                resp.putShort(0x0001) // Signing enabled
                resp.putShort(0x0210) // Dialect: SMB 2.1
                resp.putShort(1) // Negotiate context count
                // Server GUID (16 bytes)
                resp.putLong(0x0123456789ABCDEFL)
                resp.putLong(0x0FEDCBA987654321L)
                resp.putInt(0x0000007F) // Capabilities
                resp.putInt(1024 * 1024) // Max trans size
                resp.putInt(1024 * 1024) // Max read size
                resp.putInt(1024 * 1024) // Max write size
                resp.putLong(System.currentTimeMillis() * 10000L + 116444736000000000L) // System time
                resp.putLong(System.currentTimeMillis() * 10000L + 116444736000000000L) // Boot time
                resp.putShort(128) // Security buffer offset
                resp.putShort(0)   // Security buffer length
                resp.putInt(0)     // Negotiate context offset
                onLog("SMB: Negotiated dialect 2.1 with $clientIp")
                return resp.array()
            }

            0x0001 -> { // SMB2_SESSION_SETUP
                val resp = buildHeader(0, 9)
                resp.putShort(9) // Struct size
                resp.putShort(0x0001) // Session flags: GUEST
                resp.putShort(72) // Security buffer offset
                resp.putShort(0)  // Security buffer length
                onLog("SMB: Guest session setup accepted for $clientIp")
                return resp.array()
            }

            0x0003 -> { // SMB2_TREE_CONNECT
                val resp = buildHeader(0, 16)
                resp.putShort(16) // Struct size
                resp.put(0x01.toByte()) // Share type: DISK
                resp.put(0.toByte())
                resp.putInt(0x00000030) // Share flags
                resp.putInt(0x001F01FF) // Maximal access permissions
                onLog("SMB: Tree connected to share '${config.smbShareName}' for $clientIp")
                return resp.array()
            }

            0x0005 -> { // SMB2_CREATE
                val nameOffset = bb.getShort(64 + 44).toInt() and 0xFFFF
                val nameLength = bb.getShort(64 + 46).toInt() and 0xFFFF

                var reqPath = ""
                if (nameLength > 0 && nameOffset >= 64 && nameOffset + nameLength <= data.size) {
                    reqPath = String(data, nameOffset, nameLength, Charsets.UTF_16LE).replace('\\', '/').trim('/')
                }

                val targetFile = if (reqPath.isBlank()) rootDir else File(rootDir, reqPath).canonicalFile
                val rootPath = rootDir.canonicalPath
                val targetPath = targetFile.canonicalPath
                val isSafe = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
                if (!isSafe) {
                    val resp = buildHeader(0xC0000022.toInt(), 9) // STATUS_ACCESS_DENIED
                    resp.putShort(9)
                    return resp.array()
                }

                val isDir = targetFile.isDirectory || reqPath.isBlank()
                val handleId = handleGen.incrementAndGet()
                openFiles[handleId] = FileHandle(targetFile, isDir)

                val resp = buildHeader(0, 89)
                resp.putShort(89) // Struct size
                resp.put(0.toByte()) // Oplock
                resp.put(0.toByte()) // Flags
                resp.putInt(1) // Action: FILE_OPENED
                resp.putLong(targetFile.lastModified() * 10000L + 116444736000000000L) // Creation time
                resp.putLong(targetFile.lastModified() * 10000L + 116444736000000000L) // Last access
                resp.putLong(targetFile.lastModified() * 10000L + 116444736000000000L) // Last write
                resp.putLong(targetFile.lastModified() * 10000L + 116444736000000000L) // Change time
                resp.putLong(targetFile.length()) // Allocation size
                resp.putLong(targetFile.length()) // End of file
                resp.putInt(if (isDir) 0x00000010 else 0x00000080) // File attributes (DIRECTORY or NORMAL)
                resp.putInt(0) // Reserved
                resp.putLong(handleId) // FileId Persistent
                resp.putLong(handleId) // FileId Volatile
                resp.putInt(0) // Create contexts offset
                resp.putInt(0) // Create contexts length
                return resp.array()
            }

            0x0006 -> { // SMB2_CLOSE
                val handleId = bb.getLong(64 + 8)
                val handle = openFiles.remove(handleId)
                handle?.close()

                val resp = buildHeader(0, 60)
                resp.putShort(60) // Struct size
                resp.putShort(0)
                resp.putInt(0)
                resp.putLong(0)
                resp.putLong(0)
                resp.putLong(0)
                resp.putLong(0)
                resp.putLong(0)
                resp.putLong(0)
                return resp.array()
            }

            0x000E -> { // SMB2_QUERY_DIRECTORY
                val handleId = bb.getLong(64 + 8)
                val handle = openFiles[handleId]
                val dir = handle?.file ?: rootDir

                val entries = dir.listFiles() ?: emptyArray()
                val entriesBuffer = ByteArrayOutputStream()
                val dataOut = DataOutputStream(entriesBuffer)

                val allList = mutableListOf<File>()
                allList.add(File("."))
                allList.add(File(".."))
                allList.addAll(entries)

                for (i in allList.indices) {
                    val f = allList[i]
                    val isCurrentDir = f.name == "."
                    val isParent = f.name == ".."
                    val isDirectory = isCurrentDir || isParent || f.isDirectory
                    val nameBytes = f.name.toByteArray(Charsets.UTF_16LE)
                    val recordLen = 104 + nameBytes.size
                    val pad = (4 - (recordLen % 4)) % 4
                    val totalRecordLen = recordLen + pad
                    val nextOffset = if (i == allList.size - 1) 0 else totalRecordLen

                    val rec = ByteBuffer.allocate(totalRecordLen).order(ByteOrder.LITTLE_ENDIAN)
                    rec.putInt(nextOffset)
                    rec.putInt(i) // File index
                    rec.putLong(f.lastModified() * 10000L + 116444736000000000L) // Creation
                    rec.putLong(f.lastModified() * 10000L + 116444736000000000L) // Last access
                    rec.putLong(f.lastModified() * 10000L + 116444736000000000L) // Last write
                    rec.putLong(f.lastModified() * 10000L + 116444736000000000L) // Change
                    rec.putLong(if (isDirectory) 0L else f.length()) // End of file
                    rec.putLong(if (isDirectory) 0L else f.length()) // Allocation
                    rec.putInt(if (isDirectory) 0x10 else 0x80) // Attributes
                    rec.putInt(nameBytes.size) // File name length
                    rec.putInt(0) // EA size
                    rec.putShort(0) // Short name length
                    rec.put(ByteArray(24)) // Short name
                    rec.putLong(0) // Reserved
                    rec.putLong(f.hashCode().toLong()) // File ID
                    rec.put(nameBytes)
                    for (p in 0 until pad) rec.put(0.toByte())

                    dataOut.write(rec.array())
                }

                val payload = entriesBuffer.toByteArray()
                val resp = buildHeader(0, 9 + payload.size)
                resp.putShort(9) // Struct size
                resp.putShort(72) // Output buffer offset
                resp.putInt(payload.size) // Output buffer length
                resp.put(payload)
                return resp.array()
            }

            0x0008 -> { // SMB2_READ
                val handleId = bb.getLong(64 + 16)
                val readLength = bb.getInt(64 + 4)
                val offset = bb.getLong(64 + 8)

                val handle = openFiles[handleId]
                val file = handle?.file
                if (file == null || !file.exists() || file.isDirectory) {
                    val resp = buildHeader(0xC0000008.toInt(), 9) // STATUS_INVALID_HANDLE
                    resp.putShort(9)
                    return resp.array()
                }

                val buffer = ByteArray(readLength.coerceAtMost(64 * 1024))
                var actualRead = 0
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    actualRead = raf.read(buffer).coerceAtLeast(0)
                }

                val resp = buildHeader(0, 17 + actualRead)
                resp.putShort(17) // Struct size
                resp.put(80.toByte()) // Data offset
                resp.put(0.toByte())
                resp.putInt(actualRead)
                resp.putInt(0) // Remaining
                resp.putInt(0) // Reserved
                resp.put(buffer, 0, actualRead)
                return resp.array()
            }

            0x0009 -> { // SMB2_WRITE
                if (config.readOnly) {
                    val resp = buildHeader(0xC0000022.toInt(), 17) // STATUS_ACCESS_DENIED
                    resp.putShort(17)
                    return resp.array()
                }

                val handleId = bb.getLong(64 + 16)
                val dataOffset = bb.getShort(64 + 2).toInt() and 0xFFFF
                val writeLength = bb.getInt(64 + 4)
                val offset = bb.getLong(64 + 8)

                val handle = openFiles[handleId]
                val file = handle?.file
                if (file == null) {
                    val resp = buildHeader(0xC0000008.toInt(), 17)
                    resp.putShort(17)
                    return resp.array()
                }

                if (dataOffset >= 64 && dataOffset + writeLength <= data.size) {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(offset)
                        raf.write(data, dataOffset, writeLength)
                    }
                }

                val resp = buildHeader(0, 17)
                resp.putShort(17)
                resp.putShort(0)
                resp.putInt(writeLength)
                resp.putInt(0)
                resp.putLong(0)
                return resp.array()
            }

            0x0010 -> { // SMB2_QUERY_INFO
                val handleId = bb.getLong(64 + 8)
                val handle = openFiles[handleId]
                val file = handle?.file ?: rootDir
                val isDir = file.isDirectory

                val infoBuf = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                infoBuf.putLong(file.lastModified() * 10000L + 116444736000000000L) // Creation
                infoBuf.putLong(file.lastModified() * 10000L + 116444736000000000L) // Access
                infoBuf.putLong(file.lastModified() * 10000L + 116444736000000000L) // Write
                infoBuf.putLong(file.lastModified() * 10000L + 116444736000000000L) // Change
                infoBuf.putInt(if (isDir) 0x10 else 0x80)
                infoBuf.putInt(0)

                val resp = buildHeader(0, 9 + infoBuf.capacity())
                resp.putShort(9)
                resp.putShort(72)
                resp.putInt(infoBuf.capacity())
                resp.put(infoBuf.array())
                return resp.array()
            }

            0x000B -> { // SMB2_ECHO
                val resp = buildHeader(0, 4)
                resp.putShort(4)
                resp.putShort(0)
                return resp.array()
            }

            0x0004, 0x0002 -> { // TREE_DISCONNECT / LOGOFF
                val resp = buildHeader(0, 4)
                resp.putShort(4)
                resp.putShort(0)
                return resp.array()
            }

            else -> {
                val resp = buildHeader(0xC0000002.toInt(), 9) // STATUS_NOT_IMPLEMENTED
                resp.putShort(9)
                return resp.array()
            }
        }
    }

    private fun buildSmb1NegotiateResponse(): ByteArray {
        val resp = ByteBuffer.allocate(35).order(ByteOrder.LITTLE_ENDIAN)
        resp.put(0xFF.toByte())
        resp.put('S'.toByte())
        resp.put('M'.toByte())
        resp.put('B'.toByte())
        resp.put(0x72.toByte()) // SMB_COM_NEGOTIATE
        resp.putInt(0) // Status
        resp.put(0x88.toByte()) // Flags
        resp.putShort(0xC801.toShort()) // Flags2
        resp.putShort(0) // PID High
        resp.putLong(0) // Signature
        resp.putShort(0) // Reserved
        resp.putShort(0) // TID
        resp.putShort(0) // PID
        resp.putShort(0) // UID
        resp.putShort(0) // MID
        resp.put(1.toByte()) // Word count
        resp.putShort(0) // Selected dialect index: 0 (SMB2)
        resp.putShort(0) // Byte count
        return resp.array()
    }

    private class FileHandle(val file: File, val isDirectory: Boolean) : Closeable {
        override fun close() {}
    }
}
