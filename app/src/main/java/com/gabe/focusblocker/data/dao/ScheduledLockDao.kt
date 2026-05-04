package com.gabe.focusblocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gabe.focusblocker.data.entity.ScheduledLockEntity
import com.gabe.focusblocker.data.model.ScheduledLockWithRuleSet
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledLockDao {
    @Query(
        """
        SELECT sl.*, rs.name AS ruleSetName
        FROM scheduled_locks sl
        INNER JOIN rule_sets rs ON rs.id = sl.ruleSetId
        WHERE sl.completedAt IS NULL
        ORDER BY sl.startsAt ASC
        """
    )
    fun observeOpenLocks(): Flow<List<ScheduledLockWithRuleSet>>

    @Query(
        """
        SELECT sl.*, rs.name AS ruleSetName
        FROM scheduled_locks sl
        INNER JOIN rule_sets rs ON rs.id = sl.ruleSetId
        WHERE sl.completedAt IS NULL AND sl.enabled = 1
        ORDER BY sl.startsAt ASC
        """
    )
    suspend fun getEnabledOpenLocksWithRuleSet(): List<ScheduledLockWithRuleSet>

    @Query(
        """
        SELECT * FROM scheduled_locks
        WHERE completedAt IS NULL AND enabled = 1 AND startsAt <= :now
        ORDER BY startsAt ASC
        """
    )
    suspend fun getDueLocks(now: Long): List<ScheduledLockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lock: ScheduledLockEntity): Long

    @Query("UPDATE scheduled_locks SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query("UPDATE scheduled_locks SET startsAt = :startsAt, durationMinutes = :durationMinutes, enabled = 1, updatedAt = :now WHERE id = :id")
    suspend fun reschedule(id: Long, startsAt: Long, durationMinutes: Int, now: Long)

    @Query("UPDATE scheduled_locks SET completedAt = :now, enabled = 0, updatedAt = :now WHERE id = :id")
    suspend fun markCompleted(id: Long, now: Long)

    @Query("DELETE FROM scheduled_locks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_locks WHERE completedAt IS NULL")
    suspend fun deleteOpenLocks()
}
