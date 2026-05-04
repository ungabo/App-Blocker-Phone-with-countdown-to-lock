package com.gabe.focusblocker.data.model

import androidx.room.Embedded
import com.gabe.focusblocker.data.entity.ActiveSessionEntity

data class ActiveSessionWithRuleSet(
    @Embedded val session: ActiveSessionEntity,
    val ruleSetName: String
)

