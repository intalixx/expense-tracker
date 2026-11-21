package com.intalix.expense.tracker

import com.intalix.expense.tracker.parse.*
import org.junit.Assert.*
import org.junit.Test

class PaymentAppTest {
    @Test fun onlyGooglePayIsWatchedForNow() {
        assertEquals(listOf("Google Pay"), PaymentApps.all.map { it.name })
        assertTrue("com.google.android.apps.nbu.paisa.user" in Watched.packages)
        assertFalse("money.super.payments" in Watched.packages)
        assertEquals("Google Pay", PaymentApps.forPackage("com.google.android.apps.nbu.paisa.user")?.name)
        assertNull(PaymentApps.forPackage("money.super.payments"))
        assertNull(PaymentApps.forPackage(null))
    }

    @Test fun bankAppsAreWatchedToo() = assertTrue("com.kotak.bank.mobile" in Watched.packages)

    @Test fun googlePayStillParsesThroughGenericRules() {
        val r = NotificationParser.parse("JANE DOE paid you ₹1.00\nHello there", "com.google.android.apps.nbu.paisa.user") as ParseResult.Txn
        assertEquals(Direction.CREDIT, r.txn.direction)
        assertEquals("Hello there", r.txn.userNote)
    }

    @Test fun appTemplatesMatchAndNeedNoBank() {
        val app = PaymentApp("Demo", listOf("com.example.demo"), notif = listOf(debit("You sent ₹{amount} to {payee}")))
        val h = app.matchNotif("You sent ₹25.00 to Chai Stall")!!
        assertEquals(2500L, h.amountPaise); assertEquals(Direction.DEBIT, h.direction); assertEquals("Chai Stall", h.payee)
        assertNull(app.matchNotif("Something else"))
    }

    @Test fun mistakesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PaymentApp("X", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PaymentApp("X", listOf("nodots")) }
        assertThrows(IllegalArgumentException::class.java) { PaymentApp("X", listOf("a.b"), reject = listOf("Cashback")) }
    }
}
