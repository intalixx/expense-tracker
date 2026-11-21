package com.intalix.expense.tracker.parse.banks

import com.intalix.expense.tracker.parse.Bank
import com.intalix.expense.tracker.parse.credit
import com.intalix.expense.tracker.parse.debit

/** Kotak Mahindra Bank: UPI SMS and the Kotak mobile app notifications. Samples: src/test/resources/samples/kotak.txt */
val Kotak = Bank(
    name = "Kotak",
    senders = listOf("KOTAK"),
    apps = listOf("com.kotak.bank.mobile"),
    sms = listOf(
        credit("Received Rs.{amount} in your Kotak Bank AC {acct} from {payee} on {date}.UPI Ref:{ref}"),
        debit("Sent Rs.{amount} from Kotak Bank A/c X{acct} to {payee} on {date}. UPI Ref {ref}"),
        // some messages carry only the receiver's name straight after the account, with no "to" and no UPI id
        debit("Sent Rs.{amount} from Kotak Bank A/c X{acct} {payee} on {date}. UPI Ref {ref}"),
    ),
    notif = listOf(
        debit("₹{amount} sent to {payee} | Amount debited from XX{acct}"),
        credit("₹{amount} received from {payee} | Amount credited to XX{acct}"),
    ),
)
