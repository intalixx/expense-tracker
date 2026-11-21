package com.intalix.expense.tracker

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.smsOn(context)) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        // A long SMS arrives in parts: join the parts that share a sender.
        val bySender = LinkedHashMap<String, StringBuilder>()
        var time = System.currentTimeMillis()
        for (m in msgs) {
            bySender.getOrPut(m.originatingAddress ?: "") { StringBuilder() }.append(m.messageBody ?: "")
            if (m.timestampMillis > 0) time = m.timestampMillis
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, body) in bySender) Ingest.fromSms(context.applicationContext, sender, body.toString(), time)
            } finally {
                pending.finish()
            }
        }
    }
}

class PaymentNotificationListener : NotificationListenerService() {
    private val watched = com.intalix.expense.tracker.parse.Watched.packages

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.notifOn(this) || sbn.packageName !in watched) return
        val ex = sbn.notification.extras
        // Chat-style notifications relay text other people typed ("X sent you ₹500"), so they must not be read as payments.
        if (ex.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") == true) return
        // Read every text field: the payer's own note often sits in the expanded text or in a separate line.
        val text = buildList {
            add(ex.getCharSequence(Notification.EXTRA_TITLE)); add(ex.getCharSequence(Notification.EXTRA_BIG_TEXT))
            add(ex.getCharSequence(Notification.EXTRA_TEXT)); add(ex.getCharSequence(Notification.EXTRA_SUB_TEXT))
            add(ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
        }.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct().joinToString("\n")
        if (text.isBlank()) return
        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch { Ingest.fromNotification(ctx, sbn.packageName, text, sbn.postTime) }
    }
}

/** Catches up on messages that arrived while the phone was off or the process was not allowed to run. */
object InboxScanner {
    /** sinceMs = 0 scans the whole inbox. Runs off the main thread and posts no per-message notifications. */
    suspend fun scan(ctx: Context, sinceMs: Long): Int = kotlinx.coroutines.withContext(Dispatchers.IO) {
        var added = 0
        val cur = ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
            "date >= ?", arrayOf(sinceMs.toString()), "date ASC",
        ) ?: return@withContext 0
        cur.use {
            while (it.moveToNext()) {
                val r = Ingest.fromSms(ctx, it.getString(0) ?: "", it.getString(1) ?: "", it.getLong(2), quiet = true)
                if (r is Outcome.Added || r is Outcome.Queued) added++
            }
        }
        added
    }
}

/** A bank-looking message that did NOT become a record or a review item, and why. */
data class SkippedMsg(val sender: String, val body: String, val time: Long, val reason: String, val likely: Boolean)

/** Read-only: re-reads the inbox and lists money-looking messages the app ignored. Stores nothing. */
object SkippedScanner {
    suspend fun scan(ctx: Context, sinceMs: Long): List<SkippedMsg> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val out = ArrayList<SkippedMsg>()
        val cur = ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
            "date >= ?", arrayOf(sinceMs.toString()), "date DESC",
        ) ?: return@withContext out
        cur.use {
            while (it.moveToNext()) {
                val sender = it.getString(0) ?: ""
                val body = it.getString(1) ?: ""
                val time = it.getLong(2)
                if (!SmsParser.mentionsAmount(body)) continue
                val reason = when {
                    !Ingest.looksLikeBankSender(sender) -> "Sender is a phone number, not a bank name"
                    else -> (SmsParser.parse(body, sender) as? ParseResult.Ignored)?.reason?.let { r -> "Ignored: $r" }
                } ?: continue
                out.add(SkippedMsg(sender, body, time, reason, SmsParser.looksLikeMoneyMove(body)))
            }
        }
        out
    }
}
