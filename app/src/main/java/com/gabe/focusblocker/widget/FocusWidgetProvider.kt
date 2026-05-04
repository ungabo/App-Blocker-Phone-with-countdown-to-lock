package com.gabe.focusblocker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gabe.focusblocker.FocusBlockerApplication
import com.gabe.focusblocker.MainActivity
import com.gabe.focusblocker.R
import com.gabe.focusblocker.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FocusWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            ids.forEach { updateWidget(context, appWidgetManager, it) }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = baseViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            CoroutineScope(Dispatchers.IO).launch {
                val app = context.applicationContext as FocusBlockerApplication
                app.container.sessionRepository.cleanupExpiredSessions()
                val now = System.currentTimeMillis()
                val ruleSets = app.container.database.ruleSetDao().getAllOrdered()
                val active = app.container.database.activeSessionDao()
                    .getEnabledSessionsWithRuleSet()
                    .filter { it.session.expiresAt == null || it.session.expiresAt > now }
                val scheduled = app.container.database.scheduledLockDao()
                    .getEnabledOpenLocksWithRuleSet()
                    .filter { it.scheduledLock.startsAt > now }

                val prefs = context.getSharedPreferences(WidgetActionReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                val selectedSet = prefs.getInt(WidgetActionReceiver.KEY_RULE_SET_INDEX, -1)
                val selectedDelay = prefs.getInt(WidgetActionReceiver.KEY_DELAY_MINUTES, -1)
                val selectedDuration = prefs.getInt(WidgetActionReceiver.KEY_DURATION_MINUTES, -1)
                val selectedSetName = ruleSets.getOrNull(selectedSet)?.name?.shortWidgetName()

                val selection = listOf(
                    "Set: ${selectedSetName ?: "-"}",
                    "In: ${if (selectedDelay >= 0) selectedDelay.delayLabel() else "-"}",
                    "For: ${if (selectedDuration >= 0) "${selectedDuration}m" else "-"}"
                ).joinToString("  ")

                val status = if (active.isEmpty() && scheduled.isEmpty()) {
                    selection
                } else {
                    val activeText = active.take(2).joinToString(separator = "\n") {
                        "Ends ${it.ruleSetName.shortWidgetName()}: ${TimeUtils.formatRemainingTime(it.session.expiresAt?.minus(now))}"
                    }
                    val scheduledText = scheduled.take(2).joinToString(separator = "\n") {
                        "Starts ${it.ruleSetName.shortWidgetName()}: ${TimeUtils.formatRemainingTime(it.scheduledLock.startsAt - now)}"
                    }
                    listOf(selection, activeText, scheduledText)
                        .filter { it.isNotBlank() }
                        .joinToString(separator = "\n")
                }

                val updatedViews = baseViews(context).apply {
                    setTextViewText(R.id.widget_status, status)
                    setTextViewText(R.id.widget_set_a, ruleSets.getOrNull(0)?.name?.shortWidgetName() ?: "Set A")
                    setTextViewText(R.id.widget_set_b, ruleSets.getOrNull(1)?.name?.shortWidgetName() ?: "Set B")
                    setTextViewText(R.id.widget_set_c, ruleSets.getOrNull(2)?.name?.shortWidgetName() ?: "Set C")
                }
                appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
            }
        }

        private fun baseViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.focus_widget).apply {
                setOnClickPendingIntent(
                    R.id.widget_set_a,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_SET, 0)
                )
                setOnClickPendingIntent(
                    R.id.widget_set_b,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_SET, 1)
                )
                setOnClickPendingIntent(
                    R.id.widget_set_c,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_SET, 2)
                )
                setOnClickPendingIntent(
                    R.id.widget_delay_0,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, 0)
                )
                setOnClickPendingIntent(
                    R.id.widget_delay_5,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, 5)
                )
                setOnClickPendingIntent(
                    R.id.widget_delay_10,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, 10)
                )
                setOnClickPendingIntent(
                    R.id.widget_delay_20,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, 20)
                )
                setOnClickPendingIntent(
                    R.id.widget_delay_30,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DELAY, 30)
                )
                setOnClickPendingIntent(
                    R.id.widget_duration_5,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, 5)
                )
                setOnClickPendingIntent(
                    R.id.widget_duration_15,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, 15)
                )
                setOnClickPendingIntent(
                    R.id.widget_duration_30,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, 30)
                )
                setOnClickPendingIntent(
                    R.id.widget_duration_60,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, 60)
                )
                setOnClickPendingIntent(
                    R.id.widget_duration_90,
                    actionIntent(context, WidgetActionReceiver.ACTION_SELECT_DURATION, 90)
                )
                setOnClickPendingIntent(
                    R.id.widget_start,
                    actionIntent(context, WidgetActionReceiver.ACTION_START_SELECTED, 0)
                )
                setOnClickPendingIntent(
                    R.id.widget_refresh,
                    actionIntent(context, WidgetActionReceiver.ACTION_REFRESH, 0)
                )
                setOnClickPendingIntent(
                    R.id.widget_clear_countdowns,
                    actionIntent(context, WidgetActionReceiver.ACTION_CLEAR_COUNTDOWNS, 0)
                )
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
        }

        private fun actionIntent(context: Context, action: String, presetIndex: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = action
                putExtra(WidgetActionReceiver.EXTRA_VALUE, presetIndex)
            }
            return PendingIntent.getBroadcast(
                context,
                100 + presetIndex + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun String.shortWidgetName(): String {
            val compact = trim().replace(Regex("\\s+"), " ")
            return if (compact.length <= 16) compact else compact.take(15).trimEnd() + "..."
        }

        private fun Int.delayLabel(): String = if (this == 0) "Now" else "${this}m"
    }
}
