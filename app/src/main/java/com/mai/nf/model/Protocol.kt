package com.mai.nf.model

enum class Protocol(val displayName: String, val defaultPort: Int) {
    FTP("FTP", 21),
    FTPS("FTPS (Explicit)", 21),
    SMB("SMB (Windows/Samba)", 445)
}
