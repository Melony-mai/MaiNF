package com.mai.nf.server.ftp

import com.mai.nf.server.model.HostServerConfig
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FtpServer(
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
            val server = ServerSocket(config.ftpPort)
            serverSocket = server
            isRunning = true
            onLog("FTP Server started on port ${config.ftpPort}")

            scope.launch {
                while (isActive && isRunning) {
                    try {
                        val client = server.accept()
                        clientSockets.add(client)
                        val count = activeClients.incrementAndGet()
                        onClientCountChanged(count)
                        val clientIp = client.inetAddress.hostAddress ?: "Unknown"
                        onLog("FTP client connected from $clientIp")

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
            onLog("Failed to start FTP server: ${e.message}")
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
        onLog("FTP Server stopped")
    }

    private suspend fun handleClient(socket: Socket, rootDir: File) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
        var currentDir = rootDir
        var user = ""
        var isAuthenticated = false
        var passiveServer: ServerSocket? = null
        var renameFrom: File? = null

        fun resolveFile(pathArg: String): File {
            val clean = pathArg.trim()
            val target = if (clean.startsWith("/")) {
                File(rootDir, clean.trimStart('/'))
            } else {
                File(currentDir, clean)
            }
            val canonicalTarget = target.canonicalFile
            val canonicalRoot = rootDir.canonicalFile
            val rootPath = canonicalRoot.path
            val targetPath = canonicalTarget.path
            val isSafe = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
            return if (isSafe) canonicalTarget else rootDir
        }

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            fun send(code: Int, text: String) {
                writer.write("$code $text\r\n")
                writer.flush()
            }

            send(220, "MaiNF Embedded FTP Service ready")

            while (isRunning && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val parts = trimmed.split(" ", limit = 2)
                val cmd = parts[0].uppercase(Locale.ROOT)
                val arg = if (parts.size > 1) parts[1].trim() else ""

                when (cmd) {
                    "USER" -> {
                        user = arg
                        if (config.anonymousAllowed && (user.equals("anonymous", ignoreCase = true) || user.equals("ftp", ignoreCase = true))) {
                            isAuthenticated = true
                            send(230, "Guest login ok, access restrictions apply.")
                            onLog("FTP: Guest authenticated from $clientIp")
                        } else {
                            send(331, "User name okay, need password.")
                        }
                    }
                    "PASS" -> {
                        if (isAuthenticated) {
                            send(230, "User already logged in.")
                        } else if (config.anonymousAllowed && (user.equals("anonymous", ignoreCase = true) || user.isBlank())) {
                            isAuthenticated = true
                            send(230, "User logged in.")
                            onLog("FTP: Anonymous logged in from $clientIp")
                        } else if (user == config.username && arg == config.password) {
                            isAuthenticated = true
                            send(230, "User logged in, proceed.")
                            onLog("FTP: User '$user' authenticated from $clientIp")
                        } else {
                            send(530, "Not logged in, invalid credentials.")
                            onLog("FTP: Auth failed for '$user' from $clientIp")
                        }
                    }
                    "QUIT" -> {
                        send(221, "Goodbye.")
                        break
                    }
                    "NOOP" -> send(200, "OK")
                    "SYST" -> send(215, "UNIX Type: L8")
                    "FEAT" -> {
                        writer.write("211-Features:\r\n")
                        writer.write(" UTF8\r\n")
                        writer.write(" SIZE\r\n")
                        writer.write(" PASV\r\n")
                        writer.write(" EPSV\r\n")
                        writer.write("211 End\r\n")
                        writer.flush()
                    }
                    "OPTS" -> {
                        if (arg.uppercase().startsWith("UTF8")) {
                            send(200, "Always in UTF8 mode.")
                        } else {
                            send(200, "OK")
                        }
                    }
                    "TYPE" -> send(200, "Type set to $arg.")
                    "PWD", "XPWD" -> {
                        val rel = currentDir.canonicalPath.substring(rootDir.canonicalPath.length).replace('\\', '/')
                        val display = if (rel.isEmpty()) "/" else rel
                        send(257, "\"$display\" is current directory.")
                    }
                    "CWD" -> {
                        val target = resolveFile(arg)
                        if (target.exists() && target.isDirectory) {
                            currentDir = target
                            send(250, "Directory successfully changed.")
                        } else {
                            send(550, "Failed to change directory.")
                        }
                    }
                    "CDUP" -> {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canonicalPath.startsWith(rootDir.canonicalPath)) {
                            currentDir = parent
                        } else {
                            currentDir = rootDir
                        }
                        send(250, "Directory successfully changed.")
                    }
                    "PASV" -> {
                        passiveServer?.close()
                        val pasv = ServerSocket(0)
                        passiveServer = pasv
                        val localIp = localIpProvider().ifBlank { socket.localAddress.hostAddress ?: "127.0.0.1" }
                        val ipParts = localIp.split(".").map { it.toIntOrNull() ?: 0 }
                        val port = pasv.localPort
                        val p1 = port / 256
                        val p2 = port % 256
                        send(227, "Entering Passive Mode (${ipParts.joinToString(",")},$p1,$p2)")
                    }
                    "EPSV" -> {
                        passiveServer?.close()
                        val pasv = ServerSocket(0)
                        passiveServer = pasv
                        send(229, "Entering Extended Passive Mode (|||${pasv.localPort}|)")
                    }
                    "SIZE" -> {
                        val target = resolveFile(arg)
                        if (target.exists() && target.isFile) {
                            send(213, "${target.length()}")
                        } else {
                            send(550, "File not found.")
                        }
                    }
                    "LIST", "NLST" -> {
                        val pasv = passiveServer
                        if (pasv == null) {
                            send(425, "Use PASV or EPSV first.")
                            continue
                        }
                        send(150, "Opening ASCII mode data connection for file list.")
                        try {
                            pasv.accept().use { dataSocket ->
                                val dataWriter = BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8))
                                val files = currentDir.listFiles() ?: emptyArray()
                                val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)

                                for (f in files) {
                                    val isDir = f.isDirectory
                                    val perm = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
                                    val size = if (isDir) 4096 else f.length()
                                    val dateStr = sdf.format(Date(f.lastModified()))
                                    dataWriter.write("$perm 1 owner group $size $dateStr ${f.name}\r\n")
                                }
                                dataWriter.flush()
                            }
                            send(226, "Transfer complete.")
                        } finally {
                            pasv.close()
                            passiveServer = null
                        }
                    }
                    "RETR" -> {
                        val pasv = passiveServer
                        if (pasv == null) {
                            send(425, "Use PASV or EPSV first.")
                            continue
                        }
                        val target = resolveFile(arg)
                        if (!target.exists() || !target.isFile) {
                            send(550, "File not found.")
                            pasv.close()
                            passiveServer = null
                            continue
                        }

                        send(150, "Opening BINARY mode data connection for ${target.name} (${target.length()} bytes).")
                        try {
                            pasv.accept().use { dataSocket ->
                                FileInputStream(target).use { fileIn ->
                                    val dataOut = dataSocket.getOutputStream()
                                    val buffer = ByteArray(64 * 1024)
                                    var read: Int
                                    while (fileIn.read(buffer).also { read = it } != -1) {
                                        dataOut.write(buffer, 0, read)
                                    }
                                    dataOut.flush()
                                }
                            }
                            send(226, "Transfer complete.")
                            onLog("FTP: Downloaded '${target.name}' to $clientIp")
                        } finally {
                            pasv.close()
                            passiveServer = null
                        }
                    }
                    "STOR" -> {
                        if (config.readOnly) {
                            send(550, "Permission denied: Server is in Read-Only mode.")
                            continue
                        }
                        val pasv = passiveServer
                        if (pasv == null) {
                            send(425, "Use PASV or EPSV first.")
                            continue
                        }
                        val target = resolveFile(arg)
                        send(150, "Ready to receive data.")
                        try {
                            pasv.accept().use { dataSocket ->
                                val dataIn = dataSocket.getInputStream()
                                FileOutputStream(target).use { fileOut ->
                                    val buffer = ByteArray(64 * 1024)
                                    var read: Int
                                    while (dataIn.read(buffer).also { read = it } != -1) {
                                        fileOut.write(buffer, 0, read)
                                    }
                                    fileOut.flush()
                                }
                            }
                            send(226, "Transfer complete.")
                            onLog("FTP: Uploaded '${target.name}' from $clientIp")
                        } finally {
                            pasv.close()
                            passiveServer = null
                        }
                    }
                    "MKD" -> {
                        if (config.readOnly) {
                            send(550, "Read-only mode.")
                        } else {
                            val target = resolveFile(arg)
                            if (target.mkdirs()) {
                                send(257, "\"$arg\" created.")
                                onLog("FTP: Folder created '$arg' by $clientIp")
                            } else {
                                send(550, "Failed to create directory.")
                            }
                        }
                    }
                    "RMD" -> {
                        if (config.readOnly) {
                            send(550, "Read-only mode.")
                        } else {
                            val target = resolveFile(arg)
                            if (target.exists() && target.isDirectory && target.deleteRecursively()) {
                                send(250, "Directory removed.")
                                onLog("FTP: Folder deleted '$arg' by $clientIp")
                            } else {
                                send(550, "Failed to remove directory.")
                            }
                        }
                    }
                    "DELE" -> {
                        if (config.readOnly) {
                            send(550, "Read-only mode.")
                        } else {
                            val target = resolveFile(arg)
                            if (target.exists() && target.isFile && target.delete()) {
                                send(250, "File deleted.")
                                onLog("FTP: File deleted '$arg' by $clientIp")
                            } else {
                                send(550, "Failed to delete file.")
                            }
                        }
                    }
                    "RNFR" -> {
                        val target = resolveFile(arg)
                        if (target.exists()) {
                            renameFrom = target
                            send(350, "Ready for RNTO.")
                        } else {
                            send(550, "File not found.")
                        }
                    }
                    "RNTO" -> {
                        val from = renameFrom
                        if (from == null) {
                            send(503, "Bad sequence of commands.")
                        } else if (config.readOnly) {
                            send(550, "Read-only mode.")
                            renameFrom = null
                        } else {
                            val target = resolveFile(arg)
                            if (from.renameTo(target)) {
                                send(250, "Rename successful.")
                                onLog("FTP: Renamed '${from.name}' to '${target.name}' by $clientIp")
                            } else {
                                send(550, "Failed to rename.")
                            }
                            renameFrom = null
                        }
                    }
                    else -> send(502, "Command not implemented.")
                }
            }
        } catch (_: Exception) {
        } finally {
            try { passiveServer?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            clientSockets.remove(socket)
            val count = activeClients.decrementAndGet()
            onClientCountChanged(count.coerceAtLeast(0))
            onLog("FTP client disconnected: $clientIp")
        }
    }
}
