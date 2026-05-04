package com.gabe.focusblocker.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gabe.focusblocker.ScheduledLockWorker
import com.gabe.focusblocker.data.dao.ScheduledLockDao
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.entity.ScheduledLockEntity
import com.gabe.focusblocker.data.entity.SessionSource
import com.gabe.focusblocker.data.model.ScheduledLockWithRuleSet
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class ScheduledLockRepository(
    private val context: Context,
    private val scheduledLockDao: ScheduledLockDao
) {
    fun observeOpenLocks(): Flow<List<ScheduledLockWithRuleSet>> =
        scheduledLockDao.observeOpenLocks()

    suspend fun scheduleLock(
        ruleSet: RuleSetEntity,
        delayMinutes: Int,
        durationMinutes: Int,
        source: SessionSource
    ): Long {
        val now = System.currentTimeMillis()
        val startsAt = now + delayMinutes.coerceAtLeast(0) * 60_000L
        val id = scheduledLockDao.insert(
            ScheduledLockEntity(
                ruleSetId = ruleSet.id,
                startsAt = startsAt,
                durationMinutes = durationMinutes.coerceAtLeast(1),
                enabled = true,
                createdAt = now,
                updatedAt = now,
                source = source
            )
        )
        enqueueStartWorker(id, startsAt)
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        scheduledLockDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun reschedule(id: Long, delayMinutes: Int, durationMinutes: Int) {
        val now = System.currentTimeMillis()
        val startsAt = now + delayMinutes.coerceAtLeast(0) * 60_000L
        scheduledLockDao.reschedule(
            id = id,
            startsAt = startsAt,
            durationMinutes = durationMinutes.coerceAtLeast(1),
            now = now
        )
        enqueueStartWorker(id, startsAt)
    }

    suspend fun delete(id: Long) {
        scheduledLockDao.delete(id)
    }

    suspend fun deleteOpenLocks() {
        scheduledLockDao.deleteOpenLocks()
    }

    fun enqueueStartWorker(id: Long, startsAt: Long) {
        val delayMs = (startsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledLockWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ScheduledLockWorker.KEY_LOCK_ID to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled-lock-$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
