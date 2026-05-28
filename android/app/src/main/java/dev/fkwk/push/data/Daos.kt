package dev.fkwk.push.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY `order` ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY `order` ASC")
    suspend fun enabledRules(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RuleEntity>)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY postTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE forwarded = 0 AND (error IS NULL OR (error NOT LIKE '已跳过:%' AND error NOT LIKE '已屏蔽:%')) ORDER BY postTime ASC LIMIT :limit")
    suspend fun unsent(limit: Int = 100): List<LogEntity>

    @Insert
    suspend fun insert(log: LogEntity): Long

    @Query("UPDATE logs SET forwarded = :forwarded, httpCode = :code, error = :error WHERE id = :id")
    suspend fun updateResult(id: Long, forwarded: Boolean, code: Int?, error: String?)

    @Query("DELETE FROM logs WHERE postTime < :before")
    suspend fun purgeOlderThan(before: Long)
}
