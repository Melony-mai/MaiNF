package com.mai.nf.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mai.nf.R
import com.mai.nf.model.ServerProfile
import com.mai.nf.model.TransferStatus
import com.mai.nf.ui.components.LanguageDialog
import com.mai.nf.ui.screens.browser.BrowserScreen
import com.mai.nf.ui.screens.host.HostScreen
import com.mai.nf.ui.screens.local.LocalStorageScreen
import com.mai.nf.ui.screens.servers.ServerEditDialog
import com.mai.nf.ui.screens.servers.ServersScreen
import com.mai.nf.ui.screens.transfers.TransfersScreen
import com.mai.nf.ui.viewmodel.ConnectionStatus
import com.mai.nf.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPickUploadFile: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val remoteFiles by viewModel.remoteFiles.collectAsState()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsState()
    val browserError by viewModel.browserError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val localFiles by viewModel.localFiles.collectAsState()

    // Host Server State
    val hostConfig by viewModel.hostConfig.collectAsState()
    val deviceIp by viewModel.deviceIp.collectAsState()
    val ftpRunning by viewModel.ftpRunning.collectAsState()
    val smbRunning by viewModel.smbRunning.collectAsState()
    val ftpClients by viewModel.ftpClients.collectAsState()
    val smbClients by viewModel.smbClients.collectAsState()
    val hostLogs by viewModel.hostLogs.collectAsState()

    var editingServer by remember { mutableStateOf<ServerProfile?>(null) }
    var isAddingServer by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val activeCount = transfers.count {
        it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.QUEUED
    }

    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }

    if (isAddingServer || editingServer != null) {
        ServerEditDialog(
            initialProfile = editingServer,
            onDismiss = {
                isAddingServer = false
                editingServer = null
            },
            onSave = { profile ->
                viewModel.saveServer(profile)
                isAddingServer = false
                editingServer = null
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "MaiNF Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.version_text),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.about_features),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (currentServer != null && connectionStatus is ConnectionStatus.Connected) {
                            Text(
                                text = "${stringResource(R.string.connected)}: ${currentServer?.name} (${currentServer?.protocol?.name})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (ftpRunning || smbRunning) {
                            Text(
                                text = stringResource(R.string.server_running),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (currentServer != null && connectionStatus is ConnectionStatus.Connected) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = stringResource(R.string.disconnect))
                        }
                    }
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = stringResource(R.string.settings_language))
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.tab_servers)) },
                    label = { Text(stringResource(R.string.tab_servers)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.FolderShared, contentDescription = stringResource(R.string.tab_remote)) },
                    label = { Text(stringResource(R.string.tab_remote)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        BadgedBox(badge = {
                            if (activeCount > 0) {
                                Badge { Text("$activeCount") }
                            }
                        }) {
                            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.tab_transfers))
                        }
                    },
                    label = { Text(stringResource(R.string.tab_transfers)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.tab_local)) },
                    label = { Text(stringResource(R.string.tab_local)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    icon = {
                        BadgedBox(badge = {
                            if (ftpRunning || smbRunning) {
                                Badge { Text("●") }
                            }
                        }) {
                            Icon(Icons.Default.WifiTethering, contentDescription = stringResource(R.string.tab_host))
                        }
                    },
                    label = { Text(stringResource(R.string.tab_host)) }
                )
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = {
                            editingServer = null
                            isAddingServer = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
                    }
                }
                1 -> {
                    if (currentServer != null && connectionStatus is ConnectionStatus.Connected) {
                        FloatingActionButton(onClick = onPickUploadFile) {
                            Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.upload))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ServersScreen(
                    servers = servers,
                    currentServer = currentServer,
                    connectionStatus = connectionStatus,
                    onConnect = { viewModel.connectToServer(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onEdit = { editingServer = it },
                    onDelete = { viewModel.deleteServer(it.id) }
                )
                1 -> BrowserScreen(
                    currentServer = currentServer,
                    currentPath = currentPath,
                    files = remoteFiles,
                    isLoading = isLoadingFiles,
                    errorMessage = browserError,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onNavigateToDir = { viewModel.navigateToDirectory(it) },
                    onNavigateUp = { viewModel.navigateUp() },
                    onRefresh = { viewModel.refreshFiles() },
                    onCreateFolder = { viewModel.createFolder(it) },
                    onRenameItem = { item, newName -> viewModel.renameRemoteItem(item, newName) },
                    onDeleteItem = { viewModel.deleteRemoteItem(it) },
                    onDownloadItem = { viewModel.downloadFile(it) },
                    onUploadClick = onPickUploadFile,
                    onGoToServers = { viewModel.selectTab(0) }
                )
                2 -> TransfersScreen(
                    transfers = transfers,
                    onCancelTransfer = { viewModel.cancelTransfer(it) },
                    onRetryTransfer = { viewModel.retryTransfer(it) },
                    onClearCompleted = { viewModel.clearCompletedTransfers() }
                )
                3 -> LocalStorageScreen(
                    localFiles = localFiles,
                    currentServer = currentServer,
                    onRefresh = { viewModel.refreshLocalFiles() },
                    onDeleteFile = { viewModel.deleteLocalFile(it) },
                    onUploadFile = { uri, name, size ->
                        viewModel.uploadFile(uri, name, size)
                    }
                )
                4 -> HostScreen(
                    config = hostConfig,
                    deviceIp = deviceIp,
                    ftpRunning = ftpRunning,
                    smbRunning = smbRunning,
                    ftpClients = ftpClients,
                    smbClients = smbClients,
                    logs = hostLogs,
                    onUpdateConfig = { viewModel.updateHostConfig(it) },
                    onRefreshIp = { viewModel.refreshDeviceIp() },
                    onStartFtp = { viewModel.startFtp() },
                    onStopFtp = { viewModel.stopFtp() },
                    onStartSmb = { viewModel.startSmb() },
                    onStopSmb = { viewModel.stopSmb() },
                    onClearLogs = { viewModel.clearHostLogs() }
                )
            }
        }
    }
}
