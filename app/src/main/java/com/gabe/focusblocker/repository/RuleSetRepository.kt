package com.gabe.focusblocker.repository

import androidx.room.withTransaction
import com.gabe.focusblocker.data.AppDatabase
import com.gabe.focusblocker.data.entity.RuleAppEntity
import com.gabe.focusblocker.data.entity.RuleDomainEntity
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.model.RuleSetSummary
import com.gabe.focusblocker.util.DomainUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class RuleSetDetail(
    val ruleSet: RuleSetEntity,
    val selectedPackages: Set<String>,
    val domains: List<String>
)

class RuleSetRepository(private val database: AppDatabase) {
    private val ruleSetDao = database.ruleSetDao()
    private val ruleAppDao = database.ruleAppDao()
    private val ruleDomainDao = database.ruleDomainDao()

    fun observeRuleSetSummaries(): Flow<List<RuleSetSummary>> = ruleSetDao.observeRuleSetSummaries()

    fun observeRuleSetDetail(ruleSetId: Long): Flow<RuleSetDetail?> {
        return combine(
            ruleSetDao.observeAll(),
            ruleAppDao.observeForRuleSet(ruleSetId),
            ruleDomainDao.observeForRuleSet(ruleSetId)
        ) { ruleSets, ruleApps, ruleDomains ->
            val ruleSet = ruleSets.firstOrNull { it.id == ruleSetId } ?: return@combine null
            RuleSetDetail(
                ruleSet = ruleSet,
                selectedPackages = ruleApps.map { it.packageName }.toSet(),
                domains = ruleDomains.map { it.domain }
            )
        }
    }

    suspend fun getRuleSetById(ruleSetId: Long): RuleSetEntity? = ruleSetDao.getById(ruleSetId)

    suspend fun seedDefaultsIfEmpty(now: Long) {
        if (ruleSetDao.count() > 0) return

        val defaults = listOf(
            RuleSetEntity(
                name = "Set A - Short Focus Block",
                mode = RuleMode.BLOCK_LIST,
                defaultDurationMinutes = 10,
                createdAt = now,
                updatedAt = now,
                sortOrder = 0,
                isSystemPreset = true
            ),
            RuleSetEntity(
                name = "Set B - Deep Work Block",
                mode = RuleMode.BLOCK_LIST,
                defaultDurationMinutes = 120,
                createdAt = now,
                updatedAt = now,
                sortOrder = 1,
                isSystemPreset = true
            ),
            RuleSetEntity(
                name = "Set C - Essentials Only",
                mode = RuleMode.ALLOW_ONLY,
                defaultDurationMinutes = null,
                createdAt = now,
                updatedAt = now,
                sortOrder = 2,
                isSystemPreset = true
            )
        )

        defaults.forEach { ruleSetDao.insert(it) }
    }

    suspend fun saveRuleSet(
        existingId: Long?,
        name: String,
        mode: RuleMode,
        defaultDurationMinutes: Int?,
        selectedPackages: Set<String>,
        domains: List<String>,
        isSystemPreset: Boolean = false
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val ruleSetId = if (existingId == null) {
            val sortOrder = ruleSetDao.count()
            ruleSetDao.insert(
                RuleSetEntity(
                    name = name,
                    mode = mode,
                    defaultDurationMinutes = defaultDurationMinutes,
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = sortOrder,
                    isSystemPreset = isSystemPreset
                )
            )
        } else {
            val existing = ruleSetDao.getById(existingId)
                ?: error("Missing rule set $existingId")
            ruleSetDao.update(
                existing.copy(
                    name = name,
                    mode = mode,
                    defaultDurationMinutes = defaultDurationMinutes,
                    updatedAt = now
                )
            )
            existingId
        }

        ruleAppDao.deleteForRuleSet(ruleSetId)
        if (selectedPackages.isNotEmpty()) {
            ruleAppDao.insertAll(
                selectedPackages.map { packageName ->
                    RuleAppEntity(ruleSetId = ruleSetId, packageName = packageName, createdAt = now)
                }
            )
        }

        ruleDomainDao.deleteForRuleSet(ruleSetId)
        val normalizedDomains = domains
            .map { DomainUtils.normalizeDomain(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedDomains.isNotEmpty()) {
            ruleDomainDao.insertAll(
                normalizedDomains.map { domain ->
                    RuleDomainEntity(ruleSetId = ruleSetId, domain = domain, createdAt = now)
                }
            )
        }

        ruleSetId
    }

    suspend fun duplicateRuleSet(ruleSetId: Long) {
        val detail = observeRuleSetDetailOnce(ruleSetId) ?: return
        saveRuleSet(
            existingId = null,
            name = "${detail.ruleSet.name} Copy",
            mode = detail.ruleSet.mode,
            defaultDurationMinutes = detail.ruleSet.defaultDurationMinutes,
            selectedPackages = detail.selectedPackages,
            domains = detail.domains
        )
    }

    suspend fun deleteRuleSet(ruleSetId: Long) = database.withTransaction {
        val ruleSet = ruleSetDao.getById(ruleSetId) ?: return@withTransaction
        ruleAppDao.deleteForRuleSet(ruleSetId)
        ruleDomainDao.deleteForRuleSet(ruleSetId)
        database.activeSessionDao().disableSessionsForRuleSet(ruleSetId)
        ruleSetDao.delete(ruleSet)
    }

    suspend fun observeRuleSetDetailOnce(ruleSetId: Long): RuleSetDetail? {
        val ruleSet = ruleSetDao.getById(ruleSetId) ?: return null
        val apps = ruleAppDao.getForRuleSet(ruleSetId).map { it.packageName }.toSet()
        val domains = ruleDomainDao.getForRuleSet(ruleSetId).map { it.domain }
        return RuleSetDetail(ruleSet = ruleSet, selectedPackages = apps, domains = domains)
    }
}
