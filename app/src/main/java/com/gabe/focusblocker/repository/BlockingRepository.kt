package com.gabe.focusblocker.repository

import android.content.Context
import com.gabe.focusblocker.data.AppDatabase
import com.gabe.focusblocker.engine.BlockingDecision
import com.gabe.focusblocker.engine.EmergencyAllowlist
import com.gabe.focusblocker.engine.RuleEngine
import com.gabe.focusblocker.util.PackageUtils

class BlockingRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val emergencyAllowlist: EmergencyAllowlist,
    private val ruleEngine: RuleEngine
) {
    suspend fun cleanupExpiredSessions(now: Long = System.currentTimeMillis()) {
        database.activeSessionDao().disableExpiredSessions(now)
    }

    suspend fun evaluatePackage(packageName: String): BlockingDecision {
        val now = System.currentTimeMillis()
        cleanupExpiredSessions(now)

        if (PackageUtils.isSystemPackage(context, packageName)) {
            return BlockingDecision(blocked = false)
        }

        val activeSessions = database.activeSessionDao().getEnabledSessions()
            .filter { it.expiresAt == null || it.expiresAt > now }
        if (activeSessions.isEmpty()) {
            return BlockingDecision(blocked = false)
        }

        val ruleSetIds = activeSessions.map { it.ruleSetId }.distinct()
        val ruleSets = database.ruleSetDao().getByIds(ruleSetIds)
        val ruleApps = database.ruleAppDao().getForRuleSetIds(ruleSetIds)
        val allowedPackages = emergencyAllowlist.allowedPackages()

        return ruleEngine.evaluatePackage(
            packageName = packageName,
            now = now,
            activeSessions = activeSessions,
            ruleSets = ruleSets,
            ruleApps = ruleApps,
            emergencyAllowlist = allowedPackages
        )
    }

    suspend fun evaluateDomain(domain: String): BlockingDecision {
        val now = System.currentTimeMillis()
        cleanupExpiredSessions(now)

        val activeSessions = database.activeSessionDao().getEnabledSessions()
            .filter { it.expiresAt == null || it.expiresAt > now }
        if (activeSessions.isEmpty()) {
            return BlockingDecision(blocked = false)
        }

        val ruleSetIds = activeSessions.map { it.ruleSetId }.distinct()
        val ruleSets = database.ruleSetDao().getByIds(ruleSetIds)
        val ruleDomains = database.ruleDomainDao().getForRuleSetIds(ruleSetIds)

        return ruleEngine.evaluateDomain(
            domain = domain,
            now = now,
            activeSessions = activeSessions,
            ruleSets = ruleSets,
            ruleDomains = ruleDomains
        )
    }
}
