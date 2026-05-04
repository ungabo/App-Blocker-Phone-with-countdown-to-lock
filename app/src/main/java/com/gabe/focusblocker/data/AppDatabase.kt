package com.gabe.focusblocker.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gabe.focusblocker.data.dao.ActiveSessionDao
import com.gabe.focusblocker.data.dao.RuleAppDao
import com.gabe.focusblocker.data.dao.RuleDomainDao
import com.gabe.focusblocker.data.dao.RuleSetDao
import com.gabe.focusblocker.data.dao.ScheduledLockDao
import com.gabe.focusblocker.data.entity.ActiveSessionEntity
import com.gabe.focusblocker.data.entity.RuleAppEntity
import com.gabe.focusblocker.data.entity.RuleDomainEntity
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.entity.ScheduledLockEntity

@Database(
    entities = [
        RuleSetEntity::class,
        RuleAppEntity::class,
        RuleDomainEntity::class,
        ActiveSessionEntity::class,
        ScheduledLockEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleSetDao(): RuleSetDao
    abstract fun ruleAppDao(): RuleAppDao
    abstract fun ruleDomainDao(): RuleDomainDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun scheduledLockDao(): ScheduledLockDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_locks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleSetId INTEGER NOT NULL,
                        startsAt INTEGER NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        source TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
