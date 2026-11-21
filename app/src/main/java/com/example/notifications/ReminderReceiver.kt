package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FinoraNotifier.showNotification(
            context = context,
            notificationId = 9001,
            title = "Daily Expense Check-in",
            message = "Take a moment to log today's expenses and keep your Finora budget on track."
        )
    }
}
