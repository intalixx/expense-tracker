package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {
    private fun txn(s: String): ParsedTxn = (SmsParser.parse(s) as ParseResult.Txn).txn
    private fun ignored(s: String) = assertTrue(s, SmsParser.parse(s) is ParseResult.Ignored)

    @Test fun kotakReceived() {
        val t = txn("Received Rs.350.00 in your Kotak Bank AC 1234 from XYZ DOE on 18-11-26.UPI Ref:668244")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(35000L, t.amountPaise)
        assertEquals("Xyz Doe", t.counterparty); assertEquals("1234", t.accountLast4)
        assertEquals("668244", t.reference); assertEquals("Kotak", t.bank); assertTrue(t.certain)
    }
    @Test fun kotakSent() {
        val t = txn("Sent Rs.220.00 from Kotak Bank A/c X1234 to JOHN Q SAMPLE on 17-11-26. UPI Ref 6684199. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(22000L, t.amountPaise)
        assertEquals("John Q Sample", t.counterparty); assertEquals("1234", t.accountLast4)
        assertEquals("6684199", t.reference); assertTrue(t.certain)
    }
    @Test fun otherSamples() {
        assertEquals(700000L, txn("Received Rs.7000.00 in your Kotak Bank AC 1234 from Asha Rao Kumar on 16-11-26.UPI Ref:6327233").amountPaise)
        val b = txn("Sent Rs.25000.00 from Kotak Bank A/c X1234 to BAJAJ FINANCE LIMITE on 16-11-26. UPI Ref 6676.")
        assertEquals(2500000L, b.amountPaise); assertEquals("Bajaj Finance Limite", b.counterparty)
        assertEquals(49700L, txn("Sent Rs.497.00 from Kotak Bank A/c X1234 to ACME AGENCIES AD on 18-11-26. UPI Ref 6684368.").amountPaise)
    }
    @Test fun commaAmount() {
        assertEquals(12345000L, txn("Sent Rs.1,23,450.00 from Kotak Bank A/c X1234 to A B on 17-11-26. UPI Ref 6684199.").amountPaise)
    }
    @Test fun hdfcStyle() {
        val t = txn("Rs 500.00 debited from a/c **4321 on 18-11-26 to VPA shop@upi (UPI Ref No 123456789012). Avl Bal Rs 9,000.00")
        assertEquals(50000L, t.amountPaise); assertEquals(Direction.DEBIT, t.direction); assertEquals("4321", t.accountLast4)
        assertEquals("123456789012", t.reference)
    }
    @Test fun rejects() {
        ignored("123456 is your OTP for login. Do not share with anyone.")
        ignored("Rs.500 will be debited from your account on 20-11-26 for autopay")
        ignored("Payment of Rs.500 failed. Money debited will be refunded in 3 days.")
        ignored("Your a/c balance is Rs.5000. Offer: apply now for loan")
        ignored("Hi, how are you? Let's meet at 5")
    }
    @Test fun bothWordsNeedsReview() {
        val t = txn("Rs.100 debited from A/c X1234 and credited to A/c X9876 on 18-11-26")
        assertFalse(t.certain)
    }
}
