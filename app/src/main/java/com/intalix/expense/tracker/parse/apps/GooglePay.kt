package com.intalix.expense.tracker.parse.apps

import com.intalix.expense.tracker.parse.PaymentApp

/** Google Pay (India). The generic notification rules cover its wording ("X paid you ₹1.00" + the sender's note line). */
val GooglePay = PaymentApp(
    name = "Google Pay",
    packages = listOf("com.google.android.apps.nbu.paisa.user"),
)

// Starting points for the next apps (not enabled yet: add the real notification wording to samples first, then register
// the app in PaymentApps.all):
//   val SuperMoney = PaymentApp("supermoney", listOf("money.super.payments"))
