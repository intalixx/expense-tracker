package com.intalix.expense.tracker.parse.banks

import com.intalix.expense.tracker.parse.Bank
import com.intalix.expense.tracker.parse.credit
import com.intalix.expense.tracker.parse.debit

/**
 * UCO Bank: UPI SMS. These texts carry only the amount, date and last digits of the account: no payee, no UPI id and
 * no reference number. Samples: src/test/resources/samples/uco.txt
 */
val Uco = Bank(
    name = "UCO Bank",
    senders = listOf("UCOBNK"),
    sms = listOf(
        debit("A/c XX{acct} Debited with Rs.{amount} on {date} by UCO-UPI"),
        credit("A/c XX{acct} Credited with Rs.{amount} on {date} by UCO-UPI"),
    ),
)
