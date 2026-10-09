package com.shoaib.tgforward.service

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shoaib.tgforward.MainActivity
import com.shoaib.tgforward.R

class ForwardService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Source channels sun rahe hain"
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(this, ForwardService::class.java)
        try {
            ContextCompat.startForegroundService(this, restart)
        } catch (_: Exception) {
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Auto post on hai")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto post",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Source se target par copy chalta rahe"
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "forwarding"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String = "Source channels sun rahe hain") {
            val intent = Intent(context, ForwardService::class.java).putExtra(EXTRA_TEXT, text)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun update(context: Context, text: String) = start(context, text)

        fun stop(context: Context) {
            context.stopService(Intent(context, ForwardService::class.java))
        }
    }
}
