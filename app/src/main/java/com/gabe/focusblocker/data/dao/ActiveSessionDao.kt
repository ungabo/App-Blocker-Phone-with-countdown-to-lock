package com.gabe.focusblocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gabe.focusblocker.data.entity.ActiveSessionEntity
import com.gabe.focusblocker.data.model.ActiveSessionWithRuleSet
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveSessionDao {
    @Query(
        """
        SELECT a.*, rs.name AS ruleSetName
        FROM active_sessions a
        INNER JOIN rule_sets rs ON rs.id = a.ruleSetId
        WHERE a.enabled = 1
        ORDER BY a.startedAt DESC
        """
    )
    fun observeEnabledSessions(): Flow<List<ActiveSessionWithRuleSet>>

    @Query(
        """
        SELECT a.*, rs.name AS ruleSetName
        FROM active_sessions a
        INNER JOIN rule_sets rs ON rs.id = a.ruleSetId
        WHERE a.enabled = 1
        ORDER BY a.startedAt DESC
        """
    )
    suspend fun getEnabledSessionsWithRuleSet(): List<ActiveSessionWithRuleSet>

    @Query("SELECT * FROM active_sessions WHERE enabled = 1 ORDER BY startedAt DESC")
    suspend fun getEnabledSessions(): List<ActiveSessionEntity>

    @Query("SELECT * FROM active_sessions WHERE enabled = 1 AND ruleSetId = :ruleSetId ORDER BY startedAt ASC")
    suspend fun getEnabledSessionsForRuleSet(ruleSetId: Long): List<ActiveSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ActiveSessionEntity): Long

    @Query("UPDATE active_sessions SET expiresAt = :expiresAt WHERE id = :sessionId")
    suspend fun updateExpiresAt(sessionId: Long, expiresAt: Long?)

    @Query("UPDATE active_sessions SET enabled = 0 WHERE id = :sessionId")
    suspend fun disableSession(sessionId: Long)

    @Query("UPDATE active_sessions SET enabled = 0 WHERE ruleSetId = :ruleSetId")
    suspend fun disableSessionsForRuleSet(ruleSetId: Long)

    @Query("UPDATE active_sessions SET enabled = 0 WHERE enabled = 1")
    suspend fun disableAllSessions()

    @Query(
        """
        UPDATE active_sessions
        SET enabled = 0
        WHERE enabled = 1 AND expiresAt IS NOT NULL AND expiresAt <= :now
        """
    )
    suspend fun disableExpiredSessions(now: Long): Int
}
