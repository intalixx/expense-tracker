package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class BankAppNotificationTest {
    private val pkg = "com.kotak.bank.mobile"
    private fun txn(s: String) = (NotificationParser.parse(s, pkg) as ParseResult.Txn).txn

    @Test fun debitSentToUpiId() {
        val t = txn("₹1.00 sent to jane.doe@okaxis\nAmount debited from XX1234. Check out details.")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(100L, t.amountPaise)
        assertEquals("jane.doe@okaxis", t.counterparty); assertEquals("1234", t.accountLast4); assertEquals("Kotak", t.bank)
        assertTrue(t.certain); assertNull(t.userNote)
    }
    @Test fun creditReceived() {
        val t = txn("₹250.00 received from John Sample\nAmount credited to XX1234.")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(25000L, t.amountPaise)
        assertEquals("John Sample", t.counterparty); assertEquals("1234", t.accountLast4)
    }
    @Test fun creditRealShape() {
        val t = txn("₹1.00 received from JANE DOE\nAmount credited to XX1234. Check out details.")
        assertEquals(Direction.CREDIT, t.direction); assertEquals(100L, t.amountPaise)
        assertEquals("JANE DOE", t.counterparty); assertEquals("1234", t.accountLast4); assertTrue(t.certain); assertNull(t.userNote)
    }
    @Test fun chargesWithoutPayeeGoToReview() {
        val t = txn("₹5.90 debited\nAmount debited from XX1234 towards charges.")
        assertEquals(Direction.DEBIT, t.direction); assertEquals(590L, t.amountPaise)
        assertEquals("1234", t.accountLast4); assertFalse(t.certain)
    }
    @Test fun creditWithoutPayee() {
        val t = txn("₹10.00 credited\nAmount credited to XX1234.")
        assertEquals(Direction.CREDIT, t.direction); assertFalse(t.certain)
    }
    @Test fun otherPackageHasNoBank() {
        val t = (NotificationParser.parse("Paid ₹20 to Shop", "com.google.android.apps.nbu.paisa.user") as ParseResult.Txn).txn
        assertNull(t.bank); assertNull(t.accountLast4)
    }
    @Test fun loginAlertIgnored() = assertTrue(NotificationParser.parse("Login alert\nNew login from device", pkg) !is ParseResult.Txn)
}
