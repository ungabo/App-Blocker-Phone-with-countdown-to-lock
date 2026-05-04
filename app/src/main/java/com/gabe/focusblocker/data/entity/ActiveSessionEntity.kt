package com.gabe.focusblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleSetId: Long,
    val mode: RuleMode,
    val startedAt: Long,
    val expiresAt: Long?,
    val enabled: Boolean = true,
    val source: SessionSource
)

