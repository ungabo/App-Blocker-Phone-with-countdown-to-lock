package com.gabe.focusblocker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.gabe.focusblocker.FocusBlockerApplication
import com.gabe.focusblocker.MainActivity
import com.gabe.focusblocker.R
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.model.ActiveSessionWithRuleSet
import com.gabe.focusblocker.data.model.ScheduledLockWithRuleSet
import com.gabe.focusblocker.widget.RecentWidgetPresets.shortWidgetName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        requestUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        requestUpdate(context)
    }

    companion object {
        const val MAX_WIDGET_SETS = 3

        private const val COLOR_BLUE = 0xFF1565C0.toInt()
        private const val COLOR_GREEN = 0xFF2E7D32.toInt()
        private const val COLOR_LIGHT_GREEN = 0xFFC8E6C9.toInt()
        private const val COLOR_YELLOW = 0xFFFFC107.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_DARK = 0xFF18201B.toInt()

        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var tickerJob: Job? = null

        private val setButtonIds = intArrayOf(
            R.id.widget_set_0,
            R.id.widget_set_1,
            R.id.widget_set_2
        )
        private val setColumnIds = intArrayOf(
            R.id.widget_set_column_0,
            R.id.widget_set_column_1,
            R.id.widget_set_column_2
        )
        private val setStatusIds = arrayOf(
            intArrayOf(R.id.widget_set_status_0_0, R.id.widget_set_status_0_1),
            intArrayOf(R.id.widget_set_status_1_0, R.id.widget_set_status_1_1),
            intArrayOf(R.id.widget_set_status_2_0, R.id.widget_set_status_2_1)
        )
        private val delayButtons = listOf(
            R.id.widget_delay_0 to 0,
            R.id.widget_delay_5 to 5,
            R.id.widget_delay_10 to 10,
            R.id.widget_delay_20 to 20,
            R.id.widget_delay_30 to 30
        )
        private val durationButtons = listOf(
            R.id.widget_duration_5 to 5,
            R.id.widget_duration_15 to 15,
            R.id.widget_duration_30 to 30,
            R.id.widget_duration_60 to 60,
            R.id.widget_duration_90 to 90
        )

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            providerScope.launch {
                val shouldTick = updateWidgets(appContext)
                updateTicker(appContext, shouldTick)
            }
        }

        private suspend fun updateWidgets(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FocusWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return false

            val state = loadState(context)
            ids.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, state))
            }
            return state.hasLiveTimers || state.feedbackActive
        }

        private suspend fun loadState(context: Context): WidgetState {
            val app = context.applicationContext as FocusBlockerApplication
            app.container.sessionRepository.cleanupExpiredSessions()
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences(WidgetActionReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            var selectedSetId = prefs.getLong(WidgetActionReceiver.KEY_RULE_SET_ID, -1L)
            var selectedDelay = prefs.getInt(WidgetActionReceiver.KEY_DELAY_MINUTES, -1)
            var selectedDuration = prefs.getInt(WidgetActionReceiver.KEY_DURATION_MINUTES, -1)
            var feedbackUntil = prefs.getLong(WidgetActionReceiver.KEY_START_FEEDBACK_UNTIL, 0L)

            if (feedbackUntil > 0L && feedbackUntil <= now) {
                prefs.edit()
                    .remove(WidgetActionReceiver.KEY_RULE_SET_ID)
                    .remove(WidgetActionReceiver.KEY_DELAY_MINUTES)
                    .remove(WidgetActionReceiver.KEY_DURATION_MINUTES)
                    .remove(WidgetActionReceiver.KEY_START_FEEDBACK_UNTIL)
                    .apply()
                selectedSetId = -1L
                selectedDelay = -1
                selectedDuration = -1
                feedbackUntil = 0L
            }

            val active = app.container.database.activeSessionDao()
                .getEnabledSessionsWithRuleSet()
                .filter { it.session.expiresAt == null || it.session.expiresAt > now }
            val scheduled = app.container.database.scheduledLockDao()
                .getEnabledOpenLocksWithRuleSet()
                .filter { it.scheduledLock.startsAt > now }

            return WidgetState(
                now = now,
                ruleSets = app.container.database.ruleSetDao().getWidgetRuleSets(MAX_WIDGET_SETS),
                selectedSetId = selectedSetId,
                selectedDelay = selectedDelay,
                selectedDuration = selectedDuration,
                feedbackActive = feedbackUntil > now,
                activeSessions = active,
                scheduledLocks = scheduled
            )
        }

        private fun buildViews(context: Context, state: WidgetState): RemoteViews {
            return RemoteViews(context.packageName, R.layout.focus_widget).apply {
                setTextViewText(R.id.widget_run_label, "Run")
                setTextViewText(R.id.widget_in_label, "In")
                setTextViewText(R.id.widget_for_label, "For")

                bindRuleSetButtons(context, state)
                bindDelayButtons(context, state)
                bindDurationButtons(context, state)
                bindStartButton(context, state)
                bindUtilityButtons(context)
                bindRootOpenIntent(context)
                if (state.feedbackActive) {
                    applyStartFeedbackColors()
                }
            }
        }

        private fun RemoteViews.bindRuleSetButtons(context: Context, state: WidgetState) {
            setButtonIds.forEachIndexed { index, viewId ->
                val ruleSet = state.ruleSets.getOrNull(index)
                if (ruleSet == null) {
                    setViewVisibility(setColumnIds[index], View.INVISIBLE)
                    setTextViewText(viewId, "")
                    setBoolean(viewId, "setEnabled", false)
                    setStatusLines(index, emptyList())
                    return@forEachIndexed
                }

                setViewVisibility(setColumnIds[index], View.VISIBLE)
                setBoolean(viewId, "setEnabled", true)
                setTextViewText(viewId, ruleSet.name.shortWidgetName(14))
                setOnClickPendingIntent(viewId, ruleSetIntent(context, ruleSet.id, index))
                applySelectionColor(
                    viewId = viewId,
                    selected = ruleSet.id == state.selectedSetId,
                    state = state
                )
                setStatusLines(index, state.runLinesFor(ruleSet.id))
            }
        }

        private fun RemoteViews.setStatusLines(index: Int, lines: List<String>) {
            setStatusIds[index].forEachIndexed { lineIndex, statusViewId ->
                setTextViewText(statusViewId, lines.getOrNull(lineIndex) ?: " ")
            }
        }

        private fun RemoteViews.bindDelayButtons(context: Context, state: WidgetState) {
            delayButtons.forEach { (viewId, minutes) ->
                setTextViewText(viewId, minutes.delayLabel())
                setOnClickPendingIntent(
                    viewId,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, minutes)
                )
                applySelectionColor(
                    viewId = viewId,
                    selected = minutes == state.selectedDelay,
                    state = state
                )
            }
        }

        private fun RemoteViews.bindDurationButtons(context: Context, state: WidgetState) {
            durationButtons.forEach { (viewId, minutes) ->
                setTextViewText(viewId, "${minutes}m")
                setOnClickPendingIntent(
                    viewId,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, minutes)
                )
                applySelectionColor(
                    viewId = viewId,
                    selected = minutes == state.selectedDuration,
                    state = state
                )
            }
        }

        private fun RemoteViews.bindStartButton(context: Context, state: WidgetState) {
            setTextViewText(R.id.widget_start, "Start")
            setOnClickPendingIntent(
                R.id.widget_start,
                actionIntent(context, WidgetActionReceiver.ACTION_START_SELECTED, 0)
            )
            when {
                state.feedbackActive -> setButtonColor(R.id.widget_start, COLOR_LIGHT_GREEN, COLOR_DARK)
                state.isComplete -> setButtonColor(R.id.widget_start, COLOR_BLUE, COLOR_WHITE)
                state.hasAnySelection -> setButtonColor(R.id.widget_start, COLOR_YELLOW, COLOR_DARK)
            }
        }

        private fun RemoteViews.bindUtilityButtons(context: Context) {
            setOnClickPendingIntent(
                R.id.widget_refresh,
                actionIntent(context, WidgetActionReceiver.ACTION_REFRESH, 0)
            )
            setOnClickPendingIntent(
                R.id.widget_clear_countdowns,
                actionIntent(context, WidgetActionReceiver.ACTION_CLEAR_COUNTDOWNS, 0)
            )
        }

        private fun RemoteViews.bindRootOpenIntent(context: Context) {
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    50,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        private fun RemoteViews.applySelectionColor(
            viewId: Int,
            selected: Boolean,
            state: WidgetState
        ) {
            if (!selected) return
            if (state.feedbackActive) {
                setButtonColor(viewId, COLOR_LIGHT_GREEN, COLOR_DARK)
            } else if (state.isComplete) {
                setButtonColor(viewId, COLOR_GREEN, COLOR_WHITE)
            } else {
                setButtonColor(viewId, COLOR_BLUE, COLOR_WHITE)
            }
        }

        private fun RemoteViews.applyStartFeedbackColors() {
            val allButtonIds = setButtonIds.toList() +
                delayButtons.map { it.first } +
                durationButtons.map { it.first } +
                listOf(R.id.widget_start, R.id.widget_refresh, R.id.widget_clear_countdowns)
            allButtonIds.forEach { viewId ->
                setButtonColor(viewId, COLOR_LIGHT_GREEN, COLOR_DARK)
            }
        }

        private fun RemoteViews.setButtonColor(viewId: Int, backgroundColor: Int, textColor: Int) {
            setInt(viewId, "setBackgroundColor", backgroundColor)
            setTextColor(viewId, textColor)
        }

        private fun actionIntent(context: Context, action: String, value: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = action
                putExtra(WidgetActionReceiver.EXTRA_VALUE, value)
            }
            return PendingIntent.getBroadcast(
                context,
                100 + value + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun ruleSetIntent(context: Context, ruleSetId: Long, index: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_SELECT_SET
                putExtra(WidgetActionReceiver.EXTRA_RULE_SET_ID, ruleSetId)
            }
            return PendingIntent.getBroadcast(
                context,
                500 + index + ruleSetId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun updateTicker(context: Context, shouldRun: Boolean) {
            if (!shouldRun) {
                tickerJob?.cancel()
                tickerJob = null
                return
            }
            if (tickerJob?.isActive == true) return

            val appContext = context.applicationContext
            tickerJob = providerScope.launch {
                while (isActive) {
                    delay(1_000L)
                    if (!updateWidgets(appContext)) break
                }
                tickerJob = null
            }
        }

        private fun WidgetState.runLinesFor(ruleSetId: Long): List<String> {
            val activeLines = activeSessions
                .filter { it.session.ruleSetId == ruleSetId }
                .map { session ->
                    session.session.expiresAt?.let { formatCountdown(it - now) } ?: "running"
                }
            val scheduledLines = scheduledLocks
                .filter { it.scheduledLock.ruleSetId == ruleSetId }
                .map { lock ->
                    "in ${formatCountdown(lock.scheduledLock.startsAt - now)}/${lock.scheduledLock.durationMinutes}"
                }
            return (activeLines + scheduledLines).take(2)
        }

        private fun formatCountdown(remainingMs: Long): String {
            val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1_000L).toInt()
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
            } else {
                "$minutes:${seconds.twoDigits()}"
            }
        }

        private fun Int.twoDigits(): String = toString().padStart(2, '0')

        private fun Int.delayLabel(): String = if (this == 0) "Now" else "${this}m"

        private data class WidgetState(
            val now: Long,
            val ruleSets: List<RuleSetEntity>,
            val selectedSetId: Long,
            val selectedDelay: Int,
            val selectedDuration: Int,
            val feedbackActive: Boolean,
            val activeSessions: List<ActiveSessionWithRuleSet>,
            val scheduledLocks: List<ScheduledLockWithRuleSet>
        ) {
            val hasAnySelection: Boolean =
                selectedSetId >= 0L || selectedDelay >= 0 || selectedDuration >= 0
            val isComplete: Boolean =
                selectedSetId >= 0L && selectedDelay >= 0 && selectedDuration > 0
            val hasLiveTimers: Boolean =
                activeSessions.isNotEmpty() || scheduledLocks.isNotEmpty()
        }
    }
}
