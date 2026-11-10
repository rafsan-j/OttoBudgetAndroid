package com.rafsan.ottobudget

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.rafsan.ottobudget.data.local.AppDatabase
import com.rafsan.ottobudget.data.local.RecurringRuleEntity
import com.rafsan.ottobudget.domain.MfsSmsNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object RecurringAlarmScheduler {
    private const val CHANNEL_ID = "otto_recurring_channel"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recurring Bills & Income Confirmations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to confirm scheduled recurring expenses and income."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun scheduleRecurringNotification(context: Context, rule: RecurringRuleEntity) {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RecurringReminderReceiver::class.java).apply {
            putExtra("rule_id", rule.id)
            putExtra("rule_title", rule.title)
            putExtra("rule_amount", rule.amount)
            putExtra("rule_type", rule.type)
            putExtra("rule_wallet", rule.wallet)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            rule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!rule.enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, rule.dayOfMonth.coerceIn(1, maxDay))
            set(Calendar.HOUR_OF_DAY, rule.notifyHour.coerceIn(0, 23))
            set(Calendar.MINUTE, rule.notifyMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.MONTH, 1)
            }
        }

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }

    fun postImmediateNotification(
        context: Context,
        title: String,
        body: String,
        notifId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        ensureNotificationChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notifId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(notifId, notification) }
    }
}

class RecurringReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ottobudget_v6_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("recurring_feature_enabled", true)) return

        val title = intent.getStringExtra("rule_title") ?: "Scheduled Transaction"
        val amount = intent.getDoubleExtra("rule_amount", 0.0).toInt()
        val type = intent.getStringExtra("rule_type") ?: "EXPENSE"
        val wallet = intent.getStringExtra("rule_wallet") ?: "Cash"

        val verb = if (type == "INCOME") "Add +৳$amount to $wallet" else "Deduct -৳$amount from $wallet"
        RecurringAlarmScheduler.postImmediateNotification(
            context = context,
            title = "Confirm Recurring ${if (type == "INCOME") "Income" else "Expense"}: $title",
            body = "Tap to confirm: $verb (Balance updates only after your confirmation)."
        )
    }
}

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = context.getSharedPreferences("ottobudget_v6_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("sms_auto_parser_enabled", false)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "SMS"
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

        val parsed = MfsSmsNotificationParser.parseIncomingAlert(sender, fullBody) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(context).appDao().insertPendingSmsDetection(parsed)
            RecurringAlarmScheduler.postImmediateNotification(
                context = context,
                title = "Detected ${parsed.provider} Transaction",
                body = parsed.summaryText
            )
        }
    }
}

class OttoNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val prefs = applicationContext.getSharedPreferences("ottobudget_v6_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("sms_auto_parser_enabled", false)) return

        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text

        val combined = "$title $bigText".trim()
        val parsed = MfsSmsNotificationParser.parseIncomingAlert(pkg, combined) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(applicationContext).appDao().insertPendingSmsDetection(parsed)
        }
    }
}
