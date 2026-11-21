package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

object IndianCurrencyFormatter {

    /**
     * Formats an amount stored in paise (1 INR = 100 paise) into Indian numbering system.
     * Examples:
     *  50000 paise -> ₹500
     *  500000 paise -> ₹5,000
     *  5000000 paise -> ₹50,000
     *  10000000 paise -> ₹1,00,000
     *  100000000 paise -> ₹10,00,000
     *  35050 paise -> ₹350.50
     */
    fun formatPaise(paise: Long, includeSign: Boolean = false): String {
        val isNegative = paise < 0L
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = absPaise / 100L
        val remPaise = absPaise % 100L

        val formattedInteger = formatIndianInteger(rupees)
        val decimalPart = if (remPaise > 0L) {
            "." + remPaise.toString().padStart(2, '0')
        } else {
            ""
        }

        val prefix = when {
            isNegative -> "-₹"
            includeSign && paise > 0L -> "+₹"
            else -> "₹"
        }
        return "$prefix$formattedInteger$decimalPart"
    }

    fun formatCompactPaise(paise: Long): String {
        val abs = kotlin.math.abs(paise)
        val sign = if (paise < 0) "-" else ""
        val rupees = abs / 100.0
        return when {
            rupees >= 10_000_000.0 -> "${sign}₹${trimDecimal(rupees / 10_000_000.0)}Cr"
            rupees >= 100_000.0 -> "${sign}₹${trimDecimal(rupees / 100_000.0)}L"
            rupees >= 1_000.0 -> "${sign}₹${trimDecimal(rupees / 1_000.0)}K"
            else -> formatPaise(paise)
        }
    }

    private fun trimDecimal(value: Double): String {
        val bd = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
        val s = bd.toPlainString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    fun formatIndianInteger(number: Long): String {
        val str = number.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)
        val groupedRemaining = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$groupedRemaining,$lastThree"
    }

    /**
     * Parses a user-entered rupee string (e.g. "350", "1,00,000.50") to exact paise.
     * Returns null if invalid or <= 0 (unless allowZero is true).
     */
    fun parseRupeesToPaise(input: String, allowZero: Boolean = false): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd.scale() > 2) return null
            val paiseBd = bd.multiply(BigDecimal(100))
            val paise = paiseBd.longValueExact()
            if (allowZero) {
                if (paise < 0L) null else paise
            } else {
                if (paise <= 0L) null else paise
            }
        } catch (_: Exception) {
            null
        }
    }

    fun paiseToInputString(paise: Long): String {
        val rupees = paise / 100L
        val rem = kotlin.math.abs(paise % 100L)
        return if (rem == 0L) {
            rupees.toString()
        } else {
            "$rupees.${rem.toString().padStart(2, '0')}"
        }
    }
}
