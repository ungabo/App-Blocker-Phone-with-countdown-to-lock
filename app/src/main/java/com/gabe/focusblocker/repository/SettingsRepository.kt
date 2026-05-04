package com.gabe.focusblocker.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "focus_blocker_settings")

data class AppSettings(
    val blockSettingsApp: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinLockedUntil: Long = 0L,
    val temporaryUnlockEnabled: Boolean = false,
    val temporaryUnlockMinutes: Int = 5
)

class SettingsRepository(private val context: Context) {
    object Keys {
        val blockSettingsApp = booleanPreferencesKey("block_settings_app")
        val pinEnabled = booleanPreferencesKey("pin_enabled")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinFailedAttempts = intPreferencesKey("pin_failed_attempts")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
        val temporaryUnlockEnabled = booleanPreferencesKey("temporary_unlock_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            blockSettingsApp = prefs[Keys.blockSettingsApp] ?: false,
            pinEnabled = prefs[Keys.pinEnabled] ?: false,
            pinLockedUntil = prefs[Keys.pinLockedUntil] ?: 0L,
            temporaryUnlockEnabled = prefs[Keys.temporaryUnlockEnabled] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBlockSettingsApp(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.blockSettingsApp] = enabled
        }
    }

    suspend fun readPinState(): PinState {
        val prefs = context.dataStore.data.first()
        return PinState(
            enabled = prefs[Keys.pinEnabled] ?: false,
            salt = prefs[Keys.pinSalt],
            hash = prefs[Keys.pinHash],
            failedAttempts = prefs[Keys.pinFailedAttempts] ?: 0,
            lockedUntil = prefs[Keys.pinLockedUntil] ?: 0L
        )
    }

    suspend fun savePin(salt: String, hash: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.pinEnabled] = true
            prefs[Keys.pinSalt] = salt
            prefs[Keys.pinHash] = hash
            prefs[Keys.pinFailedAttempts] = 0
            prefs[Keys.pinLockedUntil] = 0L
        }
    }

    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs[Keys.pinEnabled] = false
            prefs.remove(Keys.pinSalt)
            prefs.remove(Keys.pinHash)
            prefs[Keys.pinFailedAttempts] = 0
            prefs[Keys.pinLockedUntil] = 0L
        }
    }

    suspend fun recordPinFailure(failedAttempts: Int, lockedUntil: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.pinFailedAttempts] = failedAttempts
            prefs[Keys.pinLockedUntil] = lockedUntil
        }
    }

    suspend fun resetPinFailures() {
        context.dataStore.edit { prefs ->
            prefs[Keys.pinFailedAttempts] = 0
            prefs[Keys.pinLockedUntil] = 0L
        }
    }
}

data class PinState(
    val enabled: Boolean,
    val salt: String?,
    val hash: String?,
    val failedAttempts: Int,
    val lockedUntil: Long
)
