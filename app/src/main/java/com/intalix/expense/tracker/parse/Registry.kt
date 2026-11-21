package com.intalix.expense.tracker.parse

import com.intalix.expense.tracker.parse.apps.GooglePay
import com.intalix.expense.tracker.parse.banks.Kotak
import com.intalix.expense.tracker.parse.banks.Uco

/** Every bank the parser has templates for. ADD NEW BANKS TO THIS LIST (one line). */
object Banks {
    val all: List<Bank> = listOf(
        Kotak,
        Uco,
    )

    init {
        require(all.map { it.name.lowercase() }.toSet().size == all.size) { "Two banks share a name" }
    }

    fun forPackage(pkg: String?): Bank? = if (pkg == null) null else all.firstOrNull { pkg in it.apps }

    /** "VM-KOTAKB-S" -> "KOTAKB": operator prefix ("VM-") and suffix ("-S") are dropped before matching. */
    fun forSender(sender: String?): Bank? {
        if (sender.isNullOrBlank()) return null
        val core = senderCore(sender)
        return all.firstOrNull { b -> b.senders.any { core.contains(it) } }
    }

    internal fun senderCore(sender: String): String {
        val s = sender.trim().uppercase()
        val noPrefix = if (Regex("^[A-Z0-9]{2}-").containsMatchIn(s)) s.substring(3) else s
        return noPrefix.replace(Regex("-[A-Z]$"), "")
    }
}

/** Every payment app whose notifications are read. ADD NEW APPS TO THIS LIST (one line). */
object PaymentApps {
    val all: List<PaymentApp> = listOf(
        GooglePay,
    )

    init {
        require(all.flatMap { it.packages }.let { it.toSet().size == it.size }) { "Two payment apps share a package" }
    }

    fun forPackage(pkg: String?): PaymentApp? = if (pkg == null) null else all.firstOrNull { pkg in it.packages }
}

/** Every app whose notifications are read: payment apps plus the apps of banks. */
object Watched {
    val packages: Set<String> = PaymentApps.all.flatMap { it.packages }.toSet() + Banks.all.flatMap { it.apps }
}
