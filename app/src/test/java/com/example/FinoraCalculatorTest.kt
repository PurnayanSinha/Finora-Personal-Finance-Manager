package com.example

import com.example.data.local.TransactionEntity
import com.example.domain.AnalyticsPeriod
import com.example.domain.DateFilter
import com.example.domain.FinanceCalculator
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinoraCalculatorTest {

    @Test
    fun `indian currency formatting matches lakhs and crores`() {
        assertEquals("₹500", IndianCurrencyFormatter.formatPaise(50_000L))
        assertEquals("₹5,000", IndianCurrencyFormatter.formatPaise(500_000L))
        assertEquals("₹50,000", IndianCurrencyFormatter.formatPaise(5_000_000L))
        assertEquals("₹1,00,000", IndianCurrencyFormatter.formatPaise(10_000_000L))
        assertEquals("₹10,00,000", IndianCurrencyFormatter.formatPaise(100_000_000L))
        assertEquals("₹350.50", IndianCurrencyFormatter.formatPaise(35_050L))
        assertEquals("-₹1,250", IndianCurrencyFormatter.formatPaise(-125_000L))
    }

    @Test
    fun `currency parser rejects zero negative and malformed amounts`() {
        assertNull(IndianCurrencyFormatter.parseRupeesToPaise("0"))
        assertNull(IndianCurrencyFormatter.parseRupeesToPaise("-100"))
        assertNull(IndianCurrencyFormatter.parseRupeesToPaise("abc"))
        assertNull(IndianCurrencyFormatter.parseRupeesToPaise("10.123"))
        assertEquals(0L, IndianCurrencyFormatter.parseRupeesToPaise("0", allowZero = true))
        assertEquals(1_00_000_00L, IndianCurrencyFormatter.parseRupeesToPaise("1,00,000"))
    }

    @Test
    fun `balance calculation separates credits from income and transfers from expenses`() {
        val now = 1_763_200_000_000L
        val txs = listOf(
            tx("1", TransactionType.INCOME, 5_000_000L, "Salary", now), // ₹50,000
            tx("2", TransactionType.EXPENSE, 1_500_000L, "Food & Dining", now), // ₹15,000
            tx("3", TransactionType.CREDIT, 500_000L, "Friends", now), // ₹5,000 repayment
            tx("4", TransactionType.OUTGOING_TRANSFER, 200_000L, "Family Support", now) // ₹2,000 out
        )
        // Opening ₹10,000 (1,000,000) + 50,000 + 5,000 - 15,000 - 2,000 = ₹48,000 (4,800,000)
        val summary = FinanceCalculator.calculateSummary(
            openingBalancePaise = 1_000_000L,
            allTransactions = txs,
            periodTransactions = txs
        )
        assertEquals(4_800_000L, summary.availableBalancePaise)
        assertEquals(5_000_000L, summary.periodIncomePaise)
        assertEquals(1_500_000L, summary.periodExpensePaise)
        assertEquals(500_000L, summary.periodCreditPaise)
        assertEquals(200_000L, summary.periodOutgoingTransferPaise)
        assertEquals(3_500_000L, summary.periodNetSavingsPaise)
    }

    @Test
    fun `pie chart category breakdown calculates exact percentages`() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            tx("1", TransactionType.EXPENSE, 50_000L, "Food & Dining", now),
            tx("2", TransactionType.EXPENSE, 30_000L, "Shopping", now),
            tx("3", TransactionType.EXPENSE, 20_000L, "Transportation", now),
            tx("4", TransactionType.INCOME, 100_000L, "Salary", now)
        )
        val slices = FinanceCalculator.categoryBreakdown(txs, TransactionType.EXPENSE)
        assertEquals(3, slices.size)
        assertEquals("Food & Dining", slices[0].category)
        assertEquals(50.0, slices[0].percentage, 0.01)
        assertEquals(30.0, slices[1].percentage, 0.01)
        assertEquals(20.0, slices[2].percentage, 0.01)
    }

    @Test
    fun `smart insights detect month over month category increase and savings change`() {
        val cal = Calendar.getInstance()
        val thisMonth = cal.timeInMillis
        cal.add(Calendar.MONTH, -1)
        val lastMonth = cal.timeInMillis

        val txs = listOf(
            tx("1", TransactionType.INCOME, 5_000_000L, "Salary", thisMonth),
            tx("2", TransactionType.EXPENSE, 1_150_000L, "Food & Dining", thisMonth),
            tx("3", TransactionType.INCOME, 5_000_000L, "Salary", lastMonth),
            tx("4", TransactionType.EXPENSE, 1_000_000L, "Food & Dining", lastMonth)
        )

        val insights = FinanceCalculator.generateSmartInsights(txs, thisMonth)
        assertTrue(insights.any { it.message.contains("Food & Dining") && it.message.contains("15%") })
        assertTrue(insights.any { it.title == "Top Spending Category" })
    }

    @Test
    fun `weekly monthly yearly analytics periods produce expected bucket counts`() {
        val now = System.currentTimeMillis()
        val weekRange = FinanceCalculator.resolveAnalyticsRange(AnalyticsPeriod.WEEKLY, null, null, now)
        val weekSeries = FinanceCalculator.buildPeriodSeries(emptyList(), AnalyticsPeriod.WEEKLY, weekRange)
        assertEquals(7, weekSeries.size)

        val monthRange = FinanceCalculator.resolveAnalyticsRange(AnalyticsPeriod.MONTHLY, null, null, now)
        val monthSeries = FinanceCalculator.buildPeriodSeries(emptyList(), AnalyticsPeriod.MONTHLY, monthRange)
        assertEquals(5, monthSeries.size)

        val yearRange = FinanceCalculator.resolveAnalyticsRange(AnalyticsPeriod.YEARLY, null, null, now)
        val yearSeries = FinanceCalculator.buildPeriodSeries(emptyList(), AnalyticsPeriod.YEARLY, yearRange)
        assertEquals(12, yearSeries.size)

        assertNotNull(FinanceCalculator.resolveDateRange(DateFilter.THIS_MONTH, null, null, now))
        assertNull(FinanceCalculator.resolveDateRange(DateFilter.ALL_TIME, null, null, now))
    }

    private fun tx(id: String, type: TransactionType, amountPaise: Long, category: String, date: Long) =
        TransactionEntity(
            id = id,
            userId = "u1",
            type = type,
            amountPaise = amountPaise,
            title = category,
            category = category,
            paymentMethod = "UPI",
            transactionDateMillis = date,
            createdAtMillis = date,
            updatedAtMillis = date
        )
}
