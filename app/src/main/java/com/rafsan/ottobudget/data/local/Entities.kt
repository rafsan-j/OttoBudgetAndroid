package com.rafsan.ottobudget.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupType: String,
    val initialBalance: Double,
    val numberTag: String,
    val accentHex: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String, // "EXPENSE" or "INCOME"
    val colorHex: Long,
    val iconName: String
)

@Entity(tableName = "keyword_rules", primaryKeys = ["keyword", "category"])
data class KeywordRuleEntity(
    val keyword: String,
    val category: String,
    val kind: String, // "EXPENSE" or "INCOME"
    val walletHint: String = "Cash",
    val priority: Int = 80
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER", "VAULT_LOCK", "VAULT_RELEASE"
    val amount: Double,
    val category: String,
    val wallet: String,
    val toWallet: String?,
    val description: String,
    val isMajor: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "vault_goals")
data class VaultGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetAmount: Double,
    val allowedWallets: String,
    val minWalletFloor: Double,
    val autoRuleType: String = "MANUAL",
    val iconName: String = "vault",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "EXPENSE" or "INCOME"
    val amount: Double,
    val category: String,
    val wallet: String,
    val dayOfMonth: Int, // 1..31
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val enabled: Boolean = true,
    val lastConfirmedPeriod: String = ""
)

@Entity(tableName = "pending_sms_detections")
data class PendingSmsDetectionEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val amount: Double,
    val fee: Double,
    val trxId: String,
    val wallet: String,
    val toWallet: String?,
    val category: String,
    val summaryText: String,
    val rawMessage: String,
    val detectedDate: String
)
