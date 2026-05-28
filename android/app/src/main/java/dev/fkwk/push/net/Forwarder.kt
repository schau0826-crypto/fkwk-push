package dev.fkwk.push.net

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.fkwk.push.data.LogDao
import dev.fkwk.push.data.LogEntity
import dev.fkwk.push.data.SettingsRepository
import dev.fkwk.push.domain.EvaluatedNotification
import dev.fkwk.push.engine.RuleEngine
import dev.fkwk.push.work.RetryWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 串联：规则评估 -> App 分级覆盖 -> 落库日志 -> 发布 Bark -> 更新结果。
 * 失败（网络等）则调度 RetryWorker 在恢复网络后补发。
 */
@Singleton
class Forwarder @Inject constructor(
    private val ruleEngine: RuleEngine,
    private val barkClient: BarkClient,
    private val aiHubClient: AiHubClient,
    private val deviceState: DeviceState,
    private val settingsRepo: SettingsRepository,
    private val logDao: LogDao,
    private val workManager: WorkManager
) {
    suspend fun handle(packageName: String, title: String, text: String, postTime: Long) {
        val settings = settingsRepo.current()
        if (!settings.forwardingEnabled) {
            Timber.d("转发已关闭，忽略来自 %s 的通知", packageName)
            return
        }

        val evaluated: EvaluatedNotification =
            ruleEngine.evaluate(packageName, title, text, postTime).let { n ->
                settings.packagePriorities[packageName]?.let { n.copy(priority = it) } ?: n
            }

        val blockedKeyword = settings.blockedKeywords.firstOrNull { keyword ->
            keyword.isNotBlank() && "$title\n$text".contains(keyword, ignoreCase = true)
        }
        val pausedByScreenState = settings.pauseWhenInteractive && deviceState.isActivelyUsingDevice()
        val skipReason = when {
            blockedKeyword != null -> "$SKIPPED_KEYWORD_PREFIX$blockedKeyword"
            pausedByScreenState -> SKIPPED_INTERACTIVE_ERROR
            else -> null
        }
        val logId = logDao.insert(
            LogEntity(
                postTime = postTime,
                packageName = packageName,
                title = title,
                text = text,
                priority = evaluated.priority,
                matchedRuleName = evaluated.matchedRuleName,
                forwarded = false,
                httpCode = null,
                error = skipReason
            )
        )

        if (skipReason != null) {
            Timber.d("跳过来自 %s 的通知转发：%s", packageName, skipReason)
            publishAiEvent(settings, logId, evaluated, forwarded = false, httpCode = null, error = skipReason)
            return
        }

        when (val r = barkClient.publish(settings, evaluated)) {
            is PublishResult.Success -> {
                logDao.updateResult(logId, forwarded = true, code = r.httpCode, error = null)
                publishAiEvent(settings, logId, evaluated, forwarded = true, httpCode = r.httpCode, error = null)
            }

            is PublishResult.Failure -> {
                logDao.updateResult(logId, forwarded = false, code = r.httpCode, error = r.error)
                publishAiEvent(settings, logId, evaluated, forwarded = false, httpCode = r.httpCode, error = r.error)
                Timber.w("发布失败(%s)，调度重试", r.error)
                scheduleRetry()
            }
        }
    }

    private fun scheduleRetry() {
        val req = OneTimeWorkRequestBuilder<RetryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("retry-forward", ExistingWorkPolicy.KEEP, req)
    }

    private fun publishAiEvent(
        settings: dev.fkwk.push.data.NtfySettings,
        logId: Long,
        evaluated: EvaluatedNotification,
        forwarded: Boolean,
        httpCode: Int?,
        error: String?
    ) {
        aiHubClient.publish(
            settings,
            AiNotificationEvent(
                id = "android-$logId",
                postTime = evaluated.postTime,
                packageName = evaluated.packageName,
                appName = aiHubClient.appLabel(evaluated.packageName),
                title = evaluated.title,
                text = evaluated.text,
                priority = evaluated.priority,
                matchedRuleName = evaluated.matchedRuleName,
                forwarded = forwarded,
                httpCode = httpCode,
                error = error
            )
        )
    }

    companion object {
        const val SKIPPED_INTERACTIVE_ERROR = "已跳过：本机使用中"
        const val SKIPPED_KEYWORD_PREFIX = "已屏蔽：关键词 "
    }
}
