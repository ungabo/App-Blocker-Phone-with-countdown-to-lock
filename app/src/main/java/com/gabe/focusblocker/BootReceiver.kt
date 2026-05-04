package com.gabe.focusblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gabe.focusblocker.widget.FocusWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val app = context.applicationContext as FocusBlockerApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.sessionRepository.cleanupExpiredSessions()
                app.container.database.scheduledLockDao()
                    .getEnabledOpenLocksWithRuleSet()
                    .filter { it.scheduledLock.startsAt > System.currentTimeMillis() }
                    .forEach {
                        app.container.scheduledLockRepository.enqueueStartWorker(
                            id = it.scheduledLock.id,
                            startsAt = it.scheduledLock.startsAt
                        )
                    }
                SessionNotificationHelper.refresh(context)
                FocusWidgetProvider.requestUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
