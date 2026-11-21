package com.intalix.expense.tracker

/**
 * The payee lookup: every person / merchant money went to or came from, with an optional "always this category" rule.
 * Pure helpers live here (unit-tested); the database calls are in the `AppDao` payee queries.
 */
object Payees {
    /** "To X", "From X" or "To X (Note: ...)" -> "X". Anything else (a hand-typed note) is not a payee. */
    fun fromNote(note: String): String? {
        val rest = when {
            note.startsWith("To ") -> note.removePrefix("To ")
            note.startsWith("From ") -> note.removePrefix("From ")
            else -> return null
        }
        return rest.substringBefore(" (Note: ").trim().ifEmpty { null }
    }

    /**
     * Note for a payment whose message named nobody (e.g. UCO Bank): "Payment · UCO Bank XX1234" / "Received · UCO Bank XX1234".
     * Not "To X", so [fromNote] ignores it and it never shows up as a payee. Null when we know neither the bank nor the account.
     */
    fun fallbackNote(debit: Boolean, bank: String?, last4: String?): String? {
        val where = listOfNotNull(bank?.takeIf { it.isNotBlank() }, last4?.takeIf { it.isNotBlank() }?.let { "XX$it" }).joinToString(" ")
        return if (where.isEmpty()) null else (if (debit) "Payment" else "Received") + " · " + where
    }

    private val fallbackStart = Regex("^(Payment|Received) · ")

    /** True for a record created from a message without a payee: nothing typed by hand, no "To X" / "From X". */
    fun isPayeeless(r: Rec): Boolean =
        (r.source == "sms" || r.source == "notification") && fromNote(r.note) == null &&
            (r.note.isBlank() || r.note.startsWith("(Note: ") || fallbackStart.containsMatchIn(r.note))

    /** The "(Note: ...)" part of an automatic note, without the wrapper. */
    fun userNoteOf(note: String): String? =
        note.substringAfter("(Note: ", "").takeIf { it.isNotEmpty() }?.removeSuffix(")")?.trim()?.ifEmpty { null }

    /** Remember that [name] was paid to / received from. Never touches an existing category rule. */
    suspend fun seen(dao: AppDao, name: String?, type: Int, time: Long) {
        val n = name?.trim().orEmpty()
        val key = merchantKey(n)
        if (key.isEmpty() || (type != 1 && type != 2)) return
        dao.insertPayee(Payee(key, type, n, null, false, time))
        dao.touchPayee(key, type, n, time)
    }

    /** Fill the list from records that already exist (older entries, entries added before this feature). Safe to repeat. */
    suspend fun backfill(dao: AppDao) {
        for (r in dao.recordsNow()) fromNote(r.note)?.let { seen(dao, it, r.type, r.time) }
    }

    /** Past records of this payee: those still in an "Other" category, and all of them. */
    class Past(val inOther: List<Rec>, val all: List<Rec>)

    fun past(records: List<Rec>, cats: List<Category>, key: String, type: Int): Past {
        val mine = records.filter { it.type == type && fromNote(it.note)?.let(::merchantKey) == key }
        val other = mine.filter { r -> cats.firstOrNull { it.id == r.categoryId }?.name?.startsWith("Other") == true }
        return Past(other, mine)
    }

    /**
     * Save the rule for a payee ([categoryId] null = remove it) and, if wanted, move past records to that category.
     * Returns how many records were moved. [includeOthers] also moves records already in a category the user picked.
     */
    suspend fun setRule(dao: AppDao, p: Payee, categoryId: Long?, auto: Boolean, includeOthers: Boolean): Int {
        dao.insertPayee(p.copy(categoryId = null, auto = false))
        dao.setPayeeRule(p.key, p.type, categoryId, categoryId != null && auto)
        if (categoryId == null) return 0
        val cats = dao.categoriesNow()
        if (cats.none { it.id == categoryId && it.type == p.type }) return 0
        val past = past(dao.recordsNow(), cats, p.key, p.type)
        var n = 0
        for (r in if (includeOthers) past.all else past.inOther) if (r.categoryId != categoryId) { dao.put(r.copy(categoryId = categoryId)); n++ }
        return n
    }
}
