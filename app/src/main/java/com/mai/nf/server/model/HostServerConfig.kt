package com.mai.nf.server.model

data class HostServerConfig(
    val ftpPort: Int = 2121,
    val smbPort: Int = 4450,
    val username: String = "admin",
    val password: String = "123456",
    val anonymousAllowed: Boolean = true,
    val readOnly: Boolean = false,
    val sharedFolderPath: String = "",
    val smbShareName: String = "MaiNF"
)
