package com.mai.nf.model

import com.mai.nf.server.model.HostServerConfig
import org.junit.Assert.*
import org.junit.Test

class ModelAndProtocolTest {

    @Test
    fun testProtocolDefaults() {
        assertEquals(21, Protocol.FTP.defaultPort)
        assertEquals(21, Protocol.FTPS.defaultPort)
        assertEquals(445, Protocol.SMB.defaultPort)
        assertEquals("FTP", Protocol.FTP.displayName)
        assertEquals("FTPS (Explicit)", Protocol.FTPS.displayName)
        assertEquals("SMB (Windows/Samba)", Protocol.SMB.displayName)
    }

    @Test
    fun testServerProfileCreation() {
        val profile = ServerProfile(
            id = "test-1",
            name = "Home NAS",
            protocol = Protocol.SMB,
            host = "192.168.1.100",
            port = 445,
            username = "admin",
            password = "secret",
            shareName = "Media",
            initialPath = "/Movies"
        )
        assertEquals("Home NAS", profile.name)
        assertEquals(Protocol.SMB, profile.protocol)
        assertEquals("192.168.1.100", profile.host)
        assertEquals(445, profile.port)
        assertEquals("Media", profile.shareName)
        assertEquals("/Movies", profile.initialPath)
    }

    @Test
    fun testTransferItemStatusTransitions() {
        val item = TransferItem(
            id = "item-1",
            serverProfileId = "test-1",
            serverName = "Home NAS",
            protocol = Protocol.SMB,
            fileName = "movie.mkv",
            remotePath = "/Movies/movie.mkv",
            localUri = "file:///storage/emulated/0/Download/MaiNF/movie.mkv",
            localFilePath = "/storage/emulated/0/Download/MaiNF/movie.mkv",
            isUpload = false,
            totalBytes = 1048576L,
            transferredBytes = 524288L,
            status = TransferStatus.IN_PROGRESS,
            speedBytesPerSec = 262144L
        )

        assertEquals(0.5f, item.progress, 0.001f)
        assertEquals(TransferStatus.IN_PROGRESS, item.status)
        assertEquals(262144L, item.speedBytesPerSec)

        val completed = item.copy(
            transferredBytes = 1048576L,
            status = TransferStatus.COMPLETED
        )
        assertEquals(1.0f, completed.progress, 0.001f)
        assertEquals(TransferStatus.COMPLETED, completed.status)
    }

    @Test
    fun testHostServerConfigDefaults() {
        val config = HostServerConfig()
        assertEquals(2121, config.ftpPort)
        assertEquals(4450, config.smbPort)
        assertEquals("MaiNF", config.smbShareName)
        assertTrue(config.anonymousAllowed)
        assertFalse(config.readOnly)
    }
}
