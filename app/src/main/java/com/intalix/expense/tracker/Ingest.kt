package com.intalix.expense.tracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun autoAdd(c: Context) = p(c).getBoolean("autoAdd", true)
    fun setAutoAdd(c: Context, v: Boolean) = p(c).edit().putBoolean("autoAdd", v).apply()
    fun defaultAccount(c: Context) = p(c).getLong("defaultAccount", 0L)
    fun setDefaultAccount(c: Context, id: Long) = p(c).edit().putLong("defaultAccount", id).apply()
    fun smsOn(c: Context) = p(c).getBoolean("smsOn", true)
    fun setSmsOn(c: Context, v: Boolean) = p(c).edit().putBoolean("smsOn", v).apply()
    fun notifOn(c: Context) = p(c).getBoolean("notifOn", true)
    fun setNotifOn(c: Context, v: Boolean) = p(c).edit().putBoolean("notifOn", v).apply()
    fun listenerAsked(c: Context) = p(c).getBoolean("listenerAsked", false)
    fun setListenerAsked(c: Context) = p(c).edit().putBoolean("listenerAsked", true).apply()
    fun lock(c: Context): String? = p(c).getString("lockHash", null)
    fun setLock(c: Context, hash: String?) = p(c).edit().apply { if (hash == null) remove("lockHash") else putString("lockHash", hash) }.apply()
    fun currency(c: Context) = p(c).getString("currency", "₹") ?: "₹"
    fun setCurrency(c: Context, v: String) = p(c).edit().putString("currency", v).apply()
}

sealed interface Outcome {
    data object Skipped : Outcome
    data object Duplicate : Outcome
    data class Added(val id: Long) : Outcome
    data class Queued(val id: Long) : Outcome
}

/**
 * The single place where a message becomes (or does not become) a record.
 * Everything is on-device: Room database, no network permission in the manifest.
 */
object Ingest {
    private const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L
    /** A bank SMS can lag the app notification by a while; match them within this window. */
    private const val MERGE_WINDOW_MS = 30 * 60 * 1000L
    /** A bank SMS can lag far behind the app notification. Beyond MERGE_WINDOW_MS it is only merged when the payee clearly matches. */
    private const val LATE_WINDOW_MS = 24 * 60 * 60 * 1000L
    /** SMS and notification for one payment arrive within milliseconds of each other; serialise so both can't slip past the duplicate check. */
    private val lock = Mutex()

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Bank/UPI senders are alphanumeric headers like "VM-KOTAKB-S". Plain phone numbers are people, not banks. */
    fun looksLikeBankSender(sender: String) = sender.any { it.isLetter() }

    suspend fun fromSms(ctx: Context, sender: String, body: String, timeMs: Long, quiet: Boolean = false): Outcome {
        if (!looksLikeBankSender(sender)) return Outcome.Skipped
        return process(ctx, sender, body, timeMs, "sms", quiet)
    }

    suspend fun fromNotification(ctx: Context, pkg: String, text: String, timeMs: Long): Outcome =
        process(ctx, pkg, text, timeMs, "notification")

    private suspend fun process(ctx: Context, sender: String, body: String, timeMs: Long, source: String, quiet: Boolean = false): Outcome =
        lock.withLock { processLocked(ctx, sender, body, timeMs, source, quiet) }

    private suspend fun processLocked(ctx: Context, sender: String, body: String, timeMs: Long, source: String, quiet: Boolean): Outcome {
        val dao = AppDb.get(ctx).dao()
        val key = sha("$source|$sender|$body|$timeMs")
        if (dao.markSeen(Seen(key, System.currentTimeMillis())) == -1L) return Outcome.Duplicate

        val result = if (source == "notification") NotificationParser.parse(body, sender) else SmsParser.parse(body, sender)
        val parsed = (result as? ParseResult.Txn)?.txn ?: return Outcome.Skipped
        val type = if (parsed.direction == Direction.DEBIT) 2 else 1
        val dirInt = type

        // 1) already have this exact payment? (same UPI ref + amount, or same amount in a 10-minute window)
        parsed.reference?.let { if (dao.findByRef(it, parsed.amountPaise, type) != null) return Outcome.Duplicate }

        // 1b) an SMS for a payment already added from its app notification: attach the reference instead of adding it again
        // SMS 30 min - 24 h after a same-amount notification entry: could be the same payment or a new one, so the user decides.
        var reviewReason: String? = null
        if (source == "sms") {
            var twinRec = dao.findUnmatchedNotifRec(parsed.amountPaise, type, timeMs - MERGE_WINDOW_MS, timeMs + MERGE_WINDOW_MS, timeMs)
            if (twinRec == null) {
                val late = dao.findUnmatchedNotifRec(parsed.amountPaise, type, timeMs - LATE_WINDOW_MS, timeMs + MERGE_WINDOW_MS, timeMs)
                if (late != null) reviewReason = "Same amount as an app-notification entry from earlier — is this a duplicate or a new payment?"
            }
            if (twinRec != null) {
                // no reference on the SMS: still mark the record as matched so a second SMS can't merge into it twice
                // The SMS names the account (XX1234); a payment-app notification cannot, so the entry may be on the default account.
                var merged = twinRec.copy(reference = parsed.reference ?: "sms:${sha(body).take(16)}")
                val bk = parsed.bank?.lowercase()
                val l4 = parsed.accountLast4
                val linked = if (l4 == null) null else (bk?.let { dao.link("$it:$l4") } ?: dao.link(l4))?.accountId
                if (linked != null && linked != merged.accountId && dao.accountsNow().any { it.id == linked }) merged = merged.copy(accountId = linked)
                dao.put(merged)
                return Outcome.Duplicate
            }
        }
        // Notifications look for a nearby entry to add the payee / note to; prefer one that has no payee yet (e.g. a UCO SMS).
        // An SMS is never a duplicate of a nearby entry here: the notification twin was handled above, and a bank that sends no
        // reference (UCO) must still let two same-amount payments through.
        val found = if (source == "notification") {
            val all = dao.findAllNear(parsed.amountPaise, type, timeMs - DEDUPE_WINDOW_MS, timeMs + DEDUPE_WINDOW_MS).sortedBy { Math.abs(it.time - timeMs) }
            (if (parsed.counterparty.isNullOrBlank()) null else all.firstOrNull { Payees.isPayeeless(it) }) ?: all.firstOrNull()
        } else null
        if (found != null) {
            var near: Rec = found
            // The SMS got there first: keep that record, but add the note the payer typed (only the notification has it).
            // A bank-app notification names the account (XX1234); a payment-app one cannot. If the entry went to the default account, fix it.
            if (source == "notification" && near.source == "notification" && parsed.accountLast4 != null) {
                val bk = parsed.bank?.lowercase()
                val linked = (bk?.let { dao.link("$it:${parsed.accountLast4}") } ?: dao.link(parsed.accountLast4))?.accountId
                if (linked != null && linked != near.accountId && dao.accountsNow().any { it.id == linked }) { near = near.copy(accountId = linked); dao.put(near) }
            }
            // The bank SMS named nobody (UCO) but this notification does: fill the payee in, keeping any note already there.
            val who = parsed.counterparty?.trim().orEmpty()
            var merged = near.note
            var rec = near
            if (who.isNotEmpty() && Payees.isPayeeless(near)) {
                merged = composeNote(type == 2, who, Payees.userNoteOf(near.note))
                Payees.seen(dao, who, type, timeMs)
                // an entry still in "Other" can now be categorised from the payee (rule first, then the keyword guesser)
                val cats = dao.categoriesNow()
                if (cats.firstOrNull { it.id == near.categoryId }?.name?.startsWith("Other") == true) {
                    val rule = dao.payee(merchantKey(who), type)?.takeIf { r -> r.categoryId != null && cats.any { it.id == r.categoryId && it.type == type } }
                    val guess = Classifier.classify(type, who, body)?.let { n -> cats.firstOrNull { it.type == type && it.name.equals(n, true) }?.id }
                    (rule?.categoryId ?: guess)?.let { rec = rec.copy(categoryId = it) }
                }
            }
            val un = parsed.userNote?.trim().orEmpty()
            if (un.isNotEmpty() && !merged.contains("(Note: $un)")) merged = if (merged.isEmpty()) "(Note: $un)" else "$merged (Note: $un)"
            if (merged != near.note || rec.categoryId != near.categoryId) {
                dao.put(rec.copy(note = merged))
                // refresh the "added" notification so it shows the note too
                if (!quiet) Notifier.added(ctx, parsed, merged, near.id)
            }
            return Outcome.Duplicate
        }

        // 2) an earlier notification-sourced pending item for the same payment gets replaced by this (richer) SMS
        var carriedNote: String? = null
        if (source == "sms") {
            val twin = parsed.reference?.let { dao.findPendingByRef(it, parsed.amountPaise, dirInt) }
                ?: dao.findPendingNear(parsed.amountPaise, dirInt, timeMs - MERGE_WINDOW_MS, timeMs + MERGE_WINDOW_MS)
            if (twin != null && twin.source == "notification") {
                carriedNote = NotificationParser.userNote(twin.raw)
                dao.setPendingStatus(twin.id, 2)
            }
        } else {
            val twinPending = dao.findPendingNear(parsed.amountPaise, dirInt, timeMs - MERGE_WINDOW_MS, timeMs + MERGE_WINDOW_MS)
            if (twinPending != null) {
                // The bank SMS is already waiting for review: keep it, but attach the note only the notification has.
                val un = parsed.userNote
                if (twinPending.source == "sms" && !un.isNullOrBlank() && !twinPending.raw.contains(NOTE_TAG)) {
                    dao.put(twinPending.copy(raw = twinPending.raw + NOTE_TAG + un))
                }
                return Outcome.Duplicate
            }
        }

        // 3) decide account + category
        val accounts = dao.accountsNow()
        val bankKey = parsed.bank?.lowercase()
        val link = parsed.accountLast4?.let { l -> (bankKey?.let { dao.link("$it:$l") } ?: dao.link(l)) }
            ?: bankKey?.let { dao.link(it) }
        val accountId = link?.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: Prefs.defaultAccount(ctx).takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id
        Payees.seen(dao, parsed.counterparty, type, timeMs)
        val cats = dao.categoriesNow()
        // the user's payee -> category lookup (Settings -> People & payees); only valid while the category still exists
        // A message with no payee (UCO) never matches a rule: there is nobody to look up.
        val rule = (if (merchantKey(parsed.counterparty).isEmpty()) null else dao.payee(merchantKey(parsed.counterparty), type))?.takeIf { r -> r.categoryId != null && cats.any { it.id == r.categoryId && it.type == type } }
        val fallbackCat = cats.firstOrNull { it.type == type && it.name.startsWith("Other") }?.id
            ?: cats.firstOrNull { it.type == type }?.id
        val guessed = Classifier.classify(type, parsed.counterparty, body)?.let { n -> cats.firstOrNull { it.type == type && it.name.equals(n, true) }?.id }
        val categoryId = rule?.categoryId ?: guessed ?: fallbackCat

        val autoOk = reviewReason == null && parsed.certain && accountId != null && categoryId != null &&
            (Prefs.autoAdd(ctx) || rule?.auto == true)

        val note = buildNote(parsed, carriedNote)
        if (autoOk) {
            val id = dao.put(
                Rec(accountId = accountId!!, categoryId = categoryId!!, type = type, amountPaise = parsed.amountPaise,
                    note = note, time = timeMs, source = source, reference = parsed.reference),
            )
            if (!quiet) Notifier.added(ctx, parsed, note, id)
            return Outcome.Added(id)
        }

        val reason = when {
            reviewReason != null -> reviewReason
            !parsed.certain -> parsed.why ?: "Needs review"
            source != "sms" -> "From payment-app notification"
            else -> "Needs review"
        }
        val pid = dao.put(
            Pending(time = timeMs, direction = dirInt, amountPaise = parsed.amountPaise, counterparty = parsed.counterparty,
                bank = parsed.bank, last4 = parsed.accountLast4, reference = parsed.reference, raw = body, sender = sender,
                source = source, reason = reason, suggestedCategoryId = categoryId, suggestedAccountId = accountId),
        )
        if (!quiet) Notifier.queued(ctx, parsed, pid)
        return Outcome.Queued(pid)
    }

    /** "To X" / "From X", followed by " (Note: what the payer typed)" when the payment app showed a note. */
    private fun buildNote(p: ParsedTxn, extraNote: String? = null): String =
        composeNote(p.direction == Direction.DEBIT, p.counterparty, p.userNote ?: extraNote,
            Payees.fallbackNote(p.direction == Direction.DEBIT, p.bank, p.accountLast4))


    /** [fallback] is used instead of "To X" when the message named nobody (see [Payees.fallbackNote]). */
    internal fun composeNote(debit: Boolean, who: String?, userNote: String?, fallback: String? = null): String {
        val base = if (who.isNullOrBlank()) fallback.orEmpty() else if (debit) "To $who" else "From $who"
        val n = NotificationParser.sanitizeNote(userNote).orEmpty()
        return when {
            n.isEmpty() -> base
            base.isEmpty() -> "(Note: $n)"
            else -> "$base (Note: $n)"
        }
    }

    /** Re-categorise records still sitting in "Other"/"Other income". Never touches a category the user chose. */
    suspend fun recategorize(ctx: Context): Int {
        val dao = AppDb.get(ctx).dao()
        val cats = dao.categoriesNow()
        val rules = dao.payeesNow().associateBy { it.key to it.type }
        var n = 0
        for (r in dao.recordsNow()) {
            val cur = cats.firstOrNull { it.id == r.categoryId } ?: continue
            if (!cur.name.startsWith("Other")) continue
            val payee = Payees.fromNote(r.note) ?: continue
            val target = rules[merchantKey(payee) to r.type]?.categoryId?.takeIf { id -> cats.any { it.id == id && it.type == r.type } }
                ?: Classifier.classify(r.type, payee)?.let { nm -> cats.firstOrNull { it.type == r.type && it.name.equals(nm, true) }?.id }
                ?: continue
            if (target != r.categoryId) { dao.put(r.copy(categoryId = target)); n++ }
        }
        return n
    }

    private const val NOTE_TAG = "\n[app-note] "

    /** The payer's note for a queued item: parsed from a notification, or attached to a queued SMS by a later notification. */
    private fun pendingNote(p: Pending): String? =
        if (p.source == "notification") NotificationParser.userNote(p.raw)
        else p.raw.substringAfter(NOTE_TAG, "").takeIf { it.isNotBlank() }

    /** Turn a queued item into a real record. Optionally remember merchant → category for next time. */
    suspend fun accept(ctx: Context, p: Pending, accountId: Long, categoryId: Long, remember: Boolean, auto: Boolean) {
        val dao = AppDb.get(ctx).dao()
        val type = p.direction
        val note = composeNote(type == 2, p.counterparty, pendingNote(p), Payees.fallbackNote(type == 2, p.bank, p.last4))
        dao.put(Rec(accountId = accountId, categoryId = categoryId, type = type, amountPaise = p.amountPaise, note = note,
            time = p.time, source = p.source, reference = p.reference))
        dao.setPendingStatus(p.id, 1)
        Payees.seen(dao, p.counterparty, type, p.time)
        if (remember && merchantKey(p.counterparty).isNotEmpty()) {
            dao.setPayeeRule(merchantKey(p.counterparty), type, categoryId, auto)
        }
        if (!p.last4.isNullOrBlank()) {
            val k = p.bank?.lowercase()?.let { "$it:${p.last4}" } ?: p.last4
            if (dao.link(k) == null) dao.put(AccountLink(k, accountId))
        }
    }
}

object Notifier {
    private const val CH = "txn"
    private fun ensure(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(NotificationChannel(CH, "Detected payments", NotificationManager.IMPORTANCE_DEFAULT))
        return nm
    }

    private fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33

    private fun money(p: ParsedTxn) = "₹" + Money.format(p.amountPaise)

    fun added(ctx: Context, p: ParsedTxn, note: String, id: Long) = post(
        ctx, id.toInt(), "${money(p)} ${if (p.direction == Direction.DEBIT) "spent" else "received"} — added",
        note.ifEmpty { "Tap to review or change category" },
    )

    fun queued(ctx: Context, p: ParsedTxn, id: Long) = post(
        ctx, (1_000_000 + id).toInt(), "${money(p)} ${if (p.direction == Direction.DEBIT) "paid" else "received"} — needs your review",
        p.counterparty ?: "Open the app to confirm",
    )

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        if (!allowed(ctx)) return
        val nm = ensure(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            id,
            NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setColor(0xFF14604C.toInt()).setContentTitle(title)
                .setContentText(text).setAutoCancel(true).setContentIntent(open).build(),
        )
    }
}

object Money {
    /** 123456 -> "1,234.56"; whole amounts drop the decimals ("350"). Indian digit grouping. */
    fun format(paise: Long): String {
        val neg = paise < 0
        val a = Math.abs(paise)
        val rupees = a / 100
        val frac = (a % 100).toInt()
        val s = rupees.toString()
        val grouped = if (s.length <= 3) s else {
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        return (if (neg) "-" else "") + grouped + if (frac == 0) "" else "." + frac.toString().padStart(2, '0')
    }

    /** Parse user typed "1,200.5" into paise; null if invalid. */
    fun parse(s: String): Long? {
        val t = s.replace(",", "").trim()
        if (t.isEmpty() || !t.matches(Regex("""\d+(\.\d{0,2})?"""))) return null
        return runCatching { java.math.BigDecimal(t).movePointRight(2).toBigInteger().toLong() }.getOrNull()
    }
}
