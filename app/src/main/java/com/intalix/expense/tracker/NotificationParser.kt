package com.intalix.expense.tracker

import com.intalix.expense.tracker.parse.Bank
import com.intalix.expense.tracker.parse.Banks
import com.intalix.expense.tracker.parse.PaymentApp
import com.intalix.expense.tracker.parse.PaymentApps

/**
 * Parser for payment-app notifications (Google Pay and any app listed in `PaymentApps`). Pure Kotlin so it can be unit tested.
 *
 * Unlike bank SMS, these texts are written from the user's point of view ("Paid ₹100 to X",
 * "X sent you ₹100"), so [SmsParser] would read "X sent you" as a debit. Hence a separate parser.
 * The real wording of these apps is not documented and changes; anything unrecognised is skipped,
 * and a payment with no clear payee is returned with certain = false so it goes to the review list.
 *
 * Input is every text line of the notification (title, text, big text, lines ...) joined with '\n'.
 */
object NotificationParser {
    private const val AMT = """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    // The payee ends at a separator, end of text, or a word that starts the next clause.
    private const val WHO = """([^|·•]+?)(?=\s*(?:[|·•]|$)|\s+(?:for|note|via|using|from|on|at|successful|successfully|completed|has|was|is|of|with)\b|[.!]\s|[.!]$)"""

    private val reject = listOf(
        "request", "pending", "failed", "declined", "unsuccessful", "not successful", "cashback", "reward", "scratch",
        "offer", "reminder", "will be", "initiated", "refund", "expire", "invite", "bill is due", "due on", "autopay", "mandate",
        "otp", "verification",
    )

    private val credit = listOf(
        Regex("""(?:^|\|)\s*([^|]+?)\s+(?:has\s+)?(?:sent|paid)\s+you\s+$AMT""", RegexOption.IGNORE_CASE),
        Regex("""received\s+$AMT\s+from\s+$WHO""", RegexOption.IGNORE_CASE),
        Regex("""$AMT\s+(?:was\s+|is\s+)?received\s+from\s+$WHO""", RegexOption.IGNORE_CASE),
    )
    private val debit = listOf(
        Regex("""(?:you\s+)?paid\s+$AMT\s+to\s+$WHO""", RegexOption.IGNORE_CASE),
        Regex("""payment\s+to\s+$WHO\s+of\s+$AMT""", RegexOption.IGNORE_CASE),
        Regex("""(?:your\s+)?$AMT\s+(?:payment\s+)?(?:was\s+|is\s+)?(?:paid\s+|sent\s+)?to\s+$WHO""", RegexOption.IGNORE_CASE),
        Regex("""(?:you\s+)?sent\s+$AMT\s+to\s+$WHO""", RegexOption.IGNORE_CASE),
    )
    private val bareSuccess = Regex("""$AMT\s+(?:payment\s+)?(?:successful|successfully\s+paid|sent|paid)""", RegexOption.IGNORE_CASE)

    private val noteMarker = Regex("""(?:note|message|remarks?|description)\s*[:\-]\s*([^|]{1,200})""", RegexOption.IGNORE_CASE)
    private val forNote = Regex("""^\s*(?:successful(?:ly)?|completed)?\s*for\s+(.+?)\s*(?:\||$)""", RegexOption.IGNORE_CASE)
    private val refRe = Regex(
        """(?:ref(?:erence)?\.?\s*(?:no\.?|number|id)?|transaction\s*id|txn\s*id|utr(?:\s*no\.?)?)\s*[:#\-]?\s*(\d{10,16})""",
        RegexOption.IGNORE_CASE,
    )

    // "Amount debited from XX1234." / "credited to A/c XX1234": last digits of the account, and which way the money moved.
    private val last4Re = Regex("""(?:a/c|acct?|account)?\s*(?:no\.?)?\s*(?:x{1,}|\*{1,})\s*(\d{3,4})\b""", RegexOption.IGNORE_CASE)
    private val debitedWord = Regex("""\b(?:debited|withdrawn|deducted)\b""", RegexOption.IGNORE_CASE)
    private val creditedWord = Regex("""\b(?:credited|deposited)\b""", RegexOption.IGNORE_CASE)

    /**
     * [pkg] is the app that posted the notification. If it is a bank app listed in [Banks], that bank's `notif` templates
     * are tried first; anything they do not cover falls through to the generic rules below.
     */
    fun parse(text: String, pkg: String? = null): ParseResult {
        val bank = Banks.forPackage(pkg)
        val r = parseInner(text, bank, PaymentApps.forPackage(pkg))
        val txn = (r as? ParseResult.Txn)?.txn ?: return r
        if (txn.bank != null) return r // a bank template already filled in everything
        val t = text.replace(Regex("""\s+"""), " ")
        return ParseResult.Txn(txn.copy(accountLast4 = last4Re.find(t)?.groupValues?.get(1), bank = bank?.name))
    }

    private fun parseInner(text: String, bank: Bank?, app: PaymentApp?): ParseResult {
        val t = text.replace(Regex("""\s*\n+\s*"""), " | ").replace(Regex("""[ \t]+"""), " ").trim()
        if (t.isEmpty()) return ParseResult.Ignored("empty")
        val low = t.lowercase()
        (reject + (bank?.reject ?: emptyList()) + (app?.reject ?: emptyList())).firstOrNull { low.contains(it) }?.let { return ParseResult.Ignored("not a completed payment ($it)") }
        if (!Regex("""₹|rs\.?\s*\d|inr""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return ParseResult.Ignored("no amount")

        bank?.matchNotif(t)?.let { h ->
            val payee = h.payee?.trim()?.trim('.', '!', ',', '-', '·', '•')?.replace(Regex("""\s+"""), " ")?.takeIf { it.length in 2..60 }
            return ParseResult.Txn(ParsedTxn(h.direction, h.amountPaise, payee, h.account, h.reference, bank.name, certain = true))
        }

        // A payment app may list its own templates for wording the generic rules below get wrong (see PaymentApp.notif).
        app?.matchNotif(t)?.let { h ->
            val payee = h.payee?.trim()?.trim('.', '!', ',', '-', '·', '•')?.replace(Regex("""\s+"""), " ")?.takeIf { it.length in 2..60 }
            val note = sanitizeNote(noteMarker.find(t)?.groupValues?.get(1)?.trim()?.trim('.', ' ', '-', '·'))
            return ParseResult.Txn(ParsedTxn(h.direction, h.amountPaise, payee, h.account, h.reference, null, certain = payee != null, userNote = note))
        }

        val ref = refRe.find(t)?.groupValues?.get(1)

        // Credit first: "X paid you ₹5" must not be read as a debit.
        for ((i, re) in credit.withIndex()) {
            val m = re.find(t) ?: continue
            // regex 0 captures (who, amount); 1/2 capture (amount, who)
            val (w, a) = if (i == 0) m.groupValues[1] to m.groupValues[2] else m.groupValues[2] to m.groupValues[1]
            return build(Direction.CREDIT, a, w, t, m.range.last, ref)
        }
        for ((i, re) in debit.withIndex()) {
            val m = re.find(t) ?: continue
            // regex 1 captures (who, amount); the others (amount, who)
            val (w, a) = if (i == 1) m.groupValues[1] to m.groupValues[2] else m.groupValues[2] to m.groupValues[1]
            return build(Direction.DEBIT, a, w, t, m.range.last, ref)
        }
        // Bank-app wording ("Amount debited from XX1234") with no recognisable payee: the direction is still certain, the payee is not.
        val amt = Regex(AMT, RegexOption.IGNORE_CASE).find(t)
        if (amt != null) {
            val dir = when {
                debitedWord.containsMatchIn(t) && !creditedWord.containsMatchIn(t) -> Direction.DEBIT
                creditedWord.containsMatchIn(t) && !debitedWord.containsMatchIn(t) -> Direction.CREDIT
                else -> null
            }
            if (dir != null) return build(dir, amt.groupValues[1], null, t, amt.range.last, ref)
        }
        bareSuccess.find(t)?.let { m ->
            // Amount but no payee: keep it, but only for review.
            return build(Direction.DEBIT, m.groupValues[1], null, t, m.range.last, ref)
        }
        return ParseResult.Ignored("not a payment notification")
    }

    private fun build(dir: Direction, amount: String, who: String?, t: String, matchEnd: Int, ref: String?): ParseResult {
        val paise = Money.parse(amount.replace(",", "")) ?: return ParseResult.Ignored("bad amount")
        if (paise <= 0) return ParseResult.Ignored("zero amount")
        val payee = who?.trim()?.trim('.', '!', ',', '-', '·', '•')?.replace(Regex("""\s+"""), " ")?.takeIf { it.length in 2..60 }
        val rest = if (matchEnd + 1 <= t.length) t.substring(matchEnd + 1) else ""
        val note = noteMarker.find(t)?.groupValues?.get(1)?.trim()
            ?: forNote.find(rest)?.groupValues?.get(1)?.trim()
            ?: plainNoteLine(rest)
        val userNote = sanitizeNote(note?.trim('.', ' ', '-', '·'))
        return ParseResult.Txn(
            ParsedTxn(
                direction = dir, amountPaise = paise, counterparty = payee, accountLast4 = null, reference = ref, bank = null,
                certain = payee != null, why = if (payee == null) "Payment seen in app notification but payee unclear" else null,
                userNote = userNote,
            ),
        )
    }


    private val boiler = Regex("""₹|\brs\.?\s*\d|\binr\b|\bref\b|\butr\b|\bupi\b|transaction|\bsuccessful|\bcompleted\b|\bbank\b|a/c|\baccount\b|\btap\b|\bview\b|\bpaid\b|\breceived\b|\bdebited\b|\bcredited\b|\bdetails\b""", RegexOption.IGNORE_CASE)

    /** GPay shows the payer's note as its own line under the message ("X paid you ₹1.00" / "Ek rupee with note"). */
    private fun plainNoteLine(rest: String): String? =
        rest.split('|').map { it.trim() }.firstOrNull { it.isNotEmpty() }?.takeIf { it.length <= 200 && !boiler.containsMatchIn(it) }

    /**
     * Notes are text typed by another person, so make them safe to store and show: drop control, zero-width and
     * direction-override characters, collapse whitespace, cap the length. Null when nothing is left.
     */
    private val invisible = Regex("[\u0000-\u0008\u000B-\u001F\u007F-\u009F\u200B-\u200F\u2028\u2029\u202A-\u202E\u2060\u2066-\u2069\uFEFF]")

    fun sanitizeNote(raw: String?): String? {
        if (raw == null) return null
        val clean = raw.replace(invisible, "").replace(Regex("""\s+"""), " ").trim()
        if (clean.isEmpty()) return null
        return if (clean.length > 200) clean.take(200).trimEnd() + "…" else clean
    }

    /** The note the user typed while paying, if the notification carried one. Used when accepting a queued item. */
    fun userNote(text: String): String? = ((parse(text) as? ParseResult.Txn)?.txn)?.userNote
}
