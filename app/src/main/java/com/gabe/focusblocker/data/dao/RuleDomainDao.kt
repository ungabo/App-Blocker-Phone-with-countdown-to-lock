package com.gabe.focusblocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gabe.focusblocker.data.entity.RuleDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDomainDao {
    @Query("SELECT * FROM rule_domains WHERE ruleSetId = :ruleSetId ORDER BY domain ASC")
    fun observeForRuleSet(ruleSetId: Long): Flow<List<RuleDomainEntity>>

    @Query("SELECT * FROM rule_domains WHERE ruleSetId = :ruleSetId ORDER BY domain ASC")
    suspend fun getForRuleSet(ruleSetId: Long): List<RuleDomainEntity>

    @Query("SELECT * FROM rule_domains WHERE ruleSetId IN (:ruleSetIds)")
    suspend fun getForRuleSetIds(ruleSetIds: List<Long>): List<RuleDomainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(domains: List<RuleDomainEntity>)

    @Query("DELETE FROM rule_domains WHERE ruleSetId = :ruleSetId")
    suspend fun deleteForRuleSet(ruleSetId: Long)
}

