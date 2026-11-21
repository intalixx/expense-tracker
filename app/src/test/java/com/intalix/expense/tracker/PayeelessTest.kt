package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

/** Payments whose message names nobody (e.g. UCO Bank) and how they get a payee later. */
class PayeelessTest {
    private fun rec(note: String, source: String = "sms") =
        Rec(accountId = 1, categoryId = 1, type = 2, amountPaise = 1100, note = note, time = 0, source = source)

    @Test fun fallbackNoteWording() {
        assertEquals("Payment · UCO Bank XX1234", Payees.fallbackNote(true, "UCO Bank", "1234"))
        assertEquals("Received · UCO Bank XX1234", Payees.fallbackNote(false, "UCO Bank", "1234"))
        assertEquals("Payment · XX1234", Payees.fallbackNote(true, null, "1234"))
        assertNull(Payees.fallbackNote(true, null, null))
    }
    @Test fun fallbackNoteIsNeverAPayee() = assertNull(Payees.fromNote("Payment · UCO Bank XX1234"))
    @Test fun composeUsesFallbackOnlyWithoutPayee() {
        assertEquals("Payment · UCO Bank XX1234", Ingest.composeNote(true, null, null, "Payment · UCO Bank XX1234"))
        assertEquals("To Shop", Ingest.composeNote(true, "Shop", null, "Payment · UCO Bank XX1234"))
        assertEquals("Payment · UCO Bank XX1234 (Note: hi)", Ingest.composeNote(true, "", "hi", "Payment · UCO Bank XX1234"))
    }
    @Test fun payeelessDetection() {
        assertTrue(Payees.isPayeeless(rec("")))
        assertTrue(Payees.isPayeeless(rec("Payment · UCO Bank XX1234")))
        assertTrue(Payees.isPayeeless(rec("(Note: hi)", "notification")))
        assertFalse(Payees.isPayeeless(rec("To Shop")))
        assertFalse(Payees.isPayeeless(rec("lunch with team", "manual")))
        assertFalse(Payees.isPayeeless(rec("", "manual")))
    }
    @Test fun userNoteOf() {
        assertEquals("hi", Payees.userNoteOf("Payment · UCO Bank XX1234 (Note: hi)"))
        assertEquals("hi", Payees.userNoteOf("(Note: hi)"))
        assertNull(Payees.userNoteOf("To Shop"))
    }
}
