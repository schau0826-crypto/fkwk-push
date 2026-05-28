package dev.fkwk.push.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/** 开机自启：拉起前台保活服务，并请求通知监听重新绑定。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.i("收到启动广播: %s", intent?.action)
        ForegroundKeepAliveService.start(context)
        runCatching {
            android.service.notification.NotificationListenerService
                .requestRebind(NotificationListener.componentName(context))
        }
    }
}
