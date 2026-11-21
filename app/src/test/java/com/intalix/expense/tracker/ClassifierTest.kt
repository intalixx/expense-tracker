package com.intalix.expense.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifierTest {
    private fun e(p: String) = Classifier.classifyExpense(p)

    @Test fun telecom() {
        assertEquals("Telephone", e("paytm-jiomobility@ptybl"))
        assertEquals("Telephone", e("gpayrecharge@okpayaxis"))
        assertEquals("Telephone", e("Vodafone Idea"))
        assertEquals("Telephone", e("Jio Prepaid"))
        assertEquals("Telephone", e("airtel@icici"))
        assertEquals("Telephone", e("Reliance Jio Infocomm"))
    }
    @Test fun bankHandleIsNotAMerchant() {
        assertNull(e("9000000001@airtel"))     // a person's number on Airtel Payments Bank
        assertNull(e("9000000001@superyes"))
        assertNull(e("janedoe191@oksomebank"))
        assertNull(e("Asha Rao Kumar"))
        assertNull(e("9000000002@ybl"))
    }
    @Test fun merchants() {
        assertEquals("Food", e("swiggy.stores@axb"))
        assertEquals("Food", e("upiswiggy@icici"))
        assertEquals("Food", e("Sunrise Ice Cream Parl"))
        assertEquals("Food", e("Greenleaf Caterers"))
        assertEquals("Transportation", e("cf.irctc@cashfreensdlpb"))
        assertEquals("Transportation", e("bdpg2.iruts@sbi"))
        assertEquals("Transportation", e("Indian Railways Tick"))
        assertEquals("Transportation", e("chalo158366.rzp@rxaxis"))
        assertEquals("Insurance", e("nationalinsurance.bdpg25@kotakpay"))
        assertEquals("Bills", e("Bajaj Finance Limite"))
        assertEquals("Shopping", e("relianceretail2.easebuzz@kotakpay"))
    }
    @Test fun incomeNeedsClearWording() {
        assertEquals("Salary", Classifier.classifyIncome("ACME LTD", "Rs 30000 credited to a/c by NEFT SALARY NOV"))
        assertEquals("Refunds", Classifier.classifyIncome("Amazon", "refund of Rs 200"))
        assertNull(Classifier.classifyIncome("Jane Doe", "credited Rs 5000"))
        assertNull(Classifier.classifyIncome("Mobile X777", null))
    }
    @Test fun noGuessOnEmpty() {
        assertNull(e(""))
        assertNull(Classifier.classifyExpense(null))
    }
}
