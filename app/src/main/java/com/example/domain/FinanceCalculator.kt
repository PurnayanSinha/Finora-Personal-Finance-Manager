package com.example.domain

import com.example.data.local.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FinanceCalculator {

    fun resolveDateRange(
        filter: DateFilter,
        customStartMillis: Long?,
        customEndMillis: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): DateRange? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter) {
            DateFilter.THIS_WEEK -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                setStartOfDay(cal)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 6)
                setEndOfDay(cal)
                DateRange(start, cal.timeInMillis)
            }
            DateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                setStartOfDay(cal)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                setEndOfDay(cal)
                DateRange(start, cal.timeInMillis)
            }
            DateFilter.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                setStartOfDay(cal)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                setEndOfDay(cal)
                DateRange(start, cal.timeInMillis)
            }
            DateFilter.CUSTOM -> {
                val s = customStartMillis ?: nowMillis
                val e = customEndMillis ?: nowMillis
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = minOf(s, e)
                    setStartOfDay(this)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = maxOf(s, e)
                    setEndOfDay(this)
                }
                DateRange(startCal.timeInMillis, endCal.timeInMillis)
            }
            DateFilter.ALL_TIME -> null
        }
    }

    fun resolveAnalyticsRange(
        period: AnalyticsPeriod,
        customStartMillis: Long?,
        customEndMillis: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): DateRange {
        val filter = when (period) {
            AnalyticsPeriod.WEEKLY -> DateFilter.THIS_WEEK
            AnalyticsPeriod.MONTHLY -> DateFilter.THIS_MONTH
            AnalyticsPeriod.YEARLY -> DateFilter.THIS_YEAR
            AnalyticsPeriod.CUSTOM -> DateFilter.CUSTOM
        }
        return resolveDateRange(filter, customStartMillis, customEndMillis, nowMillis)
            ?: DateRange(0L, nowMillis)
    }

    fun filterByDateRange(
        transactions: List<TransactionEntity>,
        range: DateRange?
    ): List<TransactionEntity> {
        if (range == null) return transactions
        return transactions.filter { it.transactionDateMillis in range.startMillis..range.endMillis }
    }

    /**
     * Exact balance formula:
     * Available Balance = Opening Balance + Total Income + Total Credits - Total Expenses - Outgoing Transfers
     *
     * Important accounting rule:
     * Credits (e.g. refunds/repayments/transfers received) increase Available Balance but are NOT counted in periodIncomePaise.
     * Outgoing transfers decrease Available Balance but are NOT counted in periodExpensePaise.
     */
    fun calculateSummary(
        openingBalancePaise: Long,
        allTransactions: List<TransactionEntity>,
        periodTransactions: List<TransactionEntity>
    ): FinancialSummary {
        var totalIncomeAll = BigDecimal.ZERO
        var totalExpenseAll = BigDecimal.ZERO
        var totalCreditAll = BigDecimal.ZERO
        var totalOutgoingAll = BigDecimal.ZERO

        for (tx in allTransactions) {
            val amt = BigDecimal.valueOf(tx.amountPaise)
            when (tx.type) {
                TransactionType.INCOME -> totalIncomeAll = totalIncomeAll.add(amt)
                TransactionType.EXPENSE -> totalExpenseAll = totalExpenseAll.add(amt)
                TransactionType.CREDIT -> totalCreditAll = totalCreditAll.add(amt)
                TransactionType.OUTGOING_TRANSFER -> totalOutgoingAll = totalOutgoingAll.add(amt)
            }
        }

        val availableBalance = BigDecimal.valueOf(openingBalancePaise)
            .add(totalIncomeAll)
            .add(totalCreditAll)
            .subtract(totalExpenseAll)
            .subtract(totalOutgoingAll)
            .longValueExact()

        var periodIncome = 0L
        var periodExpense = 0L
        var periodCredit = 0L
        var periodOutgoing = 0L

        for (tx in periodTransactions) {
            when (tx.type) {
                TransactionType.INCOME -> periodIncome += tx.amountPaise
                TransactionType.EXPENSE -> periodExpense += tx.amountPaise
                TransactionType.CREDIT -> periodCredit += tx.amountPaise
                TransactionType.OUTGOING_TRANSFER -> periodOutgoing += tx.amountPaise
            }
        }

        val periodNetSavings = periodIncome - periodExpense
        val savingsRate = if (periodIncome > 0L) {
            BigDecimal.valueOf(periodNetSavings * 100.0)
                .divide(BigDecimal.valueOf(periodIncome), 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }

        return FinancialSummary(
            openingBalancePaise = openingBalancePaise,
            availableBalancePaise = availableBalance,
            periodIncomePaise = periodIncome,
            periodExpensePaise = periodExpense,
            periodCreditPaise = periodCredit,
            periodOutgoingTransferPaise = periodOutgoing,
            periodNetSavingsPaise = periodNetSavings,
            savingsRatePercent = savingsRate
        )
    }

    fun categoryBreakdown(
        transactions: List<TransactionEntity>,
        type: TransactionType
    ): List<CategorySlice> {
        val filtered = transactions.filter { it.type == type }
        val totalPaise = filtered.sumOf { it.amountPaise }
        if (totalPaise <= 0L) return emptyList()

        val grouped = filtered.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
            .entries
            .sortedByDescending { it.value }

        return grouped.mapIndexed { index, entry ->
            val pct = BigDecimal.valueOf(entry.value * 100.0)
                .divide(BigDecimal.valueOf(totalPaise), 1, RoundingMode.HALF_UP)
                .toDouble()
            CategorySlice(
                category = entry.key,
                amountPaise = entry.value,
                percentage = pct,
                colorIndex = index
            )
        }
    }

    fun buildPeriodSeries(
        transactions: List<TransactionEntity>,
        period: AnalyticsPeriod,
        range: DateRange
    ): List<PeriodBarPoint> {
        val buckets = linkedMapOf<String, MutableList<TransactionEntity>>()
        val cal = Calendar.getInstance()

        when (period) {
            AnalyticsPeriod.WEEKLY -> {
                val fmt = SimpleDateFormat("EEE", Locale.ENGLISH)
                cal.timeInMillis = range.startMillis
                for (i in 0..6) {
                    buckets[fmt.format(cal.time)] = mutableListOf()
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                for (tx in transactions) {
                    val key = fmt.format(Date(tx.transactionDateMillis))
                    buckets[key]?.add(tx)
                }
            }
            AnalyticsPeriod.MONTHLY -> {
                buckets["W1"] = mutableListOf()
                buckets["W2"] = mutableListOf()
                buckets["W3"] = mutableListOf()
                buckets["W4"] = mutableListOf()
                buckets["W5"] = mutableListOf()
                for (tx in transactions) {
                    cal.timeInMillis = tx.transactionDateMillis
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val key = when {
                        day <= 7 -> "W1"
                        day <= 14 -> "W2"
                        day <= 21 -> "W3"
                        day <= 28 -> "W4"
                        else -> "W5"
                    }
                    buckets[key]?.add(tx)
                }
            }
            AnalyticsPeriod.YEARLY -> {
                val fmt = SimpleDateFormat("MMM", Locale.ENGLISH)
                cal.timeInMillis = range.startMillis
                for (m in 0..11) {
                    cal.set(Calendar.MONTH, m)
                    buckets[fmt.format(cal.time)] = mutableListOf()
                }
                for (tx in transactions) {
                    val key = fmt.format(Date(tx.transactionDateMillis))
                    buckets[key]?.add(tx)
                }
            }
            AnalyticsPeriod.CUSTOM -> {
                val spanDays = ((range.endMillis - range.startMillis) / 86_400_000L).coerceAtLeast(1L)
                if (spanDays <= 14L) {
                    val fmt = SimpleDateFormat("dd MMM", Locale.ENGLISH)
                    cal.timeInMillis = range.startMillis
                    for (i in 0..spanDays.toInt()) {
                        buckets[fmt.format(cal.time)] = mutableListOf()
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    for (tx in transactions) {
                        val key = fmt.format(Date(tx.transactionDateMillis))
                        buckets[key]?.add(tx)
                    }
                } else {
                    val fmt = SimpleDateFormat("MMM yy", Locale.ENGLISH)
                    for (tx in transactions.sortedBy { it.transactionDateMillis }) {
                        val key = fmt.format(Date(tx.transactionDateMillis))
                        buckets.getOrPut(key) { mutableListOf() }.add(tx)
                    }
                }
            }
        }

        return buckets.map { (label, txs) ->
            PeriodBarPoint(
                label = label,
                incomePaise = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise },
                expensePaise = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise },
                creditPaise = txs.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise }
            )
        }
    }

    fun buildExpenseTrendSeries(
        allTransactions: List<TransactionEntity>,
        period: AnalyticsPeriod,
        range: DateRange
    ): List<TrendPoint> {
        val filtered = filterByDateRange(allTransactions, range)
        return buildPeriodSeries(filtered, period, range).map {
            TrendPoint(label = it.label, amountPaise = it.expensePaise)
        }
    }

    fun buildCreditHistorySeries(
        allTransactions: List<TransactionEntity>,
        period: AnalyticsPeriod,
        range: DateRange
    ): List<TrendPoint> {
        val filtered = filterByDateRange(allTransactions, range)
        return buildPeriodSeries(filtered, period, range).map {
            TrendPoint(label = it.label, amountPaise = it.creditPaise)
        }
    }

    fun generateSmartInsights(
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FinancialInsight> {
        if (allTransactions.isEmpty()) {
            return listOf(
                FinancialInsight(
                    id = "empty_start",
                    title = "Start Tracking Your Finances",
                    message = "Add your first income, expense, or credit transaction to unlock personalized financial insights.",
                    sentiment = InsightSentiment.INFO
                )
            )
        }

        val thisMonthCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val thisMonth = thisMonthCal.get(Calendar.MONTH)
        val thisYear = thisMonthCal.get(Calendar.YEAR)

        val lastMonthCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, -1)
        }
        val lastMonth = lastMonthCal.get(Calendar.MONTH)
        val lastYear = lastMonthCal.get(Calendar.YEAR)

        val thisMonthTxs = allTransactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.transactionDateMillis }
            c.get(Calendar.MONTH) == thisMonth && c.get(Calendar.YEAR) == thisYear
        }
        val lastMonthTxs = allTransactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.transactionDateMillis }
            c.get(Calendar.MONTH) == lastMonth && c.get(Calendar.YEAR) == lastYear
        }

        val insights = mutableListOf<FinancialInsight>()

        // 1. Top spending category this month
        val thisMonthExpenses = thisMonthTxs.filter { it.type == TransactionType.EXPENSE }
        if (thisMonthExpenses.isNotEmpty()) {
            val topCat = thisMonthExpenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
                .maxByOrNull { it.value }
            if (topCat != null) {
                val totalExp = thisMonthExpenses.sumOf { it.amountPaise }
                val pct = if (totalExp > 0L) ((topCat.value * 100.0) / totalExp).toInt() else 0
                insights.add(
                    FinancialInsight(
                        id = "top_category",
                        title = "Top Spending Category",
                        message = "You spent the most on ${topCat.key} this month (${IndianCurrencyFormatter.formatPaise(topCat.value)}, $pct% of monthly expenses).",
                        sentiment = InsightSentiment.INFO
                    )
                )
            }
        }

        // 2. Category MoM comparison (increases and decreases)
        val thisExpByCat = thisMonthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
        val lastExpByCat = lastMonthTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }

        val increasedCats = thisExpByCat.mapNotNull { (cat, thisAmt) ->
            val lastAmt = lastExpByCat[cat] ?: 0L
            if (lastAmt > 0L && thisAmt > lastAmt) {
                val pct = (((thisAmt - lastAmt) * 100.0) / lastAmt).toInt()
                Triple(cat, pct, thisAmt - lastAmt)
            } else null
        }.sortedByDescending { it.second }

        if (increasedCats.isNotEmpty()) {
            val (cat, pct, _) = increasedCats.first()
            insights.add(
                FinancialInsight(
                    id = "cat_increase_$cat",
                    title = "Spending Trend Alert",
                    message = "Your $cat expenses increased by $pct% compared to last month.",
                    sentiment = InsightSentiment.WARNING
                )
            )
        }

        val decreasedCats = lastExpByCat.mapNotNull { (cat, lastAmt) ->
            val thisAmt = thisExpByCat[cat] ?: 0L
            if (lastAmt > 0L && thisAmt < lastAmt) {
                val pct = (((lastAmt - thisAmt) * 100.0) / lastAmt).toInt()
                Triple(cat, pct, lastAmt - thisAmt)
            } else null
        }.sortedByDescending { it.second }

        if (decreasedCats.isNotEmpty()) {
            val (cat, pct, _) = decreasedCats.first()
            insights.add(
                FinancialInsight(
                    id = "cat_decrease_$cat",
                    title = "Lower Spending Detected",
                    message = "Your $cat expenses decreased by $pct% compared to last month.",
                    sentiment = InsightSentiment.POSITIVE
                )
            )
        }

        // 3. Monthly savings comparison
        val thisIncome = thisMonthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val thisExpense = thisMonthExpenses.sumOf { it.amountPaise }
        val thisSavings = thisIncome - thisExpense

        val lastIncome = lastMonthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val lastExpense = lastMonthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val lastSavings = lastIncome - lastExpense

        if (lastMonthTxs.isNotEmpty()) {
            val delta = thisSavings - lastSavings
            if (delta > 0L) {
                insights.add(
                    FinancialInsight(
                        id = "savings_delta_up",
                        title = "Savings Momentum",
                        message = "You have saved ${IndianCurrencyFormatter.formatPaise(delta)} more than last month.",
                        sentiment = InsightSentiment.POSITIVE
                    )
                )
            } else if (delta < 0L) {
                insights.add(
                    FinancialInsight(
                        id = "savings_delta_down",
                        title = "Savings Watch",
                        message = "Your net savings are ${IndianCurrencyFormatter.formatPaise(-delta)} lower than last month.",
                        sentiment = InsightSentiment.WARNING
                    )
                )
            }
        } else if (thisIncome > 0L) {
            val rate = ((thisSavings * 100.0) / thisIncome).toInt()
            insights.add(
                FinancialInsight(
                    id = "current_savings_rate",
                    title = "Monthly Savings Rate",
                    message = if (thisSavings >= 0L) {
                        "You saved $rate% of your income this month (${IndianCurrencyFormatter.formatPaise(thisSavings)})."
                    } else {
                        "Your expenses exceeded income by ${IndianCurrencyFormatter.formatPaise(-thisSavings)} this month."
                    },
                    sentiment = if (thisSavings >= 0L) InsightSentiment.POSITIVE else InsightSentiment.WARNING
                )
            )
        }

        // 4. Credit separation highlight
        val thisCredits = thisMonthTxs.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise }
        if (thisCredits > 0L) {
            insights.add(
                FinancialInsight(
                    id = "credits_summary",
                    title = "Credited Transfers Tracked",
                    message = "You received ${IndianCurrencyFormatter.formatPaise(thisCredits)} in credits/repayments this month, tracked separately from income.",
                    sentiment = InsightSentiment.INFO
                )
            )
        }

        return insights
    }

    private fun setStartOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun setEndOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
    }
}
