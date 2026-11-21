package com.example

import android.app.Application
import com.example.data.local.FinoraDatabase
import com.example.data.prefs.SessionPreferences
import com.example.data.repository.AuthRepository
import com.example.data.repository.FinanceRepository
import com.example.notifications.FinoraNotifier

class FinoraApp : Application() {
    lateinit var database: FinoraDatabase
        private set
    lateinit var sessionPreferences: SessionPreferences
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var financeRepository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        FinoraNotifier.ensureChannel(this)
        database = FinoraDatabase.getInstance(this)
        sessionPreferences = SessionPreferences(this)
        authRepository = AuthRepository(
            context = this,
            userDao = database.userDao(),
            settingsDao = database.userSettingsDao(),
            sessionPrefs = sessionPreferences
        )
        financeRepository = FinanceRepository(
            context = this,
            transactionDao = database.transactionDao(),
            budgetDao = database.budgetDao(),
            goalDao = database.savingsGoalDao(),
            recurringDao = database.recurringTransactionDao(),
            settingsDao = database.userSettingsDao(),
            notificationDao = database.notificationDao(),
            sessionPrefs = sessionPreferences
        )
    }
}
