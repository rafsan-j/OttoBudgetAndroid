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
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val amount: Double,
    val category: String,
    val wallet: String,
    val toWallet: String?,
    val description: String,
    val isMajor: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
