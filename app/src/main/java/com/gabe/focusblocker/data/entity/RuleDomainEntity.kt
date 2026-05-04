package com.gabe.focusblocker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_domains",
    indices = [Index(value = ["ruleSetId", "domain"], unique = true)]
)
data class RuleDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleSetId: Long,
    val domain: String,
    val createdAt: Long
)

