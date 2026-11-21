package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinoraDatabase
import com.example.data.prefs.SessionPreferences
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.FinanceRepository
import com.example.domain.FinanceCalculator
import com.example.domain.FinancialSummary
import com.example.domain.RecurrenceFrequency
import com.example.domain.TransactionType
import com.example.export.ExportFormat
import com.example.export.ReportExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinoraRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: FinoraDatabase
    private lateinit var sessionPrefs: SessionPreferences
    private lateinit var authRepo: AuthRepository
    private lateinit var financeRepo: FinanceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionPrefs = SessionPreferences(context)
        authRepo = AuthRepository(
            context = context,
            userDao = db.userDao(),
            settingsDao = db.userSettingsDao(),
            sessionPrefs = sessionPrefs
        )
        financeRepo = FinanceRepository(
            context = context,
            transactionDao = db.transactionDao(),
            budgetDao = db.budgetDao(),
            goalDao = db.savingsGoalDao(),
            recurringDao = db.recurringTransactionDao(),
            settingsDao = db.userSettingsDao(),
            notificationDao = db.notificationDao(),
            sessionPrefs = sessionPrefs
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `register login password reset and multi user data isolation`() = runTest {
        val regA = authRepo.register("Alice Sharma", "alice_s", "alice@finora.in", "pass123", 1_000_000L)
        assertTrue(regA is AuthResult.Success)
        val userA = (regA as AuthResult.Success).data
        assertNotEquals("pass123", userA.passwordHash)

        // Add income, expense, and credit for User A
        val now = System.currentTimeMillis()
        financeRepo.addTransaction(userA.id, TransactionType.INCOME, 5_000_000L, "Salary", "Salary", "", "Bank Transfer", "", "", now)
        financeRepo.addTransaction(userA.id, TransactionType.EXPENSE, 35_000L, "Dinner with friends", "Food & Dining", "", "UPI", "Notes", "", now)
        financeRepo.addTransaction(userA.id, TransactionType.CREDIT, 200_000L, "Refund", "Refunds", "Amazon", "UPI", "", "", now)

        val aTxs = financeRepo.observeTransactions(userA.id).first()
        assertEquals(3, aTxs.size)

        // Logout User A and register User B
        authRepo.logout()
        val regB = authRepo.register("Bob Verma", "bob_v", "bob@finora.in", "secret99", 0L)
        assertTrue(regB is AuthResult.Success)
        val userB = (regB as AuthResult.Success).data

        // User B must see 0 of User A's transactions and cannot delete User A's records
        val bTxs = financeRepo.observeTransactions(userB.id).first()
        assertEquals(0, bTxs.size)
        assertFalse(financeRepo.deleteTransaction(userB.id, aTxs.first().id))

        // Login back as User A and verify data persists
        authRepo.logout()
        val loginA = authRepo.login("alice_s", "pass123")
        assertTrue(loginA is AuthResult.Success)
        assertEquals(3, financeRepo.observeTransactions(userA.id).first().size)

        // Edit and Delete transaction for User A and verify balance recalculation
        val expenseTx = aTxs.first { it.type == TransactionType.EXPENSE }
        financeRepo.updateTransaction(
            userId = userA.id,
            transactionId = expenseTx.id,
            type = TransactionType.EXPENSE,
            amountPaise = 50_000L,
            title = "Updated Dinner",
            category = "Food & Dining",
            sourceOrSender = "",
            paymentMethod = "UPI",
            notes = "",
            referenceNumber = "",
            transactionDateMillis = now
        )
        val afterUpdate = financeRepo.observeTransactions(userA.id).first()
        val summaryAfterUpdate = FinanceCalculator.calculateSummary(userA.openingBalancePaise, afterUpdate, afterUpdate)
        // 10,000 + 50,000 + 2,000 - 500 = 61,500 (6,150,000 paise)
        assertEquals(6_150_000L, summaryAfterUpdate.availableBalancePaise)

        assertTrue(financeRepo.deleteTransaction(userA.id, expenseTx.id))
        val afterDelete = financeRepo.observeTransactions(userA.id).first()
        assertEquals(2, afterDelete.size)

        // Password Reset Flow
        val codeRes = authRepo.requestPasswordReset("alice@finora.in")
        assertTrue(codeRes is AuthResult.Success)
        val code = (codeRes as AuthResult.Success).data
        val resetRes = authRepo.completePasswordReset("alice@finora.in", code, "newpass456")
        assertTrue(resetRes is AuthResult.Success)
        assertTrue(authRepo.login("alice_s", "pass123") is AuthResult.Error)
        assertTrue(authRepo.login("alice_s", "newpass456") is AuthResult.Success)
    }

    @Test
    fun `budget threshold 80 and 100 alerts and recurring transactions work without duplicates`() = runTest {
        val reg = authRepo.register("Priya Nair", "priya_n", "priya@finora.in", "pass123", 0L)
        val user = (reg as AuthResult.Success).data

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        financeRepo.upsertBudget(user.id, "OVERALL", 1_000_000L, month, year) // ₹10,000 limit
        financeRepo.addTransaction(user.id, TransactionType.EXPENSE, 850_000L, "Rent", "Rent", "", "Bank Transfer", "", "", cal.timeInMillis)

        var notifications = financeRepo.observeNotifications(user.id).first()
        assertTrue(notifications.any { it.title.contains("80%") })

        financeRepo.addTransaction(user.id, TransactionType.EXPENSE, 200_000L, "Groceries", "Groceries", "", "UPI", "", "", cal.timeInMillis)
        notifications = financeRepo.observeNotifications(user.id).first()
        assertTrue(notifications.any { it.title.contains("100%") })

        // Recurring transaction due today should post once and not duplicate on second run
        financeRepo.upsertRecurring(
            userId = user.id,
            recurringId = null,
            type = TransactionType.INCOME,
            title = "Monthly Salary",
            amountPaise = 7_500_000L,
            category = "Salary",
            paymentMethod = "Bank Transfer",
            notes = "",
            frequency = RecurrenceFrequency.MONTHLY,
            nextDueDateMillis = cal.timeInMillis - 60_000L,
            isActive = true
        )
        val firstPosted = financeRepo.processDueRecurringTransactions(user.id, cal.timeInMillis)
        val secondPosted = financeRepo.processDueRecurringTransactions(user.id, cal.timeInMillis)
        assertEquals(1, firstPosted)
        assertEquals(0, secondPosted)
    }

    @Test
    fun `report exporter generates non empty csv pdf and xlsx outputs`() = runTest {
        val summary = FinancialSummary(1_000_000L, 6_000_000L, 5_000_000L, 500_000L, 500_000L, 0L, 4_500_000L, 90.0)
        val txs = emptyList<com.example.data.local.TransactionEntity>()

        for (fmt in ExportFormat.entries) {
            val out = ByteArrayOutputStream()
            ReportExporter.writeReport(out, fmt, "Test User", "Monthly", summary, txs)
            assertTrue("Expected non-empty output for ${fmt.name}", out.size() > 50)
        }
    }

    @Test
    fun `create account with google creates isolated user and signs into existing on repeat`() = runTest {
        val created = authRepo.signInOrCreateWithGoogle(
            email = "finora.google@gmail.com",
            displayName = "Rohan Gupta",
            googleSubjectId = "sub_12345",
            profilePictureUrl = "https://example.com/avatar.jpg"
        )
        assertTrue(created is AuthResult.Success)
        val gUser = (created as AuthResult.Success).data
        assertEquals("GOOGLE", gUser.authProvider)
        assertEquals("Rohan Gupta", gUser.fullName)

        financeRepo.addTransaction(
            userId = gUser.id,
            type = TransactionType.INCOME,
            amountPaise = 2_500_000L,
            title = "Freelance",
            category = "Freelancing",
            sourceOrSender = "",
            paymentMethod = "Bank Transfer",
            notes = "",
            referenceNumber = "",
            transactionDateMillis = System.currentTimeMillis()
        )
        assertEquals(1, financeRepo.observeTransactions(gUser.id).first().size)

        authRepo.logout()
        val repeatSignIn = authRepo.signInOrCreateWithGoogle(
            email = "finora.google@gmail.com",
            displayName = "Rohan Gupta",
            googleSubjectId = "sub_12345",
            profilePictureUrl = null
        )
        assertTrue(repeatSignIn is AuthResult.Success)
        val sameUser = (repeatSignIn as AuthResult.Success).data
        assertEquals(gUser.id, sameUser.id)
        assertEquals(1, financeRepo.observeTransactions(sameUser.id).first().size)
    }
}
