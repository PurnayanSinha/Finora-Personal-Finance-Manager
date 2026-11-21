package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DateFilter
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.InsightSentiment
import com.example.domain.TransactionType
import com.example.ui.components.DateRangePickerModal
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinoraCard
import com.example.ui.components.IncomeVsExpenseBarChart
import com.example.ui.components.TransactionRow
import com.example.ui.components.formatShortDate
import com.example.ui.main.DashboardUiState
import com.example.ui.theme.LocalFinoraColors

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSelectDateFilter: (DateFilter, Long?, Long?) -> Unit,
    onQuickAdd: (TransactionType) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenBudgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinoraColors.current
    var showCustomRangePicker by remember { mutableStateOf(false) }

    if (showCustomRangePicker) {
        DateRangePickerModal(
            initialStartMillis = state.customStartMillis,
            initialEndMillis = state.customEndMillis,
            onRangeSelected = { s, e ->
                onSelectDateFilter(DateFilter.CUSTOM, s, e)
            },
            onDismiss = { showCustomRangePicker = false }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${state.user?.fullName?.split(" ")?.firstOrNull() ?: "Investor"}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Here is your financial overview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenCalendar,
                            modifier = Modifier.testTag("dashboard_calendar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Calendar View"
                            )
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("dashboard_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.unreadNotificationsCount > 0) {
                                        Badge {
                                            Text(state.unreadNotificationsCount.coerceAtMost(99).toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Notifications"
                                )
                            }
                        }
                    }
                }
            }

            // Total Available Balance Hero Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_balance_card")
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(semantic.heroCardStart, semantic.heroCardEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total Available Balance",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = IndianCurrencyFormatter.formatPaise(state.summary.availableBalancePaise),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.White,
                                        modifier = Modifier.testTag("total_available_balance")
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BalancePill(
                                    label = "Income",
                                    amount = IndianCurrencyFormatter.formatPaise(state.summary.periodIncomePaise),
                                    accentColor = Color(0xFF4ADE80),
                                    modifier = Modifier.weight(1f)
                                )
                                BalancePill(
                                    label = "Expenses",
                                    amount = IndianCurrencyFormatter.formatPaise(state.summary.periodExpensePaise),
                                    accentColor = Color(0xFFF87171),
                                    modifier = Modifier.weight(1f)
                                )
                                BalancePill(
                                    label = "Credited",
                                    amount = IndianCurrencyFormatter.formatPaise(state.summary.periodCreditPaise),
                                    accentColor = Color(0xFF60A5FA),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Date Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DateFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.dateFilter == filter,
                                onClick = {
                                    if (filter == DateFilter.CUSTOM) {
                                        showCustomRangePicker = true
                                    } else {
                                        onSelectDateFilter(filter, null, null)
                                    }
                                },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("date_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                    if (state.dateFilter == DateFilter.CUSTOM &&
                        state.customStartMillis != null &&
                        state.customEndMillis != null
                    ) {
                        Text(
                            text = "Custom Range: ${formatShortDate(state.customStartMillis)} – ${formatShortDate(state.customEndMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Action Buttons (Income / Expense / Credit / Goals / Recurring)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        label = "Add Income",
                        icon = Icons.Filled.ArrowDownward,
                        tint = semantic.income,
                        container = semantic.incomeContainer,
                        onClick = { onQuickAdd(TransactionType.INCOME) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_income")
                    )
                    QuickActionTile(
                        label = "Add Expense",
                        icon = Icons.Filled.ArrowUpward,
                        tint = semantic.expense,
                        container = semantic.expenseContainer,
                        onClick = { onQuickAdd(TransactionType.EXPENSE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_expense")
                    )
                    QuickActionTile(
                        label = "Add Credit",
                        icon = Icons.AutoMirrored.Filled.CallReceived,
                        tint = semantic.credit,
                        container = semantic.creditContainer,
                        onClick = { onQuickAdd(TransactionType.CREDIT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_credit")
                    )
                }
            }

            // Savings Overview Card
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Savings,
                                contentDescription = null,
                                tint = semantic.income
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Savings Overview (${state.dateFilter.label})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.summary.periodNetSavingsPaise >= 0) semantic.incomeContainer else semantic.expenseContainer
                        ) {
                            Text(
                                text = "${state.summary.savingsRatePercent}% saved",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (state.summary.periodNetSavingsPaise >= 0) semantic.income else semantic.expense,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = IndianCurrencyFormatter.formatPaise(state.summary.periodNetSavingsPaise, includeSign = true),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (state.summary.periodNetSavingsPaise >= 0) semantic.income else semantic.expense
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Opening Balance: ${IndianCurrencyFormatter.formatPaise(state.summary.openingBalancePaise)} • Credited inflows excluded from income",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = onOpenGoals,
                            modifier = Modifier.testTag("open_savings_goals_button")
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Savings Goals")
                        }
                        TextButton(
                            onClick = onOpenRecurring,
                            modifier = Modifier.testTag("open_recurring_button")
                        ) {
                            Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recurring")
                        }
                    }
                }
            }

            // Monthly Budget Summary Card
            item {
                val overallBudget = state.budgetStatuses.firstOrNull { it.budget.category == "OVERALL" }
                    ?: state.budgetStatuses.firstOrNull()
                FinoraCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenBudgets
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Spending Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (overallBudget != null) {
                        val ratio = (overallBudget.usedPercent / 100f).coerceIn(0f, 1f)
                        val barColor = when {
                            overallBudget.usedPercent >= 100f -> semantic.expense
                            overallBudget.usedPercent >= 80f -> semantic.warning
                            else -> semantic.income
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spent ${IndianCurrencyFormatter.formatPaise(overallBudget.spentPaise)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Limit ${IndianCurrencyFormatter.formatPaise(overallBudget.budget.limitPaise)} (${overallBudget.usedPercent.toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = barColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Text(
                            text = "No monthly budget configured yet. Tap to set your monthly spending limit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Income vs Expense Comparison Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Income vs Expense (This Month)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IncomeVsExpenseBarChart(points = state.monthlyBarSeries)
                }
            }

            // Smart Financial Insights
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = semantic.warning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Financial Insights",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.smartInsights.take(3).forEach { insight ->
                            val (bg, fg, icon) = when (insight.sentiment) {
                                InsightSentiment.POSITIVE -> Triple(
                                    semantic.incomeContainer,
                                    semantic.income,
                                    Icons.AutoMirrored.Filled.TrendingUp
                                )
                                InsightSentiment.WARNING -> Triple(
                                    semantic.expenseContainer,
                                    semantic.expense,
                                    Icons.AutoMirrored.Filled.TrendingDown
                                )
                                InsightSentiment.INFO -> Triple(
                                    semantic.creditContainer,
                                    semantic.credit,
                                    Icons.Filled.Lightbulb
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = bg.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = fg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = insight.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = insight.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = onOpenAllTransactions,
                        modifier = Modifier.testTag("see_all_transactions_button")
                    ) {
                        Text("See All")
                    }
                }
            }

            if (state.recentTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Transactions Yet",
                        subtitle = "Tap + below to record your first income, expense, or credit.",
                        actionLabel = "Add Expense",
                        onAction = { onQuickAdd(TransactionType.EXPENSE) }
                    )
                }
            } else {
                items(state.recentTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        onClick = { onOpenTransaction(tx.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BalancePill(
    label: String,
    amount: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2E8F0)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
