package com.rafsan.ottobudget.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rafsan.ottobudget.RecurringAlarmScheduler
import com.rafsan.ottobudget.data.local.*
import com.rafsan.ottobudget.data.sync.SupabaseSyncManager
import com.rafsan.ottobudget.domain.MfsSmsNotificationParser
import com.rafsan.ottobudget.domain.RuleEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class OttoViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(application)
    private val dao = db.appDao()
    val syncManager = SupabaseSyncManager(application, dao)

    val wallets = dao.observeWallets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val keywordRules = dao.observeKeywordRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vaultGoals = dao.observeVaultGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurringRules = dao.observeRecurringRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingSmsDetections = dao.observePendingSmsDetections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDarkTheme = MutableStateFlow(syncManager.isDarkMode)
    val showSplashWink = MutableStateFlow(true)
    val showOnboarding = MutableStateFlow(!syncManager.hasSeenOnboarding)

    // App Lock & Savings Vault Lock States
    val isPinLockEnabled = MutableStateFlow(syncManager.pinLockEnabled)
    val isBiometricEnabled = MutableStateFlow(syncManager.biometricUnlockEnabled)
    val isAppLocked = MutableStateFlow(syncManager.pinLockEnabled)

    val isVaultLockEnabled = MutableStateFlow(syncManager.vaultPinLockEnabled)
    val isVaultStealthMode = MutableStateFlow(syncManager.vaultStealthHideTab)
    val isVaultSessionUnlocked = MutableStateFlow(!syncManager.vaultPinLockEnabled)

    // Feature Toggles
    val isMonthlyCapEnabled = MutableStateFlow(syncManager.monthlyCapEnabled)
    val isRecurringEnabled = MutableStateFlow(syncManager.recurringFeatureEnabled)
    val isSmsParserEnabled = MutableStateFlow(syncManager.smsAutoParserEnabled)

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

    fun toggleMonthlyCap(enabled: Boolean) {
        isMonthlyCapEnabled.value = enabled
        syncManager.monthlyCapEnabled = enabled
        statusMessage.value = if (enabled) "Monthly Cap enabled" else "Monthly Cap hidden"
    }

    fun toggleRecurringFeature(enabled: Boolean) {
        isRecurringEnabled.value = enabled
        syncManager.recurringFeatureEnabled = enabled
        statusMessage.value = if (enabled) "Recurring confirmations enabled" else "Recurring confirmations paused"
    }

    fun toggleSmsParser(enabled: Boolean) {
        isSmsParserEnabled.value = enabled
        syncManager.smsAutoParserEnabled = enabled
        statusMessage.value = if (enabled) "On-device SMS/Alert parser enabled" else "SMS/Alert parser disabled"
    }

    fun toggleVaultLock(enabled: Boolean) {
        isVaultLockEnabled.value = enabled
        syncManager.vaultPinLockEnabled = enabled
        isVaultSessionUnlocked.value = !enabled
        statusMessage.value = if (enabled) "Savings Vault protected by PIN/Biometrics" else "Savings Vault lock disabled"
    }

    fun toggleVaultStealth(enabled: Boolean) {
        isVaultStealthMode.value = enabled
        syncManager.vaultStealthHideTab = enabled
        statusMessage.value = if (enabled) "Stealth Vault Mode ON (Long-press Total Balance or Wallets header)" else "Stealth Vault Mode OFF"
    }

    fun unlockVaultSession() {
        isVaultSessionUnlocked.value = true
    }

    fun lockVaultSession() {
        if (isVaultLockEnabled.value) {
            isVaultSessionUnlocked.value = false
        }
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
        statusMessage.value = "4-Digit Passcode saved"
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

    /**
     * Spendable Wallet Balance Calculation:
     * - "VAULT_LOCK" subtracts from `tx.wallet` so assigned savings are immediately removed from spendable balance!
     * - "VAULT_RELEASE" adds the released savings back into `tx.wallet` spendable balance!
     */
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
                "VAULT_LOCK" -> {
                    map[tx.wallet] = (map[tx.wallet] ?: 0.0) - tx.amount
                }
                "VAULT_RELEASE" -> {
                    map[tx.wallet] = (map[tx.wallet] ?: 0.0) + tx.amount
                }
            }
        }
        return map
    }

    /**
     * Computes how much money is currently saved inside each Vault Goal (`goal.id`),
     * broken down by which source wallet (`bKash`, `Bank Card`, `Cash`) holds the funds.
     */
    fun computeVaultGoalHoldings(txList: List<TransactionEntity>): Map<String, Map<String, Double>> {
        val result = mutableMapOf<String, MutableMap<String, Double>>()
        txList.forEach { tx ->
            val goalId = tx.toWallet ?: return@forEach
            if (tx.type == "VAULT_LOCK") {
                val walletMap = result.getOrPut(goalId) { mutableMapOf() }
                walletMap[tx.wallet] = (walletMap[tx.wallet] ?: 0.0) + tx.amount
            } else if (tx.type == "VAULT_RELEASE") {
                val walletMap = result.getOrPut(goalId) { mutableMapOf() }
                walletMap[tx.wallet] = ((walletMap[tx.wallet] ?: 0.0) - tx.amount).coerceAtLeast(0.0)
            }
        }
        return result
    }

    /**
     * Assigns money from `sourceWallet` into `goal`, enforcing the goal's Wallet Criteria:
     * 1. `sourceWallet` must be in `goal.allowedWallets`.
     * 2. Remaining spendable balance in `sourceWallet` must stay >= `goal.minWalletFloor`.
     */
    fun assignMoneyToVaultGoal(
        goal: VaultGoalEntity,
        sourceWallet: String,
        amount: Double,
        currentSpendableBalances: Map<String, Double>
    ): String? {
        if (amount <= 0.0) return "Enter an amount greater than ৳0"
        val allowed = goal.allowedWallets.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (allowed.isNotEmpty() && sourceWallet !in allowed) {
            return "$sourceWallet is not in the allowed wallets criteria for '${goal.title}' (${allowed.joinToString(", ")})."
        }
        val avail = currentSpendableBalances[sourceWallet] ?: 0.0
        val remainingAfter = avail - amount
        if (remainingAfter < 0.0) {
            return "Insufficient spendable balance in $sourceWallet (Available: ৳${avail.toInt()})."
        }
        if (remainingAfter < goal.minWalletFloor) {
            val maxAssignable = (avail - goal.minWalletFloor).coerceAtLeast(0.0)
            return "Wallet Floor Criteria: '${goal.title}' requires keeping at least ৳${goal.minWalletFloor.toInt()} spendable in $sourceWallet. Max you can assign right now is ৳${maxAssignable.toInt()}."
        }

        viewModelScope.launch {
            dao.upsertTransaction(
                TransactionEntity(
                    id = "vlock_${System.currentTimeMillis()}",
                    date = LocalDate.now().toString(),
                    type = "VAULT_LOCK",
                    amount = amount,
                    category = "Savings Vault",
                    wallet = sourceWallet,
                    toWallet = goal.id,
                    description = "Assigned to ${goal.title} from $sourceWallet"
                )
            )
            statusMessage.value = "Assigned ৳${amount.toInt()} from $sourceWallet to ${goal.title}"
            triggerAutoSyncIfEnabled()
        }
        return null
    }

    fun releaseMoneyFromVaultGoal(
        goal: VaultGoalEntity,
        destWallet: String,
        amount: Double,
        currentHoldingsForGoal: Map<String, Double>
    ): String? {
        val heldInWallet = currentHoldingsForGoal[destWallet] ?: 0.0
        if (amount <= 0.0 || amount > heldInWallet) {
            return "You can release up to ৳${heldInWallet.toInt()} back to $destWallet."
        }
        viewModelScope.launch {
            dao.upsertTransaction(
                TransactionEntity(
                    id = "vrel_${System.currentTimeMillis()}",
                    date = LocalDate.now().toString(),
                    type = "VAULT_RELEASE",
                    amount = amount,
                    category = "Savings Vault",
                    wallet = destWallet,
                    toWallet = goal.id,
                    description = "Released from ${goal.title} to $destWallet"
                )
            )
            statusMessage.value = "Released ৳${amount.toInt()} back to $destWallet"
            triggerAutoSyncIfEnabled()
        }
        return null
    }

    fun addVaultGoal(
        title: String,
        targetAmount: Double,
        allowedWallets: String,
        minWalletFloor: Double,
        iconName: String = "vault"
    ) {
        val clean = title.trim()
        if (clean.isBlank() || targetAmount <= 0.0) return
        viewModelScope.launch {
            dao.upsertVaultGoal(
                VaultGoalEntity(
                    id = "goal_${System.currentTimeMillis()}",
                    title = clean,
                    targetAmount = targetAmount,
                    allowedWallets = allowedWallets.ifBlank { "bKash,Bank Card,Cash" },
                    minWalletFloor = minWalletFloor.coerceAtLeast(0.0),
                    iconName = iconName
                )
            )
            statusMessage.value = "Created vault goal: $clean"
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteVaultGoal(goal: VaultGoalEntity) {
        viewModelScope.launch {
            dao.clearVaultAllocationsForGoal(goal.id)
            dao.deleteVaultGoalById(goal.id)
            statusMessage.value = "Deleted ${goal.title} & returned funds to spendable wallets"
            triggerAutoSyncIfEnabled()
        }
    }

    // Recurring Expenses & Income Management (Confirmation-Based!)
    fun addRecurringRule(
        title: String,
        type: String,
        amount: Double,
        category: String,
        wallet: String,
        dayOfMonth: Int,
        notifyHour: Int,
        notifyMinute: Int
    ) {
        val clean = title.trim()
        if (clean.isBlank() || amount <= 0.0) return
        val rule = RecurringRuleEntity(
            id = "rec_${System.currentTimeMillis()}",
            title = clean,
            type = type,
            amount = amount,
            category = category,
            wallet = wallet,
            dayOfMonth = dayOfMonth.coerceIn(1, 31),
            notifyHour = notifyHour.coerceIn(0, 23),
            notifyMinute = notifyMinute.coerceIn(0, 59),
            enabled = true,
            lastConfirmedPeriod = ""
        )
        viewModelScope.launch {
            dao.upsertRecurringRule(rule)
            RecurringAlarmScheduler.scheduleRecurringNotification(appContext, rule)
            statusMessage.value = "Scheduled $clean (Day ${rule.dayOfMonth} at %02d:%02d)".format(rule.notifyHour, rule.notifyMinute)
            triggerAutoSyncIfEnabled()
        }
    }

    fun confirmDueRecurringRule(
        rule: RecurringRuleEntity,
        currentPeriod: String,
        currentBalances: Map<String, Double>
    ): String? {
        if (rule.type == "EXPENSE") {
            val avail = currentBalances[rule.wallet] ?: 0.0
            if (avail < rule.amount) {
                return "Insufficient balance in ${rule.wallet} (Available: ৳${avail.toInt()}, Due: ৳${rule.amount.toInt()})."
            }
        }
        viewModelScope.launch {
            dao.upsertTransaction(
                TransactionEntity(
                    id = "tx_rec_${System.currentTimeMillis()}",
                    date = LocalDate.now().toString(),
                    type = rule.type,
                    amount = rule.amount,
                    category = rule.category,
                    wallet = rule.wallet,
                    toWallet = null,
                    description = "${rule.title} (Recurring)"
                )
            )
            dao.markRecurringRuleConfirmed(rule.id, currentPeriod)
            statusMessage.value = "Confirmed ${rule.title}: ${if (rule.type == "INCOME") "+" else "-"}৳${rule.amount.toInt()} (${rule.wallet})"
            triggerAutoSyncIfEnabled()
        }
        return null
    }

    fun skipDueRecurringRuleThisMonth(rule: RecurringRuleEntity, currentPeriod: String) {
        viewModelScope.launch {
            dao.markRecurringRuleConfirmed(rule.id, currentPeriod)
            statusMessage.value = "Skipped ${rule.title} for $currentPeriod"
            triggerAutoSyncIfEnabled()
        }
    }

    fun toggleRecurringRuleItem(rule: RecurringRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            dao.setRecurringRuleEnabled(rule.id, enabled)
            RecurringAlarmScheduler.scheduleRecurringNotification(appContext, rule.copy(enabled = enabled))
        }
    }

    fun deleteRecurringRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            RecurringAlarmScheduler.scheduleRecurringNotification(appContext, rule.copy(enabled = false))
            dao.deleteRecurringRuleById(rule.id)
            statusMessage.value = "Removed recurring rule: ${rule.title}"
            triggerAutoSyncIfEnabled()
        }
    }

    // Opt-In On-Device SMS / Notification Auto-Parser Confirmation & Simulation
    fun simulateOrParseSmsText(sender: String, rawSms: String) {
        val parsed = MfsSmsNotificationParser.parseIncomingAlert(sender, rawSms)
        if (parsed == null) {
            statusMessage.value = "Could not detect a valid bKash/Nagad/Bank transaction in text"
            return
        }
        viewModelScope.launch {
            dao.insertPendingSmsDetection(parsed)
            RecurringAlarmScheduler.postImmediateNotification(
                context = appContext,
                title = "Detected ${parsed.provider} Alert",
                body = parsed.summaryText
            )
            statusMessage.value = "Detected: ${parsed.summaryText}"
        }
    }

    fun approvePendingSmsDetection(
        detection: PendingSmsDetectionEntity,
        currentBalances: Map<String, Double>
    ): String? {
        if (detection.type == "EXPENSE" || detection.type == "TRANSFER") {
            val avail = currentBalances[detection.wallet] ?: 0.0
            if (avail < detection.amount) {
                return "Insufficient balance in ${detection.wallet} (Available: ৳${avail.toInt()}, Alert: ৳${detection.amount.toInt()})."
            }
        }
        viewModelScope.launch {
            dao.upsertTransaction(
                TransactionEntity(
                    id = "tx_sms_${System.currentTimeMillis()}",
                    date = detection.detectedDate,
                    type = detection.type,
                    amount = detection.amount,
                    category = detection.category,
                    wallet = detection.wallet,
                    toWallet = detection.toWallet,
                    description = "${detection.provider} (${detection.trxId})"
                )
            )
            dao.deletePendingSmsDetection(detection.id)
            statusMessage.value = "Logged ৳${detection.amount.toInt()} (${detection.provider})"
            triggerAutoSyncIfEnabled()
        }
        return null
    }

    fun dismissPendingSmsDetection(detectionId: String) {
        viewModelScope.launch {
            dao.deletePendingSmsDetection(detectionId)
        }
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
