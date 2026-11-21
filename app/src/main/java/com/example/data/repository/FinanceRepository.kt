package com.example.data.repository

import android.content.Context
import com.example.data.local.BudgetDao
import com.example.data.local.BudgetEntity
import com.example.data.local.NotificationDao
import com.example.data.local.NotificationItemEntity
import com.example.data.local.RecurringTransactionDao
import com.example.data.local.RecurringTransactionEntity
import com.example.data.local.SavingsGoalDao
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.local.UserSettingsDao
import com.example.data.local.UserSettingsEntity
import com.example.data.prefs.SessionPreferences
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.NotificationType
import com.example.domain.RecurrenceFrequency
import com.example.domain.ThemePreference
import com.example.domain.TransactionType
import com.example.notifications.FinoraNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class FinanceRepository(
    private val context: Context,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: SavingsGoalDao,
    private val recurringDao: RecurringTransactionDao,
    private val settingsDao: UserSettingsDao,
    private val notificationDao: NotificationDao,
    private val sessionPrefs: SessionPreferences
) {

    fun observeTransactions(userId: String): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactionsForUser(userId)

    fun observeTransaction(transactionId: String, userId: String): Flow<TransactionEntity?> =
        transactionDao.observeTransactionById(transactionId, userId)

    fun observeBudgets(userId: String): Flow<List<BudgetEntity>> =
        budgetDao.observeBudgetsForUser(userId)

    fun observeGoals(userId: String): Flow<List<SavingsGoalEntity>> =
        goalDao.observeGoalsForUser(userId)

    fun observeRecurring(userId: String): Flow<List<RecurringTransactionEntity>> =
        recurringDao.observeRecurringForUser(userId)

    fun observeSettings(userId: String): Flow<UserSettingsEntity> =
        settingsDao.observeSettings(userId).map { it ?: UserSettingsEntity(userId = userId) }

    fun observeNotifications(userId: String): Flow<List<NotificationItemEntity>> =
        notificationDao.observeNotifications(userId)

    suspend fun addTransaction(
        userId: String,
        type: TransactionType,
        amountPaise: Long,
        title: String,
        category: String,
        sourceOrSender: String,
        paymentMethod: String,
        notes: String,
        referenceNumber: String,
        transactionDateMillis: Long,
        recurringId: String? = null,
        dedupKey: String? = null
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        if (amountPaise <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be greater than ₹0."))
        }
        val cleanCategory = category.trim()
        if (cleanCategory.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please select a category."))
        }
        val cleanTitle = title.trim().ifEmpty {
            if (type == TransactionType.CREDIT && sourceOrSender.isNotBlank()) {
                "Credit from ${sourceOrSender.trim()}"
            } else {
                cleanCategory
            }
        }
        if (dedupKey != null && transactionDao.existsByDedupKey(userId, dedupKey)) {
            return@withContext Result.failure(IllegalStateException("Duplicate transaction prevented."))
        }

        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            amountPaise = amountPaise,
            title = cleanTitle,
            category = cleanCategory,
            sourceOrSender = sourceOrSender.trim(),
            paymentMethod = paymentMethod.trim().ifEmpty { "UPI" },
            notes = notes.trim(),
            referenceNumber = referenceNumber.trim(),
            transactionDateMillis = transactionDateMillis,
            createdAtMillis = now,
            updatedAtMillis = now,
            recurringId = recurringId,
            dedupKey = dedupKey
        )
        transactionDao.insertTransaction(entity)
        if (type == TransactionType.EXPENSE) {
            evaluateBudgetsAfterExpense(userId, transactionDateMillis)
        }
        Result.success(entity)
    }

    suspend fun updateTransaction(
        userId: String,
        transactionId: String,
        type: TransactionType,
        amountPaise: Long,
        title: String,
        category: String,
        sourceOrSender: String,
        paymentMethod: String,
        notes: String,
        referenceNumber: String,
        transactionDateMillis: Long
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        if (amountPaise <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be greater than ₹0."))
        }
        val existing = transactionDao.getTransactionById(transactionId, userId)
            ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found."))

        val cleanCategory = category.trim()
        val cleanTitle = title.trim().ifEmpty { cleanCategory }
        val updated = existing.copy(
            type = type,
            amountPaise = amountPaise,
            title = cleanTitle,
            category = cleanCategory,
            sourceOrSender = sourceOrSender.trim(),
            paymentMethod = paymentMethod.trim().ifEmpty { "UPI" },
            notes = notes.trim(),
            referenceNumber = referenceNumber.trim(),
            transactionDateMillis = transactionDateMillis,
            updatedAtMillis = System.currentTimeMillis()
        )
        transactionDao.updateTransaction(updated)
        if (type == TransactionType.EXPENSE) {
            evaluateBudgetsAfterExpense(userId, transactionDateMillis)
        }
        Result.success(updated)
    }

    suspend fun deleteTransaction(userId: String, transactionId: String): Boolean = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transactionId, userId) > 0
    }

    suspend fun upsertBudget(
        userId: String,
        category: String,
        limitPaise: Long,
        month: Int,
        year: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (limitPaise <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Budget limit must be greater than ₹0."))
        }
        val existing = budgetDao.getBudgetByCategory(userId, month, year, category)
        val budget = existing?.copy(
            limitPaise = limitPaise,
            alert80Sent = false,
            alert100Sent = false
        ) ?: BudgetEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            category = category,
            limitPaise = limitPaise,
            month = month,
            year = year
        )
        budgetDao.upsertBudget(budget)
        evaluateBudgetsForMonth(userId, month, year)
        Result.success(Unit)
    }

    suspend fun deleteBudget(userId: String, budgetId: String) = withContext(Dispatchers.IO) {
        budgetDao.deleteBudget(budgetId, userId)
    }

    private suspend fun evaluateBudgetsAfterExpense(userId: String, expenseMillis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = expenseMillis }
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        evaluateBudgetsForMonth(userId, month, year)
    }

    private suspend fun evaluateBudgetsForMonth(userId: String, month: Int, year: Int) {
        val budgets = budgetDao.getBudgetsForMonth(userId, month, year)
        if (budgets.isEmpty()) return

        val settings = settingsDao.getSettings(userId) ?: UserSettingsEntity(userId = userId)
        val allTxs = transactionDao.getTransactionsForUser(userId)
        val cal = Calendar.getInstance()
        val monthExpenses = allTxs.filter { tx ->
            if (tx.type != TransactionType.EXPENSE) return@filter false
            cal.timeInMillis = tx.transactionDateMillis
            (cal.get(Calendar.MONTH) + 1) == month && cal.get(Calendar.YEAR) == year
        }

        val totalMonthlyExpense = monthExpenses.sumOf { it.amountPaise }

        for (budget in budgets) {
            val spent = if (budget.category == "OVERALL") {
                totalMonthlyExpense
            } else {
                monthExpenses.filter { it.category == budget.category }.sumOf { it.amountPaise }
            }
            if (budget.limitPaise <= 0L) continue
            val ratio = (spent.toDouble() / budget.limitPaise.toDouble()) * 100.0
            val label = if (budget.category == "OVERALL") "Monthly Overall Budget" else "${budget.category} Budget"

            if (ratio >= 100.0 && !budget.alert100Sent) {
                budgetDao.updateBudget(budget.copy(alert80Sent = true, alert100Sent = true))
                if (settings.budgetAlertsEnabled) {
                    val title = "Budget Limit Reached (100%)"
                    val msg = "$label has reached ${ratio.toInt()}% (${IndianCurrencyFormatter.formatPaise(spent)} of ${IndianCurrencyFormatter.formatPaise(budget.limitPaise)})."
                    insertAndNotify(userId, title, msg, NotificationType.BUDGET_100)
                }
            } else if (ratio >= 80.0 && !budget.alert80Sent) {
                budgetDao.updateBudget(budget.copy(alert80Sent = true))
                if (settings.budgetAlertsEnabled) {
                    val title = "Budget Warning (80%)"
                    val msg = "$label is at ${ratio.toInt()}% (${IndianCurrencyFormatter.formatPaise(spent)} of ${IndianCurrencyFormatter.formatPaise(budget.limitPaise)})."
                    insertAndNotify(userId, title, msg, NotificationType.BUDGET_80)
                }
            }
        }
    }

    suspend fun upsertGoal(
        userId: String,
        goalId: String?,
        goalName: String,
        targetPaise: Long,
        currentPaise: Long,
        targetDateMillis: Long?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = goalName.trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a goal name."))
        }
        if (targetPaise <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Target amount must be greater than ₹0."))
        }
        if (currentPaise < 0L) {
            return@withContext Result.failure(IllegalArgumentException("Current saved amount cannot be negative."))
        }
        val existing = goalId?.let { goalDao.getGoalById(it, userId) }
        val goal = SavingsGoalEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            goalName = cleanName,
            targetPaise = targetPaise,
            currentPaise = currentPaise,
            targetDateMillis = targetDateMillis,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
        )
        goalDao.upsertGoal(goal)
        Result.success(Unit)
    }

    suspend fun contributeToGoal(
        userId: String,
        goalId: String,
        deltaPaise: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = goalDao.getGoalById(goalId, userId)
            ?: return@withContext Result.failure(IllegalArgumentException("Savings goal not found."))
        val nextAmount = (existing.currentPaise + deltaPaise).coerceAtLeast(0L)
        goalDao.upsertGoal(existing.copy(currentPaise = nextAmount))
        if (nextAmount >= existing.targetPaise && existing.currentPaise < existing.targetPaise) {
            val settings = settingsDao.getSettings(userId)
            if (settings?.goalRemindersEnabled != false) {
                insertAndNotify(
                    userId = userId,
                    title = "Savings Goal Achieved!",
                    message = "Congratulations! You reached your ${existing.goalName} goal of ${IndianCurrencyFormatter.formatPaise(existing.targetPaise)}.",
                    type = NotificationType.GOAL_PROGRESS
                )
            }
        }
        Result.success(Unit)
    }

    suspend fun deleteGoal(userId: String, goalId: String) = withContext(Dispatchers.IO) {
        goalDao.deleteGoal(goalId, userId)
    }

    suspend fun upsertRecurring(
        userId: String,
        recurringId: String?,
        type: TransactionType,
        title: String,
        amountPaise: Long,
        category: String,
        paymentMethod: String,
        notes: String,
        frequency: RecurrenceFrequency,
        nextDueDateMillis: Long,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (amountPaise <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Amount must be greater than ₹0."))
        }
        val cleanTitle = title.trim().ifEmpty { category }
        val existing = recurringId?.let { recurringDao.getRecurringById(it, userId) }
        val item = RecurringTransactionEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            title = cleanTitle,
            amountPaise = amountPaise,
            category = category,
            paymentMethod = paymentMethod,
            notes = notes.trim(),
            frequency = frequency,
            nextDueDateMillis = nextDueDateMillis,
            isActive = isActive,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
        )
        recurringDao.upsertRecurring(item)
        Result.success(Unit)
    }

    suspend fun toggleRecurringActive(userId: String, recurringId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        val existing = recurringDao.getRecurringById(recurringId, userId) ?: return@withContext
        recurringDao.upsertRecurring(existing.copy(isActive = isActive))
    }

    suspend fun deleteRecurring(userId: String, recurringId: String) = withContext(Dispatchers.IO) {
        recurringDao.deleteRecurring(recurringId, userId)
    }

    /**
     * Automatically posts any due recurring transactions up to `nowMillis` using an idempotent dedupKey
     * so duplicate transactions are never created.
     */
    suspend fun processDueRecurringTransactions(
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        val dueItems = recurringDao.getDueRecurring(userId, nowMillis)
        if (dueItems.isEmpty()) return@withContext 0

        val settings = settingsDao.getSettings(userId) ?: UserSettingsEntity(userId = userId)
        var postedCount = 0

        for (item in dueItems) {
            var cursorMillis = item.nextDueDateMillis
            var safetyCounter = 0
            while (cursorMillis <= nowMillis && safetyCounter < 12) {
                safetyCounter++
                val dedupKey = "rec_${item.id}_$cursorMillis"
                if (!transactionDao.existsByDedupKey(userId, dedupKey)) {
                    val result = addTransaction(
                        userId = userId,
                        type = item.type,
                        amountPaise = item.amountPaise,
                        title = item.title,
                        category = item.category,
                        sourceOrSender = if (item.type == TransactionType.CREDIT) item.title else "",
                        paymentMethod = item.paymentMethod,
                        notes = if (item.notes.isNotBlank()) "${item.notes} (Auto-posted)" else "Recurring ${item.frequency.label}",
                        referenceNumber = "",
                        transactionDateMillis = cursorMillis,
                        recurringId = item.id,
                        dedupKey = dedupKey
                    )
                    if (result.isSuccess) {
                        postedCount++
                    }
                }
                cursorMillis = advanceDate(cursorMillis, item.frequency)
            }
            recurringDao.upsertRecurring(item.copy(nextDueDateMillis = cursorMillis))
        }

        if (postedCount > 0 && settings.recurringRemindersEnabled) {
            insertAndNotify(
                userId = userId,
                title = "Recurring Transactions Posted",
                message = "$postedCount scheduled recurring ${if (postedCount == 1) "entry" else "entries"} processed automatically.",
                type = NotificationType.RECURRING_DUE
            )
        }
        postedCount
    }

    private fun advanceDate(millis: Long, frequency: RecurrenceFrequency): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        when (frequency) {
            RecurrenceFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurrenceFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurrenceFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    suspend fun updateSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.upsertSettings(settings)
        sessionPrefs.setFallbackTheme(settings.themePreference)
        FinoraNotifier.scheduleDailyReminder(context, settings.dailyReminderEnabled)
    }

    suspend fun updateTheme(userId: String, theme: ThemePreference) = withContext(Dispatchers.IO) {
        val current = settingsDao.getSettings(userId) ?: UserSettingsEntity(userId = userId)
        val updated = current.copy(themePreference = theme)
        settingsDao.upsertSettings(updated)
        sessionPrefs.setFallbackTheme(theme)
    }

    suspend fun markAllNotificationsRead(userId: String) = withContext(Dispatchers.IO) {
        notificationDao.markAllRead(userId)
    }

    suspend fun markNotificationRead(userId: String, notificationId: String) = withContext(Dispatchers.IO) {
        notificationDao.markRead(notificationId, userId)
    }

    suspend fun clearNotifications(userId: String) = withContext(Dispatchers.IO) {
        notificationDao.clearAll(userId)
    }

    suspend fun triggerTestReminder(userId: String) = withContext(Dispatchers.IO) {
        insertAndNotify(
            userId = userId,
            title = "Finora Reminder",
            message = "Daily expense tracking reminder is active. Keep logging your transactions!",
            type = NotificationType.DAILY_REMINDER
        )
    }

    private suspend fun insertAndNotify(
        userId: String,
        title: String,
        message: String,
        type: NotificationType
    ) {
        val item = NotificationItemEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            message = message,
            type = type,
            isRead = false,
            createdAtMillis = System.currentTimeMillis()
        )
        notificationDao.insertNotification(item)
        FinoraNotifier.showNotification(
            context = context,
            notificationId = item.id.hashCode(),
            title = title,
            message = message
        )
    }
}
