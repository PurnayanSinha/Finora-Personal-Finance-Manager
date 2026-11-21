package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.FinanceCategories
import com.example.domain.IndianCurrencyFormatter
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinoraCard
import com.example.ui.main.BudgetStatusItem
import com.example.ui.theme.LocalFinoraColors
import java.text.DateFormatSymbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    month: Int,
    year: Int,
    budgetStatuses: List<BudgetStatusItem>,
    onChangeMonthYear: (Int, Int) -> Unit,
    onSaveBudget: (category: String, limitInput: String, month: Int, year: Int, onDone: () -> Unit) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinoraColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf("OVERALL") }
    var editingLimit by remember { mutableStateOf("") }
    var deleteCandidateId by remember { mutableStateOf<String?>(null) }

    val monthName = remember(month) {
        DateFormatSymbols().months[(month - 1).coerceIn(0, 11)]
    }

    if (deleteCandidateId != null) {
        ConfirmDeleteDialog(
            title = "Delete Budget?",
            message = "Remove this spending limit for $monthName $year?",
            onConfirm = {
                deleteCandidateId?.let(onDeleteBudget)
                deleteCandidateId = null
            },
            onDismiss = { deleteCandidateId = null }
        )
    }

    if (showAddDialog) {
        val options = listOf("OVERALL") + FinanceCategories.expenseCategories
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Set Budget ($monthName $year)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = if (editingCategory == "OVERALL") "Overall Monthly Budget" else editingCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Budget Scope / Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(if (opt == "OVERALL") "Overall Monthly Budget" else opt) },
                                    onClick = {
                                        editingCategory = opt
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editingLimit,
                        onValueChange = { editingLimit = it },
                        label = { Text("Monthly Limit (INR)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveBudget(editingCategory, editingLimit, month, year) {
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_budget_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budgets & Planning",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Button(
                        onClick = {
                            editingCategory = "OVERALL"
                            editingLimit = ""
                            showAddDialog = true
                        },
                        modifier = Modifier.testTag("add_budget_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Budget")
                    }
                }
            }

            // Planning Shortcuts Row (Goals, Recurring, Calendar)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenGoals,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("budgets_goals_shortcut")
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Goals", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenRecurring,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("budgets_recurring_shortcut")
                    ) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recurring", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("budgets_calendar_shortcut")
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calendar", maxLines = 1)
                    }
                }
            }

            // Month Switcher
            item {
                FinoraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val prevMonth = if (month == 1) 12 else month - 1
                            val prevYear = if (month == 1) year - 1 else year
                            onChangeMonthYear(prevMonth, prevYear)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                        }
                        Text(
                            text = "$monthName $year",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = {
                            val nextMonth = if (month == 12) 1 else month + 1
                            val nextYear = if (month == 12) year + 1 else year
                            onChangeMonthYear(nextMonth, nextYear)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                        }
                    }
                }
            }

            if (budgetStatuses.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Budgets for $monthName",
                        subtitle = "Set an overall monthly limit or category budgets to track spending and receive 80% & 100% alerts.",
                        actionLabel = "Set Monthly Budget",
                        onAction = {
                            editingCategory = "OVERALL"
                            editingLimit = ""
                            showAddDialog = true
                        }
                    )
                }
            } else {
                items(budgetStatuses, key = { it.budget.id }) { status ->
                    val ratio = (status.usedPercent / 100f).coerceIn(0f, 1f)
                    val barColor = when {
                        status.usedPercent >= 100f -> semantic.expense
                        status.usedPercent >= 80f -> semantic.warning
                        else -> semantic.income
                    }
                    val title = if (status.budget.category == "OVERALL") {
                        "Overall Monthly Budget"
                    } else {
                        status.budget.category
                    }

                    FinoraCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            editingCategory = status.budget.category
                            editingLimit = IndianCurrencyFormatter.paiseToInputString(status.budget.limitPaise)
                            showAddDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = barColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${status.usedPercent.toInt()}% used",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = barColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { deleteCandidateId = status.budget.id },
                                modifier = Modifier.testTag("delete_budget_${status.budget.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete $title",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = IndianCurrencyFormatter.formatPaise(status.spentPaise),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = IndianCurrencyFormatter.formatPaise(status.budget.limitPaise),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (status.remainingPaise >= 0) "Remaining" else "Over Limit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = IndianCurrencyFormatter.formatPaise(kotlin.math.abs(status.remainingPaise)),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (status.remainingPaise >= 0) semantic.income else semantic.expense
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
