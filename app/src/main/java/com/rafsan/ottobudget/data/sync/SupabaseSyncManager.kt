package com.rafsan.ottobudget.data.sync

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.rafsan.ottobudget.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SupabaseSyncManager(
    private val context: Context,
    private val dao: AppDao
) {
    private val prefs = context.getSharedPreferences("ottobudget_v6_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode_enabled", false)
        set(value) { prefs.edit().putBoolean("dark_mode_enabled", value).commit() }

    var monthlyCapEnabled: Boolean
        get() = prefs.getBoolean("monthly_cap_enabled", true)
        set(value) { prefs.edit().putBoolean("monthly_cap_enabled", value).commit() }

    var recurringFeatureEnabled: Boolean
        get() = prefs.getBoolean("recurring_feature_enabled", true)
        set(value) { prefs.edit().putBoolean("recurring_feature_enabled", value).commit() }

    var smsAutoParserEnabled: Boolean
        get() = prefs.getBoolean("sms_auto_parser_enabled", false)
        set(value) { prefs.edit().putBoolean("sms_auto_parser_enabled", value).commit() }

    var vaultPinLockEnabled: Boolean
        get() = prefs.getBoolean("vault_pin_lock_enabled", true)
        set(value) { prefs.edit().putBoolean("vault_pin_lock_enabled", value).commit() }

    var vaultStealthHideTab: Boolean
        get() = prefs.getBoolean("vault_stealth_hide_tab", false)
        set(value) { prefs.edit().putBoolean("vault_stealth_hide_tab", value).commit() }

    var pinHash: String
        get() = prefs.getString("security_pin_hash", "") ?: ""
        set(value) { prefs.edit().putString("security_pin_hash", value).commit() }

    var pinLockEnabled: Boolean
        get() = prefs.getBoolean("security_pin_enabled", false) && pinHash.isNotEmpty()
        set(value) { prefs.edit().putBoolean("security_pin_enabled", value).commit() }

    var biometricUnlockEnabled: Boolean
        get() = prefs.getBoolean("security_biometric_enabled", false)
        set(value) { prefs.edit().putBoolean("security_biometric_enabled", value).commit() }

    fun hashPinCode(rawPin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("otto_pin_v7_salt_" + rawPin.trim()).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun verifyPinCode(rawPin: String): Boolean {
        if (pinHash.isEmpty()) return true
        return hashPinCode(rawPin) == pinHash
    }

    var userName: String
        get() = prefs.getString("user_name", "User") ?: "User"
        set(value) = prefs.edit().putString("user_name", value.trim().ifBlank { "User" }).apply()

    var monthlyBudget: Double
        get() = prefs.getFloat("monthly_budget", 10000f).toDouble()
        set(value) = prefs.edit().putFloat("monthly_budget", value.toFloat()).apply()

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "") ?: ""
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() = prefs.getString("supabase_key", "") ?: ""
        set(value) = prefs.edit().putString("supabase_key", value.trim()).apply()

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_sync_enabled", value).apply()

    var lastSyncedLabel: String
        get() = prefs.getString("last_synced_label", "Not synced yet") ?: "Not synced yet"
        set(value) = prefs.edit().putString("last_synced_label", value).apply()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean("seen_onboarding_v6", false)
        set(value) = prefs.edit().putBoolean("seen_onboarding_v6", value).apply()

    private fun deriveAesKey(secretSeed: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(("otto_v6_salt_" + secretSeed).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encryptVaultPayload(plainJson: String, keySeed: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = "OttoVaultV6Fixed".toByteArray(Charsets.UTF_8).copyOf(12)
            cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(keySeed), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            "OTTO_V6_ENC:" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Base64.encodeToString(plainJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptVaultPayload(cipherText: String, keySeed: String): String {
        val clean = cipherText.trim()
        return if (clean.startsWith("OTTO_V6_ENC:")) {
            val rawBytes = Base64.decode(clean.removePrefix("OTTO_V6_ENC:"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = "OttoVaultV6Fixed".toByteArray(Charsets.UTF_8).copyOf(12)
            cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(keySeed), GCMParameterSpec(128, iv))
            String(cipher.doFinal(rawBytes), Charsets.UTF_8)
        } else {
            String(Base64.decode(clean, Base64.NO_WRAP), Charsets.UTF_8)
        }
    }

    private suspend fun buildVaultJsonString(): String {
        val root = JSONObject()
        root.put("version", 8)
        root.put("userName", userName)
        root.put("monthlyBudget", monthlyBudget)
        root.put("monthlyCapEnabled", monthlyCapEnabled)
        root.put("isDarkMode", isDarkMode)

        val walletsArr = JSONArray()
        dao.getAllWalletsOnce().forEach { w ->
            walletsArr.put(JSONObject().apply {
                put("id", w.id); put("name", w.name); put("groupType", w.groupType)
                put("initialBalance", w.initialBalance); put("numberTag", w.numberTag); put("accentHex", w.accentHex)
            })
        }
        root.put("wallets", walletsArr)

        val catsArr = JSONArray()
        dao.getAllCategoriesOnce().forEach { c ->
            catsArr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("kind", c.kind)
                put("colorHex", c.colorHex); put("iconName", c.iconName)
            })
        }
        root.put("categories", catsArr)

        val rulesArr = JSONArray()
        dao.getAllKeywordRulesOnce().forEach { r ->
            rulesArr.put(JSONObject().apply {
                put("keyword", r.keyword); put("category", r.category); put("kind", r.kind)
                put("walletHint", r.walletHint); put("priority", r.priority)
            })
        }
        root.put("rules", rulesArr)

        val goalsArr = JSONArray()
        dao.getAllVaultGoalsOnce().forEach { g ->
            goalsArr.put(JSONObject().apply {
                put("id", g.id); put("title", g.title); put("targetAmount", g.targetAmount)
                put("allowedWallets", g.allowedWallets); put("minWalletFloor", g.minWalletFloor)
                put("autoRuleType", g.autoRuleType); put("iconName", g.iconName)
            })
        }
        root.put("vaultGoals", goalsArr)

        val recArr = JSONArray()
        dao.getAllRecurringRulesOnce().forEach { rr ->
            recArr.put(JSONObject().apply {
                put("id", rr.id); put("title", rr.title); put("type", rr.type); put("amount", rr.amount)
                put("category", rr.category); put("wallet", rr.wallet); put("dayOfMonth", rr.dayOfMonth)
                put("notifyHour", rr.notifyHour); put("notifyMinute", rr.notifyMinute)
                put("enabled", rr.enabled); put("lastConfirmedPeriod", rr.lastConfirmedPeriod)
            })
        }
        root.put("recurringRules", recArr)

        val txArr = JSONArray()
        dao.getAllTransactionsOnce().forEach { t ->
            txArr.put(JSONObject().apply {
                put("id", t.id); put("date", t.date); put("type", t.type); put("amount", t.amount)
                put("category", t.category); put("wallet", t.wallet); put("toWallet", t.toWallet ?: "")
                put("description", t.description); put("isMajor", t.isMajor); put("updatedAt", t.updatedAt)
            })
        }
        root.put("transactions", txArr)
        return root.toString()
    }

    private suspend fun restoreFromVaultJsonString(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        if (root.has("userName")) userName = root.optString("userName", userName)
        if (root.has("monthlyBudget")) monthlyBudget = root.optDouble("monthlyBudget", monthlyBudget)
        if (root.has("monthlyCapEnabled")) monthlyCapEnabled = root.optBoolean("monthlyCapEnabled", monthlyCapEnabled)

        root.optJSONArray("wallets")?.let { arr ->
            val list = mutableListOf<WalletEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(WalletEntity(o.getString("id"), o.getString("name"), o.optString("groupType", "LIQUID"), o.getDouble("initialBalance"), o.optString("numberTag", "Account"), o.optLong("accentHex", 0xFF1E293B)))
            }
            if (list.isNotEmpty()) dao.insertWallets(list)
        }

        root.optJSONArray("categories")?.let { arr ->
            val list = mutableListOf<CategoryEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(CategoryEntity(o.getString("id"), o.getString("name"), o.getString("kind"), o.optLong("colorHex", 0xFF1E2329), o.optString("iconName", "ledger")))
            }
            if (list.isNotEmpty()) dao.insertCategories(list)
        }

        root.optJSONArray("rules")?.let { arr ->
            val list = mutableListOf<KeywordRuleEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(KeywordRuleEntity(o.getString("keyword"), o.getString("category"), o.getString("kind"), o.optString("walletHint", "Cash"), o.optInt("priority", 85)))
            }
            if (list.isNotEmpty()) dao.insertKeywordRules(list)
        }

        root.optJSONArray("vaultGoals")?.let { arr ->
            val list = mutableListOf<VaultGoalEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(VaultGoalEntity(o.getString("id"), o.getString("title"), o.getDouble("targetAmount"), o.optString("allowedWallets", "bKash,Bank Card,Cash"), o.optDouble("minWalletFloor", 0.0), o.optString("autoRuleType", "MANUAL"), o.optString("iconName", "vault")))
            }
            if (list.isNotEmpty()) dao.insertVaultGoals(list)
        }

        root.optJSONArray("recurringRules")?.let { arr ->
            val list = mutableListOf<RecurringRuleEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(RecurringRuleEntity(o.getString("id"), o.getString("title"), o.getString("type"), o.getDouble("amount"), o.getString("category"), o.getString("wallet"), o.optInt("dayOfMonth", 1), o.optInt("notifyHour", 9), o.optInt("notifyMinute", 0), o.optBoolean("enabled", true), o.optString("lastConfirmedPeriod", "")))
            }
            if (list.isNotEmpty()) dao.insertRecurringRules(list)
        }

        var txCount = 0
        root.optJSONArray("transactions")?.let { arr ->
            val list = mutableListOf<TransactionEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val toW = o.optString("toWallet", "").ifBlank { null }
                list.add(TransactionEntity(o.getString("id"), o.getString("date"), o.getString("type"), o.getDouble("amount"), o.getString("category"), o.getString("wallet"), toW, o.getString("description"), o.optBoolean("isMajor", false), o.optLong("updatedAt", System.currentTimeMillis()), true))
            }
            txCount = list.size
            if (list.isNotEmpty()) dao.insertTransactions(list)
        }
        return txCount
    }

    suspend fun syncVaultToCloud(): Result<String> = withContext(Dispatchers.IO) {
        val url = supabaseUrl.trimEnd('/')
        val key = supabaseAnonKey
        if (url.isBlank() || key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter Supabase URL & Anon Key first"))
        }

        runCatching {
            val bucketJson = JSONObject().apply {
                put("id", "otto_vault")
                put("name", "otto_vault")
                put("public", false)
            }
            val createBucketReq = Request.Builder()
                .url("$url/storage/v1/bucket")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .post(bucketJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            runCatching { client.newCall(createBucketReq).execute().close() }

            val plainJson = buildVaultJsonString()
            val encryptedBlob = encryptVaultPayload(plainJson, key)

            val uploadReq = Request.Builder()
                .url("$url/storage/v1/object/otto_vault/vault.enc")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("x-upsert", "true")
                .post(encryptedBlob.toRequestBody("text/plain".toMediaType()))
                .build()

            client.newCall(uploadReq).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Cloud sync HTTP ${response.code}: Check URL/Key permissions")
                }
            }

            val stamp = "Today, " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
            lastSyncedLabel = stamp
            stamp
        }
    }

    suspend fun pullVaultFromCloud(): Result<Int> = withContext(Dispatchers.IO) {
        val url = supabaseUrl.trimEnd('/')
        val key = supabaseAnonKey
        if (url.isBlank() || key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter Supabase URL & Anon Key first"))
        }

        runCatching {
            val getReq = Request.Builder()
                .url("$url/storage/v1/object/otto_vault/vault.enc")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            client.newCall(getReq).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("No cloud backup found or HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IllegalStateException("Empty cloud vault")
                val decrypted = decryptVaultPayload(body, key)
                val count = restoreFromVaultJsonString(decrypted)
                val stamp = "Today, " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
                lastSyncedLabel = stamp
                count
            }
        }
    }

    suspend fun exportCsvToUri(uri: Uri, transactions: List<TransactionEntity>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write("Date,Type,Amount,Category,Wallet,ToWallet,Description,ExcludeFromDaily,ID\n")
                    for (tx in transactions) {
                        val safeDesc = tx.description.replace("\n", " ").replace("\"", "\"\"")
                        val safeCat = tx.category.replace("\"", "\"\"")
                        val safeWallet = tx.wallet.replace("\"", "\"\"")
                        val safeTo = (tx.toWallet ?: "").replace("\"", "\"\"")
                        writer.write("${tx.date},${tx.type},${tx.amount},\"$safeCat\",\"$safeWallet\",\"$safeTo\",\"$safeDesc\",${tx.isMajor},${tx.id}\n")
                    }
                }
            }
            transactions.size
        }
    }

    suspend fun importBackupFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: ""

            if (content.trim().startsWith("{")) {
                return@runCatching restoreFromVaultJsonString(content.trim())
            }

            val lines = content.lines()
            if (lines.isEmpty()) return@runCatching 0
            val imported = mutableListOf<TransactionEntity>()
            val header = lines.first().lowercase()
            val isLegacySheet = header.contains("cost") && header.contains("catagory")

            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue
                val cols = parseCsvLine(line)
                if (isLegacySheet && cols.size >= 5) {
                    val date = cols[0].ifBlank { LocalDate.now().toString() }
                    val amt = cols[1].toDoubleOrNull() ?: 0.0
                    val cat = cols[2].ifBlank { "Other" }
                    val wallet = cols[3].ifBlank { "Cash" }
                    val desc = cols[4].ifBlank { cat }
                    val isMaj = cols.getOrNull(5)?.equals("TRUE", ignoreCase = true) ?: false
                    if (amt > 0.0 && cat != "Null") {
                        imported.add(TransactionEntity("imp_${System.currentTimeMillis()}_$i", date, "EXPENSE", amt, cat, wallet, null, desc, isMaj))
                    }
                } else if (cols.size >= 7) {
                    val date = cols[0].ifBlank { LocalDate.now().toString() }
                    val type = cols[1].uppercase().ifBlank { "EXPENSE" }
                    val amt = cols[2].toDoubleOrNull() ?: 0.0
                    val cat = cols[3].ifBlank { "Other" }
                    val wallet = cols[4].ifBlank { "Cash" }
                    val toWallet = cols[5].ifBlank { null }
                    val desc = cols[6].ifBlank { cat }
                    val isMaj = cols.getOrNull(7)?.equals("true", ignoreCase = true) ?: false
                    if (amt > 0.0) {
                        imported.add(TransactionEntity(cols.getOrNull(8)?.ifBlank { null } ?: "imp_${System.currentTimeMillis()}_$i", date, type, amt, cat, wallet, toWallet, desc, isMaj))
                    }
                }
            }
            if (imported.isNotEmpty()) dao.insertTransactions(imported)
            imported.size
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
