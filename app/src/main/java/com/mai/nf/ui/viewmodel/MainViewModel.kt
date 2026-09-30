package com.mai.nf.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mai.nf.MaiNFApplication
import com.mai.nf.client.ClientFactory
import com.mai.nf.client.RemoteFileClient
import com.mai.nf.model.RemoteFile
import com.mai.nf.model.ServerProfile
import com.mai.nf.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ConnectionStatus {
    object Disconnected : ConnectionStatus
    object Connecting : ConnectionStatus
    object Connected : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MaiNFApplication
    private val serverRepository = app.serverRepository
    private val transferManager = app.transferManager
    val localServerManager = app.localServerManager

    val servers: StateFlow<List<ServerProfile>> = serverRepository.servers
    val transfers: StateFlow<List<TransferItem>> = transferManager.queueRepository.transfers

    val hostConfig = localServerManager.config
    val deviceIp = localServerManager.deviceIp
    val ftpRunning = localServerManager.ftpRunning
    val smbRunning = localServerManager.smbRunning
    val ftpClients = localServerManager.ftpClients
    val smbClients = localServerManager.smbClients
    val hostLogs = localServerManager.logs

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _currentServer = MutableStateFlow<ServerProfile?>(null)
    val currentServer: StateFlow<ServerProfile?> = _currentServer.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _remoteFiles = MutableStateFlow<List<RemoteFile>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFile>> = _remoteFiles.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _browserError = MutableStateFlow<String?>(null)
    val browserError: StateFlow<String?> = _browserError.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _localFiles = MutableStateFlow<List<File>>(emptyList())
    val localFiles: StateFlow<List<File>> = _localFiles.asStateFlow()

    private var activeClient: RemoteFileClient? = null

    init {
        refreshLocalFiles()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        if (index == 3) {
            refreshLocalFiles()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun connectToServer(profile: ServerProfile) {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting
            _browserError.value = null
            _isLoadingFiles.value = true

            // Switch to Browser tab to give instant visual feedback
            _selectedTab.value = 1
            _currentServer.value = profile

            val client = ClientFactory.createClient(profile)
            val result = client.connect()

            if (result.isSuccess) {
                activeClient?.close()
                activeClient = client
                _connectionStatus.value = ConnectionStatus.Connected
                serverRepository.updateLastConnected(profile.id)

                val initial = if (profile.initialPath.isNotBlank()) profile.initialPath else "/"
                _currentPath.value = initial
                loadRemoteFiles(initial)
            } else {
                client.close()
                val err = result.exceptionOrNull()?.message ?: "Failed to connect to ${profile.host}"
                _connectionStatus.value = ConnectionStatus.Error(err)
                _browserError.value = err
                _isLoadingFiles.value = false
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            activeClient?.close()
            activeClient = null
            _currentServer.value = null
            _connectionStatus.value = ConnectionStatus.Disconnected
            _remoteFiles.value = emptyList()
            _currentPath.value = "/"
            _browserError.value = null
        }
    }

    fun refreshFiles() {
        if (_currentServer.value == null) return
        viewModelScope.launch {
            loadRemoteFiles(_currentPath.value)
        }
    }

    fun navigateToDirectory(folderName: String) {
        val current = _currentPath.value
        val newPath = if (current.endsWith("/")) "$current$folderName" else "$current/$folderName"
        _currentPath.value = newPath
        loadRemoteFiles(newPath)
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value
        if (current == "/" || current.isBlank()) return false

        val trimmed = current.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val parent = if (lastSlash <= 0) "/" else trimmed.substring(0, lastSlash)

        _currentPath.value = parent
        loadRemoteFiles(parent)
        return true
    }

    private fun loadRemoteFiles(path: String) {
        val client = activeClient ?: return
        viewModelScope.launch {
            _isLoadingFiles.value = true
            _browserError.value = null

            val result = client.listFiles(path)
            if (result.isSuccess) {
                _remoteFiles.value = result.getOrNull() ?: emptyList()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to list directory contents"
                _browserError.value = err
            }
            _isLoadingFiles.value = false
        }
    }

    fun createFolder(folderName: String) {
        val client = activeClient ?: return
        val current = _currentPath.value
        val targetPath = if (current.endsWith("/")) "$current$folderName" else "$current/$folderName"

        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = client.createDirectory(targetPath)
            if (result.isSuccess) {
                loadRemoteFiles(current)
            } else {
                _browserError.value = result.exceptionOrNull()?.message ?: "Failed to create folder"
                _isLoadingFiles.value = false
            }
        }
    }

    fun deleteRemoteItem(item: RemoteFile) {
        val client = activeClient ?: return
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = if (item.isDirectory) {
                client.deleteDirectory(item.path)
            } else {
                client.deleteFile(item.path)
            }

            if (result.isSuccess) {
                loadRemoteFiles(_currentPath.value)
            } else {
                _browserError.value = result.exceptionOrNull()?.message ?: "Failed to delete ${item.name}"
                _isLoadingFiles.value = false
            }
        }
    }

    fun renameRemoteItem(item: RemoteFile, newName: String) {
        val client = activeClient ?: return
        val parent = item.path.substringBeforeLast('/', "")
        val targetPath = if (parent.isBlank() || parent == "/") "/$newName" else "$parent/$newName"

        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = client.rename(item.path, targetPath)
            if (result.isSuccess) {
                loadRemoteFiles(_currentPath.value)
            } else {
                _browserError.value = result.exceptionOrNull()?.message ?: "Failed to rename ${item.name}"
                _isLoadingFiles.value = false
            }
        }
    }

    fun downloadFile(remoteFile: RemoteFile) {
        val profile = _currentServer.value ?: return
        transferManager.downloadFile(profile, remoteFile)
    }

    fun uploadFile(uri: Uri, displayName: String, fileSize: Long) {
        val profile = _currentServer.value ?: return
        transferManager.uploadFile(
            profile = profile,
            localUri = uri,
            fileName = displayName,
            remoteDir = _currentPath.value,
            fileSize = fileSize
        )
    }

    fun cancelTransfer(id: String) {
        transferManager.cancelTransfer(id)
    }

    fun retryTransfer(id: String) {
        transferManager.retryTransfer(id)
    }

    fun clearCompletedTransfers() {
        transferManager.queueRepository.clearCompleted()
    }

    fun saveServer(profile: ServerProfile) {
        viewModelScope.launch {
            serverRepository.saveServer(profile)
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            if (_currentServer.value?.id == id) {
                disconnect()
            }
            serverRepository.deleteServer(id)
        }
    }

    fun refreshLocalFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val maiDir = File(pubDir, "MaiNF")
            val targetDir = if (maiDir.exists()) maiDir else (getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getApplication<Application>().filesDir)

            val files = targetDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            _localFiles.value = files
        }
    }

    fun deleteLocalFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
                refreshLocalFiles()
            }
        }
    }

    fun updateHostConfig(config: com.mai.nf.server.model.HostServerConfig) {
        localServerManager.updateConfig(config)
    }

    fun refreshDeviceIp() {
        localServerManager.refreshDeviceIp()
    }

    fun startFtp() {
        localServerManager.startFtp()
    }

    fun stopFtp() {
        localServerManager.stopFtp()
    }

    fun startSmb() {
        localServerManager.startSmb()
    }

    fun stopSmb() {
        localServerManager.stopSmb()
    }

    fun clearHostLogs() {
        localServerManager.clearLogs()
    }

    override fun onCleared() {
        super.onCleared()
        activeClient?.close()
    }
}
