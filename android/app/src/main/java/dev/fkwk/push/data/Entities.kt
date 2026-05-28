package dev.fkwk.push.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.fkwk.push.domain.MatchField
import dev.fkwk.push.domain.MatchOp
import dev.fkwk.push.domain.Priority
import dev.fkwk.push.domain.Rule

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val order: Int,
    val field: MatchField,
    val op: MatchOp,
    val pattern: String,
    val priority: Priority,
    val activeFromMinute: Int?,
    val activeToMinute: Int?
) {
    fun toDomain() = Rule(id, name, enabled, order, field, op, pattern, priority, activeFromMinute, activeToMinute)

    companion object {
        fun from(r: Rule) = RuleEntity(
            r.id, r.name, r.enabled, r.order, r.field, r.op, r.pattern, r.priority,
            r.activeFromMinute, r.activeToMinute
        )
    }
}

/** 转发日志，供本地 UI 排查“哪条通知被识别成什么优先级、是否转发成功”。 */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postTime: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val priority: Priority,
    val matchedRuleName: String?,
    val forwarded: Boolean,
    val httpCode: Int?,        // null 表示尚未发送 / 入队
    val error: String?
)
