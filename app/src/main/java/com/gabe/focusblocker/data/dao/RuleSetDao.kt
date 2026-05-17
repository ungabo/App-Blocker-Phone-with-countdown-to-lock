package com.gabe.focusblocker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gabe.focusblocker.data.entity.RuleSetEntity
import com.gabe.focusblocker.data.model.RuleSetSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSetDao {
    @Query(
        """
        SELECT rs.*,
               (SELECT COUNT(*) FROM rule_apps ra WHERE ra.ruleSetId = rs.id) AS appCount,
               (SELECT COUNT(*) FROM rule_domains rd WHERE rd.ruleSetId = rs.id) AS domainCount
        FROM rule_sets rs
        ORDER BY rs.sortOrder ASC, rs.updatedAt DESC
        """
    )
    fun observeRuleSetSummaries(): Flow<List<RuleSetSummary>>

    @Query("SELECT * FROM rule_sets ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAll(): Flow<List<RuleSetEntity>>

    @Query("SELECT * FROM rule_sets ORDER BY sortOrder ASC, updatedAt DESC")
    suspend fun getAllOrdered(): List<RuleSetEntity>

    @Query("SELECT * FROM rule_sets WHERE showInWidget = 1 ORDER BY sortOrder ASC, updatedAt DESC LIMIT :limit")
    suspend fun getWidgetRuleSets(limit: Int): List<RuleSetEntity>

    @Query(
        """
        SELECT * FROM rule_sets
        WHERE showInWidget = 1 AND id != :excludeId
        ORDER BY COALESCE(lastUsedAt, 0) ASC, sortOrder ASC, updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getLeastRecentlyUsedWidgetRuleSets(excludeId: Long, limit: Int): List<RuleSetEntity>

    @Query("SELECT * FROM rule_sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RuleSetEntity?

    @Query("SELECT * FROM rule_sets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<RuleSetEntity>

    @Query("SELECT COUNT(*) FROM rule_sets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ruleSet: RuleSetEntity): Long

    @Update
    suspend fun update(ruleSet: RuleSetEntity)

    @Query("UPDATE rule_sets SET showInWidget = :showInWidget, updatedAt = :now WHERE id = :id")
    suspend fun setShowInWidget(id: Long, showInWidget: Boolean, now: Long)

    @Query("UPDATE rule_sets SET lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Delete
    suspend fun delete(ruleSet: RuleSetEntity)
}
