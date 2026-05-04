package com.gabe.focusblocker.data.model

import androidx.room.Embedded
import com.gabe.focusblocker.data.entity.ScheduledLockEntity

data class ScheduledLockWithRuleSet(
    @Embedded val scheduledLock: ScheduledLockEntity,
    val ruleSetName: String
)

