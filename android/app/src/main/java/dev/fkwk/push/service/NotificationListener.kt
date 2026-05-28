package dev.fkwk.push.service

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.fkwk.push.data.SettingsRepository
import dev.fkwk.push.net.Forwarder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * 核心通知监听。读取系统通知，过滤监控包名，交给 Forwarder。
 * 监听/发布骨架参考 ShiftHackZ/NTFY-Interceptor-Android (MIT)。
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject lateinit var forwarder: Forwarder
    @Inject lateinit var settingsRepo: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 去重：key + 内容 hash，短时间窗内重复（如微信通知 update）只处理一次
    private val recent = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 200
    }
    private val dedupWindowMs = 3_000L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // 包名白名单过滤（默认微信 + 企微）
        val monitored = runBlocking { settingsRepo.current().monitoredPackages }
        if (pkg !in monitored) return

        val notification = sbn.notification ?: return

        // 跳过“正在运行”类常驻/进度通知
        val flags = notification.flags
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return  // 折叠摘要，避免重复

        val captured = extractNotification(notification) ?: return
        val title = captured.title
        val text = captured.text

        // 微信常见脱敏：标题“微信”、正文“你有一条新消息”等，仍然转发（用户底线=拿到通知内容）
        val dedupKey = "$pkg|${sbn.key}|${title.hashCode()}|${text.hashCode()}"
        val now = System.currentTimeMillis()
        val last = recent[dedupKey]
        if (last != null && now - last < dedupWindowMs) return
        recent[dedupKey] = now

        val postTime = if (sbn.postTime > 0) sbn.postTime else now
        scope.launch {
            runCatching { forwarder.handle(pkg, title, text, postTime) }
                .onFailure { Timber.e(it, "处理通知失败") }
        }
    }

    private fun extractNotification(notification: Notification): CapturedNotification? {
        val extras = notification.extras ?: return null
        val title = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        val bodyParts = buildList {
            addUseful(this, extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            addUseful(this, extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            extractMessagingLines(extras).forEach { addUseful(this, it) }
        }.distinct()

        val text = bodyParts.joinToString("\n")
        if (title.isBlank() && text.isBlank()) return null
        return CapturedNotification(title = title, text = text)
    }

    @Suppress("DEPRECATION")
    private fun extractMessagingLines(extras: Bundle): List<String> {
        val bundles: Array<Parcelable> =
            extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return Notification.MessagingStyle.Message
            .getMessagesFromBundleArray(bundles)
            .takeLast(5)
            .mapNotNull { message ->
                val text = message.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) return@mapNotNull null
                val sender = firstNonBlank(
                    message.senderPerson?.name?.toString(),
                    message.sender?.toString()
                )
                if (sender.isBlank()) text else "$sender: $text"
            }
    }

    private fun addUseful(target: MutableList<String>, value: String?) {
        val normalized = value?.trim().orEmpty()
        if (normalized.isNotBlank()) target.add(normalized)
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

    override fun onListenerConnected() {
        Timber.i("NotificationListener 已连接")
        // 监听连接成功 = 拉起前台保活服务
        ForegroundKeepAliveService.start(this)
    }

    override fun onListenerDisconnected() {
        Timber.w("NotificationListener 断开，尝试请求重连")
        NotificationListenerService.requestRebind(componentName(this))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun componentName(ctx: android.content.Context) =
            android.content.ComponentName(ctx, NotificationListener::class.java)
    }

    private data class CapturedNotification(
        val title: String,
        val text: String
    )
}
