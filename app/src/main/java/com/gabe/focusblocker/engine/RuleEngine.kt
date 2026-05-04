package com.gabe.focusblocker.engine

import com.gabe.focusblocker.data.entity.ActiveSessionEntity
import com.gabe.focusblocker.data.entity.RuleAppEntity
import com.gabe.focusblocker.data.entity.RuleDomainEntity
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.util.DomainUtils

data class BlockingDecision(
    val blocked: Boolean,
    val mode: RuleMode? = null,
    val ruleSetNames: List<String> = emptyList(),
    val remainingMs: Long? = null
)

class RuleEngine {
    fun evaluatePackage(
        packageName: String,
        now: Long,
        activeSessions: List<ActiveSessionEntity>,
        ruleSets: List<RuleSetEntity>,
        ruleApps: List<RuleAppEntity>,
        emergencyAllowlist: Set<String>
    ): BlockingDecision {
        if (packageName in emergencyAllowlist) {
            return BlockingDecision(blocked = false)
        }

        val activeById = ruleSets.associateBy { it.id }
        val allowOnlySessions = activeSessions.filter { it.mode == RuleMode.ALLOW_ONLY }
        if (allowOnlySessions.isNotEmpty()) {
            val allowedPackages = ruleApps
                .filter { it.ruleSetId in allowOnlySessions.map(ActiveSessionEntity::ruleSetId).toSet() }
                .map { it.packageName }
                .toSet()

            if (packageName in allowedPackages) {
                return BlockingDecision(blocked = false)
            }

            return BlockingDecision(
                blocked = true,
                mode = RuleMode.ALLOW_ONLY,
                ruleSetNames = allowOnlySessions.mapNotNull { activeById[it.ruleSetId]?.name }.distinct(),
                remainingMs = remainingForSessions(now, allowOnlySessions)
            )
        }

        val matches = activeSessions.filter { session ->
            session.mode == RuleMode.BLOCK_LIST && ruleApps.any {
                it.ruleSetId == session.ruleSetId && it.packageName == packageName
            }
        }

        if (matches.isEmpty()) {
            return BlockingDecision(blocked = false)
        }

        return BlockingDecision(
            blocked = true,
            mode = RuleMode.BLOCK_LIST,
            ruleSetNames = matches.mapNotNull { activeById[it.ruleSetId]?.name }.distinct(),
            remainingMs = remainingForSessions(now, matches)
        )
    }

    fun evaluateDomain(
        domain: String,
        now: Long,
        activeSessions: List<ActiveSessionEntity>,
        ruleSets: List<RuleSetEntity>,
        ruleDomains: List<RuleDomainEntity>
    ): BlockingDecision {
        val activeById = ruleSets.associateBy { it.id }
        val allowOnlySessions = activeSessions.filter { it.mode == RuleMode.ALLOW_ONLY }
        if (allowOnlySessions.isNotEmpty()) {
            val allowedDomains = ruleDomains
                .filter { it.ruleSetId in allowOnlySessions.map(ActiveSessionEntity::ruleSetId).toSet() }
                .map { it.domain }
                .toSet()

            return if (DomainUtils.matchesAny(domain, allowedDomains)) {
                BlockingDecision(blocked = false)
            } else {
                BlockingDecision(
                    blocked = true,
                    mode = RuleMode.ALLOW_ONLY,
                    ruleSetNames = allowOnlySessions.mapNotNull { activeById[it.ruleSetId]?.name }.distinct(),
                    remainingMs = remainingForSessions(now, allowOnlySessions)
                )
            }
        }

        val matchingSessions = activeSessions.filter { session ->
            session.mode == RuleMode.BLOCK_LIST && ruleDomains.any {
                it.ruleSetId == session.ruleSetId && DomainUtils.matchesDomain(domain, it.domain)
            }
        }

        return if (matchingSessions.isEmpty()) {
            BlockingDecision(blocked = false)
        } else {
            BlockingDecision(
                blocked = true,
                mode = RuleMode.BLOCK_LIST,
                ruleSetNames = matchingSessions.mapNotNull { activeById[it.ruleSetId]?.name }.distinct(),
                remainingMs = remainingForSessions(now, matchingSessions)
            )
        }
    }

    private fun remainingForSessions(now: Long, sessions: List<ActiveSessionEntity>): Long? {
        val finiteExpirations = sessions.mapNotNull { it.expiresAt }
        val nearest = finiteExpirations.minOrNull() ?: return null
        return (nearest - now).coerceAtLeast(0L)
    }
}

