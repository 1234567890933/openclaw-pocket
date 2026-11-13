package com.openclaude.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.openclaude.mobile.R
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.util.Logs

/**
 * Foreground service that keeps the agent process alive while the screen is off.
 *
 * Android will freeze a background process within seconds, which would look like
 * the agent "hanging" mid-turn. A dataSync foreground service with an ongoing
 * notification is the honest way to say "work is happening".
 */
class RuntimeService : Service() {

    companion object {
        const val CHANNEL_ID = "ocm.runtime"
        const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.openclaude.mobile.STOP_AGENT"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceLocator.agentRepository.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Agent running", "Working on your task")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Logs.i("service", "runtime service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Logs.i("service", "runtime service destroyed")
        super.onDestroy()
    }

    private fun buildNotification(title: String, body: String): Notification {
        val stopIntent = Intent(this, RuntimeService::class.java).setAction(ACTION_STOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = android.app.PendingIntent.getService(this, 1, stopIntent, flags)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_app_mark)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", pending).build())
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent runtime",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the agent is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
