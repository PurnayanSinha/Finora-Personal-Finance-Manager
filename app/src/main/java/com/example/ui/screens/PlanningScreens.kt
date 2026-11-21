package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.local.RecurringTransactionEntity
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.FinanceCategories
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.RecurrenceFrequency
import com.example.domain.TransactionType
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinoraCard
import com.example.ui.components.SingleDatePickerModal
import com.example.ui.components.TransactionRow
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalFinoraColors
import java.text.DateFormatSymbols
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    goals: List<SavingsGoalEntity>,
    onSaveGoal: (goalId: String?, name: String, targetInput: String, currentInput: String, targetDateMillis: Long?, onDone: () -> Unit) -> Unit,
    onContributeGoal: (goalId: String, amountInput: String, isWithdraw: Boolean) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val semantic = LocalFinoraColors.current

    var showFormDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var contributeTarget by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var deleteGoalId by remember { mutableStateOf<String?>(null) }

    if (deleteGoalId != null) {
        ConfirmDeleteDialog(
            title = "Delete Savings Goal?",
            message = "Are you sure you want to remove this financial goal?",
            onConfirm = {
                deleteGoalId?.let(onDeleteGoal)
                deleteGoalId = null
            },
            onDismiss = { deleteGoalId = null }
        )
    }

    if (showFormDialog) {
        var name by remember { mutableStateOf(editingGoal?.goalName ?: "") }
        var target by remember {
            mutableStateOf(editingGoal?.let { IndianCurrencyFormatter.paiseToInputString(it.targetPaise) } ?: "")
        }
        var current by remember {
            mutableStateOf(editingGoal?.let { IndianCurrencyFormatter.paiseToInputString(it.currentPaise) } ?: "0")
        }
        var targetDate by remember { mutableStateOf(editingGoal?.targetDateMillis) }
        var showDatePicker by remember { mutableStateOf(false) }

        if (showDatePicker) {
            SingleDatePickerModal(
                initialMillis = targetDate ?: System.currentTimeMillis(),
                onDateSelected = { targetDate = it },
                onDismiss = { showDatePicker = false }
            )
        }

        AlertDialog(
            onDismissRequest = { showFormDialog = false },
            title = { Text(if (editingGoal == null) "New Savings Goal" else "Edit Savings Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Goal Name (e.g., Emergency Fund)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_name_input")
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target Amount (INR)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_target_input")
                    )
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current Saved (INR)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_current_input")
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(targetDate?.let { "Target: ${formatShortDate(it)}" } ?: "Set Target Date (Optional)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveGoal(editingGoal?.id, name, target, current, targetDate) {
                            showFormDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (contributeTarget != null) {
        val goal = contributeTarget!!
        var amount by remember { mutableStateOf("") }
        var isWithdraw by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { contributeTarget = null },
            title = { Text("Update ${goal.goalName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isWithdraw,
                            onClick = { isWithdraw = false },
                            label = { Text("Add Funds") }
                        )
                        FilterChip(
                            selected = isWithdraw,
                            onClick = { isWithdraw = true },
                            label = { Text("Withdraw") }
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (INR)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_contribute_amount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onContributeGoal(goal.id, amount, isWithdraw)
                        contributeTarget = null
                    },
                    modifier = Modifier.testTag("confirm_goal_contribute_button")
                ) {
                    Text(if (isWithdraw) "Withdraw" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { contributeTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("goals_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingGoal = null
                            showFormDialog = true
                        },
                        modifier = Modifier.testTag("add_goal_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New Goal")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 660.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (goals.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Savings Goals Yet",
                            subtitle = "Plan for a laptop, vacation, or emergency fund and track your progress.",
                            icon = Icons.Filled.Flag,
                            actionLabel = "Create Goal",
                            onAction = {
                                editingGoal = null
                                showFormDialog = true
                            }
                        )
                    }
                } else {
                    items(goals, key = { it.id }) { goal ->
                        val ratio = if (goal.targetPaise > 0L) {
                            (goal.currentPaise.toFloat() / goal.targetPaise.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val pct = (ratio * 100f).toInt()
                        val remaining = (goal.targetPaise - goal.currentPaise).coerceAtLeast(0L)

                        FinoraCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                editingGoal = goal
                                showFormDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.goalName, style = MaterialTheme.typography.titleMedium)
                                    goal.targetDateMillis?.let { dt ->
                                        Text(
                                            text = "Target Date: ${formatShortDate(dt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (pct >= 100) semantic.incomeContainer else semantic.creditContainer
                                ) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (pct >= 100) semantic.income else semantic.credit,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(onClick = { deleteGoalId = goal.id }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${goal.goalName}")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = if (pct >= 100) semantic.income else semantic.credit,
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
                                Text(
                                    text = "Saved: ${IndianCurrencyFormatter.formatPaise(goal.currentPaise)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Remaining: ${IndianCurrencyFormatter.formatPaise(remaining)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { contributeTarget = goal },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add / Withdraw Savings")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(
    items: List<RecurringTransactionEntity>,
    onSaveRecurring: (
        id: String?,
        type: TransactionType,
        title: String,
        amountInput: String,
        category: String,
        paymentMethod: String,
        notes: String,
        frequency: RecurrenceFrequency,
        nextDueMillis: Long,
        onDone: () -> Unit
    ) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onProcessDueNow: () -> Unit,
    onDeleteRecurring: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val semantic = LocalFinoraColors.current
    var showFormDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var deleteCandidateId by remember { mutableStateOf<String?>(null) }

    if (deleteCandidateId != null) {
        ConfirmDeleteDialog(
            title = "Delete Recurring Entry?",
            message = "Stop and remove this scheduled transaction?",
            onConfirm = {
                deleteCandidateId?.let(onDeleteRecurring)
                deleteCandidateId = null
            },
            onDismiss = { deleteCandidateId = null }
        )
    }

    if (showFormDialog) {
        var type by remember { mutableStateOf(editingItem?.type ?: TransactionType.EXPENSE) }
        var title by remember { mutableStateOf(editingItem?.title ?: "") }
        var amount by remember {
            mutableStateOf(editingItem?.let { IndianCurrencyFormatter.paiseToInputString(it.amountPaise) } ?: "")
        }
        var category by remember {
            mutableStateOf(editingItem?.category ?: FinanceCategories.categoriesFor(type).first())
        }
        var paymentMethod by remember {
            mutableStateOf(editingItem?.paymentMethod ?: FinanceCategories.paymentMethods.first())
        }
        var frequency by remember { mutableStateOf(editingItem?.frequency ?: RecurrenceFrequency.MONTHLY) }
        var nextDue by remember { mutableLongStateOf(editingItem?.nextDueDateMillis ?: System.currentTimeMillis()) }
        var notes by remember { mutableStateOf(editingItem?.notes ?: "") }
        var catExpanded by remember { mutableStateOf(false) }
        var showDatePicker by remember { mutableStateOf(false) }

        if (showDatePicker) {
            SingleDatePickerModal(
                initialMillis = nextDue,
                onDateSelected = { nextDue = it },
                onDismiss = { showDatePicker = false }
            )
        }

        AlertDialog(
            onDismissRequest = { showFormDialog = false },
            title = { Text(if (editingItem == null) "New Recurring Schedule" else "Edit Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(TransactionType.INCOME, TransactionType.EXPENSE).forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = {
                                    type = t
                                    category = FinanceCategories.categoriesFor(t).first()
                                },
                                label = { Text(t.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g., House Rent / Salary)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (INR)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = !catExpanded }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            FinanceCategories.categoriesFor(type).forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(freq.label) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next Due: ${formatShortDate(nextDue)}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveRecurring(
                        editingItem?.id,
                        type,
                        title,
                        amount,
                        category,
                        paymentMethod,
                        notes,
                        frequency,
                        nextDue
                    ) {
                        showFormDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("recurring_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onProcessDueNow) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Process Due Entries")
                    }
                    IconButton(onClick = {
                        editingItem = null
                        showFormDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Recurring")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 660.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Recurring Transactions",
                            subtitle = "Automate monthly salary, house rent, electricity bills, or subscriptions.",
                            icon = Icons.Filled.Repeat,
                            actionLabel = "Add Schedule",
                            onAction = {
                                editingItem = null
                                showFormDialog = true
                            }
                        )
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        val color = if (item.type == TransactionType.INCOME) semantic.income else semantic.expense
                        FinoraCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                editingItem = item
                                showFormDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${item.category} • ${item.frequency.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Next Due: ${formatShortDate(item.nextDueDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = IndianCurrencyFormatter.formatPaise(item.amountPaise),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = color
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = item.isActive,
                                            onCheckedChange = { onToggleActive(item.id, it) }
                                        )
                                        IconButton(onClick = { deleteCandidateId = item.id }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    allTransactions: List<TransactionEntity>,
    onOpenTransaction: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val semantic = LocalFinoraColors.current
    val now = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) } // 0..11
    var selectedDay by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }

    val monthName = DateFormatSymbols().months[currentMonth]
    val daysInMonth = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val firstDayOffset = remember(currentYear, currentMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        (c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
    }

    val txByDay = remember(allTransactions, currentYear, currentMonth) {
        val cal = Calendar.getInstance()
        allTransactions.filter { tx ->
            cal.timeInMillis = tx.transactionDateMillis
            cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
        }.groupBy { tx ->
            cal.timeInMillis = tx.transactionDateMillis
            cal.get(Calendar.DAY_OF_MONTH)
        }
    }

    val selectedTxs = txByDay[selectedDay].orEmpty().sortedByDescending { it.transactionDateMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("calendar_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 660.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FinoraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (currentMonth == 0) {
                                    currentMonth = 11
                                    currentYear--
                                } else {
                                    currentMonth--
                                }
                                selectedDay = 1
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                            }
                            Text("$monthName $currentYear", style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = {
                                if (currentMonth == 11) {
                                    currentMonth = 0
                                    currentYear++
                                } else {
                                    currentMonth++
                                }
                                selectedDay = 1
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = d,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        val totalCells = firstDayOffset + daysInMonth
                        val rows = (totalCells + 6) / 7
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (r in 0 until rows) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (c in 0..6) {
                                        val cellIdx = r * 7 + c
                                        val dayNum = cellIdx - firstDayOffset + 1
                                        if (dayNum in 1..daysInMonth) {
                                            val dayTxs = txByDay[dayNum].orEmpty()
                                            val isSelected = dayNum == selectedDay
                                            val hasIncome = dayTxs.any { it.type == TransactionType.INCOME }
                                            val hasExpense = dayTxs.any { it.type == TransactionType.EXPENSE }
                                            val hasCredit = dayTxs.any { it.type == TransactionType.CREDIT }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    )
                                                    .clickable { selectedDay = dayNum },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = dayNum.toString(),
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (dayTxs.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            if (hasIncome) Dot(semantic.income)
                                                            if (hasExpense) Dot(semantic.expense)
                                                            if (hasCredit) Dot(semantic.credit)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Activity on $selectedDay $monthName $currentYear (${selectedTxs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (selectedTxs.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Activity",
                            subtitle = "No transactions recorded on this date."
                        )
                    }
                } else {
                    items(selectedTxs, key = { it.id }) { tx ->
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

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}
