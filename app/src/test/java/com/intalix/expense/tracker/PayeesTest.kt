package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class PayeesTest {
    private fun rec(id: Long, note: String, type: Int, cat: Long) =
        Rec(id = id, accountId = 1, categoryId = cat, type = type, amountPaise = 100, note = note, time = 0, source = "sms")

    @Test fun payeeFromNote() {
        assertEquals("Jane Doe", Payees.fromNote("To Jane Doe"))
        assertEquals("Jane Doe", Payees.fromNote("From Jane Doe (Note: rent)"))
        assertEquals("jane@okaxis", Payees.fromNote("To jane@okaxis"))
        assertNull("a hand-typed note is not a payee", Payees.fromNote("lunch with team"))
        assertNull(Payees.fromNote("To "))
        assertNull(Payees.fromNote("(Note: only a note)"))
    }

    @Test fun spellingsOfOneNameShareAKey() {
        assertEquals(merchantKey("JANE  DOE"), merchantKey("Jane Doe"))
        assertEquals(merchantKey("Jane.Doe"), merchantKey("jane doe"))
        assertNotEquals("a UPI id is a different payee from the name", merchantKey("jane@okaxis"), merchantKey("Jane Doe"))
    }

    @Test fun pastRecordsSplitIntoOtherAndAll() {
        val other = Category(id = 9, name = "Other", icon = "other", type = 2)
        val food = Category(id = 2, name = "Food", icon = "food", type = 2)
        val recs = listOf(
            rec(1, "To Jane Doe", 2, 9),                      // in Other
            rec(2, "To JANE DOE (Note: lunch)", 2, 2),        // already Food
            rec(3, "From Jane Doe", 1, 9),                    // other direction: must not match
            rec(4, "To Somebody Else", 2, 9),                 // other payee
            rec(5, "typed by hand", 2, 9),                    // no payee
        )
        val p = Payees.past(recs, listOf(other, food), merchantKey("Jane Doe"), 2)
        assertEquals(listOf(1L), p.inOther.map { it.id })
        assertEquals(listOf(1L, 2L), p.all.map { it.id })
    }
}
