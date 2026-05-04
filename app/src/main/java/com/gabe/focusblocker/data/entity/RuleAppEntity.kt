package com.gabe.focusblocker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_apps",
    indices = [Index(value = ["ruleSetId", "packageName"], unique = true)]
)
data class RuleAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleSetId: Long,
    val packageName: String,
    val createdAt: Long
)

