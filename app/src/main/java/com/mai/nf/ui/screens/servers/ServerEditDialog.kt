package com.mai.nf.ui.screens.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mai.nf.R
import com.mai.nf.model.Protocol
import com.mai.nf.model.ServerProfile

@Composable
fun ServerEditDialog(
    initialProfile: ServerProfile?,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var protocol by remember { mutableStateOf(initialProfile?.protocol ?: Protocol.FTP) }
    var host by remember { mutableStateOf(initialProfile?.host ?: "") }
    var port by remember { mutableStateOf(initialProfile?.port?.toString() ?: protocol.defaultPort.toString()) }
    var username by remember { mutableStateOf(initialProfile?.username ?: "") }
    var password by remember { mutableStateOf(initialProfile?.password ?: "") }
    var isAnonymous by remember { mutableStateOf(initialProfile?.isAnonymous ?: false) }
    var shareName by remember { mutableStateOf(initialProfile?.shareName ?: "") }
    var domain by remember { mutableStateOf(initialProfile?.domain ?: "") }
    var passiveMode by remember { mutableStateOf(initialProfile?.passiveMode ?: true) }
    var initialPath by remember { mutableStateOf(initialProfile?.initialPath ?: "/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProfile == null) stringResource(R.string.add_server) else stringResource(R.string.edit_server))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = protocol.ordinal) {
                    Protocol.values().forEach { p ->
                        Tab(
                            selected = protocol == p,
                            onClick = {
                                protocol = p
                                port = p.defaultPort.toString()
                            },
                            text = { Text(p.displayName.substringBefore(" ")) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_name)) },
                    placeholder = { Text(stringResource(R.string.server_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.host_ip)) },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(stringResource(R.string.port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.anonymous_access), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it }
                    )
                }

                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (protocol == Protocol.SMB) {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text(stringResource(R.string.share_name)) },
                        placeholder = { Text(stringResource(R.string.share_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(stringResource(R.string.domain_workgroup)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (protocol == Protocol.FTP || protocol == Protocol.FTPS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.passive_mode), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = passiveMode,
                            onCheckedChange = { passiveMode = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = initialPath,
                    onValueChange = { initialPath = it },
                    label = { Text(stringResource(R.string.initial_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank { host.ifBlank { "Server" } }
                    val finalPort = port.toIntOrNull() ?: protocol.defaultPort
                    val profile = (initialProfile ?: ServerProfile(
                        name = finalName,
                        protocol = protocol,
                        host = host
                    )).copy(
                        name = finalName,
                        protocol = protocol,
                        host = host.trim(),
                        port = finalPort,
                        username = username.trim(),
                        password = password,
                        isAnonymous = isAnonymous,
                        shareName = shareName.trim(),
                        domain = domain.trim(),
                        passiveMode = passiveMode,
                        initialPath = if (initialPath.isBlank()) "/" else initialPath.trim()
                    )
                    onSave(profile)
                },
                enabled = host.isNotBlank() && (protocol != Protocol.SMB || shareName.isNotBlank())
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
