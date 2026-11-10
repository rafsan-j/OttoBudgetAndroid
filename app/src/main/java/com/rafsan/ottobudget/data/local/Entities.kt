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

/**
 * Transaction types:
 * - "EXPENSE"
 * - "INCOME"
 * - "TRANSFER"
 * - "VAULT_LOCK" (Subtracts from `wallet` spendable balance and assigns to Vault Goal ID stored in `toWallet`, never counts as an expense!)
 * - "VAULT_RELEASE" (Releases funds from Vault Goal ID stored in `toWallet` back into `wallet` spendable balance!)
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,
    val amount: Double,
    val category: String,
    val wallet: String,
    val toWallet: String?,
    val description: String,
    val isMajor: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

/**
 * Separate Savings Vault Goal with Wallet-Specific Criteria:
 * - `allowedWallets`: Comma-separated list of wallets allowed to fund this goal (e.g. "bKash,Bank Card,Cash").
 * - `minWalletFloor`: Minimum spendable balance that must remain in a wallet after assigning money to this goal.
 * - `autoRuleType`: "MANUAL", "INCOME_PERCENT", or "EXCESS_SWEEP".
 */
@Entity(tableName = "vault_goals")
data class VaultGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetAmount: Double,
    val allowedWallets: String, // e.g., "bKash,Bank Card,Cash"
    val minWalletFloor: Double, // e.g., 500.0 (keep at least ৳500 spendable in source wallet)
    val autoRuleType: String = "MANUAL", // "MANUAL", "INCOME_10_PCT", "ROUND_UP"
    val iconName: String = "vault",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Confirmation-Based Recurring Expense / Income Rule:
 * - Never modifies wallet balances automatically!
 * - Surfaces a Pending Confirmation banner on the Home screen on or after `dayOfMonth` each month
 *   (until `lastConfirmedPeriod == currentYearMonth`), and schedules a local notification at `notifyHour:notifyMinute`.
 */
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
    val lastConfirmedPeriod: String = "" // e.g. "2026-11"
)

/**
 * Opt-In On-Device SMS / Notification Auto-Parser Detection Queue:
 * - Holds detected bKash, Nagad, Rocket, and Bangladeshi Bank alerts for 1-tap user confirmation on the Home screen.
 */
@Entity(tableName = "pending_sms_detections")
data class PendingSmsDetectionEntity(
    @PrimaryKey val id: String,
    val provider: String, // "bKash", "Nagad", "Rocket", "Bank Card"
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
