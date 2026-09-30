package com.mai.nf.client

import com.mai.nf.model.Protocol
import com.mai.nf.model.ServerProfile

object ClientFactory {
    fun createClient(profile: ServerProfile): RemoteFileClient {
        return when (profile.protocol) {
            Protocol.FTP, Protocol.FTPS -> FtpClientImpl(profile)
            Protocol.SMB -> SmbClientImpl(profile)
        }
    }
}
