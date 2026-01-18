package com.example.finwise_lab

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class NotificationScheduler {
    companion object {
        fun scheduleTwiceDailyNotification(context: Context, notificationType: String, hour1: Int, minute1: Int, hour2: Int, minute2: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager


            val intent1 = Intent(context, TwiceDailyNotificationReceiver::class.java).apply {
                putExtra("notificationType", notificationType)
            }
            val pendingIntent1 = PendingIntent.getBroadcast(
                context,
                notificationType.hashCode(),
                intent1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar1 = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour1)
                set(Calendar.MINUTE, minute1)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar1.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent1
            )


            val intent2 = Intent(context, TwiceDailyNotificationReceiver::class.java).apply {
                putExtra("notificationType", notificationType)
            }
            val pendingIntent2 = PendingIntent.getBroadcast(
                context,
                (notificationType.hashCode() + 1),
                intent2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar2 = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour2)
                set(Calendar.MINUTE, minute2)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar2.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent2
            )
        }
    }
}

class TwiceDailyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Helper to check if two dates are in the same month and year
        fun isSameMonth(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance()
            val cal2 = Calendar.getInstance()
            cal1.time = date1
            cal2.time = date2
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
        }

        val notificationType = intent.getStringExtra("notificationType") ?: return
        val notificationHelper = NotificationHelper(context)
        when (notificationType) {
            "daily_summary" -> notificationHelper.showDailySummary()
            "transaction_reminder" -> notificationHelper.showTransactionReminder()
            "budget_alerts" -> {

                val sharedPreferences = context.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
                val transactionsJson = sharedPreferences.getString("transactions", "[]")
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Transaction>>() {}.type
                val transactions: List<Transaction> = gson.fromJson(transactionsJson, type) ?: emptyList()
                val currentMonth = java.util.Calendar.getInstance().time
                val monthlyExpenses = transactions.filter { it.type == TransactionType.EXPENSE && isSameMonth(it.date, currentMonth) }
                val totalSpent = monthlyExpenses.sumOf { it.amount }
                val budgetLimit = sharedPreferences.getFloat("monthlyBudget", 0f).toDouble()
                if (budgetLimit > 0) {
                    android.util.Log.d("BudgetNotification", "Checking budget: spent=$totalSpent, limit=$budgetLimit")
                    if (totalSpent == budgetLimit) {
                        android.util.Log.d("BudgetNotification", "Budget Limit Reached triggered")
                        notificationHelper.showBudgetLimitReached()
                    } else if (totalSpent > budgetLimit) {
                        android.util.Log.d("BudgetNotification", "Budget Exceeded triggered")
                        notificationHelper.showBudgetExceeded()
                    }
                }
            }
        }
    }
}
