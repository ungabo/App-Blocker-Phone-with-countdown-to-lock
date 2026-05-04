package com.gabe.focusblocker.util

import kotlin.math.max

object TimeUtils {
    fun now(): Long = System.currentTimeMillis()

    fun formatRemainingTime(remainingMs: Long?): String {
        if (remainingMs == null) return "Until stopped"
        val totalSeconds = max(0L, remainingMs / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun minutesToLabel(minutes: Int?): String =
        minutes?.let { "${it} min" } ?: "Manual"
}

