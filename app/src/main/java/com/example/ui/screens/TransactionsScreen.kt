package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.domain.DateFilter
import com.example.domain.FinanceCalculator
import com.example.domain.FinanceCategories
import com.example.domain.TransactionSortOrder
import com.example.domain.TransactionType
import com.example.ui.components.DateRangePickerModal
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TransactionRow
import com.example.ui.components.formatShortDate
import com.example.ui.main.TransactionHistoryFilter

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    filter: TransactionHistoryFilter,
    onUpdateFilter: ((TransactionHistoryFilter) -> TransactionHistoryFilter) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DateRangePickerModal(
            initialStartMillis = filter.dateRange?.startMillis,
            initialEndMillis = filter.dateRange?.endMillis,
            onRangeSelected = { s, e ->
                val resolved = FinanceCalculator.resolveDateRange(DateFilter.CUSTOM, s, e)
                onUpdateFilter { it.copy(dateRange = resolved) }
            },
            onDismiss = { showDatePicker = false }
        )
    }

    val availableCategories = remember(filter.type) {
        if (filter.type != null) {
            FinanceCategories.categoriesFor(filter.type)
        } else {
            (FinanceCategories.expenseCategories + FinanceCategories.incomeCategories + FinanceCategories.creditCategories).distinct()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.headlineMedium
                )
                TextButton(
                    onClick = {
                        val nextOrder = if (filter.sortOrder == TransactionSortOrder.NEWEST_FIRST) {
                            TransactionSortOrder.OLDEST_FIRST
                        } else {
                            TransactionSortOrder.NEWEST_FIRST
                        }
                        onUpdateFilter { it.copy(sortOrder = nextOrder) }
                    },
                    modifier = Modifier.testTag("sort_transactions_button")
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(filter.sortOrder.label)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = filter.query,
                onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                placeholder = { Text("Search title, category, sender, notes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { onUpdateFilter { it.copy(query = "") } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transactions_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter.type == null,
                    onClick = { onUpdateFilter { it.copy(type = null, category = null) } },
                    label = { Text("All") },
                    modifier = Modifier.testTag("filter_type_all")
                )
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = filter.type == t,
                        onClick = { onUpdateFilter { it.copy(type = if (it.type == t) null else t, category = null) } },
                        label = { Text(t.label) },
                        modifier = Modifier.testTag("filter_type_${t.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Category & Date Range filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showCategoryMenu = true },
                        modifier = Modifier.testTag("filter_category_button")
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(filter.category ?: "Category")
                    }
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories") },
                            onClick = {
                                onUpdateFilter { it.copy(category = null) }
                                showCategoryMenu = false
                            }
                        )
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    onUpdateFilter { it.copy(category = cat) }
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.testTag("filter_date_range_button")
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (filter.dateRange != null) {
                            "${formatShortDate(filter.dateRange.startMillis)} - ${formatShortDate(filter.dateRange.endMillis)}"
                        } else {
                            "Date Range"
                        }
                    )
                }

                if (filter.dateRange != null || filter.category != null || filter.type != null || filter.query.isNotBlank()) {
                    TextButton(
                        onClick = { onUpdateFilter { TransactionHistoryFilter() } },
                        modifier = Modifier.testTag("clear_all_filters_button")
                    ) {
                        Text("Reset")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                EmptyStateCard(
                    title = "No Matching Transactions",
                    subtitle = "Try adjusting your filters or record a new transaction.",
                    actionLabel = "Add Transaction",
                    onAction = onAddTransaction
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionRow(
                            transaction = tx,
                            onClick = { onOpenTransaction(tx.id) }
                        )
                    }
                }
            }
        }
    }
}
