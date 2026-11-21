package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.NotificationType
import com.example.domain.RecurrenceFrequency
import com.example.domain.ThemePreference
import com.example.domain.TransactionType

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["usernameLower"], unique = true),
        Index(value = ["emailLower"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val username: String,
    val usernameLower: String,
    val email: String,
    val emailLower: String,
    val passwordHash: String,
    val passwordSalt: String,
    val openingBalancePaise: Long = 0L,
    val profileImagePath: String? = null,
    val authProvider: String = "LOCAL",
    val googleSubjectId: String? = null,
    val resetCode: String? = null,
    val resetCodeExpiresAtMillis: Long? = null,
    val createdAtMillis: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "transactionDateMillis"]),
        Index(value = ["userId", "type"]),
        Index(value = ["userId", "dedupKey"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: TransactionType,
    val amountPaise: Long,
    val title: String,
    val category: String,
    val sourceOrSender: String = "",
    val paymentMethod: String,
    val notes: String = "",
    val referenceNumber: String = "",
    val transactionDateMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val recurringId: String? = null,
    val dedupKey: String? = null
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "month", "year", "category"], unique = true)
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String, // "OVERALL" or expense category name
    val limitPaise: Long,
    val month: Int, // 1..12
    val year: Int,
    val alert80Sent: Boolean = false,
    val alert100Sent: Boolean = false
)

@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalName: String,
    val targetPaise: Long,
    val currentPaise: Long,
    val targetDateMillis: Long?,
    val createdAtMillis: Long
)

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: TransactionType,
    val title: String,
    val amountPaise: Long,
    val category: String,
    val paymentMethod: String,
    val notes: String = "",
    val frequency: RecurrenceFrequency,
    val nextDueDateMillis: Long,
    val isActive: Boolean = true,
    val createdAtMillis: Long
)

@Entity(
    tableName = "user_settings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserSettingsEntity(
    @PrimaryKey val userId: String,
    val currency: String = "INR",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val dailyReminderEnabled: Boolean = false,
    val budgetAlertsEnabled: Boolean = true,
    val recurringRemindersEnabled: Boolean = true,
    val goalRemindersEnabled: Boolean = true
)

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId", "createdAtMillis"])]
)
data class NotificationItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val createdAtMillis: Long
)
