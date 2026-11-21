package com.intalix.expense.tracker

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Keypad expression logic for the amount field: digits, '.', and + - × ÷ with normal precedence. */
object Calc {
    private const val OPS = "+-×÷"
    private const val MAX_INT_DIGITS = 9
    private const val MAX_LEN = 40

    private fun isOp(c: Char) = c in OPS

    private fun currentNumber(expr: String): String = expr.takeLastWhile { !isOp(it) }

    /** Applies one key press ("0".."9", ".", "+", "-", "×", "÷", "⌫") and returns the new expression. */
    fun press(expr: String, key: String): String {
        if (key == "⌫") return expr.dropLast(1)
        if (key.length != 1) return expr
        val k = key[0]
        when {
            k.isDigit() -> {
                if (expr.length >= MAX_LEN) return expr
                val cur = currentNumber(expr)
                val dot = cur.indexOf('.')
                if (dot >= 0) return if (cur.length - dot - 1 >= 2) expr else expr + k
                if (cur == "0") return expr.dropLast(1) + k
                return if (cur.length >= MAX_INT_DIGITS) expr else expr + k
            }
            k == '.' -> {
                val cur = currentNumber(expr)
                if (cur.contains('.')) return expr
                return if (cur.isEmpty()) expr + "0." else expr + "."
            }
            isOp(k) -> {
                if (expr.isEmpty()) return expr
                return if (isOp(expr.last())) expr.dropLast(1) + k else expr + k
            }
        }
        return expr
    }

    /** Value of the expression (a trailing operator is ignored), or null if empty/invalid/divide by zero. */
    fun evaluate(expr: String): BigDecimal? {
        if (expr.isEmpty()) return null
        val nums = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        val cur = StringBuilder()
        try {
            for (c in expr) {
                if (isOp(c)) {
                    if (cur.isEmpty()) return null
                    nums.add(BigDecimal(cur.toString())); ops.add(c); cur.setLength(0)
                } else cur.append(c)
            }
            if (cur.isNotEmpty()) nums.add(BigDecimal(cur.toString())) else if (ops.isNotEmpty()) ops.removeAt(ops.lastIndex)
        } catch (e: NumberFormatException) { return null }
        if (nums.isEmpty()) return null

        // First pass: × and ÷
        val n2 = arrayListOf(nums[0]); val o2 = ArrayList<Char>()
        for (i in ops.indices) {
            val rhs = nums[i + 1]
            when (ops[i]) {
                '×' -> n2[n2.lastIndex] = n2.last().multiply(rhs)
                '÷' -> { if (rhs.signum() == 0) return null; n2[n2.lastIndex] = n2.last().divide(rhs, MathContext(20, RoundingMode.HALF_UP)) }
                else -> { o2.add(ops[i]); n2.add(rhs) }
            }
        }
        var acc = n2[0]
        for (i in o2.indices) acc = if (o2[i] == '+') acc.add(n2[i + 1]) else acc.subtract(n2[i + 1])
        return acc
    }

    /** Amount in paise (rounded to 2 decimals), or null unless the result is a positive amount. */
    fun toPaise(expr: String): Long? {
        val v = evaluate(expr) ?: return null
        val p = v.setScale(2, RoundingMode.HALF_UP)
        if (p.signum() <= 0 || p.precision() > 15) return null
        return p.movePointRight(2).toLong()
    }

    fun hasOperator(expr: String) = expr.any { isOp(it) }
}
