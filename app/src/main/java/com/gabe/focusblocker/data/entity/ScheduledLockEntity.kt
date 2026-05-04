package com.gabe.focusblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_locks")
data class ScheduledLockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleSetId: Long,
    val startsAt: Long,
    val durationMinutes: Int,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val source: SessionSource
)

