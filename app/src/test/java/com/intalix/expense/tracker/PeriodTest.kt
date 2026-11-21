package com.intalix.expense.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodTest {
    private fun t(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m, d, h, 0) }.timeInMillis
    private val wed = t(2026, Calendar.NOVEMBER, 18)

    @Test fun spansCoverAnchorAndAreContiguousWhenShifted() {
        for (p in Period.entries) {
            val s = Periods.span(p, wed)
            assertTrue("$p contains anchor", wed >= s.start && wed < s.end)
            val next = Periods.span(p, Periods.shift(p, wed, 1))
            assertEquals("$p next starts where this ends", s.end, next.start)
        }
    }

    @Test fun monthQuarterHalfYear() {
        assertEquals(Span(t(2026, 10, 1, 0), t(2026, 11, 1, 0)), Periods.span(Period.Month, wed))
        assertEquals(Span(t(2026, 8, 1, 0), t(2026, 11, 1, 0)), Periods.span(Period.Quarter, wed))
        assertEquals(Span(t(2026, 5, 1, 0), t(2026, 11, 1, 0)), Periods.span(Period.Half, wed))
        assertEquals(Span(t(2026, 0, 1, 0), t(2027, 0, 1, 0)), Periods.span(Period.Year, wed))
    }

    @Test fun bucketCounts() {
        fun n(p: Period) = Periods.buckets(p, Periods.span(p, wed)).size
        assertEquals(24, n(Period.Day)); assertEquals(7, n(Period.Week)); assertEquals(30, n(Period.Month))
        assertEquals(6, n(Period.Half)); assertEquals(12, n(Period.Year))
        assertTrue(n(Period.Quarter) in 13..14)
    }

    @Test fun bucketsTileTheSpan() {
        for (p in Period.entries) {
            val s = Periods.span(p, wed); val b = Periods.buckets(p, s)
            assertEquals(s.start, b.first().start); assertEquals(s.end, b.last().end)
            b.zipWithNext().forEach { (x, y) -> assertEquals(x.end, y.start) }
        }
    }
}
