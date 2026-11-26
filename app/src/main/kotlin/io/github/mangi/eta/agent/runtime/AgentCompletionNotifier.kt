package io.github.mangi.eta.agent.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.ui.MainActivity

/**
 * 任务结束提醒：一次 Agent run 结束后发一条可点击的通知。
 * 点击直达对应会话；默认只在应用不可见时提醒，避免正看着界面时打扰。
 */
internal object AgentCompletionNotifier {
    private const val CHANNEL_LOUD = "eta_completion_v2"
    private const val CHANNEL_SILENT = "eta_completion_silent_v2"
    private const val BASE_ID = 20000

    fun notifyFinished(
        context: Context,
        conversationId: String?,
        ok: Boolean,
        preview: String?,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val allowed = if (ok) {
            Prefs.isEnabled(Prefs.Keys.AGENT_NOTIFY_ON_COMPLETE)
        } else {
            Prefs.isEnabled(Prefs.Keys.AGENT_NOTIFY_ON_FAILURE)
        }
        if (!allowed) return
        if (Prefs.isEnabled(Prefs.Keys.AGENT_NOTIFY_ONLY_BACKGROUND) && EtaApp.isAppForeground()) return
        val silent = Prefs.isEnabled(Prefs.Keys.AGENT_NOTIFY_SILENT)
        runCatching {
            ensureChannel(manager, silent)
            val channelId = if (silent) CHANNEL_SILENT else CHANNEL_LOUD
            val intent = Intent(context, MainActivity::class.java).apply {
                if (conversationId != null) {
                    action = MainActivity.ACTION_OPEN_CONVERSATION_ID
                    putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                conversationId?.hashCode() ?: 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = preview?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: "点开查看结果"
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (ok) "任务已完成" else "任务失败")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify(BASE_ID + (conversationId?.hashCode() ?: 0), notification)
        }
    }

    private fun ensureChannel(manager: NotificationManager, silent: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val id = if (silent) CHANNEL_SILENT else CHANNEL_LOUD
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                id,
                if (silent) "任务完成提醒（静默）" else "任务完成提醒",
                if (silent) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
}
