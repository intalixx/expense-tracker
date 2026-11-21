package com.intalix.expense.tracker.parse

import com.intalix.expense.tracker.Direction
import com.intalix.expense.tracker.Money

/**
 * One bank (or bank app) the parser knows about. To add a bank, create `parse/banks/<Name>.kt` with one of these
 * and add it to [Banks.all]. See CONTRIBUTING.md.
 *
 * @param name     Shown in the app and stored in account links ("kotak:1234"). NEVER rename a bank that has shipped.
 * @param senders  SMS sender IDs of the bank, without the operator prefix / suffix: "KOTAKB" matches "VM-KOTAKB-S".
 * @param apps     Android package names of the bank's app, so its notifications are read too.
 * @param sms      Templates for the bank's SMS texts, tried in order, first match wins.
 * @param notif    Templates for the bank app's notification text. The lines of a notification are joined with " | ".
 * @param reject   Extra phrases (lower case) that mean "this is not a completed payment". Added to the global list.
 */
class Bank(
    val name: String,
    val senders: List<String> = emptyList(),
    val apps: List<String> = emptyList(),
    val sms: List<Template> = emptyList(),
    val notif: List<Template> = emptyList(),
    val reject: List<String> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Bank needs a name" }
        require(senders.all { it.length >= 4 && it == it.uppercase() && !it.contains(' ') }) {
            "$name: sender IDs must be UPPER CASE, at least 4 characters, no spaces (e.g. \"KOTAKB\")"
        }
        require(reject.all { it == it.lowercase() }) { "$name: reject phrases must be lower case" }
    }

    /** First SMS template that matches, or null. */
    fun matchSms(text: String): Hit? = first(sms, text)

    /** First notification template that matches, or null. */
    fun matchNotif(text: String): Hit? = first(notif, text)

    private fun first(list: List<Template>, text: String): Hit? {
        for (t in list) t.match(text)?.let { return it }
        return null
    }
}

/** What a template pulled out of a message. */
class Hit(
    val template: Template,
    val direction: Direction,
    val amountPaise: Long,
    val account: String?,
    val payee: String?,
    val reference: String?,
)

/**
 * A message shape written as a plain sentence with placeholders (see [PLACEHOLDERS]). Build one with [debit] or [credit].
 * Letter case and runs of spaces do not matter. Everything that is not a placeholder must match literally.
 */
class Template internal constructor(val direction: Direction, val source: String, val regex: Regex) {
    fun match(text: String): Hit? {
        val m = regex.find(text) ?: return null
        fun g(name: String): String? = runCatching { m.groups[name]?.value }.getOrNull()?.takeIf { it.isNotBlank() }
        val paise = g("amount")?.let { Money.parse(it) }?.takeIf { it > 0 && it < Long.MAX_VALUE / 4 } ?: return null
        return Hit(this, direction, paise, g("account"), g("payee"), g("ref"))
    }
}

/** A money-out message. Example: `debit("Rs.{amount} debited from A/c XX{acct} to {payee}. UPI Ref {ref}")`. */
fun debit(pattern: String) = Template(Direction.DEBIT, pattern, expand(pattern))

/** A money-in message. */
fun credit(pattern: String) = Template(Direction.CREDIT, pattern, expand(pattern))

/**
 * Escape hatch for wording the placeholders cannot express. Plain regex, case-insensitive; it MUST contain the named
 * group (?<amount>...) and may contain (?<account>...), (?<payee>...), (?<ref>...). Prefer [debit] / [credit].
 */
fun debitRegex(regex: String) = rawTemplate(Direction.DEBIT, regex)
fun creditRegex(regex: String) = rawTemplate(Direction.CREDIT, regex)

private fun rawTemplate(dir: Direction, regex: String): Template {
    require("(?<amount>" in regex) { "A regex template needs a named group (?<amount>...)" }
    return Template(dir, regex, Regex(regex, RegexOption.IGNORE_CASE))
}

/**
 * Placeholders usable in [debit] / [credit] patterns. The first five are captured; `{date}` and `{any}` just match.
 *  {amount} 1,234.50   {acct} last 3-4 digits of the account/card   {payee} who was paid / who paid
 *  {ref} UPI / transaction reference number   {date} 18-11-26, 18Nov26, 18-11-2026 14:05   {any} anything (short)
 * `{payee}` stops at "on", "via", "ref", "upi", "txn", " | ", a full stop followed by a space, or a comma,
 * so put the text that follows the payee right after it in the pattern.
 */
val PLACEHOLDERS: Map<String, String> = mapOf(
    "amount" to """(?<amount>[0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
    "acct" to """(?<account>[0-9]{3,4})""",
    "payee" to """(?<payee>.+?)(?=\s+(?:on|via|using|ref|upi|txn|if|not|call|avl|bal|is)\b|\s*[|·•]|\.\s|\.$|,|$)""",
    "ref" to """(?<ref>[0-9]{4,})""",
    "date" to """(?:\d{1,2}[-/ ]?(?:\d{1,2}|[A-Za-z]{3,9})[-/ ]?\d{2,4}(?:[ T,]+\d{1,2}:\d{2}(?::\d{2})?)?)""",
    "any" to """.*?""",
)

private val token = Regex("""\{(\w+)\}|\s+""") // escape the closing brace: Android's regex engine rejects a bare "}", the desktop JVM accepts it

internal fun expand(pattern: String): Regex {
    val sb = StringBuilder()
    val captured = HashSet<String>()
    var pos = 0
    for (m in token.findAll(pattern)) {
        if (m.range.first > pos) sb.append(Regex.escape(pattern.substring(pos, m.range.first)))
        val name = m.groups[1]?.value
        if (name == null) sb.append("""\s*""") else {
            val re = PLACEHOLDERS[name] ?: throw IllegalArgumentException("Unknown placeholder {$name} in \"$pattern\". Use: ${PLACEHOLDERS.keys}")
            if (name != "date" && name != "any") require(captured.add(name)) { "{$name} used twice in \"$pattern\"" }
            sb.append(re)
        }
        pos = m.range.last + 1
    }
    if (pos < pattern.length) sb.append(Regex.escape(pattern.substring(pos)))
    require("amount" in captured) { "Pattern needs {amount}: \"$pattern\"" }
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

/** "JOHN q SAMPLE" -> "John Q Sample". UPI ids and paths are left alone. Null when empty or implausibly long. */
fun cleanPayee(raw: String): String? {
    val s = raw.trim().trim('.', ',', ':', '-', ' ')
    if (s.length < 2 || s.length > 60) return null
    return s.split(' ').joinToString(" ") { w ->
        if (w.contains('@') || w.contains('/')) w else w.lowercase().replaceFirstChar { it.titlecase() }
    }
}
