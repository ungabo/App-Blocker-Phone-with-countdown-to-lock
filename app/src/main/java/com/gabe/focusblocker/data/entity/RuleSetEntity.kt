package com.gabe.focusblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule_sets")
data class RuleSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: RuleMode,
    val defaultDurationMinutes: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int = 0,
    val isSystemPreset: Boolean = false
)

