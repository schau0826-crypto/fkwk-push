package dev.fkwk.push.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.fkwk.push.data.LogDao
import dev.fkwk.push.data.SettingsRepository
import dev.fkwk.push.domain.EvaluatedNotification
import dev.fkwk.push.net.BarkClient
import dev.fkwk.push.net.PublishResult
import timber.log.Timber

/** 网络恢复后补发所有未成功转发的通知。 */
@HiltWorker
class RetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val logDao: LogDao,
    private val barkClient: BarkClient,
    private val settingsRepo: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepo.current()
        if (settings.barkServerUrl.isBlank() || settings.barkDeviceKey.isBlank()) return Result.success()

        val pending = logDao.unsent()
        if (pending.isEmpty()) return Result.success()

        Timber.i("补发 %d 条未成功的通知", pending.size)
        var allOk = true
        for (log in pending) {
            val n = EvaluatedNotification(
                packageName = log.packageName,
                title = log.title,
                text = log.text,
                priority = log.priority,
                matchedRuleName = log.matchedRuleName,
                postTime = log.postTime
            )
            when (val r = barkClient.publish(settings, n)) {
                is PublishResult.Success ->
                    logDao.updateResult(log.id, true, r.httpCode, null)
                is PublishResult.Failure -> {
                    logDao.updateResult(log.id, false, r.httpCode, r.error)
                    allOk = false
                }
            }
        }
        // 仍有失败 -> retry（触发指数退避）
        return if (allOk) Result.success() else Result.retry()
    }
}
