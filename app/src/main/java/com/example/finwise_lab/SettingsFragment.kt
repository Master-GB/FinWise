package com.example.finwise_lab

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.example.finwise_lab.database.CurrencyDatabase
import com.example.finwise_lab.database.CurrencyDao
import com.example.finwise_lab.CurrencyDetails
import com.example.finwise_lab.database.BudgetDao
import com.example.finwise_lab.database.BudgetEntity
import com.example.finwise_lab.Transaction
import com.example.finwise_lab.TransactionType
import com.example.finwise_lab.database.TransactionEntity
import com.example.finwise_lab.database.TransactionDao

class SettingsFragment : BaseFragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackupToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackupFromFile(it) }
    }

    private lateinit var currencyDb: CurrencyDatabase
    private lateinit var currencyDao: CurrencyDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var transactionDao: TransactionDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateNotificationBadge(it) }
    }

    private fun updateNotificationBadge(view: View) {
        val badge = view.findViewById<android.widget.TextView>(R.id.tvNotificationBadge)
        val notificationsJson = sharedPreferences.getString("push_notifications", "[]")
        val type = object : com.google.gson.reflect.TypeToken<List<Any>>() {}.type
        val notifications: List<Any> = try {
            com.google.gson.Gson().fromJson(notificationsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        if (notifications.isNotEmpty()) {
            badge?.visibility = View.VISIBLE
            badge?.text = if (notifications.size > 9) "9+" else notifications.size.toString()
        } else {
            badge?.visibility = View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateNotificationBadge()
        sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        currencyDb = CurrencyDatabase.getDatabase(requireContext())
        currencyDao = currencyDb.currencyDao()
        budgetDao = currencyDb.budgetDao()
        transactionDao = currencyDb.transactionDao()

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            // Navigate via BottomNavigationView to ensure correct selection
            (requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation))?.apply {
                selectedItemId = R.id.navigation_home
            }
        }


        val btnNotification = view.findViewById<View>(R.id.btnNotification)
        updateNotificationBadge(view)
        btnNotification.setOnClickListener {
            val intent = android.content.Intent(requireContext(), NotificationActivity::class.java)
            startActivity(intent)
        }


        setupClickHandlers(view)
    }

    private fun setupClickHandlers(view: View) {

        view.findViewById<LinearLayout>(R.id.layoutMyAccount).setOnClickListener {
            val intent = Intent(requireContext(), MyAccountActivity::class.java)
            startActivity(intent)
        }

        view.findViewById<LinearLayout>(R.id.layoutNotification).setOnClickListener {
            showNotificationPreferencesDialog()
        }

        view.findViewById<LinearLayout>(R.id.layoutBackup).setOnClickListener {
            showBackupRestoreDialog()
        }

        view.findViewById<LinearLayout>(R.id.layoutCurrency).setOnClickListener {
            showCurrencyDialog()
        }

        view.findViewById<LinearLayout>(R.id.layoutBudget).setOnClickListener {
            showBudgetDialog()
        }

        // Other Section
        view.findViewById<LinearLayout>(R.id.layoutHelp).setOnClickListener {
            Toast.makeText(context, "Help & Support coming soon!", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<LinearLayout>(R.id.layoutInvite).setOnClickListener {
            Toast.makeText(context, "Invite Friends coming soon!", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<LinearLayout>(R.id.layoutAbout).setOnClickListener {
            Toast.makeText(context, "About App coming soon!", Toast.LENGTH_SHORT).show()
        }


        val layoutSignOut = view.findViewById<View>(R.id.layoutSignOut)
        layoutSignOut.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("Sign Out")
            builder.setMessage("Are you sure you want to sign out?")
            builder.setPositiveButton("OK") { dialog, _ ->

                // Use DatabaseHelper to set logged-in state to false in SQLite
                val dbHelper = com.example.finwise_lab.database.DatabaseHelper(requireContext())
                val userEmail = dbHelper.getLoggedInUserEmail()
                if (userEmail != null) {
                    dbHelper.setLoggedIn(userEmail, false)
                }
                val intent = android.content.Intent(requireContext(), SignInActivity::class.java)
                startActivity(intent)
                requireActivity().finishAffinity()
                dialog.dismiss()
            }
            builder.setNegativeButton("CANCEL") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.setOnShowListener {
                val blueColor = try {
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.blue)
                } catch (e: Exception) {
                    androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                }
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(blueColor)
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(blueColor)
            }
            dialog.show()
        }
    }

    private fun showNotificationPreferencesDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notification_preferences, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()


        val sharedPreferences = requireContext().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
        val switchDailySummary = dialogView.findViewById<SwitchMaterial>(R.id.switchDailySummary)
        val switchBudgetAlerts = dialogView.findViewById<SwitchMaterial>(R.id.switchBudgetAlerts)
        val switchTransactionReminders = dialogView.findViewById<SwitchMaterial>(R.id.switchTransactionReminders)


        switchDailySummary.isChecked = sharedPreferences.getBoolean("daily_summary", true)
        switchBudgetAlerts.isChecked = sharedPreferences.getBoolean("budget_alerts", true)
        switchTransactionReminders.isChecked = sharedPreferences.getBoolean("transaction_reminders", true)


        dialogView.findViewById<Button>(R.id.btnTestNotifications).setOnClickListener {
            val notificationHelper = NotificationHelper(requireContext())


            if (switchDailySummary.isChecked) {
                notificationHelper.showDailySummary()
            }

            if (switchBudgetAlerts.isChecked) {
                notificationHelper.showBudgetAlert()
            }

            if (switchTransactionReminders.isChecked) {
                notificationHelper.showTransactionReminder()
            }
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {

            sharedPreferences.edit().apply {
                putBoolean("daily_summary", switchDailySummary.isChecked)
                putBoolean("budget_alerts", switchBudgetAlerts.isChecked)
                putBoolean("transaction_reminders", switchTransactionReminders.isChecked)
                apply()
            }

            if (switchDailySummary.isChecked) {
                NotificationScheduler.scheduleTwiceDailyNotification(requireContext(), "daily_summary", 9, 0, 18, 0)
            }

            if (switchTransactionReminders.isChecked) {
                NotificationScheduler.scheduleTwiceDailyNotification(requireContext(), "transaction_reminder", 10, 0, 20, 0)
            }
            if (switchBudgetAlerts.isChecked) {
                NotificationScheduler.scheduleTwiceDailyNotification(requireContext(), "budget_alerts", 16, 0, 20, 0)
            }

            Toast.makeText(context, "Notification preferences saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCurrencyDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_currency, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val currencies = getCurrencyList()
        val currencyAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            currencies.map { "${it.code} - ${it.name}" }
        )
        val actvCurrency = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCurrency)
        actvCurrency.setAdapter(currencyAdapter)


        val tvCurrent = dialogView.findViewById<TextView>(R.id.tvCurrentCurrency)
        runBlocking {
               currencyDao.getCurrency()?.let { details ->
                val displayText = "${details.code} - ${details.name}"
                actvCurrency.setText(displayText, false)
                tvCurrent.text = "Current Currency: ${details.code}"
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val sel = actvCurrency.text.toString()
                if (sel.isNotEmpty()) {
                    val code = sel.split(" - ")[0]
                    currencies.find { it.code == code }?.let { ent ->
                        val details = CurrencyDetails(
                            code = ent.code,
                            name = ent.name,
                            symbol = getCurrencySymbol(ent.code)
                        )
                        currencyDao.clearCurrency()
                        currencyDao.insertCurrency(details)
                        Toast.makeText(context, "Currency updated successfully!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun getCurrencyList(): List<Currency> {
        return listOf(
            Currency("USD", "United States Dollar"),
            Currency("EUR", "Euro"),
            Currency("LKR", "Sri Lankan Rupee"),
            Currency("GBP", "British Pound"),
            Currency("JPY", "Japanese Yen"),
            Currency("AUD", "Australian Dollar"),
            Currency("CAD", "Canadian Dollar"),
            Currency("CHF", "Swiss Franc"),
            Currency("CNY", "Chinese Yuan"),
            Currency("INR", "Indian Rupee"),
            Currency("BRL", "Brazilian Real")
        )
    }

    private fun getCurrencySymbol(code: String): String {
        return when (code) {
            "USD" -> "$"
            "EUR" -> "€"
            "LKR" -> "Rs."
            "GBP" -> "£"
            "JPY" -> "¥"
            "AUD" -> "A$"
            "CAD" -> "C$"
            "CHF" -> "Fr"
            "CNY" -> "¥"
            "INR" -> "₹"
            "BRL" -> "R$"
            else -> ""
        }
    }

    private fun showBudgetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_monthly_budget, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val tvCurrentBudget = dialogView.findViewById<TextView>(R.id.tvCurrentBudget)
        val etBudget = dialogView.findViewById<TextInputEditText>(R.id.etBudget)


        viewLifecycleOwner.lifecycleScope.launch {
            val currentBudget = budgetDao.getBudget() ?: 0.0
            val symbol = currencyDao.getCurrency()?.symbol ?: ""
            tvCurrentBudget.text = "Current Monthly Budget: $symbol${"%,.2f".format(currentBudget)}"
            etBudget.setText(currentBudget.toString())
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val budgetText = etBudget.text.toString()
            if (budgetText.isNotEmpty()) {
                val budget = budgetText.toDoubleOrNull()
                if (budget != null && budget >= 0) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        budgetDao.insertBudget(BudgetEntity(monthlyBudget = budget))
                    }
                    dialog.dismiss()
                    Toast.makeText(context, "Monthly budget updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter a valid non-negative number", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Please enter a budget amount", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }



    private fun showBackupRestoreDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_backup_restore, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnBackup).setOnClickListener {
            if (createBackup()) {
                Toast.makeText(context, "Backup created successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create backup", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<Button>(R.id.btnExport).setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("finwise_backup_$timestamp.json")
        }


        dialogView.findViewById<Button>(R.id.btnRestore).setOnClickListener {
            if (restoreFromBackup()) {
                Toast.makeText(context, "Data restored successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to restore data", Toast.LENGTH_SHORT).show()
            }
        }


        dialogView.findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }


        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createBackup(): Boolean {
        return try {

            val transactions = runBlocking { transactionDao.getAll() }
                .map { e -> Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                ) }
            val currencyPrefs = runBlocking { currencyDao.getCurrency() } ?: CurrencyDetails("LKR", "Sri Lankan Rupee", "Rs.")
            val budget = runBlocking { budgetDao.getBudget() ?: 0.0 }


            Log.d("Backup", "Creating backup with:")
            Log.d("Backup", "Transactions: ${transactions.size} transactions")
            transactions.forEach { transaction ->
                Log.d("Backup", "- ${transaction.title}: ${transaction.amount}")
            }
            Log.d("Backup", "Currency: ${currencyPrefs.code} (${currencyPrefs.name}) - Symbol: ${currencyPrefs.symbol}")
            Log.d("Backup", "Monthly Budget: $budget")

            val backupData = BackupData(
                transactions = transactions,
                currencyPreferences = currencyPrefs,
                monthlyBudget = budget
            )
            val json = gson.toJson(backupData)
            requireContext().openFileOutput("finwise_backup.json", Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun restoreFromBackup(): Boolean {
        return try {
            val json = requireContext().openFileInput("finwise_backup.json").bufferedReader().use {
                it.readText()
            }
            val backupData = gson.fromJson(json, BackupData::class.java)


            runBlocking {
                transactionDao.getAll().forEach { transactionDao.deleteById(it.id) }
                backupData.transactions.forEach { t ->
                    transactionDao.insert(
                        TransactionEntity(
                            id = t.id,
                            title = t.title,
                            amount = t.amount,
                            date = t.date.time,
                            type = t.type.name,
                            category = t.category
                        )
                    )
                }
            }


            runBlocking {
                currencyDao.clearCurrency()
                currencyDao.insertCurrency(backupData.currencyPreferences)
            }


            runBlocking { budgetDao.insertBudget(BudgetEntity(monthlyBudget = backupData.monthlyBudget)) }

            Toast.makeText(context, "Data restored successfully!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to restore data", Toast.LENGTH_SHORT).show()
            return false
        }
    }



    private fun exportBackupToFile(uri: Uri) {
        try {


            val transactions = runBlocking { transactionDao.getAll() }
                .map { e -> Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                ) }


            val backupData = BackupData(
                transactions = transactions,
                currencyPreferences = runBlocking { currencyDao.getCurrency() } ?: CurrencyDetails("LKR", "Sri Lankan Rupee", "Rs."),
                monthlyBudget = runBlocking { budgetDao.getBudget() ?: 0.0 }
            )
            val json = gson.toJson(backupData)

            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }

            Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export backup", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importBackupFromFile(uri: Uri) {
        try {

            val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            } ?: throw Exception("Failed to read backup file")


            val backupData = gson.fromJson(json, BackupData::class.java)

            runBlocking {
                transactionDao.getAll().forEach { transactionDao.deleteById(it.id) }
                backupData.transactions.forEach { t ->
                    transactionDao.insert(
                        TransactionEntity(
                            id = t.id,
                            title = t.title,
                            amount = t.amount,
                            date = t.date.time,
                            type = t.type.name,
                            category = t.category
                        )
                    )
                }
            }

            runBlocking {
                currencyDao.clearCurrency()
                currencyDao.insertCurrency(backupData.currencyPreferences)
            }

            runBlocking { budgetDao.insertBudget(BudgetEntity(monthlyBudget = backupData.monthlyBudget)) }

            Toast.makeText(context, "Backup imported successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to import backup", Toast.LENGTH_SHORT).show()
        }
    }

    data class BackupData(
        val transactions: List<Transaction>,
        val currencyPreferences: CurrencyDetails,
        val monthlyBudget: Double
    )
}