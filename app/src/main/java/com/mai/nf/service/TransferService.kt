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
import com.mai.nf.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        const val CHANNEL_ID = "mainf_transfers_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Transfer engine ready", "", 0, false))
        observeTransfers()
    }

    private fun observeTransfers() {
        val transferManager = (application as MaiNFApplication).transferManager
        scope.launch {
            transferManager.queueRepository.transfers.collectLatest { list ->
                val active = list.find { it.status == TransferStatus.IN_PROGRESS }
                    ?: list.find { it.status == TransferStatus.QUEUED }

                if (active != null) {
                    val title = if (active.isUpload) "Uploading: ${active.fileName}" else "Downloading: ${active.fileName}"
                    val sub = "${active.formattedTransferred} • ${active.formattedSpeed}"
                    val notif = buildNotification(title, sub, active.progressPercent, true)
                    notificationManager.notify(NOTIFICATION_ID, notif)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int, indeterminate: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress > 0) {
            builder.setProgress(100, progress, false)
        } else if (indeterminate) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live file transfer progress for MaiNF"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
