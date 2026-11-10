package com.rafsan.ottobudget.domain

import com.rafsan.ottobudget.data.local.PendingSmsDetectionEntity
import java.time.LocalDate

/**
 * 100% Offline On-Device Regex Parser for Bangladeshi Mobile Financial Services (bKash, Nagad, Rocket)
 * and Commercial Bank SMS/Push Notifications (City Bank, BRAC, EBL, DBBL, Islami Bank).
 * Extracts Amount, Fee, TrxID, Provider/Wallet, and Transaction Direction without internet access.
 */
object MfsSmsNotificationParser {

    private val amountRegex = Regex("(?:Tk|BDT|৳)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val feeRegex = Regex("(?:Fee|Charge)\\s*(?:Tk|BDT|৳)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val trxIdRegex = Regex("(?:TrxID|TxnId|TxID|Ref)\\s*[:#-]?\\s*([A-Z0-9]{6,14})", RegexOption.IGNORE_CASE)

    fun parseIncomingAlert(senderOrPkg: String, bodyText: String): PendingSmsDetectionEntity? {
        val cleanBody = bodyText.replace("\n", " ").trim()
        if (cleanBody.length < 12) return null

        val lowerSender = senderOrPkg.lowercase()
        val lowerBody = cleanBody.lowercase()

        // Identify Provider & Target Wallet
        val provider = when {
            lowerSender.contains("bkash") || lowerBody.contains("bkash") || lowerBody.contains("trxid") && lowerBody.contains("tk ") -> "bKash"
            lowerSender.contains("nagad") || lowerBody.contains("nagad") -> "Nagad"
            lowerSender.contains("rocket") || lowerSender.contains("16216") || lowerBody.contains("rocket") -> "Rocket"
            lowerSender.contains("bank") || lowerSender.contains("city") || lowerSender.contains("brac") ||
                lowerSender.contains("ebl") || lowerSender.contains("dbbl") || lowerBody.contains("card") ||
                lowerBody.contains("a/c") || lowerBody.contains("debited") || lowerBody.contains("credited") -> "Bank Card"
            else -> return null
        }

        val amountMatch = amountRegex.find(cleanBody) ?: return null
        val rawAmount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (rawAmount <= 0.0) return null

        val feeAmount = feeRegex.find(cleanBody)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val trxId = trxIdRegex.find(cleanBody)?.groupValues?.get(1)?.uppercase() ?: "AUTO_${System.currentTimeMillis() % 10000}"

        // Determine Transaction Type (INCOME, EXPENSE, or TRANSFER such as Cash Out / ATM Withdrawal)
        val isCashOutOrAtm = lowerBody.contains("cash out") || lowerBody.contains("cash-out") || lowerBody.contains("atm withdrawal") || lowerBody.contains("withdrawn")
        val isIncoming = lowerBody.contains("received") || lowerBody.contains("cash in") || lowerBody.contains("cash-in") ||
            lowerBody.contains("credited") || lowerBody.contains("deposit")

        val type = when {
            isCashOutOrAtm && provider == "Bank Card" -> "TRANSFER" // ATM withdrawal -> Card to Cash
            isIncoming -> "INCOME"
            else -> "EXPENSE"
        }

        val totalDeducted = if (type == "EXPENSE") rawAmount + feeAmount else rawAmount
        val walletName = when (provider) {
            "bKash" -> "bKash"
            "Nagad" -> "Nagad"
            "Rocket" -> "Rocket"
            else -> "Bank Card"
        }
        val toWallet = if (type == "TRANSFER") "Cash" else null

        val inferredCategory = when {
            type == "INCOME" -> "Salary & Job"
            type == "TRANSFER" -> "Transfer"
            lowerBody.contains("recharge") || lowerBody.contains("desco") || lowerBody.contains("dpdc") || lowerBody.contains("bill") -> "Utilities & Bills"
            lowerBody.contains("shwapno") || lowerBody.contains("agora") || lowerBody.contains("meena") -> "Groceries"
            lowerBody.contains("food") || lowerBody.contains("cafe") || lowerBody.contains("restaurant") || lowerBody.contains("foodpanda") -> "Food & Dining"
            lowerBody.contains("uber") || lowerBody.contains("pathao") || lowerBody.contains("mrt") -> "Transport"
            else -> "Other"
        }

        val actionVerb = when {
            isCashOutOrAtm -> "Cash Out / ATM"
            isIncoming -> "Received"
            lowerBody.contains("send money") -> "Send Money"
            lowerBody.contains("payment") || lowerBody.contains("purchase") -> "Payment"
            else -> "Alert"
        }

        val feeSuffix = if (feeAmount > 0.0) " (+৳${feeAmount.toInt()} fee)" else ""
        val summary = "Log ৳${totalDeducted.toInt()} ${if (type == "INCOME") "to" else "from"} $walletName? ($actionVerb$feeSuffix • TrxID $trxId)"

        return PendingSmsDetectionEntity(
            id = "sms_${trxId}_${System.currentTimeMillis()}",
            provider = provider,
            type = type,
            amount = totalDeducted,
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
