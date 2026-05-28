package dev.fkwk.push.domain

import kotlinx.serialization.Serializable

/** 通知优先级。最终由 Bark 映射为对应的 iOS 提醒级别。 */
enum class Priority {
    URGENT,
    NORMAL,
    LOW;

    companion object {
        /** 未命中任何规则时的默认优先级（决策第 4 条：低优先级转发）。 */
        val DEFAULT = LOW
    }
}

/** 规则匹配维度。 */
enum class MatchField {
    PACKAGE,   // 来源 app 包名，如 com.tencent.mm（微信）/ com.tencent.wework（企微）
    TITLE,     // 通知标题（通常是联系人 / 群名）
    TEXT,      // 通知正文
    ANY        // 标题或正文任一命中
}

/** 匹配方式。 */
enum class MatchOp {
    CONTAINS,
    EQUALS,
    REGEX
}

/**
 * 一条用户自定义规则。命中后赋予对应优先级；按 [order] 从小到大评估，先命中先生效。
 */
@Serializable
data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    val field: MatchField,
    val op: MatchOp,
    val pattern: String,
    val priority: Priority,
    // 时间段限定（24h 制，分钟数；null 表示全天生效）。用于“工作时段才转发企微”这类需求。
    val activeFromMinute: Int? = null,
    val activeToMinute: Int? = null
)

/** 规则集合的导入/导出容器。 */
@Serializable
data class RuleBackup(
    val version: Int = 1,
    val rules: List<Rule>
)

/** 一条被捕获的通知，经规则引擎判定后的结果。 */
data class EvaluatedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val priority: Priority,
    val matchedRuleName: String?,   // null = 未命中，走默认
    val postTime: Long
)
