package com.gabe.focusblocker.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.gabe.focusblocker.repository.SettingsRepository

class EmergencyAllowlist(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend fun allowedPackages(): Set<String> {
        val settings = settingsRepository.current()
        val packages = buildSet {
            add(context.packageName)
            add("com.android.systemui")
            add("android")

            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(homeIntent, 0)
                .mapTo(this) { it.activityInfo.packageName }

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager?.defaultDialerPackage?.let(::add)

            Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)

            val inputMethod =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    ?.substringBefore("/")
            inputMethod?.let(::add)

            if (!settings.blockSettingsApp) {
                add("com.android.settings")
            }
        }

        return packages
    }
}

