package com.rafsan.ottobudget.domain

import com.rafsan.ottobudget.data.local.PendingSmsDetectionEntity
import java.time.LocalDate

/**
 * Production-grade 100% Offline On-Device Regex Parser for bKash, Nagad, Rocket, Upay,
 * and Bangladeshi Commercial Bank SMS & Push Notifications.
 */
object MfsSmsNotificationParser {

    private val primaryAmountRegex = Regex(
        "(?:Payment of|Cash Out|Cash-Out|Cash In|Cash-In|Send Money|You have received|received|debited|credited|spent|withdrawn|purchase of|Bill|Recharge)?\\s*(?:Tk|BDT|৳)\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val feeRegex = Regex(
        "(?:Fee|Charge|Commission)\\s*(?:Tk|BDT|৳)?\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val trxIdRegex = Regex(
        "(?:TrxID|TxnId|TxID|TranID|Ref)\\s*[:#-]?\\s*([A-Z0-9]{6,16})",
        RegexOption.IGNORE_CASE
    )
    private val merchantOrCounterpartyRegex = Regex(
        "(?:to|from|at)\\s+([A-Za-z0-9_\\-\\.& ]{2,26}?)(?:\\s+is\\s+successful|\\s+successful|\\.|\\s+Fee|\\s+Bal|\\s+TrxID|\\s+TxnId|$)",
        RegexOption.IGNORE_CASE
    )

    fun parseIncomingAlert(senderOrPkg: String, bodyText: String): PendingSmsDetectionEntity? {
        val cleanBody = bodyText.replace("\n", " ").trim()
        if (cleanBody.length < 10) return null

        val lowerSender = senderOrPkg.lowercase()
        val lowerBody = cleanBody.lowercase()

        // Ignore OTP or verification codes
        if (lowerBody.contains("otp") || lowerBody.contains("verification code") || lowerBody.contains("pin is")) {
            return null
        }

        val provider = when {
            lowerSender.contains("bkash") || lowerBody.contains("bkash") || (lowerBody.contains("trxid") && lowerBody.contains("tk")) -> "bKash"
            lowerSender.contains("nagad") || lowerBody.contains("nagad") || lowerBody.contains("txnid") -> "Nagad"
            lowerSender.contains("rocket") || lowerSender.contains("16216") || lowerBody.contains("rocket") -> "Rocket"
            lowerSender.contains("upay") || lowerBody.contains("upay") -> "Upay"
            lowerSender.contains("bank") || lowerSender.contains("city") || lowerSender.contains("brac") ||
                lowerSender.contains("ebl") || lowerSender.contains("dbbl") || lowerSender.contains("islami") ||
                lowerBody.contains("card") || lowerBody.contains("a/c") || lowerBody.contains("debited") ||
                lowerBody.contains("credited") || lowerBody.contains("atm") -> "Bank Card"
            else -> return null
        }

        val amountMatch = primaryAmountRegex.find(cleanBody) ?: return null
        val rawAmount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (rawAmount <= 0.0) return null

        val feeAmount = feeRegex.find(cleanBody)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val trxId = trxIdRegex.find(cleanBody)?.groupValues?.get(1)?.uppercase()
            ?: "TX${(System.currentTimeMillis() % 1000000)}"
        val counterparty = merchantOrCounterpartyRegex.find(cleanBody)?.groupValues?.get(1)?.trim()

        val isAtmOrCashOutToHand = lowerBody.contains("atm withdrawal") || lowerBody.contains("withdrawn from atm")
        val isCashOut = lowerBody.contains("cash out") || lowerBody.contains("cash-out")
        val isIncoming = lowerBody.contains("received") || lowerBody.contains("cash in") ||
            lowerBody.contains("cash-in") || lowerBody.contains("credited") || lowerBody.contains("deposit")

        val type = when {
            isAtmOrCashOutToHand -> "TRANSFER"
            isIncoming -> "INCOME"
            else -> "EXPENSE"
        }

        val totalAmount = if (type == "EXPENSE") rawAmount + feeAmount else rawAmount
        val walletName = when (provider) {
            "bKash" -> "bKash"
            "Nagad" -> "Nagad"
            "Rocket" -> "Rocket"
            "Upay" -> "Upay"
            else -> "Bank Card"
        }
        val toWallet = if (type == "TRANSFER") "Cash" else null

        val inferredCategory = when {
            type == "INCOME" -> "Salary & Job"
            type == "TRANSFER" -> "Transfer"
            lowerBody.contains("recharge") || lowerBody.contains("desco") || lowerBody.contains("dpdc") ||
                lowerBody.contains("wasa") || lowerBody.contains("titas") || lowerBody.contains("bill") -> "Utilities & Bills"
            lowerBody.contains("shwapno") || lowerBody.contains("agora") || lowerBody.contains("meena") ||
                lowerBody.contains("unimart") -> "Groceries"
            lowerBody.contains("food") || lowerBody.contains("cafe") || lowerBody.contains("restaurant") ||
                lowerBody.contains("coffee") || lowerBody.contains("pizza") || lowerBody.contains("burger") -> "Food & Dining"
            lowerBody.contains("uber") || lowerBody.contains("pathao") || lowerBody.contains("mrt") ||
                lowerBody.contains("ticket") -> "Transport"
            else -> "Other"
        }

        val actionLabel = when {
            isAtmOrCashOutToHand -> "ATM Transfer"
            isCashOut -> "Cash Out"
            isIncoming -> "Income"
            lowerBody.contains("send money") -> "Send Money"
            lowerBody.contains("recharge") -> "Recharge"
            else -> "Expense"
        }

        val cpInfo = if (!counterparty.isNullOrBlank()) " • $counterparty" else ""
        val feeSuffix = if (feeAmount > 0.0) " (incl. ৳${feeAmount.toInt()} fee)" else ""
        val summary = "${actionLabel}: ৳${totalAmount.toInt()}$feeSuffix via $walletName$cpInfo (TrxID $trxId)"

        return PendingSmsDetectionEntity(
            id = "sms_${trxId}_${System.currentTimeMillis()}",
            provider = provider,
            type = type,
            amount = totalAmount,
            fee = feeAmount,
            trxId = trxId,
            wallet = walletName,
            toWallet = toWallet,
            category = inferredCategory,
            summaryText = summary,
            rawMessage = cleanBody,
            detectedDate = LocalDate.now().toString()
        )
    }
}
