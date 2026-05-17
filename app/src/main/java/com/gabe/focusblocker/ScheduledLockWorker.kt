package com.gabe.focusblocker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gabe.focusblocker.widget.FocusWidgetProvider

class ScheduledLockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FocusBlockerApplication
        val now = System.currentTimeMillis()
        val dueLocks = app.container.database.scheduledLockDao().getDueLocks(now)
        dueLocks.forEach { lock ->
            val ruleSet = app.container.ruleSetRepository.getRuleSetById(lock.ruleSetId)
            if (ruleSet != null) {
                app.container.sessionRepository.startSession(
                    ruleSet = ruleSet,
                    source = lock.source,
                    durationMinutesOverride = lock.durationMinutes
                )
                app.container.ruleSetRepository.markRuleSetUsed(ruleSet.id, now)
            }
            app.container.database.scheduledLockDao().markCompleted(lock.id, now)
        }

        SessionNotificationHelper.refresh(applicationContext)
        FocusWidgetProvider.requestUpdate(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_LOCK_ID = "lock_id"
    }
}
