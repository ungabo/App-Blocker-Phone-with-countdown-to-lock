package com.gabe.focusblocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gabe.focusblocker.data.entity.RuleAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleAppDao {
    @Query("SELECT * FROM rule_apps WHERE ruleSetId = :ruleSetId ORDER BY packageName ASC")
    fun observeForRuleSet(ruleSetId: Long): Flow<List<RuleAppEntity>>

    @Query("SELECT * FROM rule_apps WHERE ruleSetId = :ruleSetId ORDER BY packageName ASC")
    suspend fun getForRuleSet(ruleSetId: Long): List<RuleAppEntity>

    @Query("SELECT * FROM rule_apps WHERE ruleSetId IN (:ruleSetIds)")
    suspend fun getForRuleSetIds(ruleSetIds: List<Long>): List<RuleAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<RuleAppEntity>)

    @Query("DELETE FROM rule_apps WHERE ruleSetId = :ruleSetId")
    suspend fun deleteForRuleSet(ruleSetId: Long)
}

