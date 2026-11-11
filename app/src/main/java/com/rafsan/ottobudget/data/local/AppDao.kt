package com.rafsan.ottobudget.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM wallets ORDER BY id ASC")
    fun observeWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY id ASC")
    suspend fun getAllWalletsOnce(): List<WalletEntity>

    @Query("SELECT * FROM categories ORDER BY kind ASC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind ASC, name ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM keyword_rules ORDER BY priority DESC, LENGTH(keyword) DESC")
    fun observeKeywordRules(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules ORDER BY priority DESC, LENGTH(keyword) DESC")
    suspend fun getAllKeywordRulesOnce(): List<KeywordRuleEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC, updatedAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, updatedAt DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM vault_goals ORDER BY createdAt ASC")
    fun observeVaultGoals(): Flow<List<VaultGoalEntity>>

    @Query("SELECT * FROM vault_goals ORDER BY createdAt ASC")
    suspend fun getAllVaultGoalsOnce(): List<VaultGoalEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY dayOfMonth ASC")
    fun observeRecurringRules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY dayOfMonth ASC")
    suspend fun getAllRecurringRulesOnce(): List<RecurringRuleEntity>

    @Query("SELECT * FROM pending_sms_detections ORDER BY id DESC")
    fun observePendingSmsDetections(): Flow<List<PendingSmsDetectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallet(wallet: WalletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<WalletEntity>)

    @Query("UPDATE wallets SET initialBalance = :newInitialBalance, accentHex = :newAccentHex WHERE name = :walletName")
    suspend fun updateWalletBalanceAndColor(walletName: String, newInitialBalance: Double, newAccentHex: Long)

    @Query("UPDATE wallets SET initialBalance = 0.0")
    suspend fun resetAllWalletBalances()

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun deleteWalletById(walletId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET name = :newName, iconName = :newIcon WHERE id = :catId")
    suspend fun updateCategoryNameAndIcon(catId: String, newName: String, newIcon: String)

    @Query("UPDATE keyword_rules SET category = :newCategoryName WHERE category = :oldCategoryName")
    suspend fun renameCategoryInRules(oldCategoryName: String, newCategoryName: String)

    @Query("UPDATE transactions SET category = :newCategoryName WHERE category = :oldCategoryName")
    suspend fun renameCategoryInTransactions(oldCategoryName: String, newCategoryName: String)

    @Query("DELETE FROM categories WHERE id = :catId")
    suspend fun deleteCategoryById(catId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKeywordRule(rule: KeywordRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywordRules(rules: List<KeywordRuleEntity>)

    @Query("DELETE FROM keyword_rules WHERE keyword = :keyword AND category = :category")
    suspend fun deleteKeywordRule(keyword: String, category: String)

    @Query("DELETE FROM keyword_rules WHERE category = :categoryName")
    suspend fun deleteRulesForCategory(categoryName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txs: List<TransactionEntity>)

    @Delete
    suspend fun deleteTransaction(tx: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // Vault Goals DAO
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVaultGoal(goal: VaultGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultGoals(goals: List<VaultGoalEntity>)

    @Query("DELETE FROM vault_goals WHERE id = :goalId")
    suspend fun deleteVaultGoalById(goalId: String)

    @Query("SELECT * FROM transactions WHERE toWallet = :goalId AND (type = 'VAULT_LOCK' OR type = 'VAULT_RELEASE')")
    suspend fun getVaultAllocationsForGoalOnce(goalId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE toWallet = :goalId AND (type = 'VAULT_LOCK' OR type = 'VAULT_RELEASE')")
    suspend fun clearVaultAllocationsForGoal(goalId: String)

    @Query("DELETE FROM transactions WHERE toWallet = :goalId AND wallet = :walletName AND (type = 'VAULT_LOCK' OR type = 'VAULT_RELEASE')")
    suspend fun clearVaultAllocationsForGoalAndWallet(goalId: String, walletName: String)

    // Scheduler / Recurring Rules DAO
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringRule(rule: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRules(rules: List<RecurringRuleEntity>)

    @Query("UPDATE recurring_rules SET lastConfirmedPeriod = :period WHERE id = :ruleId")
    suspend fun markRecurringRuleConfirmed(ruleId: String, period: String)

    @Query("UPDATE recurring_rules SET enabled = :enabled WHERE id = :ruleId")
    suspend fun setRecurringRuleEnabled(ruleId: String, enabled: Boolean)

    @Query("DELETE FROM recurring_rules WHERE id = :ruleId")
    suspend fun deleteRecurringRuleById(ruleId: String)

    // Pending SMS / Notification Auto-Parser Detections DAO
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSmsDetection(detection: PendingSmsDetectionEntity)

    @Query("DELETE FROM pending_sms_detections WHERE id = :detectionId")
    suspend fun deletePendingSmsDetection(detectionId: String)

    @Query("DELETE FROM pending_sms_detections")
    suspend fun clearAllPendingSmsDetections()
}
