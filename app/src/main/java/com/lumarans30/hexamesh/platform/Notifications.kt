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
import com.lumarans30.hexamesh.node.NodeState

class Notifications(context: Context) {

    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

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

        manager.createNotificationChannel(channel)
    }

    fun build(state: NodeState, modelLabel: String?): Notification {
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

        val builder =
            Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hexagon)
                .setContentTitle(app.getString(R.string.app_name))
                .setContentText(contentText(state, modelLabel))
                .setContentIntent(openIntent)
                .addAction(
                    Notification.Action.Builder(
                        null as android.graphics.drawable.Icon?,
                        app.getString(R.string.stop_node),
                        stopIntent,
                    ).build()
                )
                .setOngoing(true)

        if (state is NodeState.Running) builder.setSubText(state.serverUrl)
        if (state is NodeState.Starting || state is NodeState.Stopping) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun update(state: NodeState, modelLabel: String?) {
        manager.notify(NOTIFICATION_ID, build(state, modelLabel))
    }

    private fun contentText(state: NodeState, modelLabel: String?): String =
        when (state) {
            is NodeState.Starting ->
                app.getString(
                    R.string.notif_starting,
                    modelLabel ?: app.getString(R.string.notif_server),
                )
            is NodeState.Running ->
                app.getString(
                    R.string.notif_running,
                    modelLabel ?: app.getString(R.string.notif_all_models),
                )
            is NodeState.Stopping ->
                app.getString(
                    R.string.notif_stopping,
                    modelLabel ?: app.getString(R.string.notif_server),
                )
            is NodeState.Error -> app.getString(R.string.notif_error, state.message)
            is NodeState.Stopped -> app.getString(R.string.notif_stopped)
        }

    companion object {
        const val CHANNEL_ID = "hexamesh_node"
        const val NOTIFICATION_ID = 1
    }
}
