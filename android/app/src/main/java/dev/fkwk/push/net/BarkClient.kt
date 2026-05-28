package dev.fkwk.push.net

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fkwk.push.data.NtfySettings
import dev.fkwk.push.domain.EvaluatedNotification
import dev.fkwk.push.domain.Priority
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BarkPush(
    val device_key: String,
    val title: String,
    val body: String,
    val group: String,
    val level: String,
    val icon: String? = null,
    val isArchive: Int = 1
)

@Singleton
class BarkClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { encodeDefaults = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    fun publish(settings: NtfySettings, n: EvaluatedNotification): PublishResult {
        if (settings.barkServerUrl.isBlank()) {
            return PublishResult.Failure(null, "未配置 Bark 服务器地址")
        }
        if (settings.barkDeviceKey.isBlank()) {
            return PublishResult.Failure(null, "未配置 Bark Device Key")
        }
        if (settings.barkDeviceKey.matches(Regex("[0-9a-fA-F]{64}"))) {
            return PublishResult.Failure(null, "当前填的是 iOS Device Token；Bark 需要首页推送 URL 里的 Key")
        }

        val payload = BarkPush(
            device_key = settings.barkDeviceKey,
            title = n.barkTitle(settings),
            body = n.text.ifBlank { "（无正文）" },
            group = appLabel(n.packageName),
            level = n.priority.toBarkLevel(settings),
            icon = n.iconUrl(settings)
        )
        val req = Request.Builder()
            .url("${settings.barkServerUrl.trimEnd('/')}/push")
            .post(json.encodeToString(payload).toRequestBody(mediaJson))
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) PublishResult.Success(resp.code)
                else PublishResult.Failure(resp.code, resp.message.ifBlank { "HTTP ${resp.code}" })
            }
        } catch (e: Exception) {
            PublishResult.Failure(null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun Priority.toBarkLevel(settings: NtfySettings): String = when (this) {
        Priority.URGENT -> settings.barkUrgentLevel.apiValue
        Priority.NORMAL -> settings.barkNormalLevel.apiValue
        Priority.LOW -> settings.barkLowLevel.apiValue
    }

    private fun appLabel(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.tencent.wework" -> "企业微信"
        else -> runCatching {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)
    }

    private fun EvaluatedNotification.barkTitle(settings: NtfySettings): String {
        val app = appLabel(packageName)
        val rawTitle = title.ifBlank { app }
        val rendered = settings.barkTitleTemplate
            .ifBlank { "{app}-{title}" }
            .replace("{app}", app)
            .replace("{title}", rawTitle)
            .replace("{package}", packageName)
            .trim()
        return rendered.ifBlank { rawTitle }
    }

    private fun EvaluatedNotification.iconUrl(settings: NtfySettings): String? {
        if (!settings.barkIconEnabled) return null
        val base = settings.barkIconBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$packageName.png"
    }
}
