package com.mai.nf.model

import java.util.UUID

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol = Protocol.FTP,
    val host: String,
    val port: Int = protocol.defaultPort,
    val username: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val shareName: String = "", // For SMB
    val domain: String = "",    // For SMB domain or workgroup
    val passiveMode: Boolean = true, // For FTP
    val initialPath: String = "/",
    val lastConnected: Long = 0L
)
