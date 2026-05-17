package com.gabe.focusblocker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gabe.focusblocker.util.TimeUtils

object SessionNotificationHelper {
    private const val CHANNEL_ID = "active_focus_sessions_silent"
    private const val NOTIFICATION_ID = 1001

    suspend fun refresh(context: Context) {
        val app = context.applicationContext as FocusBlockerApplication
        app.container.sessionRepository.cleanupExpiredSessions()
        val now = System.currentTimeMillis()
        val sessions = app.container.database.activeSessionDao()
            .getEnabledSessionsWithRuleSet()
            .filter { it.session.expiresAt == null || it.session.expiresAt > now }
        val scheduledLocks = app.container.database.scheduledLockDao()
            .getEnabledOpenLocksWithRuleSet()
            .filter { it.scheduledLock.startsAt > now }

        if (sessions.isEmpty() && scheduledLocks.isEmpty()) {
            cancel(context)
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val activeContent = sessions.joinToString(separator = "\n") {
            "Ends ${it.ruleSetName}: ${TimeUtils.formatRemainingTime(it.session.expiresAt?.minus(now))}"
        }
        val scheduledContent = scheduledLocks.take(3).joinToString(separator = "\n") {
            "Starts ${it.ruleSetName}: ${TimeUtils.formatRemainingTime(it.scheduledLock.startsAt - now)}"
        }
        val content = listOf(activeContent, scheduledContent)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
        val title = when {
            sessions.isNotEmpty() && scheduledLocks.isNotEmpty() -> "Focus locks and countdowns"
            sessions.isNotEmpty() -> "Focus lock is active"
            else -> "Focus lock countdown"
        }
        val text = buildList {
            if (sessions.isNotEmpty()) {
                add("${sessions.size} active lock${if (sessions.size == 1) "" else "s"}")
            }
            if (scheduledLocks.isNotEmpty()) {
                add("${scheduledLocks.size} countdown${if (scheduledLocks.size == 1) "" else "s"}")
            }
        }.joinToString(" + ")
        val nextEnd = sessions.mapNotNull { it.session.expiresAt }.minOrNull()
        val nextStart = scheduledLocks.minByOrNull { it.scheduledLock.startsAt }?.scheduledLock?.startsAt
        val chronometerTarget = nextEnd ?: nextStart
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_blocker)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(LongArray(0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (chronometerTarget != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setWhen(chronometerTarget)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            }
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active focus sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
