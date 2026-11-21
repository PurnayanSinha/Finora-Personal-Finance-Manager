package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.domain.FinanceCategories
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.TransactionType
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.FinoraCard
import com.example.ui.components.SingleDatePickerModal
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatShortDate
import com.example.ui.components.iconForCategory
import com.example.ui.components.transactionColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    initialType: TransactionType,
    existingTransaction: TransactionEntity?,
    onSave: (
        type: TransactionType,
        amountInput: String,
        title: String,
        category: String,
        sourceOrSender: String,
        paymentMethod: String,
        notes: String,
        referenceNumber: String,
        dateMillis: Long
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedType by rememberSaveable { mutableStateOf(existingTransaction?.type ?: initialType) }
    var amountInput by rememberSaveable {
        mutableStateOf(existingTransaction?.let { IndianCurrencyFormatter.paiseToInputString(it.amountPaise) } ?: "")
    }
    var title by rememberSaveable { mutableStateOf(existingTransaction?.title ?: "") }
    var category by rememberSaveable {
        mutableStateOf(existingTransaction?.category ?: FinanceCategories.categoriesFor(initialType).first())
    }
    var sourceOrSender by rememberSaveable { mutableStateOf(existingTransaction?.sourceOrSender ?: "") }
    var paymentMethod by rememberSaveable {
        mutableStateOf(existingTransaction?.paymentMethod ?: FinanceCategories.paymentMethods.first())
    }
    var notes by rememberSaveable { mutableStateOf(existingTransaction?.notes ?: "") }
    var referenceNumber by rememberSaveable { mutableStateOf(existingTransaction?.referenceNumber ?: "") }
    var dateMillis by rememberSaveable {
        mutableLongStateOf(existingTransaction?.transactionDateMillis ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(existingTransaction?.id) {
        if (existingTransaction != null) {
            selectedType = existingTransaction.type
            amountInput = IndianCurrencyFormatter.paiseToInputString(existingTransaction.amountPaise)
            title = existingTransaction.title
            category = existingTransaction.category
            sourceOrSender = existingTransaction.sourceOrSender
            paymentMethod = existingTransaction.paymentMethod
            notes = existingTransaction.notes
            referenceNumber = existingTransaction.referenceNumber
            dateMillis = existingTransaction.transactionDateMillis
        }
    }

    if (showDatePicker) {
        SingleDatePickerModal(
            initialMillis = dateMillis,
            onDateSelected = { selected -> dateMillis = selected },
            onDismiss = { showDatePicker = false }
        )
    }

    val categories = FinanceCategories.categoriesFor(selectedType)
    val parsedPaise = IndianCurrencyFormatter.parseRupeesToPaise(amountInput)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingTransaction == null) "Record ${selectedType.label}"
                        else "Edit Transaction"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("form_back_button")) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Transaction Type Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = {
                                selectedType = t
                                val validCats = FinanceCategories.categoriesFor(t)
                                if (category !in validCats) {
                                    category = validCats.first()
                                }
                            },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("form_type_${t.name.lowercase()}")
                        )
                    }
                }

                // Amount Input
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount (INR)") },
                    prefix = { Text("₹ ") },
                    supportingText = {
                        if (parsedPaise != null) {
                            Text("Formatted: ${IndianCurrencyFormatter.formatPaise(parsedPaise)}")
                        } else if (amountInput.isNotBlank()) {
                            Text("Enter a valid positive amount", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = amountInput.isNotBlank() && parsedPaise == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input")
                )

                // Title / Description
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            when (selectedType) {
                                TransactionType.INCOME -> "Income Title (e.g., November Salary)"
                                TransactionType.EXPENSE -> "Description / Title (e.g., Dinner with friends)"
                                TransactionType.CREDIT -> "Credit Title (e.g., UPI Refund / Friend Repayment)"
                                TransactionType.OUTGOING_TRANSFER -> "Transfer Title"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_title_input")
                )

                // Source / Sender for Credit or Transfer
                if (selectedType == TransactionType.CREDIT || selectedType == TransactionType.OUTGOING_TRANSFER) {
                    OutlinedTextField(
                        value = sourceOrSender,
                        onValueChange = { sourceOrSender = it },
                        label = {
                            Text(
                                if (selectedType == TransactionType.CREDIT) "Sender / Source Name"
                                else "Recipient Name"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_sender_input")
                    )
                }

                // Category Chips
                Text(
                    text = if (selectedType == TransactionType.CREDIT) "Credit Source Category" else "Category",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            leadingIcon = if (category == cat) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("category_chip_${cat.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Payment Method
                Text(text = "Payment Method", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FinanceCategories.paymentMethods.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method) },
                            modifier = Modifier.testTag("payment_chip_${method.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Date Picker Row
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_date_button")
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transaction Date: ${formatShortDate(dateMillis)}")
                }

                // Optional Reference & Notes
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("Transaction Reference (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_reference_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_notes_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        onSave(
                            selectedType,
                            amountInput,
                            title,
                            category,
                            sourceOrSender,
                            paymentMethod,
                            notes,
                            referenceNumber,
                            dateMillis
                        )
                    },
                    enabled = parsedPaise != null,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_transaction_button")
                ) {
                    Text(
                        text = if (existingTransaction == null) "Save ${selectedType.label}" else "Update Transaction",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: TransactionEntity?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "Delete Transaction?",
            message = "This will permanently remove this record and recalculate your available balance.",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
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
            if (transaction == null) {
                Text(
                    text = "Transaction not found.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                val (fg, bg) = transactionColor(transaction.type)
                val sign = when (transaction.type) {
                    TransactionType.INCOME, TransactionType.CREDIT -> "+"
                    TransactionType.EXPENSE, TransactionType.OUTGOING_TRANSFER -> "-"
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FinoraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconForCategory(transaction.category, transaction.type),
                                    contentDescription = transaction.category,
                                    tint = fg,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = transaction.title,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = bg
                                ) {
                                    Text(
                                        text = transaction.type.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "$sign${IndianCurrencyFormatter.formatPaise(transaction.amountPaise)}",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = fg
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailLine("Category", transaction.category)
                        DetailLine("Payment Method", transaction.paymentMethod)
                        DetailLine("Date & Time", formatDateTime(transaction.transactionDateMillis))
                        if (transaction.sourceOrSender.isNotBlank()) {
                            DetailLine("Sender / Source", transaction.sourceOrSender)
                        }
                        if (transaction.referenceNumber.isNotBlank()) {
                            DetailLine("Reference ID", transaction.referenceNumber)
                        }
                        if (transaction.notes.isNotBlank()) {
                            DetailLine("Notes", transaction.notes)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("delete_transaction_button")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                        Button(
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("edit_transaction_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
