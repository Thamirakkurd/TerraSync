package com.terrasync.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class OfflineDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "terrasync_offline_downloads"
        private const val NOTIF_ID = 39201
        private const val ACTION_START_OR_UPDATE = "com.terrasync.app.action.OFFLINE_START_OR_UPDATE"
        private const val ACTION_PAUSE_ALL = "com.terrasync.app.action.OFFLINE_PAUSE_ALL"

        fun startOrUpdate(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, OfflineDownloadService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            try {
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, OfflineDownloadService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private lateinit var offlineMaps: OfflineMapManager
    @Volatile private var lastNotifyMs = 0L

    override fun onCreate() {
        super.onCreate()
        offlineMaps = OfflineMapManager.getInstance(applicationContext)
        ensureChannel()
        offlineMaps.setServiceListener {
            handleManagerUpdate()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        if (intent?.action == ACTION_PAUSE_ALL) {
            offlineMaps.pauseAllDownloads()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = buildNotification(forceProgress = true)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notif, serviceType)
        lastNotifyMs = SystemClock.elapsedRealtime()

        if (!offlineMaps.hasAnyActiveDownload()) {
            stopForegroundCompat()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        offlineMaps.pauseAllDownloads()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        if (::offlineMaps.isInitialized) {
            offlineMaps.setServiceListener(null)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleManagerUpdate() {
        if (!offlineMaps.hasAnyActiveDownload()) {
            stopForegroundCompat()
            stopSelf()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyMs < 450L) return
        lastNotifyMs = now
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIF_ID, buildNotification(forceProgress = false))
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Offline Maps & Roads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress for background offline map and road downloads"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(forceProgress: Boolean): Notification {
        val summary = offlineMaps.getActiveSummary()
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, OfflineDownloadService::class.java).apply {
                action = ACTION_PAUSE_ALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (summary != null) {
            "${summary.packTitle} • ${summary.stageLabel}"
        } else if (forceProgress) {
            "Preparing offline download..."
        } else {
            "Offline download active"
        }
        val text = summary?.detailText ?: "Downloading offline data in background"
        val pct = summary?.percent?.coerceIn(0, 100) ?: 0
        val indeterminate = summary == null || summary.indeterminate

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_layers)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .setProgress(100, pct, indeterminate)
            .addAction(0, "Pause", pauseIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
