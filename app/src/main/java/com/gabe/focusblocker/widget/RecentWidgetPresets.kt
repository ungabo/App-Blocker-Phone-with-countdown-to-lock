package com.gabe.focusblocker.widget

import android.content.Context
import com.gabe.focusblocker.data.entity.RuleSetEntity

data class RecentWidgetPreset(
    val ruleSetId: Long,
    val ruleSetName: String,
    val delayMinutes: Int,
    val durationMinutes: Int
)

object RecentWidgetPresets {
    private const val PREFS_NAME = "focus_widget_recent_presets"
    private const val MAX_PRESETS = 2

    fun record(
        context: Context,
        ruleSet: RuleSetEntity,
        delayMinutes: Int,
        durationMinutes: Int
    ) {
        val newPreset = RecentWidgetPreset(
            ruleSetId = ruleSet.id,
            ruleSetName = ruleSet.name,
            delayMinutes = delayMinutes.coerceAtLeast(0),
            durationMinutes = durationMinutes.coerceAtLeast(1)
        )
        val updated = (listOf(newPreset) + get(context).filterNot {
            it.ruleSetId == newPreset.ruleSetId &&
                it.delayMinutes == newPreset.delayMinutes &&
                it.durationMinutes == newPreset.durationMinutes
        }).take(MAX_PRESETS)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            updated.forEachIndexed { index, preset ->
                putLong(key(index, "rule_set_id"), preset.ruleSetId)
                putString(key(index, "rule_set_name"), preset.ruleSetName)
                putInt(key(index, "delay_minutes"), preset.delayMinutes)
                putInt(key(index, "duration_minutes"), preset.durationMinutes)
            }
            for (index in updated.size until MAX_PRESETS) {
                remove(key(index, "rule_set_id"))
                remove(key(index, "rule_set_name"))
                remove(key(index, "delay_minutes"))
                remove(key(index, "duration_minutes"))
            }
        }.apply()
    }

    fun get(context: Context): List<RecentWidgetPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (0 until MAX_PRESETS).mapNotNull { index ->
            val ruleSetId = prefs.getLong(key(index, "rule_set_id"), -1L)
            if (ruleSetId < 0L) return@mapNotNull null
            RecentWidgetPreset(
                ruleSetId = ruleSetId,
                ruleSetName = prefs.getString(key(index, "rule_set_name"), null).orEmpty(),
                delayMinutes = prefs.getInt(key(index, "delay_minutes"), 0),
                durationMinutes = prefs.getInt(key(index, "duration_minutes"), 1)
            )
        }
    }

    fun labelFor(preset: RecentWidgetPreset): String {
        return "${preset.ruleSetName.shortWidgetName()} ${preset.delayLabel()} for ${preset.durationMinutes}m"
    }

    private fun RecentWidgetPreset.delayLabel(): String {
        return if (delayMinutes == 0) "now" else "in ${delayMinutes}m"
    }

    fun String.shortWidgetName(maxLength: Int = 16): String {
        val compact = trim().replace(Regex("\\s+"), " ")
        return if (compact.length <= maxLength) compact else compact.take(maxLength - 1).trimEnd() + "..."
    }

    private fun key(index: Int, suffix: String): String = "preset_${index}_$suffix"
}
