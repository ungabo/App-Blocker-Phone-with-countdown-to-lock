package com.gabe.focusblocker.engine

import android.util.Base64
import com.gabe.focusblocker.repository.SettingsRepository
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinCheckResult {
    data object Success : PinCheckResult()
    data object Disabled : PinCheckResult()
    data class Failure(val attemptsRemaining: Int) : PinCheckResult()
    data class Locked(val remainingMs: Long) : PinCheckResult()
}

class PinManager(private val settingsRepository: SettingsRepository) {
    private val random = SecureRandom()

    suspend fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = hashPin(pin, salt)
        settingsRepository.savePin(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            hash = Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    suspend fun clearPin() {
        settingsRepository.clearPin()
    }

    suspend fun verify(pin: String): PinCheckResult {
        val state = settingsRepository.readPinState()
        if (!state.enabled) return PinCheckResult.Disabled

        val now = System.currentTimeMillis()
        if (state.lockedUntil > now) {
            return PinCheckResult.Locked(state.lockedUntil - now)
        }

        val salt = state.salt?.let { Base64.decode(it, Base64.NO_WRAP) }
        val expectedHash = state.hash?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || expectedHash == null) return PinCheckResult.Failure(MAX_ATTEMPTS)

        val suppliedHash = hashPin(pin, salt)
        if (suppliedHash.contentEquals(expectedHash)) {
            settingsRepository.resetPinFailures()
            return PinCheckResult.Success
        }

        val failures = state.failedAttempts + 1
        val lockedUntil = if (failures >= MAX_ATTEMPTS) now + LOCKOUT_MS else 0L
        settingsRepository.recordPinFailure(
            failedAttempts = if (lockedUntil > 0L) 0 else failures,
            lockedUntil = lockedUntil
        )
        return if (lockedUntil > 0L) {
            PinCheckResult.Locked(LOCKOUT_MS)
        } else {
            PinCheckResult.Failure(MAX_ATTEMPTS - failures)
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }
}
