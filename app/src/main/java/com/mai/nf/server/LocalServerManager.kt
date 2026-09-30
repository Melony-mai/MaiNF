package com.mai.nf.server

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Environment
import com.mai.nf.server.ftp.FtpServer
import com.mai.nf.server.model.HostServerConfig
import com.mai.nf.server.smb.SmbServer
import com.mai.nf.service.HostServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalServerManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("host_server_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<HostServerConfig> = _config.asStateFlow()

    private val _deviceIp = MutableStateFlow(detectDeviceIp())
    val deviceIp: StateFlow<String> = _deviceIp.asStateFlow()

    private val _ftpRunning = MutableStateFlow(false)
    val ftpRunning: StateFlow<Boolean> = _ftpRunning.asStateFlow()

    private val _smbRunning = MutableStateFlow(false)
    val smbRunning: StateFlow<Boolean> = _smbRunning.asStateFlow()

    private val _ftpClients = MutableStateFlow(0)
    val ftpClients: StateFlow<Int> = _ftpClients.asStateFlow()

    private val _smbClients = MutableStateFlow(0)
    val smbClients: StateFlow<Int> = _smbClients.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var ftpServer: FtpServer? = null
    private var smbServer: SmbServer? = null

    init {
        refreshDeviceIp()
    }

    fun refreshDeviceIp() {
        _deviceIp.value = detectDeviceIp()
    }

    fun updateConfig(newConfig: HostServerConfig) {
        _config.value = newConfig
        saveConfig(newConfig)
    }

    fun startFtp(): Result<Unit> {
        return runCatching {
            if (_ftpRunning.value) return@runCatching
            refreshDeviceIp()
            val server = FtpServer(
                config = _config.value,
                localIpProvider = { _deviceIp.value },
                onLog = { addLog(it) },
                onClientCountChanged = { _ftpClients.value = it }
            )
            server.start()
            ftpServer = server
            _ftpRunning.value = true
            updateForegroundService()
        }
    }

    fun stopFtp() {
        ftpServer?.stop()
        ftpServer = null
        _ftpRunning.value = false
        _ftpClients.value = 0
        updateForegroundService()
    }

    fun startSmb(): Result<Unit> {
        return runCatching {
            if (_smbRunning.value) return@runCatching
            refreshDeviceIp()
            val server = SmbServer(
                config = _config.value,
                localIpProvider = { _deviceIp.value },
                onLog = { addLog(it) },
                onClientCountChanged = { _smbClients.value = it }
            )
            server.start()
            smbServer = server
            _smbRunning.value = true
            updateForegroundService()
        }
    }

    fun stopSmb() {
        smbServer?.stop()
        smbServer = null
        _smbRunning.value = false
        _smbClients.value = 0
        updateForegroundService()
    }

    fun stopAll() {
        stopFtp()
        stopSmb()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timeStr] $message"
        val current = _logs.value.toMutableList()
        if (current.size > 200) current.removeAt(0)
        current.add(entry)
        _logs.value = current
    }

    private fun detectDeviceIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress ?: ""
                        if (hostAddr.isNotBlank() && !hostAddr.startsWith("127.")) {
                            return hostAddr
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun updateForegroundService() {
        val anyRunning = _ftpRunning.value || _smbRunning.value
        val intent = Intent(context, HostServerService::class.java)
        if (anyRunning) {
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        } else {
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    private fun loadConfig(): HostServerConfig {
        val defaultDir = getSharedDirectoryDefault().absolutePath
        val jsonStr = prefs.getString("config_json", null) ?: return HostServerConfig(sharedFolderPath = defaultDir)
        return try {
            val obj = JSONObject(jsonStr)
            HostServerConfig(
                ftpPort = obj.optInt("ftpPort", 2121),
                smbPort = obj.optInt("smbPort", 4450),
                username = obj.optString("username", "admin"),
                password = obj.optString("password", "123456"),
                anonymousAllowed = obj.optBoolean("anonymousAllowed", true),
                readOnly = obj.optBoolean("readOnly", false),
                sharedFolderPath = obj.optString("sharedFolderPath", defaultDir),
                smbShareName = obj.optString("smbShareName", "MaiNF")
            )
        } catch (_: Exception) {
            HostServerConfig(sharedFolderPath = defaultDir)
        }
    }

    private fun saveConfig(cfg: HostServerConfig) {
        try {
            val obj = JSONObject().apply {
                put("ftpPort", cfg.ftpPort)
                put("smbPort", cfg.smbPort)
                put("username", cfg.username)
                put("password", cfg.password)
                put("anonymousAllowed", cfg.anonymousAllowed)
                put("readOnly", cfg.readOnly)
                put("sharedFolderPath", cfg.sharedFolderPath)
                put("smbShareName", cfg.smbShareName)
            }
            prefs.edit().putString("config_json", obj.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun getSharedDirectoryDefault(): File {
        val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val maiDir = File(pubDir, "MaiNF")
        if (!maiDir.exists()) maiDir.mkdirs()
        return if (maiDir.canWrite()) maiDir else (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
    }
}
