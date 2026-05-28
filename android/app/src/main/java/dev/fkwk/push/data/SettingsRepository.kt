package dev.fkwk.push.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fkwk.push.domain.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class BarkLevel(val apiValue: String, val label: String) {
    PASSIVE("passive", "仅通知中心"),
    ACTIVE("active", "横幅弹窗"),
    TIME_SENSITIVE("timeSensitive", "时效性通知")
}

/** 推送连接与转发的全局配置。 */
data class NtfySettings(
    val barkServerUrl: String = "https://api.day.app",
    val barkDeviceKey: String = "",
    val barkTitleTemplate: String = "{app}-{title}",
    val barkIconEnabled: Boolean = false,
    val barkIconBaseUrl: String = "",
    val barkIconUploadToken: String = "",
    val barkLowLevel: BarkLevel = BarkLevel.ACTIVE,
    val barkNormalLevel: BarkLevel = BarkLevel.ACTIVE,
    val barkUrgentLevel: BarkLevel = BarkLevel.TIME_SENSITIVE,
    val forwardingEnabled: Boolean = true,
    val pauseWhenInteractive: Boolean = false,
    val blockedKeywords: Set<String> = emptySet(),
    val aiHubEnabled: Boolean = false,
    val aiHubUrl: String = "",
    val aiHubToken: String = "",
    // 只转发这些包名的通知，避免全量噪声。默认微信 + 企业微信。
    val monitoredPackages: Set<String> = DEFAULT_PACKAGES,
    val packagePriorities: Map<String, Priority> = DEFAULT_PACKAGE_PRIORITIES
) {
    companion object {
        val DEFAULT_PACKAGES = setOf("com.tencent.mm", "com.tencent.wework")
        val DEFAULT_PACKAGE_PRIORITIES = mapOf(
            "com.tencent.mm" to Priority.NORMAL,
            "com.tencent.wework" to Priority.URGENT
        )
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val BARK_SERVER_URL = stringPreferencesKey("bark_server_url")
        val BARK_DEVICE_KEY = stringPreferencesKey("bark_device_key")
        val BARK_TITLE_TEMPLATE = stringPreferencesKey("bark_title_template")
        val BARK_ICON_ENABLED = booleanPreferencesKey("bark_icon_enabled")
        val BARK_ICON_BASE_URL = stringPreferencesKey("bark_icon_base_url")
        val BARK_ICON_UPLOAD_TOKEN = stringPreferencesKey("bark_icon_upload_token")
        val BARK_LOW_LEVEL = stringPreferencesKey("bark_low_level")
        val BARK_NORMAL_LEVEL = stringPreferencesKey("bark_normal_level")
        val BARK_URGENT_LEVEL = stringPreferencesKey("bark_urgent_level")
        val FORWARDING = booleanPreferencesKey("forwarding_enabled")
        val PAUSE_WHEN_INTERACTIVE = booleanPreferencesKey("pause_when_interactive")
        val BLOCKED_KEYWORDS = stringSetPreferencesKey("blocked_keywords")
        val AI_HUB_ENABLED = booleanPreferencesKey("ai_hub_enabled")
        val AI_HUB_URL = stringPreferencesKey("ai_hub_url")
        val AI_HUB_TOKEN = stringPreferencesKey("ai_hub_token")
        val PACKAGES = stringSetPreferencesKey("monitored_packages")
        val PACKAGE_PRIORITIES = stringSetPreferencesKey("package_priorities")
    }

    val settings: Flow<NtfySettings> = context.dataStore.data.map { p ->
        NtfySettings(
            barkServerUrl = p[Keys.BARK_SERVER_URL] ?: "https://api.day.app",
            barkDeviceKey = p[Keys.BARK_DEVICE_KEY] ?: "",
            barkTitleTemplate = p[Keys.BARK_TITLE_TEMPLATE] ?: "{app}-{title}",
            barkIconEnabled = p[Keys.BARK_ICON_ENABLED] ?: false,
            barkIconBaseUrl = p[Keys.BARK_ICON_BASE_URL] ?: "",
            barkIconUploadToken = p[Keys.BARK_ICON_UPLOAD_TOKEN] ?: "",
            barkLowLevel = p[Keys.BARK_LOW_LEVEL].toBarkLevel(BarkLevel.ACTIVE),
            barkNormalLevel = p[Keys.BARK_NORMAL_LEVEL].toBarkLevel(BarkLevel.ACTIVE),
            barkUrgentLevel = p[Keys.BARK_URGENT_LEVEL].toBarkLevel(BarkLevel.TIME_SENSITIVE),
            forwardingEnabled = p[Keys.FORWARDING] ?: true,
            pauseWhenInteractive = p[Keys.PAUSE_WHEN_INTERACTIVE] ?: false,
            blockedKeywords = p[Keys.BLOCKED_KEYWORDS] ?: emptySet(),
            aiHubEnabled = p[Keys.AI_HUB_ENABLED] ?: false,
            aiHubUrl = p[Keys.AI_HUB_URL] ?: "",
            aiHubToken = p[Keys.AI_HUB_TOKEN] ?: "",
            monitoredPackages = p[Keys.PACKAGES] ?: NtfySettings.DEFAULT_PACKAGES,
            packagePriorities = p[Keys.PACKAGE_PRIORITIES].toPriorityMap()
        )
    }

    suspend fun current(): NtfySettings = settings.first()

    suspend fun update(transform: (NtfySettings) -> NtfySettings) {
        val cur = current()
        val next = transform(cur)
        context.dataStore.edit { p ->
            p[Keys.BARK_SERVER_URL] = next.barkServerUrl
            p[Keys.BARK_DEVICE_KEY] = next.barkDeviceKey
            p[Keys.BARK_TITLE_TEMPLATE] = next.barkTitleTemplate
            p[Keys.BARK_ICON_ENABLED] = next.barkIconEnabled
            p[Keys.BARK_ICON_BASE_URL] = next.barkIconBaseUrl
            p[Keys.BARK_ICON_UPLOAD_TOKEN] = next.barkIconUploadToken
            p[Keys.BARK_LOW_LEVEL] = next.barkLowLevel.name
            p[Keys.BARK_NORMAL_LEVEL] = next.barkNormalLevel.name
            p[Keys.BARK_URGENT_LEVEL] = next.barkUrgentLevel.name
            p[Keys.FORWARDING] = next.forwardingEnabled
            p[Keys.PAUSE_WHEN_INTERACTIVE] = next.pauseWhenInteractive
            p[Keys.BLOCKED_KEYWORDS] = next.blockedKeywords
            p[Keys.AI_HUB_ENABLED] = next.aiHubEnabled
            p[Keys.AI_HUB_URL] = next.aiHubUrl
            p[Keys.AI_HUB_TOKEN] = next.aiHubToken
            p[Keys.PACKAGES] = next.monitoredPackages
            p[Keys.PACKAGE_PRIORITIES] = next.packagePriorities
                .map { (pkg, priority) -> "$pkg=${priority.name}" }
                .toSet()
        }
    }

    private fun String?.toBarkLevel(fallback: BarkLevel): BarkLevel =
        this?.let { runCatching { BarkLevel.valueOf(it) }.getOrNull() } ?: fallback

    private fun Set<String>?.toPriorityMap(): Map<String, Priority> {
        if (this == null) return NtfySettings.DEFAULT_PACKAGE_PRIORITIES
        return mapNotNull { raw ->
            val pkg = raw.substringBefore('=').trim()
            val priority = raw.substringAfter('=', "").trim()
            val parsed = runCatching { Priority.valueOf(priority) }.getOrNull()
            if (pkg.isBlank() || parsed == null) null else pkg to parsed
        }.toMap()
    }
}
