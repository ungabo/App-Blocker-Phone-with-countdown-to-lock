package com.gabe.focusblocker.repository

import com.gabe.focusblocker.data.dao.ActiveSessionDao
import com.gabe.focusblocker.data.entity.ActiveSessionEntity
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.entity.SessionSource
import com.gabe.focusblocker.data.model.ActiveSessionWithRuleSet
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val activeSessionDao: ActiveSessionDao) {
    fun observeEnabledSessions(): Flow<List<ActiveSessionWithRuleSet>> =
        activeSessionDao.observeEnabledSessions()

    suspend fun startSession(
        ruleSet: RuleSetEntity,
        source: SessionSource,
        durationMinutesOverride: Int? = ruleSet.defaultDurationMinutes
    ): Long {
        val now = System.currentTimeMillis()
        val expiresAt = durationMinutesOverride?.let { now + it * 60_000L }
        val existing = activeSessionDao.getEnabledSessionsForRuleSet(ruleSet.id)
            .firstOrNull { it.expiresAt == null || it.expiresAt > now }

        if (existing != null) {
            val mergedExpiresAt = when {
                existing.expiresAt == null || expiresAt == null -> null
                else -> maxOf(existing.expiresAt, expiresAt)
            }
            activeSessionDao.updateExpiresAt(existing.id, mergedExpiresAt)
            return existing.id
        }

        return activeSessionDao.insert(
            ActiveSessionEntity(
                ruleSetId = ruleSet.id,
                mode = ruleSet.mode,
                startedAt = now,
                expiresAt = expiresAt,
                enabled = true,
                source = source
            )
        )
    }

    suspend fun endSession(sessionId: Long) {
        activeSessionDao.disableSession(sessionId)
    }

    suspend fun endAllSessions() {
        activeSessionDao.disableAllSessions()
    }

    suspend fun cleanupExpiredSessions(now: Long = System.currentTimeMillis()) {
        activeSessionDao.disableExpiredSessions(now)
    }
}
