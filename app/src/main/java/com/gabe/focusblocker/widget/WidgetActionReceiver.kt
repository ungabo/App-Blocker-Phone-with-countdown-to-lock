package com.gabe.focusblocker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gabe.focusblocker.FocusBlockerApplication
import com.gabe.focusblocker.SessionNotificationHelper
import com.gabe.focusblocker.data.entity.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FocusBlockerApplication
                when (intent?.action) {
                    ACTION_SELECT_SET -> saveSelection(
                        context,
                        KEY_RULE_SET_INDEX,
                        intent.getIntExtra(EXTRA_VALUE, -1)
                    )
                    ACTION_SELECT_DELAY -> saveSelection(
                        context,
                        KEY_DELAY_MINUTES,
                        intent.getIntExtra(EXTRA_VALUE, -1)
                    )
                    ACTION_SELECT_DURATION -> saveSelection(
                        context,
                        KEY_DURATION_MINUTES,
                        intent.getIntExtra(EXTRA_VALUE, -1)
                    )
                    ACTION_START_SELECTED -> scheduleFromSelections(context, app)
                    ACTION_CLEAR_COUNTDOWNS -> app.container.scheduledLockRepository.deleteOpenLocks()
                    ACTION_REFRESH -> Unit
                }
                SessionNotificationHelper.refresh(context)
                FocusWidgetProvider.requestUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun scheduleFromSelections(context: Context, app: FocusBlockerApplication) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ruleSetIndex = prefs.getInt(KEY_RULE_SET_INDEX, -1)
        val delayMinutes = prefs.getInt(KEY_DELAY_MINUTES, -1)
        val durationMinutes = prefs.getInt(KEY_DURATION_MINUTES, -1)
        if (ruleSetIndex < 0 || delayMinutes < 0 || durationMinutes < 0) return

        val ruleSet = app.container.database.ruleSetDao().getAllOrdered().getOrNull(ruleSetIndex) ?: return
        app.container.scheduledLockRepository.scheduleLock(
            ruleSet = ruleSet,
            delayMinutes = delayMinutes,
            durationMinutes = durationMinutes,
            source = SessionSource.WIDGET
        )
        prefs.edit().clear().apply()
    }

    private fun saveSelection(context: Context, key: String, value: Int) {
        if (value < 0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, value)
            .apply()
    }

    companion object {
        const val ACTION_SELECT_SET = "com.gabe.focusblocker.widget.SELECT_SET"
        const val ACTION_SELECT_DELAY = "com.gabe.focusblocker.widget.SELECT_DELAY"
        const val ACTION_SELECT_DURATION = "com.gabe.focusblocker.widget.SELECT_DURATION"
        const val ACTION_START_SELECTED = "com.gabe.focusblocker.widget.START_SELECTED"
        const val ACTION_REFRESH = "com.gabe.focusblocker.widget.REFRESH"
        const val ACTION_CLEAR_COUNTDOWNS = "com.gabe.focusblocker.widget.CLEAR_COUNTDOWNS"
        const val EXTRA_VALUE = "value"
        const val PREFS_NAME = "focus_widget_selection"
        const val KEY_RULE_SET_INDEX = "rule_set_index"
        const val KEY_DELAY_MINUTES = "delay_minutes"
        const val KEY_DURATION_MINUTES = "duration_minutes"
    }
}
