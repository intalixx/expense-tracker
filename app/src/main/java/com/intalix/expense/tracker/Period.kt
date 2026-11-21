package com.intalix.expense.tracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Time windows the Analysis tab can look at. */
enum class Period(val label: String) {
    Day("Daily"), Week("Weekly"), Month("Monthly"), Quarter("3 Months"), Half("6 Months"), Year("Yearly"),
}

/** Half-open range [start, end) in epoch millis. */
data class Span(val start: Long, val end: Long)

/** One bar of the flow chart. [label] is empty when the axis label should be skipped. */
data class Bucket(val start: Long, val end: Long, val label: String, val full: String)

object Periods {
    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
    private fun Calendar.dayStart() = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private fun Calendar.monthStart() = dayStart().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private fun fmt(pattern: String, t: Long) = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))

    /** The window that contains [anchor]. 3 and 6 month windows end with the anchor's month. */
    fun span(p: Period, anchor: Long): Span {
        val c = cal(anchor).dayStart()
        when (p) {
            Period.Day -> { val s = c.timeInMillis; c.add(Calendar.DAY_OF_MONTH, 1); return Span(s, c.timeInMillis) }
            Period.Week -> {
                c.add(Calendar.DAY_OF_MONTH, -((c.get(Calendar.DAY_OF_WEEK) - c.firstDayOfWeek + 7) % 7))
                val s = c.timeInMillis; c.add(Calendar.DAY_OF_MONTH, 7); return Span(s, c.timeInMillis)
            }
            Period.Month -> { c.monthStart(); val s = c.timeInMillis; c.add(Calendar.MONTH, 1); return Span(s, c.timeInMillis) }
            Period.Quarter, Period.Half -> {
                val n = if (p == Period.Quarter) 3 else 6
                c.monthStart(); c.add(Calendar.MONTH, 1); val e = c.timeInMillis
                c.add(Calendar.MONTH, -n); return Span(c.timeInMillis, e)
            }
            Period.Year -> {
                c.monthStart(); c.set(Calendar.MONTH, Calendar.JANUARY); val s = c.timeInMillis
                c.add(Calendar.YEAR, 1); return Span(s, c.timeInMillis)
            }
        }
    }

    /** Moves the anchor by [n] whole windows (negative = back). */
    fun shift(p: Period, anchor: Long, n: Int): Long = cal(anchor).apply {
        when (p) {
            Period.Day -> add(Calendar.DAY_OF_MONTH, n)
            Period.Week -> add(Calendar.DAY_OF_MONTH, 7 * n)
            Period.Month -> add(Calendar.MONTH, n)
            Period.Quarter -> add(Calendar.MONTH, 3 * n)
            Period.Half -> add(Calendar.MONTH, 6 * n)
            Period.Year -> add(Calendar.YEAR, n)
        }
    }.timeInMillis

    fun label(p: Period, s: Span): String {
        val last = s.end - 1
        return when (p) {
            Period.Day -> fmt("EEE, d MMM yyyy", s.start)
            Period.Week -> fmt("d MMM", s.start) + " – " + fmt("d MMM yyyy", last)
            Period.Month -> fmt("MMMM yyyy", s.start)
            Period.Quarter, Period.Half ->
                if (fmt("yyyy", s.start) == fmt("yyyy", last)) fmt("MMM", s.start) + " – " + fmt("MMM yyyy", last)
                else fmt("MMM yyyy", s.start) + " – " + fmt("MMM yyyy", last)
            Period.Year -> fmt("yyyy", s.start)
        }
    }

    /** Unit used in "Average per …". */
    fun unit(p: Period) = when (p) {
        Period.Day -> "hour"; Period.Week, Period.Month -> "day"; Period.Quarter -> "week"; Period.Half, Period.Year -> "month"
    }

    /** Splits [s] into the bars of the flow chart: hours, days, weeks or months depending on the window. */
    fun buckets(p: Period, s: Span): List<Bucket> {
        val out = ArrayList<Bucket>()
        val c = cal(s.start)
        var i = 0
        while (c.timeInMillis < s.end) {
            val a = c.timeInMillis
            when (p) {
                Period.Day -> c.add(Calendar.HOUR_OF_DAY, 1)
                Period.Week, Period.Month -> c.add(Calendar.DAY_OF_MONTH, 1)
                Period.Quarter -> c.add(Calendar.DAY_OF_MONTH, 7)
                Period.Half, Period.Year -> c.add(Calendar.MONTH, 1)
            }
            val b = minOf(c.timeInMillis, s.end)
            val (label, full) = when (p) {
                Period.Day -> (if (i % 6 == 0) fmt("ha", a).lowercase().replace(" ", "") else "") to fmt("h a", a)
                Period.Week -> fmt("EEE", a) to fmt("EEE, d MMM", a)
                Period.Month -> cal(a).get(Calendar.DAY_OF_MONTH).let { d -> (if (d == 1 || d % 5 == 0) "$d" else "") to fmt("EEE, d MMM", a) }
                Period.Quarter -> (if (i % 2 == 0) fmt("d MMM", a) else "") to (fmt("d MMM", a) + " – " + fmt("d MMM", b - 1))
                Period.Half, Period.Year -> fmt("MMM", a) to fmt("MMMM yyyy", a)
            }
            out.add(Bucket(a, b, label, full))
            i++
        }
        return out
    }
}
