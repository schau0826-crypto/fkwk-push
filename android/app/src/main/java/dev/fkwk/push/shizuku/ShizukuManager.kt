package dev.fkwk.push.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Shizuku 封装：用于“一键授予”那些麻烦的权限（通知监听、忽略电池优化等），
 * 提升初次配置体验。Shizuku 拿到的是 shell(uid=2000) 权限，能执行 `cmd` 系命令。
 *
 * 注意：Shizuku 解决“授权繁琐”，但解决不了各厂商 Android 的自启动/后台限制，
 * 那些仍需用户在系统设置里手动开（见引导页）。
 */
object ShizukuManager {

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        isAvailable() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        if (!isAvailable()) {
            Timber.w("Shizuku 未运行")
            return
        }
        if (!hasPermission()) Shizuku.requestPermission(requestCode)
    }

    /**
     * 通过 Shizuku 执行 shell 命令。例如授予通知监听：
     *   cmd notification allow_listener dev.fkwk.push/dev.fkwk.push.service.NotificationListener
     * 实际执行需经由 Shizuku 的 UserService / newProcess，此处留出接入点。
     */
    fun runCommand(vararg cmd: String): String {
        // 后续可在这里接入 Shizuku UserService 执行 shell 命令；当前先保留为显式接入点。
        Timber.d("Shizuku 执行: %s", cmd.joinToString(" "))
        return ""
    }
}
