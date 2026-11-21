package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE usernameLower = :usernameLower LIMIT 1")
    suspend fun getUserByUsername(usernameLower: String): UserEntity?

    @Query("SELECT * FROM users WHERE emailLower = :emailLower LIMIT 1")
    suspend fun getUserByEmail(emailLower: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDateMillis DESC, createdAtMillis DESC")
    fun observeTransactionsForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDateMillis DESC, createdAtMillis DESC")
    suspend fun getTransactionsForUser(userId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND userId = :userId LIMIT 1")
    fun observeTransactionById(transactionId: String, userId: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND userId = :userId LIMIT 1")
    suspend fun getTransactionById(transactionId: String, userId: String): TransactionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE userId = :userId AND dedupKey = :dedupKey)")
    suspend fun existsByDedupKey(userId: String, dedupKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId AND userId = :userId")
    suspend fun deleteTransaction(transactionId: String, userId: String): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY year DESC, month DESC, category ASC")
    fun observeBudgetsForUser(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month AND year = :year")
    suspend fun getBudgetsForMonth(userId: String, month: Int, year: Int): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month AND year = :year AND category = :category LIMIT 1")
    suspend fun getBudgetByCategory(userId: String, month: Int, year: Int, category: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :budgetId AND userId = :userId")
    suspend fun deleteBudget(budgetId: String, userId: String): Int
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeGoalsForUser(userId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :goalId AND userId = :userId LIMIT 1")
    suspend fun getGoalById(goalId: String, userId: String): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :goalId AND userId = :userId")
    suspend fun deleteGoal(goalId: String, userId: String): Int
}

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId ORDER BY nextDueDateMillis ASC")
    fun observeRecurringForUser(userId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1 AND nextDueDateMillis <= :upToMillis")
    suspend fun getDueRecurring(userId: String, upToMillis: Long): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :recurringId AND userId = :userId LIMIT 1")
    suspend fun getRecurringById(recurringId: String, userId: String): RecurringTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurring(item: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :recurringId AND userId = :userId")
    suspend fun deleteRecurring(recurringId: String, userId: String): Int
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    fun observeSettings(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettings(userId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettingsEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(item: NotificationItemEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId AND userId = :userId")
    suspend fun markRead(notificationId: String, userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}
