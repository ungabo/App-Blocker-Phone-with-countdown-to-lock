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
                val shouldRefreshNotification = when (intent?.action) {
                    ACTION_SELECT_SET -> {
                        saveSelection(
                            context,
                            KEY_RULE_SET_ID,
                            intent.getLongExtra(EXTRA_RULE_SET_ID, -1L)
                        )
                        false
                    }
                    ACTION_SELECT_DELAY -> {
                        saveSelection(
                            context,
                            KEY_DELAY_MINUTES,
                            intent.getIntExtra(EXTRA_VALUE, -1)
                        )
                        false
                    }
                    ACTION_SELECT_DURATION -> {
                        saveSelection(
                            context,
                            KEY_DURATION_MINUTES,
                            intent.getIntExtra(EXTRA_VALUE, -1)
                        )
                        false
                    }
                    ACTION_START_SELECTED -> scheduleFromSelections(context, app)
                    ACTION_START_RECENT -> scheduleRecent(
                        context,
                        app,
                        intent.getIntExtra(EXTRA_INDEX, -1)
                    )
                    ACTION_CLEAR_COUNTDOWNS -> {
                        clearSelections(context)
                        false
                    }
                    ACTION_REFRESH -> true
                    else -> false
                }
                if (shouldRefreshNotification) {
                    SessionNotificationHelper.refresh(context)
                }
                FocusWidgetProvider.requestUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun scheduleFromSelections(context: Context, app: FocusBlockerApplication): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ruleSetId = prefs.getLong(KEY_RULE_SET_ID, -1L)
        val delayMinutes = prefs.getInt(KEY_DELAY_MINUTES, -1)
        val durationMinutes = prefs.getInt(KEY_DURATION_MINUTES, -1)
        if (ruleSetId < 0L || delayMinutes < 0 || durationMinutes <= 0) return false

        val ruleSet = app.container.ruleSetRepository.getRuleSetById(ruleSetId) ?: return false
        app.container.scheduledLockRepository.scheduleLock(
            ruleSet = ruleSet,
            delayMinutes = delayMinutes,
            durationMinutes = durationMinutes,
            source = SessionSource.WIDGET
        )
        app.container.ruleSetRepository.markRuleSetUsed(ruleSet.id)
        prefs.edit()
            .putLong(KEY_START_FEEDBACK_UNTIL, System.currentTimeMillis() + START_FEEDBACK_MS)
            .apply()
        return true
    }

    private suspend fun scheduleRecent(context: Context, app: FocusBlockerApplication, index: Int): Boolean {
        val preset = RecentWidgetPresets.get(context).getOrNull(index) ?: return false
        val ruleSet = app.container.ruleSetRepository.getRuleSetById(preset.ruleSetId) ?: return false
        app.container.scheduledLockRepository.scheduleLock(
            ruleSet = ruleSet,
            delayMinutes = preset.delayMinutes,
            durationMinutes = preset.durationMinutes,
            source = SessionSource.WIDGET
        )
        app.container.ruleSetRepository.markRuleSetUsed(ruleSet.id)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_RULE_SET_ID, ruleSet.id)
            .putInt(KEY_DELAY_MINUTES, preset.delayMinutes)
            .putInt(KEY_DURATION_MINUTES, preset.durationMinutes)
            .putLong(KEY_START_FEEDBACK_UNTIL, System.currentTimeMillis() + START_FEEDBACK_MS)
            .apply()
        return true
    }

    private fun saveSelection(context: Context, key: String, value: Long) {
        if (value < 0L) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, value)
            .apply()
    }

    private fun saveSelection(context: Context, key: String, value: Int) {
        if (value < 0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, value)
            .apply()
    }

    private fun clearSelections(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RULE_SET_ID)
            .remove(KEY_DELAY_MINUTES)
            .remove(KEY_DURATION_MINUTES)
            .remove(KEY_START_FEEDBACK_UNTIL)
            .apply()
    }

    companion object {
        const val ACTION_SELECT_SET = "com.gabe.focusblocker.widget.SELECT_SET"
        const val ACTION_SELECT_DELAY = "com.gabe.focusblocker.widget.SELECT_DELAY"
        const val ACTION_SELECT_DURATION = "com.gabe.focusblocker.widget.SELECT_DURATION"
        const val ACTION_START_SELECTED = "com.gabe.focusblocker.widget.START_SELECTED"
        const val ACTION_START_RECENT = "com.gabe.focusblocker.widget.START_RECENT"
        const val ACTION_REFRESH = "com.gabe.focusblocker.widget.REFRESH"
        const val ACTION_CLEAR_COUNTDOWNS = "com.gabe.focusblocker.widget.CLEAR_COUNTDOWNS"
        const val EXTRA_VALUE = "value"
        const val EXTRA_RULE_SET_ID = "rule_set_id"
        const val EXTRA_INDEX = "index"
        const val PREFS_NAME = "focus_widget_selection"
        const val KEY_RULE_SET_ID = "rule_set_id"
        const val KEY_DELAY_MINUTES = "delay_minutes"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val KEY_START_FEEDBACK_UNTIL = "start_feedback_until"
        private const val START_FEEDBACK_MS = 1_000L
    }
}
