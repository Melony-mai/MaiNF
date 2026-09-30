package com.mai.nf.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mai.nf.MainActivity
import com.mai.nf.MaiNFApplication
import com.mai.nf.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class HostServerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        const val CHANNEL_ID = "mainf_host_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "com.mai.nf.action.STOP_HOST_SERVERS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("MaiNF Server Starting", "Preparing services..."))
        observeServers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val app = application as MaiNFApplication
            app.localServerManager.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun observeServers() {
        val app = application as MaiNFApplication
        val serverManager = app.localServerManager

        scope.launch {
            combineLatest(serverManager.ftpRunning, serverManager.smbRunning, serverManager.deviceIp) { ftp, smb, ip ->
                Triple(ftp, smb, ip)
            }.collectLatest { (ftp, smb, ip) ->
                if (!ftp && !smb) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val activeList = mutableListOf<String>()
                    val cfg = serverManager.config.value
                    if (ftp) activeList.add("FTP (:${cfg.ftpPort})")
                    if (smb) activeList.add("SMB (:${cfg.smbPort})")

                    val title = "MaiNF Server Active: ${activeList.joinToString(" & ")}"
                    val content = if (ip.isNotBlank()) "Available at $ip" else "Running on local network"
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
                }
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HostServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MaiNF Host Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of incoming FTP & SMB servers"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun <T1, T2, T3, R> combineLatest(
        f1: kotlinx.coroutines.flow.Flow<T1>,
        f2: kotlinx.coroutines.flow.Flow<T2>,
        f3: kotlinx.coroutines.flow.Flow<T3>,
        transform: suspend (T1, T2, T3) -> R
    ): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(f1, f2, f3, transform)
}
