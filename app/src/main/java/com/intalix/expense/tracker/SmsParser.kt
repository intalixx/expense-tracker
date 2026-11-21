package com.intalix.expense.tracker

import com.intalix.expense.tracker.parse.Banks
import com.intalix.expense.tracker.parse.cleanPayee
import java.math.BigDecimal

enum class Direction { DEBIT, CREDIT }

/** A payment message that was understood. [certain] is false when the text was ambiguous. */
data class ParsedTxn(
    val direction: Direction,
    val amountPaise: Long,
    val counterparty: String?,
    val accountLast4: String?,
    val reference: String?,
    val bank: String?,
    val certain: Boolean,
    val why: String? = null,
    /** Text the payer typed while paying (only available from payment-app notifications). */
    val userNote: String? = null,
)

sealed interface ParseResult {
    data class Txn(val txn: ParsedTxn) : ParseResult
    /** Not a completed payment (OTP, promo, failed, pending, balance-only ...). Never creates an entry. */
    data class Ignored(val reason: String) : ParseResult
}

/**
 * Pure-Kotlin parser for Indian bank / UPI transaction texts.
 * Design rule: when in doubt, either ignore the message or return certain=false
 * (which sends it to the review list) — it must never silently guess a wrong entry.
 */
object SmsParser {

    private val rejectPhrases = listOf(
        "otp", "one time password", "one-time password", "verification code", "do not share",
        "will be debited", "will be credited", "has been requested", "payment request", "collect request",
        "requested money", "requests rs", "request of rs", "failed", "declined", "unsuccessful", "not successful",
        "could not be processed", "insufficient", "reversed", "reversal", "is due", "due on", "due by",
        "minimum amount due", "statement", "pre-approved", "offer", "apply now", "loan of", "cashback of",
        "click ", "kyc", "mandate", "autopay", "e-mandate", "standing instruction", "scheduled",
    )

    private val debitWords = Regex("""\b(debited|sent|paid|spent|withdrawn|purchase|purchased|transferred to|payment of|deducted)\b""")
    private val creditWords = Regex("""\b(credited|received|deposited|refund(?:ed)?)\b""")

    private val amountRe = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val acctRe = Regex(
        """(?:a/c|a/c\.|ac|acct|account|card)\s*(?:no\.?|number)?\s*(?:ending(?:\s*with)?|[x*]+)?\s*([0-9]{3,4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val refRe = Regex("""(?:upi\s*ref(?:erence)?(?:\s*(?:no|number|id))?\.?\s*:?\s*|rrn\s*:?\s*|ref(?:erence)?\s*(?:no|number|id)?\.?\s*:?\s*|txn\s*id\s*:?\s*)([0-9]{4,})""", RegexOption.IGNORE_CASE)
    private val toRe = Regex("""\b(?:to|at|towards|info:?)\s+(?:vpa\s+)?(.+?)(?=\s+(?:on|via|using|ref|upi|txn|if|not|call|avl|bal|is)\b|\.\s|\.$|,|$)""", RegexOption.IGNORE_CASE)
    private val fromRe = Regex("""\bfrom\s+(?:vpa\s+)?(.+?)(?=\s+(?:on|via|using|ref|upi|txn|if|not|call|avl|bal|is)\b|\.\s|\.$|,|$)""", RegexOption.IGNORE_CASE)

    private val banks = listOf(
        "kotak" to "Kotak", "hdfc" to "HDFC", "icici" to "ICICI", "sbi" to "SBI", "axis" to "Axis",
        "yes bank" to "Yes Bank", "indusind" to "IndusInd", "idfc" to "IDFC First", "pnb" to "PNB",
        "bank of baroda" to "Bank of Baroda", "canara" to "Canara", "union bank" to "Union Bank",
        "federal" to "Federal", "paytm" to "Paytm Bank", "au small" to "AU Small Finance",
    )

    /** True if the text contains a rupee amount at all (used by the "skipped messages" view to ignore chatter). */
    fun mentionsAmount(body: String) = amountRe.containsMatchIn(body)

    private val moneyWords = Regex("""\b(credit(?:ed)?|debit(?:ed)?|cr|dr|sent|received|paid|payment|salary|neft|imps|rtgs|upi|transfer(?:red)?|deposit(?:ed)?|withdr[a-z]*|refund(?:ed)?|spent|purchase[d]?|txn|transaction)\b""")

    /** Broad "this might be money moving" test - wider than the strict parser on purpose. */
    fun looksLikeMoneyMove(body: String) = moneyWords.containsMatchIn(body.lowercase())

    /**
     * [sender] is the SMS sender ID ("VM-KOTAKB-S"). When it belongs to a bank in [Banks], that bank's templates are tried
     * first; anything they do not cover falls through to the generic rules below (so unknown wording still works).
     */
    fun parse(body: String, sender: String? = null): ParseResult {
        val text = body.replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()
        val knownBank = Banks.forSender(sender)

        for (p in rejectPhrases + (knownBank?.reject ?: emptyList())) {
            if (lower.contains(p)) return ParseResult.Ignored("contains \"$p\"")
        }

        knownBank?.matchSms(text)?.let { h ->
            return ParseResult.Txn(
                ParsedTxn(h.direction, h.amountPaise, h.payee?.let(::cleanPayee), h.account, h.reference, knownBank.name, certain = true),
            )
        }

        val hasDebit = debitWords.containsMatchIn(lower)
        val hasCredit = creditWords.containsMatchIn(lower)
        if (!hasDebit && !hasCredit) return ParseResult.Ignored("no debit/credit word")

        val amount = pickAmount(text) ?: return ParseResult.Ignored("no amount")

        val direction: Direction
        var certain = true
        var why: String? = null
        when {
            hasDebit && hasCredit -> {
                // e.g. "debited from your a/c and credited to X" — decide by the first keyword but flag for review.
                val d = debitWords.find(lower)!!.range.first
                val c = creditWords.find(lower)!!.range.first
                direction = if (d < c) Direction.DEBIT else Direction.CREDIT
                certain = false
                why = "Both debit and credit words in message"
            }
            hasDebit -> direction = Direction.DEBIT
            else -> direction = Direction.CREDIT
        }

        val counterparty = pickCounterparty(text, direction)
        val last4 = acctRe.find(text)?.groupValues?.get(1)
        val ref = refRe.find(text)?.groupValues?.get(1)
        val bank = knownBank?.name ?: banks.firstOrNull { lower.contains(it.first) }?.second

        return ParseResult.Txn(ParsedTxn(direction, amount, counterparty, last4, ref, bank, certain, why))
    }

    /** First rupee amount that is not a balance/limit figure, in paise. Null if none or unparseable. */
    private fun pickAmount(text: String): Long? {
        for (m in amountRe.findAll(text)) {
            val before = text.substring((m.range.first - 25).coerceAtLeast(0), m.range.first).lowercase()
            if (before.contains("bal") || before.contains("avl") || before.contains("limit") || before.contains("available")) continue
            val v = runCatching { BigDecimal(m.groupValues[1].replace(",", "")) }.getOrNull() ?: return null
            val paise = v.movePointRight(2).toBigInteger()
            if (paise.signum() <= 0 || paise.bitLength() > 62) return null
            return paise.toLong()
        }
        return null
    }

    private fun pickCounterparty(text: String, dir: Direction): String? {
        val primary = if (dir == Direction.DEBIT) toRe else fromRe
        val secondary = if (dir == Direction.DEBIT) fromRe else toRe
        for (re in listOf(primary, secondary)) {
            for (m in re.findAll(text)) {
                val c = clean(m.groupValues[1])
                if (c != null && !looksLikeAccount(c)) return c
            }
        }
        return null
    }

    private fun looksLikeAccount(s: String): Boolean {
        val l = s.lowercase()
        return acctRe.containsMatchIn(s) || l.contains("bank") || l.startsWith("a/c") || l.startsWith("ac ") ||
            l.matches(Regex("""[x*\d\s]+""")) || l.contains("your ")
    }

    private fun clean(raw: String): String? = cleanPayee(raw)
}
