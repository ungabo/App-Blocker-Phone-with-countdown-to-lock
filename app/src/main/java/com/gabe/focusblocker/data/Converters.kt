package com.gabe.focusblocker.data

import androidx.room.TypeConverter
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.data.entity.SessionSource

class Converters {
    @TypeConverter
    fun toRuleMode(value: String): RuleMode = RuleMode.valueOf(value)

    @TypeConverter
    fun fromRuleMode(value: RuleMode): String = value.name

    @TypeConverter
    fun toSessionSource(value: String): SessionSource = SessionSource.valueOf(value)

    @TypeConverter
    fun fromSessionSource(value: SessionSource): String = value.name
}

