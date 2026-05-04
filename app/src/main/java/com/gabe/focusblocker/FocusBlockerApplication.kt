package com.gabe.focusblocker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusBlockerApplication : Application() {
    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.ruleSetRepository.seedDefaultsIfEmpty(now = System.currentTimeMillis())
            container.sessionRepository.cleanupExpiredSessions()
            val now = System.currentTimeMillis()
            container.database.scheduledLockDao()
                .getEnabledOpenLocksWithRuleSet()
                .filter { it.scheduledLock.startsAt > now }
                .forEach {
                    container.scheduledLockRepository.enqueueStartWorker(
                        id = it.scheduledLock.id,
                        startsAt = it.scheduledLock.startsAt
                    )
                }
            SessionNotificationHelper.refresh(this@FocusBlockerApplication)
        }
    }
}
