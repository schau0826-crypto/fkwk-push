package dev.fkwk.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.fkwk.push.R
import timber.log.Timber

/**
 * 主保活锚点：常驻前台服务 + 持久通知。Android 对带前台通知的进程更宽容。
 */
class ForegroundKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(FG_ID, notification)
        // 被杀后尽量重建
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.fg_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        Timber.w("前台保活服务被销毁")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "fkwk_keepalive"
        private const val FG_ID = 1001

        fun start(ctx: Context) {
            val intent = Intent(ctx, ForegroundKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }
}
