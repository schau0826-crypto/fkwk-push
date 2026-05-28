package dev.fkwk.push.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.fkwk.push.domain.MatchField
import dev.fkwk.push.domain.MatchOp
import dev.fkwk.push.domain.Priority

class Converters {
    @TypeConverter fun fieldToString(v: MatchField) = v.name
    @TypeConverter fun stringToField(v: String) = MatchField.valueOf(v)
    @TypeConverter fun opToString(v: MatchOp) = v.name
    @TypeConverter fun stringToOp(v: String) = MatchOp.valueOf(v)
    @TypeConverter fun priorityToString(v: Priority) = v.name
    @TypeConverter fun stringToPriority(v: String) = Priority.valueOf(v)
}

@Database(
    entities = [RuleEntity::class, LogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao
}
