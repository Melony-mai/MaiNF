package com.mai.nf.ui.screens.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mai.nf.R
import com.mai.nf.server.model.HostServerConfig

@Composable
fun HostScreen(
    config: HostServerConfig,
    deviceIp: String,
    ftpRunning: Boolean,
    smbRunning: Boolean,
    ftpClients: Int,
    smbClients: Int,
    logs: List<String>,
    onUpdateConfig: (HostServerConfig) -> Unit,
    onRefreshIp: () -> Unit,
    onStartFtp: () -> Unit,
    onStopFtp: () -> Unit,
    onStartSmb: () -> Unit,
    onStopSmb: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var ftpPortInput by remember(config.ftpPort) { mutableStateOf(config.ftpPort.toString()) }
    var smbPortInput by remember(config.smbPort) { mutableStateOf(config.smbPort.toString()) }
    var usernameInput by remember(config.username) { mutableStateOf(config.username) }
    var passwordInput by remember(config.password) { mutableStateOf(config.password) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device IP Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (deviceIp.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.device_ip_address),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (deviceIp.isNotBlank()) deviceIp else stringResource(R.string.device_ip_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (deviceIp.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                if (deviceIp.isNotBlank()) {
                    IconButton(onClick = { copyToClipboard("IP", deviceIp) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP")
                    }
                }

                IconButton(onClick = onRefreshIp) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh IP")
                }
            }
        }

        // FTP Server Card
        val ftpUri = if (deviceIp.isNotBlank()) "ftp://$deviceIp:${config.ftpPort}/" else ""
        ServerControlCard(
            title = stringResource(R.string.ftp_server_card),
            helpText = stringResource(R.string.ftp_server_help),
            isRunning = ftpRunning,
            url = ftpUri,
            port = ftpPortInput,
            onPortChange = {
                ftpPortInput = it
                it.toIntOrNull()?.let { p -> onUpdateConfig(config.copy(ftpPort = p)) }
            },
            onToggle = { if (ftpRunning) onStopFtp() else onStartFtp() },
            onCopy = { copyToClipboard("FTP URL", ftpUri) },
            icon = Icons.Default.Dns
        )

        // SMB Server Card
        val smbWindowsPath = if (deviceIp.isNotBlank()) "\\\\$deviceIp:${config.smbPort}\\${config.smbShareName}" else ""
        val smbUri = if (deviceIp.isNotBlank()) "smb://$deviceIp:${config.smbPort}/${config.smbShareName}" else ""
        ServerControlCard(
            title = stringResource(R.string.smb_server_card),
            helpText = stringResource(R.string.smb_server_help),
            isRunning = smbRunning,
            url = smbWindowsPath,
            secondaryUrl = smbUri,
            port = smbPortInput,
            onPortChange = {
                smbPortInput = it
                it.toIntOrNull()?.let { p -> onUpdateConfig(config.copy(smbPort = p)) }
            },
            onToggle = { if (smbRunning) onStopSmb() else onStartSmb() },
            onCopy = { copyToClipboard("SMB Path", smbWindowsPath) },
            icon = Icons.Default.FolderShared
        )

        // Security & Shared Directory Configuration
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.server_shared_folder),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = config.sharedFolderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (config.readOnly) stringResource(R.string.server_read_only) else stringResource(R.string.server_read_write),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = config.readOnly,
                        onCheckedChange = { onUpdateConfig(config.copy(readOnly = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.server_anonymous),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = config.anonymousAllowed,
                        onCheckedChange = { onUpdateConfig(config.copy(anonymousAllowed = it)) }
                    )
                }

                if (!config.anonymousAllowed) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = {
                            usernameInput = it
                            onUpdateConfig(config.copy(username = it))
                        },
                        label = { Text(stringResource(R.string.server_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            onUpdateConfig(config.copy(password = it))
                        },
                        label = { Text(stringResource(R.string.server_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Server Logs Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.server_activity_logs),
                            style = MaterialTheme.typography.titleSmall
                        )
                        val totalClients = ftpClients + smbClients
                        if (totalClients > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge { Text("$totalClients") }
                        }
                    }

                    TextButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                        Text(stringResource(R.string.clear_logs))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_activity_yet),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(logs.size) {
                            listState.animateScrollToItem((logs.size - 1).coerceAtLeast(0))
                        }
                        LazyColumn(state = listState) {
                            items(logs) { entry ->
                                Text(
                                    text = entry,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerControlCard(
    title: String,
    helpText: String,
    isRunning: Boolean,
    url: String,
    secondaryUrl: String = "",
    port: String,
    onPortChange: (String) -> Unit,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isRunning) stringResource(R.string.server_running) else stringResource(R.string.server_stopped),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                    )
                }

                Button(
                    onClick = onToggle,
                    colors = if (isRunning) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isRunning) stringResource(R.string.stop) else stringResource(R.string.start))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isRunning && url.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = url,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (secondaryUrl.isNotBlank()) {
                                Text(
                                    text = secondaryUrl,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                        }
                    }
                }
            } else if (!isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text(stringResource(R.string.server_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
