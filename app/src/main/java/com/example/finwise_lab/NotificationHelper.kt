package com.example.finwise_lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "FinWise_Channel"
        const val DAILY_SUMMARY_ID = 1
        const val BUDGET_ALERT_ID = 2
        const val TRANSACTION_REMINDER_ID = 3
        const val BUDGET_LIMIT_REACHED_ID = 4
        const val BUDGET_EXCEEDED_ID = 5
    }

    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val gson = Gson()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FinWise Notifications"
            val descriptionText = "Notifications for FinWise app"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getTransactions(): List<Transaction> {
        val sharedPreferences = context.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val transactionsJson = sharedPreferences.getString("transactions", "[]")
        return try {
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson<List<Transaction>>(transactionsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCurrentBudget(): Double {
        val sharedPreferences = context.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getFloat("monthlyBudget", 0f).toDouble()
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    fun showDailySummary() {
        val transactions = getTransactions()
        val today = Calendar.getInstance().time

        val todayTransactions = transactions.filter { transaction ->
            isSameDay(transaction.date, today)
        }

        val dailyIncome = todayTransactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val dailyExpense = todayTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val totalBalance = dailyIncome - dailyExpense

        val intent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Daily Summary")
                .setContentText("Income: $${String.format("%.2f", dailyIncome)}, Expense: $${String.format("%.2f", dailyExpense)}, Balance: $${String.format("%.2f", totalBalance)}")
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Daily Summary")
                .setContentText("Income: $${String.format("%.2f", dailyIncome)}, Expense: $${String.format("%.2f", dailyExpense)}, Balance: $${String.format("%.2f", totalBalance)}")
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        }

        notificationManager.notify(DAILY_SUMMARY_ID, notification)
        savePushNotification("Daily Summary", "Income: $${String.format("%.2f", dailyIncome)}, Expense: $${String.format("%.2f", dailyExpense)}, Balance: $${String.format("%.2f", totalBalance)}")
    }

    fun showBudgetAlert() {
        val transactions = getTransactions()
        val currentMonth = Calendar.getInstance().time


        val monthlyExpenses = transactions.filter { transaction ->
            isSameMonth(transaction.date, currentMonth) && transaction.type == TransactionType.EXPENSE
        }

        val totalSpent = monthlyExpenses.sumOf { it.amount }
        val budgetLimit = getCurrentBudget()
        val percentage = (totalSpent / budgetLimit) * 100

        val intent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Alert")
                .setContentText("You've spent $${String.format("%.2f", totalSpent)} (${String.format("%.1f", percentage)}%) of your $${String.format("%.2f", budgetLimit)} monthly budget")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Alert")
                .setContentText("You've spent $${String.format("%.2f", totalSpent)} (${String.format("%.1f", percentage)}%) of your $${String.format("%.2f", budgetLimit)} monthly budget")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        }

        notificationManager.notify(BUDGET_ALERT_ID, notification)
        savePushNotification("Budget Alert", "You've spent $${String.format("%.2f", totalSpent)} (${String.format("%.1f", percentage)}%) of your $${String.format("%.2f", budgetLimit)} monthly budget")
    }

    fun showBudgetLimitReached() {
        val transactions = getTransactions()
        val currentMonth = Calendar.getInstance().time

        val monthlyExpenses = transactions.filter { transaction ->
            isSameMonth(transaction.date, currentMonth) && transaction.type == TransactionType.EXPENSE
        }

        val totalSpent = monthlyExpenses.sumOf { it.amount }
        val budgetLimit = getCurrentBudget()

        val intent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Limit Reached!")
                .setContentText("You've reached your monthly budget limit of $${String.format("%.2f", budgetLimit)}")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Limit Reached!")
                .setContentText("You've reached your monthly budget limit of $${String.format("%.2f", budgetLimit)}")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        }

        notificationManager.notify(BUDGET_LIMIT_REACHED_ID, notification)
        savePushNotification("Budget Limit Reached!", "You've reached your monthly budget limit of $${String.format("%.2f", budgetLimit)}")
    }

    fun showBudgetExceeded() {
        val transactions = getTransactions()
        val currentMonth = Calendar.getInstance().time

        val monthlyExpenses = transactions.filter { transaction ->
            isSameMonth(transaction.date, currentMonth) && transaction.type == TransactionType.EXPENSE
        }

        val totalSpent = monthlyExpenses.sumOf { it.amount }
        val budgetLimit = getCurrentBudget()
        val exceededAmount = totalSpent - budgetLimit

        val intent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Exceeded!")
                .setContentText("You've exceeded your monthly budget by $${String.format("%.2f", exceededAmount)}")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Budget Exceeded!")
                .setContentText("You've exceeded your monthly budget by $${String.format("%.2f", exceededAmount)}")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        }

        notificationManager.notify(BUDGET_EXCEEDED_ID, notification)
        savePushNotification("Budget Exceeded!", "You've exceeded your monthly budget by $${String.format("%.2f", exceededAmount)}")
    }

    fun showTransactionReminder() {
        val intent = Intent(context, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Transaction Reminder")
                .setContentText("Don't forget to log your transactions for today!")
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Transaction Reminder")
                .setContentText("Don't forget to log your transactions for today!")
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .build()
        }

        notificationManager.notify(TRANSACTION_REMINDER_ID, notification)
        savePushNotification("Transaction Reminder", "Don't forget to log your transactions for today!")
    }


    private fun savePushNotification(title: String, message: String) {
        val sharedPreferences = context.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("push_notifications", "[]")
        val type = object : TypeToken<MutableList<PushNotification>>() {}.type
        val notifications: MutableList<PushNotification> = gson.fromJson(json, type) ?: mutableListOf()
        notifications.add(0, PushNotification(title, message, System.currentTimeMillis()))
        val editor = sharedPreferences.edit()
        editor.putString("push_notifications", gson.toJson(notifications))
        editor.apply()


        val intent = Intent("notification_updated")
        context.sendBroadcast(intent)
    }


    data class PushNotification(val title: String, val message: String, val timestamp: Long)
}