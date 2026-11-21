package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AnalyticsPeriod
import com.example.domain.IndianCurrencyFormatter
import com.example.export.ExportFormat
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DateRangePickerModal
import com.example.ui.components.FinoraCard
import com.example.ui.components.HorizontalDistributionChart
import com.example.ui.components.IncomeVsExpenseBarChart
import com.example.ui.components.TrendLineChart
import com.example.ui.components.formatShortDate
import com.example.ui.main.AnalyticsUiState
import com.example.ui.theme.LocalFinoraColors
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    onSelectPeriod: (AnalyticsPeriod, Long?, Long?) -> Unit,
    onShareExport: (ExportFormat) -> Unit,
    onSaveExportToStream: (ExportFormat, OutputStream?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = LocalFinoraColors.current
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingSaveFormat by remember { mutableStateOf<ExportFormat?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingSaveFormat
        if (uri != null && fmt != null) {
            val stream = context.contentResolver.openOutputStream(uri)
            onSaveExportToStream(fmt, stream)
        }
        pendingSaveFormat = null
    }

    if (showCustomRangePicker) {
        DateRangePickerModal(
            initialStartMillis = state.customStartMillis,
            initialEndMillis = state.customEndMillis,
            onRangeSelected = { s, e -> onSelectPeriod(AnalyticsPeriod.CUSTOM, s, e) },
            onDismiss = { showCustomRangePicker = false }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Financial Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose a format to save to your device or share (${state.period.label}):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ExportFormat.entries.forEach { fmt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showExportDialog = false
                                    pendingSaveFormat = fmt
                                    createDocumentLauncher.launch("Finora_Report.${fmt.extension}")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_save_${fmt.extension}")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save ${fmt.extension.uppercase()}")
                            }
                            OutlinedButton(
                                onClick = {
                                    showExportDialog = false
                                    onShareExport(fmt)
                                },
                                modifier = Modifier.testTag("export_share_${fmt.extension}")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share ${fmt.label}", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            }
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Analytics & Reports",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${state.filteredTransactions.size} transactions in period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("open_export_dialog_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export")
                    }
                }
            }

            // Period Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = state.period == period,
                                onClick = {
                                    if (period == AnalyticsPeriod.CUSTOM) {
                                        showCustomRangePicker = true
                                    } else {
                                        onSelectPeriod(period, null, null)
                                    }
                                },
                                label = { Text(period.label) },
                                modifier = Modifier.testTag("analytics_period_${period.name.lowercase()}")
                            )
                        }
                    }
                    if (state.period == AnalyticsPeriod.CUSTOM &&
                        state.customStartMillis != null &&
                        state.customEndMillis != null
                    ) {
                        Text(
                            text = "Range: ${formatShortDate(state.customStartMillis)} – ${formatShortDate(state.customEndMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Period KPI Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMiniCard(
                        title = "Income",
                        value = IndianCurrencyFormatter.formatPaise(state.summary.periodIncomePaise),
                        color = semantic.income,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniCard(
                        title = "Expenses",
                        value = IndianCurrencyFormatter.formatPaise(state.summary.periodExpensePaise),
                        color = semantic.expense,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniCard(
                        title = "Credits",
                        value = IndianCurrencyFormatter.formatPaise(state.summary.periodCreditPaise),
                        color = semantic.credit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // A. Expense Category Pie Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Expense Category Breakdown",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap any slice to inspect exact amount & share",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryDonutChart(
                        slices = state.expenseSlices,
                        centerTitle = "Total Spent"
                    )
                }
            }

            // B. Income vs Expense Bar Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Income vs Expense Comparison",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IncomeVsExpenseBarChart(points = state.barSeries)
                }
            }

            // C. Expense Trend Line Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Expense Trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrendLineChart(
                        points = state.expenseTrend,
                        lineColor = semantic.expense,
                        seriesLabel = "Expense"
                    )
                }
            }

            // D. Income Distribution Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Income Distribution",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDistributionChart(slices = state.incomeSlices)
                }
            }

            // E. Credit History Chart
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Money Credited History",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Refunds, repayments & transfers received (tracked separate from income)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrendLineChart(
                        points = state.creditHistory,
                        lineColor = semantic.credit,
                        seriesLabel = "Credits"
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    FinoraCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
