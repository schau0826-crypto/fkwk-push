package dev.fkwk.push.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * 空壳无障碍服务：二级保活锚点。
 * 不读取、不解析任何窗口内容（accessibility_service_config 已声明 canRetrieveWindowContent=false）。
 * 唯一作用：让 Android 系统把本进程当作“辅助功能必需”，降低被杀概率。
 */
class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("保活无障碍服务已连接")
        ForegroundKeepAliveService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 故意留空：不处理任何事件，不读取内容
    }

    override fun onInterrupt() { /* no-op */ }
}
