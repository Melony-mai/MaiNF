package com.mai.nf.data

import android.content.Context
import com.mai.nf.model.Protocol
import com.mai.nf.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ServerRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "server_profiles.json")

    private val _servers = MutableStateFlow<List<ServerProfile>>(emptyList())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    init {
        loadServers()
    }

    private fun loadServers() {
        if (!file.exists()) {
            val defaults = listOf(
                ServerProfile(
                    name = "Example FTP Server",
                    protocol = Protocol.FTP,
                    host = "192.168.1.100",
                    port = 21,
                    username = "anonymous",
                    isAnonymous = true,
                    initialPath = "/"
                ),
                ServerProfile(
                    name = "Example SMB Share",
                    protocol = Protocol.SMB,
                    host = "192.168.1.100",
                    port = 445,
                    username = "guest",
                    shareName = "shared",
                    isAnonymous = true,
                    initialPath = "/"
                )
            )
            _servers.value = defaults
            saveServersSync(defaults)
            return
        }

        try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<ServerProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ServerProfile(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        protocol = Protocol.valueOf(obj.optString("protocol", Protocol.FTP.name)),
                        host = obj.optString("host"),
                        port = obj.optInt("port", 21),
                        username = obj.optString("username"),
                        password = obj.optString("password"),
                        isAnonymous = obj.optBoolean("isAnonymous", false),
                        shareName = obj.optString("shareName"),
                        domain = obj.optString("domain"),
                        passiveMode = obj.optBoolean("passiveMode", true),
                        initialPath = obj.optString("initialPath", "/"),
                        lastConnected = obj.optLong("lastConnected", 0L)
                    )
                )
            }
            _servers.value = list
        } catch (_: Exception) {
            _servers.value = emptyList()
        }
    }

    suspend fun saveServer(profile: ServerProfile) = withContext(Dispatchers.IO) {
        val current = _servers.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        _servers.value = current
        saveServersSync(current)
    }

    suspend fun deleteServer(id: String) = withContext(Dispatchers.IO) {
        val updated = _servers.value.filterNot { it.id == id }
        _servers.value = updated
        saveServersSync(updated)
    }

    suspend fun updateLastConnected(id: String) = withContext(Dispatchers.IO) {
        val current = _servers.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(lastConnected = System.currentTimeMillis())
            _servers.value = current
            saveServersSync(current)
        }
    }

    private fun saveServersSync(list: List<ServerProfile>) {
        try {
            val jsonArray = JSONArray()
            for (s in list) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("protocol", s.protocol.name)
                    put("host", s.host)
                    put("port", s.port)
                    put("username", s.username)
                    put("password", s.password)
                    put("isAnonymous", s.isAnonymous)
                    put("shareName", s.shareName)
                    put("domain", s.domain)
                    put("passiveMode", s.passiveMode)
                    put("initialPath", s.initialPath)
                    put("lastConnected", s.lastConnected)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (_: Exception) {}
    }
}
