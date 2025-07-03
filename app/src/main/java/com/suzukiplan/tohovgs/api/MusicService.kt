package com.suzukiplan.tohovgs.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.suzukiplan.tohovgs.MainActivity
import com.suzukiplan.tohovgs.R

class MusicService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d("Starting foreground service")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = getString(R.string.app_name_long)
        val channelId = getString(R.string.notify_channel_id)
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivities(
            this,
            0,
            arrayOf(activityIntent),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId).apply {
            setContentTitle(name)
            setContentText(getString(R.string.service_description))
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentIntent(pendingIntent)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }.build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        Logger.d("Foreground service started")
        return START_REDELIVER_INTENT
    }

    override fun stopService(name: Intent?): Boolean {
        Logger.d("Foreground service stopped")
        return super.stopService(name)
    }
}