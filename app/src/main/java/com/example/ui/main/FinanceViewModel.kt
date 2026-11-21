package com.example.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BudgetEntity
import com.example.data.local.NotificationItemEntity
import com.example.data.local.RecurringTransactionEntity
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.FinanceRepository
import com.example.domain.AnalyticsPeriod
import com.example.domain.CategorySlice
import com.example.domain.DateFilter
import com.example.domain.DateRange
import com.example.domain.FinanceCalculator
import com.example.domain.FinancialInsight
import com.example.domain.FinancialSummary
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.PeriodBarPoint
import com.example.domain.RecurrenceFrequency
import com.example.domain.ThemePreference
import com.example.domain.TransactionSortOrder
import com.example.domain.TransactionType
import com.example.domain.TrendPoint
import com.example.export.ExportFormat
import com.example.export.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Calendar

data class BudgetStatusItem(
    val budget: BudgetEntity,
    val spentPaise: Long,
    val remainingPaise: Long,
    val usedPercent: Float
)

data class DashboardUiState(
    val user: UserEntity? = null,
    val dateFilter: DateFilter = DateFilter.THIS_MONTH,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val summary: FinancialSummary = FinancialSummary(0, 0, 0, 0, 0, 0, 0, 0.0),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val smartInsights: List<FinancialInsight> = emptyList(),
    val budgetStatuses: List<BudgetStatusItem> = emptyList(),
    val monthlyBarSeries: List<PeriodBarPoint> = emptyList(),
    val unreadNotificationsCount: Int = 0
)

data class TransactionHistoryFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val category: String? = null,
    val dateRange: DateRange? = null,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST_FIRST
)

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTHLY,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val summary: FinancialSummary = FinancialSummary(0, 0, 0, 0, 0, 0, 0, 0.0),
    val expenseSlices: List<CategorySlice> = emptyList(),
    val incomeSlices: List<CategorySlice> = emptyList(),
    val barSeries: List<PeriodBarPoint> = emptyList(),
    val expenseTrend: List<TrendPoint> = emptyList(),
    val creditHistory: List<TrendPoint> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val insights: List<FinancialInsight> = emptyList()
)

sealed class UiEvent {
    data class Snackbar(val message: String) : UiEvent()
    data class ShareFile(val uri: Uri, val mimeType: String, val title: String) : UiEvent()
}

class FinanceViewModel(
    private val userId: String,
    private val authRepository: AuthRepository,
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val user: StateFlow<UserEntity?> = authRepository.observeUser(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allTransactions: StateFlow<List<TransactionEntity>> = financeRepository.observeTransactions(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = financeRepository.observeBudgets(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val goals: StateFlow<List<SavingsGoalEntity>> = financeRepository.observeGoals(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val recurringTransactions: StateFlow<List<RecurringTransactionEntity>> =
        financeRepository.observeRecurring(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val settings: StateFlow<UserSettingsEntity> = financeRepository.observeSettings(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserSettingsEntity(userId = userId))

    val notifications: StateFlow<List<NotificationItemEntity>> =
        financeRepository.observeNotifications(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _dashboardFilter = MutableStateFlow(DateFilter.THIS_MONTH)
    private val _dashboardCustomStart = MutableStateFlow<Long?>(null)
    private val _dashboardCustomEnd = MutableStateFlow<Long?>(null)

    private val _historyFilter = MutableStateFlow(TransactionHistoryFilter())
    val historyFilter: StateFlow<TransactionHistoryFilter> = _historyFilter.asStateFlow()

    private val _analyticsPeriod = MutableStateFlow(AnalyticsPeriod.MONTHLY)
    private val _analyticsCustomStart = MutableStateFlow<Long?>(null)
    private val _analyticsCustomEnd = MutableStateFlow<Long?>(null)

    private val _budgetMonthYear = MutableStateFlow(
        Calendar.getInstance().let { Pair(it.get(Calendar.MONTH) + 1, it.get(Calendar.YEAR)) }
    )
    val budgetMonthYear: StateFlow<Pair<Int, Int>> = _budgetMonthYear.asStateFlow()

    init {
        viewModelScope.launch {
            financeRepository.processDueRecurringTransactions(userId)
        }
    }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        user,
        allTransactions,
        budgets,
        notifications,
        combine(_dashboardFilter, _dashboardCustomStart, _dashboardCustomEnd) { f, s, e -> Triple(f, s, e) }
    ) { currentUser, txs, userBudgets, userNotifications, filterTuple ->
        val (filter, customS, customE) = filterTuple
        val opening = currentUser?.openingBalancePaise ?: 0L
        val range = FinanceCalculator.resolveDateRange(filter, customS, customE)
        val periodTxs = FinanceCalculator.filterByDateRange(txs, range)
        val summary = FinanceCalculator.calculateSummary(opening, txs, periodTxs)

        val nowCal = Calendar.getInstance()
        val curMonth = nowCal.get(Calendar.MONTH) + 1
        val curYear = nowCal.get(Calendar.YEAR)
        val monthBudgets = userBudgets.filter { it.month == curMonth && it.year == curYear }
        val budgetStatuses = computeBudgetStatuses(monthBudgets, txs, curMonth, curYear)

        val monthRange = FinanceCalculator.resolveAnalyticsRange(AnalyticsPeriod.MONTHLY, null, null)
        val monthTxs = FinanceCalculator.filterByDateRange(txs, monthRange)
        val barSeries = FinanceCalculator.buildPeriodSeries(monthTxs, AnalyticsPeriod.MONTHLY, monthRange)

        DashboardUiState(
            user = currentUser,
            dateFilter = filter,
            customStartMillis = customS,
            customEndMillis = customE,
            summary = summary,
            recentTransactions = periodTxs.take(6),
            smartInsights = FinanceCalculator.generateSmartInsights(txs),
            budgetStatuses = budgetStatuses,
            monthlyBarSeries = barSeries,
            unreadNotificationsCount = userNotifications.count { !it.isRead }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardUiState())

    val filteredHistoryTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        _historyFilter
    ) { txs, filter ->
        val q = filter.query.trim().lowercase()
        val filtered = txs.filter { tx ->
            val matchesQuery = q.isEmpty() ||
                tx.title.lowercase().contains(q) ||
                tx.category.lowercase().contains(q) ||
                tx.notes.lowercase().contains(q) ||
                tx.sourceOrSender.lowercase().contains(q) ||
                tx.paymentMethod.lowercase().contains(q)
            val matchesType = filter.type == null || tx.type == filter.type
            val matchesCategory = filter.category == null || tx.category == filter.category
            val matchesRange = filter.dateRange == null ||
                tx.transactionDateMillis in filter.dateRange.startMillis..filter.dateRange.endMillis
            matchesQuery && matchesType && matchesCategory && matchesRange
        }
        when (filter.sortOrder) {
            TransactionSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.transactionDateMillis }
            TransactionSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.transactionDateMillis }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val analyticsState: StateFlow<AnalyticsUiState> = combine(
        user,
        allTransactions,
        _analyticsPeriod,
        _analyticsCustomStart,
        _analyticsCustomEnd
    ) { currentUser, txs, period, customS, customE ->
        val range = FinanceCalculator.resolveAnalyticsRange(period, customS, customE)
        val filtered = FinanceCalculator.filterByDateRange(txs, range)
        val opening = currentUser?.openingBalancePaise ?: 0L
        val summary = FinanceCalculator.calculateSummary(opening, txs, filtered)

        AnalyticsUiState(
            period = period,
            customStartMillis = customS,
            customEndMillis = customE,
            summary = summary,
            expenseSlices = FinanceCalculator.categoryBreakdown(filtered, TransactionType.EXPENSE),
            incomeSlices = FinanceCalculator.categoryBreakdown(filtered, TransactionType.INCOME),
            barSeries = FinanceCalculator.buildPeriodSeries(filtered, period, range),
            expenseTrend = FinanceCalculator.buildExpenseTrendSeries(txs, period, range),
            creditHistory = FinanceCalculator.buildCreditHistorySeries(txs, period, range),
            filteredTransactions = filtered,
            insights = FinanceCalculator.generateSmartInsights(txs)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AnalyticsUiState())

    val selectedMonthBudgetStatuses: StateFlow<List<BudgetStatusItem>> = combine(
        budgets,
        allTransactions,
        _budgetMonthYear
    ) { userBudgets, txs, (month, year) ->
        val filteredBudgets = userBudgets.filter { it.month == month && it.year == year }
        computeBudgetStatuses(filteredBudgets, txs, month, year)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private fun computeBudgetStatuses(
        monthBudgets: List<BudgetEntity>,
        allTxs: List<TransactionEntity>,
        month: Int,
        year: Int
    ): List<BudgetStatusItem> {
        val cal = Calendar.getInstance()
        val monthExpenses = allTxs.filter { tx ->
            if (tx.type != TransactionType.EXPENSE) return@filter false
            cal.timeInMillis = tx.transactionDateMillis
            (cal.get(Calendar.MONTH) + 1) == month && cal.get(Calendar.YEAR) == year
        }
        val totalMonthlyExpense = monthExpenses.sumOf { it.amountPaise }
        return monthBudgets.sortedBy { if (it.category == "OVERALL") "" else it.category }.map { b ->
            val spent = if (b.category == "OVERALL") {
                totalMonthlyExpense
            } else {
                monthExpenses.filter { it.category == b.category }.sumOf { it.amountPaise }
            }
            val remaining = b.limitPaise - spent
            val pct = if (b.limitPaise > 0L) ((spent.toDouble() / b.limitPaise.toDouble()) * 100f).toFloat() else 0f
            BudgetStatusItem(b, spent, remaining, pct)
        }
    }

    fun observeTransaction(transactionId: String) =
        financeRepository.observeTransaction(transactionId, userId)

    fun setDashboardDateFilter(filter: DateFilter, customStart: Long? = null, customEnd: Long? = null) {
        _dashboardFilter.value = filter
        if (filter == DateFilter.CUSTOM) {
            _dashboardCustomStart.value = customStart
            _dashboardCustomEnd.value = customEnd
        }
    }

    fun setAnalyticsPeriod(period: AnalyticsPeriod, customStart: Long? = null, customEnd: Long? = null) {
        _analyticsPeriod.value = period
        if (period == AnalyticsPeriod.CUSTOM) {
            _analyticsCustomStart.value = customStart
            _analyticsCustomEnd.value = customEnd
        }
    }

    fun updateHistoryFilter(transform: (TransactionHistoryFilter) -> TransactionHistoryFilter) {
        _historyFilter.value = transform(_historyFilter.value)
    }

    fun setBudgetMonthYear(month: Int, year: Int) {
        _budgetMonthYear.value = Pair(month, year)
    }

    fun saveTransaction(
        editingId: String?,
        type: TransactionType,
        amountInput: String,
        title: String,
        category: String,
        sourceOrSender: String,
        paymentMethod: String,
        notes: String,
        referenceNumber: String,
        dateMillis: Long,
        onSuccess: () -> Unit
    ) {
        val paise = IndianCurrencyFormatter.parseRupeesToPaise(amountInput, allowZero = false)
        if (paise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Please enter a valid positive amount.")) }
            return
        }
        viewModelScope.launch {
            val result = if (editingId.isNullOrBlank()) {
                financeRepository.addTransaction(
                    userId = userId,
                    type = type,
                    amountPaise = paise,
                    title = title,
                    category = category,
                    sourceOrSender = sourceOrSender,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    referenceNumber = referenceNumber,
                    transactionDateMillis = dateMillis
                )
            } else {
                financeRepository.updateTransaction(
                    userId = userId,
                    transactionId = editingId,
                    type = type,
                    amountPaise = paise,
                    title = title,
                    category = category,
                    sourceOrSender = sourceOrSender,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    referenceNumber = referenceNumber,
                    transactionDateMillis = dateMillis
                )
            }
            if (result.isSuccess) {
                _events.emit(UiEvent.Snackbar(if (editingId == null) "${type.label} saved" else "Transaction updated"))
                onSuccess()
            } else {
                _events.emit(UiEvent.Snackbar(result.exceptionOrNull()?.message ?: "Could not save transaction."))
            }
        }
    }

    fun deleteTransaction(transactionId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val deleted = financeRepository.deleteTransaction(userId, transactionId)
            if (deleted) {
                _events.emit(UiEvent.Snackbar("Transaction deleted"))
                onSuccess()
            }
        }
    }

    fun saveBudget(category: String, limitInput: String, month: Int, year: Int, onSuccess: () -> Unit = {}) {
        val paise = IndianCurrencyFormatter.parseRupeesToPaise(limitInput, allowZero = false)
        if (paise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Enter a valid positive budget limit.")) }
            return
        }
        viewModelScope.launch {
            val res = financeRepository.upsertBudget(userId, category, paise, month, year)
            if (res.isSuccess) {
                _events.emit(UiEvent.Snackbar("Budget saved"))
                onSuccess()
            } else {
                _events.emit(UiEvent.Snackbar(res.exceptionOrNull()?.message ?: "Failed to save budget"))
            }
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            financeRepository.deleteBudget(userId, budgetId)
            _events.emit(UiEvent.Snackbar("Budget removed"))
        }
    }

    fun saveGoal(
        goalId: String?,
        name: String,
        targetInput: String,
        currentInput: String,
        targetDateMillis: Long?,
        onSuccess: () -> Unit = {}
    ) {
        val targetPaise = IndianCurrencyFormatter.parseRupeesToPaise(targetInput, allowZero = false)
        val currentPaise = if (currentInput.isBlank()) 0L else IndianCurrencyFormatter.parseRupeesToPaise(currentInput, allowZero = true)
        if (targetPaise == null || currentPaise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Enter valid target and saved amounts.")) }
            return
        }
        viewModelScope.launch {
            val res = financeRepository.upsertGoal(userId, goalId, name, targetPaise, currentPaise, targetDateMillis)
            if (res.isSuccess) {
                _events.emit(UiEvent.Snackbar("Savings goal saved"))
                onSuccess()
            } else {
                _events.emit(UiEvent.Snackbar(res.exceptionOrNull()?.message ?: "Could not save goal"))
            }
        }
    }

    fun contributeToGoal(goalId: String, amountInput: String, isWithdraw: Boolean) {
        val paise = IndianCurrencyFormatter.parseRupeesToPaise(amountInput, allowZero = false)
        if (paise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Enter a valid positive amount.")) }
            return
        }
        viewModelScope.launch {
            val delta = if (isWithdraw) -paise else paise
            financeRepository.contributeToGoal(userId, goalId, delta)
            _events.emit(UiEvent.Snackbar(if (isWithdraw) "Withdrawn from goal" else "Added to savings goal"))
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            financeRepository.deleteGoal(userId, goalId)
            _events.emit(UiEvent.Snackbar("Savings goal deleted"))
        }
    }

    fun saveRecurring(
        recurringId: String?,
        type: TransactionType,
        title: String,
        amountInput: String,
        category: String,
        paymentMethod: String,
        notes: String,
        frequency: RecurrenceFrequency,
        nextDueDateMillis: Long,
        onSuccess: () -> Unit = {}
    ) {
        val paise = IndianCurrencyFormatter.parseRupeesToPaise(amountInput, allowZero = false)
        if (paise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Enter a valid positive amount.")) }
            return
        }
        viewModelScope.launch {
            val res = financeRepository.upsertRecurring(
                userId = userId,
                recurringId = recurringId,
                type = type,
                title = title,
                amountPaise = paise,
                category = category,
                paymentMethod = paymentMethod,
                notes = notes,
                frequency = frequency,
                nextDueDateMillis = nextDueDateMillis,
                isActive = true
            )
            if (res.isSuccess) {
                financeRepository.processDueRecurringTransactions(userId)
                _events.emit(UiEvent.Snackbar("Recurring schedule saved"))
                onSuccess()
            } else {
                _events.emit(UiEvent.Snackbar(res.exceptionOrNull()?.message ?: "Failed to save schedule"))
            }
        }
    }

    fun toggleRecurring(recurringId: String, active: Boolean) {
        viewModelScope.launch {
            financeRepository.toggleRecurringActive(userId, recurringId, active)
        }
    }

    fun deleteRecurring(recurringId: String) {
        viewModelScope.launch {
            financeRepository.deleteRecurring(userId, recurringId)
            _events.emit(UiEvent.Snackbar("Recurring transaction deleted"))
        }
    }

    fun processDueRecurringNow() {
        viewModelScope.launch {
            val count = financeRepository.processDueRecurringTransactions(userId)
            _events.emit(
                UiEvent.Snackbar(
                    if (count > 0) "Processed $count due recurring ${if (count == 1) "entry" else "entries"}"
                    else "No recurring transactions due right now"
                )
            )
        }
    }

    fun updateProfile(
        fullName: String,
        username: String,
        email: String,
        openingBalanceInput: String,
        onSuccess: () -> Unit
    ) {
        val openingPaise = if (openingBalanceInput.isBlank()) 0L
        else IndianCurrencyFormatter.parseRupeesToPaise(openingBalanceInput, allowZero = true)
        if (openingPaise == null) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Enter a valid opening balance.")) }
            return
        }
        viewModelScope.launch {
            when (val res = authRepository.updateProfile(userId, fullName, username, email, openingPaise)) {
                is AuthResult.Success -> {
                    _events.emit(UiEvent.Snackbar("Profile updated"))
                    onSuccess()
                }
                is AuthResult.Error -> _events.emit(UiEvent.Snackbar(res.message))
            }
        }
    }

    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            when (val res = authRepository.saveProfilePhoto(userId, uri)) {
                is AuthResult.Success -> _events.emit(UiEvent.Snackbar("Profile picture updated"))
                is AuthResult.Error -> _events.emit(UiEvent.Snackbar(res.message))
            }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (newPassword != confirmPassword) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("New passwords do not match.")) }
            return
        }
        viewModelScope.launch {
            when (val res = authRepository.changePassword(userId, currentPassword, newPassword)) {
                is AuthResult.Success -> {
                    _events.emit(UiEvent.Snackbar("Password changed successfully"))
                    onSuccess()
                }
                is AuthResult.Error -> _events.emit(UiEvent.Snackbar(res.message))
            }
        }
    }

    fun updateTheme(theme: ThemePreference) {
        viewModelScope.launch {
            financeRepository.updateTheme(userId, theme)
        }
    }

    fun updateSettings(newSettings: UserSettingsEntity) {
        viewModelScope.launch {
            financeRepository.updateSettings(newSettings)
        }
    }

    fun triggerTestReminder() {
        viewModelScope.launch {
            financeRepository.triggerTestReminder(userId)
            _events.emit(UiEvent.Snackbar("Reminder notification triggered"))
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            financeRepository.markAllNotificationsRead(userId)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            financeRepository.markNotificationRead(userId, notificationId)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            financeRepository.clearNotifications(userId)
        }
    }

    fun shareExportReport(context: Context, format: ExportFormat) {
        viewModelScope.launch {
            try {
                val state = analyticsState.value
                val name = user.value?.fullName ?: "Finora User"
                val uri = withContext(Dispatchers.IO) {
                    ReportExporter.exportToCacheUri(
                        context = context,
                        format = format,
                        userName = name,
                        periodLabel = state.period.label,
                        summary = state.summary,
                        transactions = state.filteredTransactions
                    )
                }
                _events.emit(UiEvent.ShareFile(uri, format.mimeType, "Share Finora ${format.label}"))
            } catch (e: Exception) {
                _events.emit(UiEvent.Snackbar("Export failed: ${e.localizedMessage}"))
            }
        }
    }

    fun writeExportToOutputStream(format: ExportFormat, stream: OutputStream?) {
        if (stream == null) return
        viewModelScope.launch {
            try {
                val state = analyticsState.value
                val name = user.value?.fullName ?: "Finora User"
                withContext(Dispatchers.IO) {
                    stream.use { out ->
                        ReportExporter.writeReport(
                            outputStream = out,
                            format = format,
                            userName = name,
                            periodLabel = state.period.label,
                            summary = state.summary,
                            transactions = state.filteredTransactions
                        )
                    }
                }
                _events.emit(UiEvent.Snackbar("${format.label} saved successfully"))
            } catch (e: Exception) {
                _events.emit(UiEvent.Snackbar("Failed to save file: ${e.localizedMessage}"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun deleteAccount(password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val res = authRepository.deleteAccount(userId, password)) {
                is AuthResult.Success -> { /* activeUserIdFlow emits null, navigating back to Login */ }
                is AuthResult.Error -> onError(res.message)
            }
        }
    }

    class Factory(
        private val userId: String,
        private val authRepository: AuthRepository,
        private val financeRepository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FinanceViewModel(userId, authRepository, financeRepository) as T
        }
    }
}
