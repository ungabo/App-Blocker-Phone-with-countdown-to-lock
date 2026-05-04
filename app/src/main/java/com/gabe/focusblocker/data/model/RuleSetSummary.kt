package com.gabe.focusblocker.data.model

import androidx.room.Embedded
import com.gabe.focusblocker.data.entity.RuleSetEntity

data class RuleSetSummary(
    @Embedded val ruleSet: RuleSetEntity,
    val appCount: Int,
    val domainCount: Int
)

