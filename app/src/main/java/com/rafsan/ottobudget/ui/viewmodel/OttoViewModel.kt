package com.rafsan.ottobudget.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rafsan.ottobudget.data.local.*
import com.rafsan.ottobudget.data.sync.SupabaseSyncManager
import com.rafsan.ottobudget.domain.RuleEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OttoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    val syncManager = SupabaseSyncManager(application, dao)

    val wallets = dao.observeWallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val keywordRules = dao.observeKeywordRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Persisted Dark Mode loaded directly from SharedPreferences on app launch!
    val isDarkTheme = MutableStateFlow(syncManager.isDarkMode)
    val showSplashWink = MutableStateFlow(true)
    val showOnboarding = MutableStateFlow(!syncManager.hasSeenOnboarding)

    // PIN & Biometric Security StateFlows
    val isPinLockEnabled = MutableStateFlow(syncManager.pinLockEnabled)
    val isBiometricEnabled = MutableStateFlow(syncManager.biometricUnlockEnabled)
    val isAppLocked = MutableStateFlow(syncManager.pinLockEnabled)

    val userName = MutableStateFlow(syncManager.userName)
    val monthlyBudgetTarget = MutableStateFlow(syncManager.monthlyBudget)
    val excludeMajorGlobal = MutableStateFlow(true)
    val autoSyncState = MutableStateFlow(syncManager.autoSyncEnabled)
    val lastSyncedText = MutableStateFlow(syncManager.lastSyncedLabel)
    val statusMessage = MutableStateFlow<String?>(null)

    fun toggleDarkTheme() {
        val next = !isDarkTheme.value
        isDarkTheme.value = next
        syncManager.isDarkMode = next
    }

    fun setDarkThemeExplicit(enabled: Boolean) {
        isDarkTheme.value = enabled
        syncManager.isDarkMode = enabled
    }

    fun saveOrUpdatePin(newPin: String, enableBiometric: Boolean = isBiometricEnabled.value) {
        val clean = newPin.trim()
        if (clean.length != 4 || clean.any { !it.isDigit() }) {
            statusMessage.value = "PIN must be 4 digits"
            return
        }
        syncManager.pinHash = syncManager.hashPinCode(clean)
        syncManager.pinLockEnabled = true
        syncManager.biometricUnlockEnabled = enableBiometric
        isPinLockEnabled.value = true
        isBiometricEnabled.value = enableBiometric
        isAppLocked.value = false
        statusMessage.value = "App PIN Lock enabled"
    }

    fun disablePinLock() {
        syncManager.pinHash = ""
        syncManager.pinLockEnabled = false
        syncManager.biometricUnlockEnabled = false
        isPinLockEnabled.value = false
        isBiometricEnabled.value = false
        isAppLocked.value = false
        statusMessage.value = "App PIN Lock disabled"
    }

    fun toggleBiometricUnlock(enabled: Boolean) {
        if (!isPinLockEnabled.value && enabled) {
            statusMessage.value = "Set a 4-digit backup PIN first"
            return
        }
        syncManager.biometricUnlockEnabled = enabled
        isBiometricEnabled.value = enabled
        statusMessage.value = if (enabled) "Biometric unlock enabled" else "Biometric unlock disabled"
    }

    fun tryUnlockWithPin(enteredPin: String): Boolean {
        val ok = syncManager.verifyPinCode(enteredPin)
        if (ok) {
            isAppLocked.value = false
        }
        return ok
    }

    fun onBiometricUnlockSuccess() {
        isAppLocked.value = false
    }

    fun lockAppNow() {
        if (isPinLockEnabled.value) {
            isAppLocked.value = true
        } else {
            statusMessage.value = "Enable a 4-digit PIN in Settings → Security first"
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun finishSplashWink() {
        showSplashWink.value = false
    }

    fun toggleAutoSync(enabled: Boolean) {
        syncManager.autoSyncEnabled = enabled
        autoSyncState.value = enabled
        if (enabled) triggerAutoSyncIfEnabled()
    }

    private fun triggerAutoSyncIfEnabled() {
        if (syncManager.autoSyncEnabled && syncManager.supabaseUrl.isNotBlank() && syncManager.supabaseAnonKey.isNotBlank()) {
            viewModelScope.launch {
                syncManager.syncVaultToCloud().onSuccess { stamp ->
                    lastSyncedText.value = stamp
                }
            }
        }
    }

    fun updateProfile(newName: String, newBudget: Double) {
        val clean = newName.trim().ifBlank { "User" }
        userName.value = clean
        syncManager.userName = clean
        if (newBudget > 0) {
            monthlyBudgetTarget.value = newBudget
            syncManager.monthlyBudget = newBudget
        }
        statusMessage.value = "Profile saved"
        triggerAutoSyncIfEnabled()
    }

    fun computeWalletBalances(walletList: List<WalletEntity>, txList: List<TransactionEntity>): Map<String, Double> {
        val map = walletList.associate { it.name to it.initialBalance }.toMutableMap()
        txList.forEach { tx ->
            when (tx.type) {
                "EXPENSE" -> map[tx.wallet] = (map[tx.wallet] ?: 0.0) - tx.amount
                "INCOME" -> map[tx.wallet] = (map[tx.wallet] ?: 0.0) + tx.amount
                "TRANSFER" -> {
                    map[tx.wallet] = (map[tx.wallet] ?: 0.0) - tx.amount
                    tx.toWallet?.let { dest -> map[dest] = (map[dest] ?: 0.0) + tx.amount }
                }
            }
        }
        return map
    }

    fun inferBrandColorHex(name: String): Long {
        val lower = name.lowercase().trim()
        return when {
            lower.contains("bkash") || lower.contains("pink") -> 0xFFE11D48L
            lower.contains("nagad") || lower.contains("orange") -> 0xFFF97316L
            lower.contains("rocket") || lower.contains("upay") || lower.contains("purple") -> 0xFF8B5CF6L
            lower.contains("cash") -> 0xFFC8F904L
            lower.contains("rapid") || lower.contains("metro") || lower.contains("transit") || lower.contains("pass") -> 0xFF0284C7L
            lower.contains("saving") || lower.contains("vault") || lower.contains("dps") -> 0xFF059669L
            else -> 0xFF1E293BL
        }
    }

    fun setExactWalletBalanceAndColor(
        wallet: WalletEntity,
        desiredCurrentBalance: Double,
        currentLiveBalance: Double,
        newAccentHex: Long
    ) {
        val delta = desiredCurrentBalance - currentLiveBalance
        val newInitial = wallet.initialBalance + delta
        viewModelScope.launch {
            dao.updateWalletBalanceAndColor(wallet.name, newInitial, newAccentHex)
            statusMessage.value = "${wallet.name} updated"
            triggerAutoSyncIfEnabled()
        }
    }

    fun addWallet(name: String, initialBal: Double, tag: String, customColorHex: Long? = null) {
        if (name.isBlank()) return
        val resolvedColor = customColorHex ?: inferBrandColorHex(name)
        viewModelScope.launch {
            dao.upsertWallet(
                WalletEntity(
                    id = "w_${System.currentTimeMillis()}",
                    name = name.trim(),
                    groupType = "LIQUID",
                    initialBalance = initialBal,
                    numberTag = tag.ifBlank { "Account" },
                    accentHex = resolvedColor
                )
            )
            statusMessage.value = "Added ${name.trim()}"
            triggerAutoSyncIfEnabled()
        }
    }

    fun removeWallet(walletId: String) {
        viewModelScope.launch {
            dao.deleteWalletById(walletId)
            statusMessage.value = "Wallet removed"
            triggerAutoSyncIfEnabled()
        }
    }

    fun completeOnboarding() {
        syncManager.hasSeenOnboarding = true
        showOnboarding.value = false
    }

    fun addTransaction(
        type: String,
        rawAmount: String,
        description: String,
        category: String,
        wallet: String,
        toWallet: String? = null,
        date: String,
        isMajor: Boolean = false
    ) {
        val evaluated = RuleEngine.evaluateMathExpression(rawAmount)
        if (evaluated <= 0.0) return
        viewModelScope.launch {
            dao.upsertTransaction(
                TransactionEntity(
                    id = "tx_${System.currentTimeMillis()}",
                    date = date,
                    type = type,
                    amount = evaluated,
                    category = if (type == "TRANSFER") "Transfer" else category,
                    wallet = wallet,
                    toWallet = if (type == "TRANSFER") toWallet else null,
                    description = description.trim().ifBlank { if (type == "TRANSFER") "$wallet to $toWallet" else category },
                    isMajor = isMajor
                )
            )
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
            triggerAutoSyncIfEnabled()
        }
    }

    fun addCategory(name: String, kind: String, iconName: String = "ledger", commaKeywords: String = "") {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            dao.upsertCategory(
                CategoryEntity(
                    id = "cat_${System.currentTimeMillis()}",
                    name = cleanName,
                    kind = kind,
                    colorHex = if (kind == "INCOME") 0xFF0ECB81 else 0xFF1E2329,
                    iconName = iconName
                )
            )
            commaKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { kw ->
                dao.upsertKeywordRule(
                    KeywordRuleEntity(kw, cleanName, kind, "Cash", if (kw.contains(" ")) 110 else 85)
                )
            }
            statusMessage.value = "Saved $cleanName"
            triggerAutoSyncIfEnabled()
        }
    }

    fun renameCategory(cat: CategoryEntity, newName: String, newIcon: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            dao.updateCategoryNameAndIcon(cat.id, clean, newIcon)
            if (clean != cat.name) {
                dao.renameCategoryInRules(cat.name, clean)
                dao.renameCategoryInTransactions(cat.name, clean)
            }
            statusMessage.value = "Updated $clean"
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteCategory(cat: CategoryEntity) {
        viewModelScope.launch {
            dao.deleteCategoryById(cat.id)
            dao.deleteRulesForCategory(cat.name)
            statusMessage.value = "Removed ${cat.name}"
            triggerAutoSyncIfEnabled()
        }
    }

    fun addKeywordToCategory(keyword: String, category: CategoryEntity, walletHint: String = "Cash") {
        val kw = keyword.trim().lowercase()
        if (kw.isBlank()) return
        viewModelScope.launch {
            dao.upsertKeywordRule(
                KeywordRuleEntity(kw, category.name, category.kind, walletHint, if (kw.contains(" ")) 110 else 85)
            )
            triggerAutoSyncIfEnabled()
        }
    }

    fun updateKeywordRule(oldKeyword: String, newKeyword: String, category: CategoryEntity) {
        val cleanNew = newKeyword.trim().lowercase()
        if (cleanNew.isBlank()) return
        viewModelScope.launch {
            dao.deleteKeywordRule(oldKeyword, category.name)
            dao.upsertKeywordRule(
                KeywordRuleEntity(cleanNew, category.name, category.kind, "Cash", if (cleanNew.contains(" ")) 110 else 85)
            )
            triggerAutoSyncIfEnabled()
        }
    }

    fun removeKeywordRule(keyword: String, categoryName: String) {
        viewModelScope.launch {
            dao.deleteKeywordRule(keyword, categoryName)
            triggerAutoSyncIfEnabled()
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val res = syncManager.exportCsvToUri(uri, transactions.value)
            statusMessage.value = res.fold(
                onSuccess = { "Exported $it records" },
                onFailure = { "Export failed: ${it.message}" }
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val res = syncManager.importBackupFromUri(uri)
            statusMessage.value = res.fold(
                onSuccess = { "Imported $it records" },
                onFailure = { "Import failed: ${it.message}" }
            )
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            dao.clearAllTransactions()
            dao.resetAllWalletBalances()
            statusMessage.value = "Cleared all local data"
        }
    }

    fun syncNowToCloud() {
        viewModelScope.launch {
            val res = syncManager.syncVaultToCloud()
            res.fold(
                onSuccess = { stamp ->
                    lastSyncedText.value = stamp
                    statusMessage.value = "Encrypted vault synced to cloud"
                },
                onFailure = { err ->
                    statusMessage.value = err.message ?: "Sync failed"
                }
            )
        }
    }

    fun pullFromCloud() {
        viewModelScope.launch {
            val res = syncManager.pullVaultFromCloud()
            res.fold(
                onSuccess = { count ->
                    lastSyncedText.value = syncManager.lastSyncedLabel
                    statusMessage.value = "Restored $count records from cloud vault"
                },
                onFailure = { err ->
                    statusMessage.value = err.message ?: "Restore failed"
                }
            )
        }
    }
}
