package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    private fun txn(s: String): ParsedTxn = (NotificationParser.parse(s) as ParseResult.Txn).txn
    private fun ignored(s: String) = assertTrue(s, NotificationParser.parse(s) is ParseResult.Ignored)

    @Test fun paidTo() {
        val t = txn("Paid ₹320 to California Burrito")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(32000L, t.amountPaise)
        assertEquals("California Burrito", t.counterparty); assertTrue(t.certain)
    }
    @Test fun paymentToOfSuccessful() {
        val t = txn("Payment to Zomato of ₹230 successful")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(23000L, t.amountPaise); assertEquals("Zomato", t.counterparty)
    }
    @Test fun yourPaymentCompleted() {
        val t = txn("Your ₹10 payment to Ramesh Kumar has been completed")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(1000L, t.amountPaise); assertEquals("Ramesh Kumar", t.counterparty)
    }
    @Test fun sentYou_isCredit() {
        val t = txn("Rahul Sharma sent you ₹500")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(50000L, t.amountPaise); assertEquals("Rahul Sharma", t.counterparty)
    }
    @Test fun paidYou_isCredit() {
        assertEquals(Direction.CREDIT, txn("Rahul paid you ₹1,250.50").direction)
    }
    @Test fun receivedFrom() {
        val t = txn("Received ₹75 from Anita")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(7500L, t.amountPaise); assertEquals("Anita", t.counterparty)
    }
    @Test fun userNoteAfterMarker() {
        val t = txn("Paid ₹320 to Ramesh\nNote: lunch with team")
        assertEquals("lunch with team", t.userNote); assertEquals("Ramesh", t.counterparty)
    }
    @Test fun userNoteFromForClause() {
        val t = txn("Paid ₹200 to Ramesh for dinner")
        assertEquals("Ramesh", t.counterparty); assertEquals("dinner", t.userNote)
    }
    @Test fun gpayRealShape_noteOnItsOwnLine() {
        val t = txn("JANE DOE paid you ₹1.00\nEk rupee with note")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(100L, t.amountPaise)
        assertEquals("JANE DOE", t.counterparty); assertEquals("Ek rupee with note", t.userNote)
    }
    @Test fun gpayRealShape_noNote() {
        val t = txn("JANE DOE paid you ₹1.00")
        assertNull(t.userNote); assertEquals("JANE DOE", t.counterparty)
    }
    @Test fun boilerplateLineIsNotANote() { assertNull(txn("Paid ₹20 to Shop\nUPI Ref No: 123456789012").userNote) }
    @Test fun noNote() { assertNull(txn("Paid ₹320 to California Burrito").userNote) }
    @Test fun reference() {
        assertEquals("123456789012", txn("Paid ₹20 to Shop\nUPI Ref No: 123456789012").reference)
    }
    @Test fun amountOnlyGoesToReview() {
        val t = txn("₹40 Payment Successful")
        assertFalse(t.certain); assertNull(t.counterparty)
    }
    @Test fun noteHelper() {
        assertEquals("rent", NotificationParser.userNote("Paid ₹5000 to Landlord\nNote: rent"))
        assertNull(NotificationParser.userNote("nothing here"))
    }

    @Test fun skipsNonPayments() {
        ignored("Rahul requested ₹500 from you")
        ignored("Payment of ₹500 to Rahul failed")
        ignored("Your payment to Rahul of ₹500 is pending")
        ignored("You earned ₹5 cashback")
        ignored("Win up to ₹1000 cashback! Scratch now")
        ignored("Hello there")
        ignored("")
    }
}
