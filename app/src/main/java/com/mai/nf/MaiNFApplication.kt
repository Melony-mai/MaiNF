package com.mai.nf

import android.app.Application
import com.mai.nf.data.ServerRepository
import com.mai.nf.data.TransferQueueRepository
import com.mai.nf.service.TransferManager

class MaiNFApplication : Application() {

    lateinit var serverRepository: ServerRepository
        private set

    lateinit var transferQueueRepository: TransferQueueRepository
        private set

    lateinit var transferManager: TransferManager
        private set

    lateinit var localServerManager: com.mai.nf.server.LocalServerManager
        private set

    override fun onCreate() {
        super.onCreate()
        serverRepository = ServerRepository(this)
        transferQueueRepository = TransferQueueRepository()
        transferManager = TransferManager(this, transferQueueRepository, serverRepository)
        localServerManager = com.mai.nf.server.LocalServerManager(this)
    }
}
