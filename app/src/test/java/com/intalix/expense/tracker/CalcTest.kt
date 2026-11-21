package com.intalix.expense.tracker

import org.junit.Assert.*
import org.junit.Test

class CalcTest {
    private fun type(s: String) = s.fold("") { e, c -> Calc.press(e, c.toString()) }

    @Test fun precedence() { assertEquals(1400L, Calc.toPaise("2+3×4")) }
    @Test fun division() { assertEquals(2500L, Calc.toPaise("100÷4")); assertEquals(3333L, Calc.toPaise("100÷3")) }
    @Test fun decimals() { assertEquals(1275L, Calc.toPaise("12.75")); assertEquals(1000L, Calc.toPaise("12.5-2.5")) }
    @Test fun trailingOperatorIgnored() { assertEquals(500L, Calc.toPaise("5+")) }
    @Test fun invalid() {
        assertNull(Calc.toPaise("")); assertNull(Calc.toPaise("5÷0")); assertNull(Calc.toPaise("0")); assertNull(Calc.toPaise("3-5"))
        assertNull(Calc.toPaise("."))
    }
    @Test fun keypadRules() {
        assertEquals("0.5", type(".5")); assertEquals("5", type("05")); assertEquals("1.25", type("1.255"))
        assertEquals("1.2", type("1..2")); assertEquals("5+2", type("5+-+2")); assertEquals("", type("+"))
        assertEquals("5-", type("5+-")); assertEquals("12", Calc.press("123", "⌫"))
        assertEquals("1+0.5", type("1+.5")); assertEquals("1+5", type("1+05"))
    }
    @Test fun digitCap() { assertEquals(9, type("1".repeat(20)).length) }
}
