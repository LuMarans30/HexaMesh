package com.lumarans30.hexamesh.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lumarans30.hexamesh.MainActivity
import com.lumarans30.hexamesh.MeshService
import com.lumarans30.hexamesh.R
import java.io.File

class Notifications(context: Context) {

    private val app = context.applicationContext

    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = app.getString(R.string.channel_description)
                setShowBadge(false)
            }

        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun build(modelPath: String?): Notification {
        val openIntent =
            PendingIntent.getActivity(
                app,
                0,
                Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            PendingIntent.getService(
                app,
                1,
                Intent(app, MeshService::class.java).setAction(MeshService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText =
            modelPath?.let { app.getString(R.string.notif_running, File(it).name) }
                ?: app.getString(R.string.notif_waiting_model)

        return Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hexagon)
            .setContentTitle(app.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    app.getString(R.string.stop_node),
                    stopIntent,
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "hexamesh_node"
        const val NOTIFICATION_ID = 1
    }
}