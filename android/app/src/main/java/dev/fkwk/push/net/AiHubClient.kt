package dev.fkwk.push.net

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fkwk.push.data.NtfySettings
import dev.fkwk.push.domain.Priority
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiNotificationEvent(
    val id: String,
    val postTime: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val priority: Priority,
    val matchedRuleName: String?,
    val forwarded: Boolean,
    val httpCode: Int?,
    val error: String?
)

@Singleton
class AiHubClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { encodeDefaults = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun publish(settings: NtfySettings, event: AiNotificationEvent) {
        if (!settings.aiHubEnabled) return
        val base = settings.aiHubUrl.trim().trimEnd('/')
        if (base.isBlank()) return

        val builder = Request.Builder()
            .url("$base/ai/events")
            .post(json.encodeToString(event).toRequestBody(mediaJson))
        val token = settings.aiHubToken.trim()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        try {
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("AI Hub 上报失败：HTTP %s", resp.code)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "AI Hub 上报异常")
        }
    }

    fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString().ifBlank { packageName }
    }.getOrDefault(packageName)
}
