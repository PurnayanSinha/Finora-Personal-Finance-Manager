package com.example.domain

enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    CREDIT("Credit"),
    OUTGOING_TRANSFER("Transfer Out")
}

enum class DateFilter(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom"),
    ALL_TIME("All Time")
}

enum class AnalyticsPeriod(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom")
}

enum class RecurrenceFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class ThemePreference(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class NotificationType {
    BUDGET_80,
    BUDGET_100,
    RECURRING_DUE,
    GOAL_PROGRESS,
    DAILY_REMINDER,
    SYSTEM
}

enum class TransactionSortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First")
}

object FinanceCategories {
    val incomeCategories = listOf(
        "Salary",
        "Freelancing",
        "Business",
        "Investments",
        "Interest",
        "Gifts",
        "Other Income"
    )

    val expenseCategories = listOf(
        "Food & Dining",
        "Groceries",
        "Shopping",
        "Transportation",
        "Fuel",
        "Rent",
        "Electricity & Bills",
        "Healthcare",
        "Education",
        "Entertainment",
        "Travel",
        "Subscriptions",
        "Personal Care",
        "Other Expenses"
    )

    val creditCategories = listOf(
        "Friends",
        "Family",
        "Bank Transfer",
        "UPI Transfer",
        "Refunds",
        "Loan Repayments",
        "Other Sources"
    )

    val outgoingTransferCategories = listOf(
        "Transfer to Friends",
        "Family Support",
        "Loan Given",
        "Bank Transfer Out",
        "Wallet Top-up"
    )

    val paymentMethods = listOf(
        "UPI",
        "Bank Transfer",
        "Debit Card",
        "Credit Card",
        "Cash",
        "Net Banking",
        "Digital Wallet"
    )

    fun categoriesFor(type: TransactionType): List<String> = when (type) {
        TransactionType.INCOME -> incomeCategories
        TransactionType.EXPENSE -> expenseCategories
        TransactionType.CREDIT -> creditCategories
        TransactionType.OUTGOING_TRANSFER -> outgoingTransferCategories
    }
}

data class DateRange(
    val startMillis: Long,
    val endMillis: Long
)

data class FinancialSummary(
    val openingBalancePaise: Long,
    val availableBalancePaise: Long,
    val periodIncomePaise: Long,
    val periodExpensePaise: Long,
    val periodCreditPaise: Long,
    val periodOutgoingTransferPaise: Long,
    val periodNetSavingsPaise: Long,
    val savingsRatePercent: Double
)

data class CategorySlice(
    val category: String,
    val amountPaise: Long,
    val percentage: Double,
    val colorIndex: Int
)

data class PeriodBarPoint(
    val label: String,
    val incomePaise: Long,
    val expensePaise: Long,
    val creditPaise: Long
)

data class TrendPoint(
    val label: String,
    val amountPaise: Long
)

enum class InsightSentiment {
    POSITIVE,
    WARNING,
    INFO
}

data class FinancialInsight(
    val id: String,
    val title: String,
    val message: String,
    val sentiment: InsightSentiment
)
