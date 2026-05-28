package dev.fkwk.push.engine

import dev.fkwk.push.data.RuleDao
import dev.fkwk.push.domain.EvaluatedNotification
import dev.fkwk.push.domain.MatchField
import dev.fkwk.push.domain.MatchOp
import dev.fkwk.push.domain.Priority
import dev.fkwk.push.domain.Rule
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则引擎：对捕获到的通知按 order 顺序评估，先命中先生效。
 * 未命中任何规则 -> 默认低优先级（决策第 4 条）。
 */
@Singleton
class RuleEngine @Inject constructor(
    private val ruleDao: RuleDao
) {
    suspend fun evaluate(
        packageName: String,
        title: String,
        text: String,
        postTime: Long
    ): EvaluatedNotification {
        val nowMinute = currentMinuteOfDay(postTime)
        val rules = ruleDao.enabledRules().map { it.toDomain() }

        for (rule in rules) {
            if (!rule.isActiveAt(nowMinute)) continue
            if (rule.matches(packageName, title, text)) {
                return EvaluatedNotification(
                    packageName, title, text, rule.priority, rule.name, postTime
                )
            }
        }
        return EvaluatedNotification(
            packageName, title, text, Priority.DEFAULT, null, postTime
        )
    }

    private fun currentMinuteOfDay(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}

/** 时间段判断，支持跨午夜（如 22:00-07:00）。null 表示全天。 */
internal fun Rule.isActiveAt(minuteOfDay: Int): Boolean {
    val from = activeFromMinute ?: return true
    val to = activeToMinute ?: return true
    return if (from <= to) {
        minuteOfDay in from..to
    } else {
        // 跨午夜
        minuteOfDay >= from || minuteOfDay <= to
    }
}

internal fun Rule.matches(packageName: String, title: String, text: String): Boolean {
    val haystack = when (field) {
        MatchField.PACKAGE -> packageName
        MatchField.TITLE -> title
        MatchField.TEXT -> text
        MatchField.ANY -> "$title\n$text"
    }
    return when (op) {
        MatchOp.CONTAINS -> haystack.contains(pattern, ignoreCase = true)
        MatchOp.EQUALS -> haystack.equals(pattern, ignoreCase = true)
        MatchOp.REGEX -> runCatching { Regex(pattern).containsMatchIn(haystack) }.getOrDefault(false)
    }
}
