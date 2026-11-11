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

data class UndoDeletedPayload(
    val label: String,
    val restoreAction: suspend () -> Unit
)

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
    val showSpotlightTour = MutableStateFlow(false)

    // App Lock & Savings Vault Lock States
    val isPinLockEnabled = MutableStateFlow(syncManager.pinLockEnabled)
    val isBiometricEnabled = MutableStateFlow(syncManager.biometricUnlockEnabled)
    val isAppLocked = MutableStateFlow(syncManager.pinLockEnabled)

    val isVaultLockEnabled = MutableStateFlow(syncManager.vaultPinLockEnabled)
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

    // Undo Delete StateFlow (emits whenever an item is deleted so UI shows an Undo snackbar!)
    val pendingUndoEvent = MutableStateFlow<UndoDeletedPayload?>(null)

    fun clearUndoEvent() {
        pendingUndoEvent.value = null
    }

    fun executeUndo(payload: UndoDeletedPayload) {
        viewModelScope.launch {
            payload.restoreAction()
            pendingUndoEvent.value = null
            statusMessage.value = "Restored ${payload.label}"
            triggerAutoSyncIfEnabled()
        }
    }

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
        statusMessage.value = if (enabled) "Scheduler enabled" else "Scheduler paused"
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

    fun completeChatOnboarding(
        chosenName: String,
        incomePref: String,
        walletPref: String,
        capAnswer: String,
        vaultGoalAnswer: String
    ) {
        val cleanName = chosenName.trim().ifBlank { "User" }
        userName.value = cleanName
        syncManager.userName = cleanName

        // Parse Monthly Cap answer
        if (capAnswer.lowercase().contains("no") || capAnswer.lowercase().contains("off")) {
            isMonthlyCapEnabled.value = false
            syncManager.monthlyCapEnabled = false
        } else {
            val digits = Regex("[0-9]+").findAll(capAnswer.replace(",", "")).mapNotNull { it.value.toDoubleOrNull() }.firstOrNull()
            if (digits != null && digits > 0.0) {
                monthlyBudgetTarget.value = digits
                syncManager.monthlyBudget = digits
            }
            isMonthlyCapEnabled.value = true
            syncManager.monthlyCapEnabled = true
        }

        viewModelScope.launch {
            // If user picked Nagad in wallet preferences, ensure Nagad wallet exists
            if (walletPref.lowercase().contains("nagad")) {
                val existing = dao.getAllWalletsOnce().any { it.name.equals("Nagad", ignoreCase = true) }
                if (!existing) {
                    dao.upsertWallet(WalletEntity("w_nagad", "Nagad", "LIQUID", 0.0, "Mobile Wallet", 0xFFF97316))
                }
            }
            // If user entered a custom Vault Goal during onboarding, create it
            val cleanGoal = vaultGoalAnswer.trim()
            if (cleanGoal.isNotEmpty() && !cleanGoal.lowercase().contains("later") && !cleanGoal.lowercase().contains("skip")) {
                val existingGoals = dao.getAllVaultGoalsOnce()
                if (existingGoals.none { it.title.equals(cleanGoal, ignoreCase = true) }) {
                    dao.upsertVaultGoal(
                        VaultGoalEntity(
                            id = "goal_onb_${System.currentTimeMillis()}",
                            title = cleanGoal,
                            targetAmount = 20000.0,
                            allowedWallets = "bKash,Bank Card,Cash",
                            minWalletFloor = 500.0,
                            iconName = "vault"
                        )
                    )
                }
            }

            syncManager.postOnboardingAnswersZeroDatabase(
                mapOf(
                    "name" to cleanName,
                    "incomeSources" to incomePref,
                    "wallets" to walletPref,
                    "monthlyCap" to capAnswer,
                    "firstVaultGoal" to vaultGoalAnswer
                )
            )
        }

        syncManager.hasSeenOnboarding = true
        showOnboarding.value = false
        if (!syncManager.hasSeenSpotlightTour) {
            showSpotlightTour.value = true
        }
    }

    fun skipChatOnboarding() {
        syncManager.hasSeenOnboarding = true
        showOnboarding.value = false
        if (!syncManager.hasSeenSpotlightTour) {
            showSpotlightTour.value = true
        }
    }

    fun finishSpotlightTour() {
        syncManager.hasSeenSpotlightTour = true
        showSpotlightTour.value = false
    }

    fun replaySpotlightTour() {
        showSpotlightTour.value = true
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
     * Computes Available (Spendable) Wallet Balances (`VAULT_LOCK` subtracted, `VAULT_RELEASE` added back).
     */
    fun computeAvailableWalletBalances(walletList: List<WalletEntity>, txList: List<TransactionEntity>): Map<String, Double> {
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
     * Computes Total (Actual) Wallet Balances physically sitting in each wallet (ignoring Vault deductions).
     */
    fun computeTotalActualWalletBalances(walletList: List<WalletEntity>, txList: List<TransactionEntity>): Map<String, Double> {
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

    fun assignMoneyToVaultGoal(
        goal: VaultGoalEntity,
        sourceWallet: String,
        amount: Double,
        currentAvailableBalances: Map<String, Double>
    ): String? {
        if (amount <= 0.0) return "Enter an amount greater than ৳0"
        val allowed = goal.allowedWallets.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (allowed.isNotEmpty() && sourceWallet !in allowed) {
            return "$sourceWallet is not in the allowed wallets for '${goal.title}' (${allowed.joinToString(", ")})."
        }
        val avail = currentAvailableBalances[sourceWallet] ?: 0.0
        val remainingAfter = avail - amount
        if (remainingAfter < 0.0) {
            return "Insufficient available balance in $sourceWallet (Available: ৳${avail.toInt()})."
        }
        if (remainingAfter < goal.minWalletFloor) {
            val maxAssignable = (avail - goal.minWalletFloor).coerceAtLeast(0.0)
            return "Wallet Floor Criteria: '${goal.title}' keeps at least ৳${goal.minWalletFloor.toInt()} available in $sourceWallet. Max you can assign is ৳${maxAssignable.toInt()}."
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

    /**
     * Full Edit for a Savings Vault Goal:
     * Updates Goal Title, Icon, Target Amount, Allowed Wallets, Min Floor, AND directly adjusts assigned amounts per wallet!
     */
    fun updateVaultGoalAndHoldings(
        goal: VaultGoalEntity,
        newTitle: String,
        newTargetAmount: Double,
        newIconName: String,
        newAllowedWallets: String,
        newMinFloor: Double,
        updatedWalletHoldings: Map<String, Double>
    ) {
        val cleanTitle = newTitle.trim().ifBlank { goal.title }
        viewModelScope.launch {
            dao.upsertVaultGoal(
                goal.copy(
                    title = cleanTitle,
                    targetAmount = newTargetAmount.coerceAtLeast(1.0),
                    iconName = newIconName,
                    allowedWallets = newAllowedWallets.ifBlank { goal.allowedWallets },
                    minWalletFloor = newMinFloor.coerceAtLeast(0.0)
                )
            )
            // Replace per-wallet holdings with the exact edited amounts
            dao.clearVaultAllocationsForGoal(goal.id)
            updatedWalletHoldings.forEach { (wName, amt) ->
                if (amt > 0.0) {
                    dao.upsertTransaction(
                        TransactionEntity(
                            id = "vlock_edit_${goal.id}_${wName}_${System.currentTimeMillis()}",
                            date = LocalDate.now().toString(),
                            type = "VAULT_LOCK",
                            amount = amt,
                            category = "Savings Vault",
                            wallet = wName,
                            toWallet = goal.id,
                            description = "Assigned to $cleanTitle from $wName"
                        )
                    )
                }
            }
            statusMessage.value = "Updated $cleanTitle"
            triggerAutoSyncIfEnabled()
        }
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

    fun deleteVaultGoalWithUndo(goal: VaultGoalEntity) {
        viewModelScope.launch {
            val savedAllocations = dao.getVaultAllocationsForGoalOnce(goal.id)
            dao.clearVaultAllocationsForGoal(goal.id)
            dao.deleteVaultGoalById(goal.id)
            pendingUndoEvent.value = UndoDeletedPayload(
                label = goal.title,
                restoreAction = {
                    dao.upsertVaultGoal(goal)
                    if (savedAllocations.isNotEmpty()) dao.insertTransactions(savedAllocations)
                }
            )
            triggerAutoSyncIfEnabled()
        }
    }

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
                return "Insufficient available balance in ${rule.wallet} (Available: ৳${avail.toInt()}, Due: ৳${rule.amount.toInt()})."
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
                    description = "${rule.title} (Scheduled)"
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

    fun deleteRecurringRuleWithUndo(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            RecurringAlarmScheduler.scheduleRecurringNotification(appContext, rule.copy(enabled = false))
            dao.deleteRecurringRuleById(rule.id)
            pendingUndoEvent.value = UndoDeletedPayload(
                label = rule.title,
                restoreAction = {
                    dao.upsertRecurringRule(rule)
                    RecurringAlarmScheduler.scheduleRecurringNotification(appContext, rule)
                }
            )
            triggerAutoSyncIfEnabled()
        }
    }

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
            statusMessage.value = "New alert ready in Home Notification button!"
        }
    }

    fun approvePendingSmsDetection(
        detection: PendingSmsDetectionEntity,
        currentBalances: Map<String, Double>
    ): String? {
        if (detection.type == "EXPENSE" || detection.type == "TRANSFER") {
            val avail = currentBalances[detection.wallet] ?: 0.0
            if (avail < detection.amount) {
                return "Insufficient available balance in ${detection.wallet} (Available: ৳${avail.toInt()}, Alert: ৳${detection.amount.toInt()})."
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

    fun removeWalletWithUndo(wallet: WalletEntity) {
        viewModelScope.launch {
            dao.deleteWalletById(wallet.id)
            pendingUndoEvent.value = UndoDeletedPayload(
                label = wallet.name,
                restoreAction = { dao.upsertWallet(wallet) }
            )
            triggerAutoSyncIfEnabled()
        }
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

    /**
     * Edit an existing past transaction (Triggered via Long-Press on any transaction row!).
     */
    fun updateExistingTransaction(
        original: TransactionEntity,
        newType: String,
        newAmount: Double,
        newDescription: String,
        newCategory: String,
        newWallet: String,
        newToWallet: String?,
        newDate: String
    ) {
        if (newAmount <= 0.0) return
        viewModelScope.launch {
            dao.upsertTransaction(
                original.copy(
                    type = newType,
                    amount = newAmount,
                    description = newDescription.trim().ifBlank { if (newType == "TRANSFER") "$newWallet to $newToWallet" else newCategory },
                    category = if (newType == "TRANSFER") "Transfer" else newCategory,
                    wallet = newWallet,
                    toWallet = if (newType == "TRANSFER") newToWallet else null,
                    date = newDate,
                    updatedAt = System.currentTimeMillis()
                )
            )
            statusMessage.value = "Transaction updated"
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteTransactionWithUndo(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
            pendingUndoEvent.value = UndoDeletedPayload(
                label = tx.description.ifBlank { tx.category },
                restoreAction = { dao.upsertTransaction(tx) }
            )
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

    fun deleteCategoryWithUndo(cat: CategoryEntity) {
        viewModelScope.launch {
            val existingRules = dao.getAllKeywordRulesOnce().filter { it.category == cat.name }
            dao.deleteCategoryById(cat.id)
            dao.deleteRulesForCategory(cat.name)
            pendingUndoEvent.value = UndoDeletedPayload(
                label = cat.name,
                restoreAction = {
                    dao.upsertCategory(cat)
                    if (existingRules.isNotEmpty()) dao.insertKeywordRules(existingRules)
                }
            )
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
