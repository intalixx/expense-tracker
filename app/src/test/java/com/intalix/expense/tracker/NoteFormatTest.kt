package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class NoteFormatTest {
    private fun txn(s: String) = (NotificationParser.parse(s) as ParseResult.Txn).txn

    @Test fun numericNoteFromRealGpayShape() {
        val t = txn("JANE DOE paid you ₹1.00\n11")
        assertEquals("11", t.userNote); assertEquals("JANE DOE", t.counterparty)
    }
    @Test fun composeWithNote() = assertEquals("From Jane Doe (Note: 11)", Ingest.composeNote(false, "Jane Doe", "11"))
    @Test fun composeWithoutNote() = assertEquals("To Shop", Ingest.composeNote(true, "Shop", null))
    @Test fun composeNoteOnly() = assertEquals("(Note: hi)", Ingest.composeNote(false, null, "hi"))
    @Test fun sanitizeStripsControlAndInvisible() =
        assertEquals("a b c", NotificationParser.sanitizeNote("a\u0000 \u202Eb\u200B\n\n c"))
    @Test fun sanitizeBlankIsNull() = assertNull(NotificationParser.sanitizeNote(" \u200B \n"))
    @Test fun sanitizeCapsLength() {
        val out = NotificationParser.sanitizeNote("x".repeat(500))!!
        assertEquals(201, out.length); assertTrue(out.endsWith("…"))
    }
    @Test fun longNoteKept() {
        val note = "word ".repeat(30).trim()
        assertEquals(note, txn("JANE DOE paid you ₹5.00\n$note").userNote)
    }
}
