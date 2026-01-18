package com.example.finwise_lab

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.finwise_lab.database.BudgetDao
import com.example.finwise_lab.database.CurrencyDatabase as AppDatabase
import com.example.finwise_lab.database.TransactionDao
import com.example.finwise_lab.database.TransactionEntity
import com.example.finwise_lab.NotificationHelper
import com.example.finwise_lab.TransactionAdapter
import com.example.finwise_lab.databinding.DialogAddTransactionBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import java.io.FileInputStream

class HomeFragment : BaseFragment() {
    private data class CurrencyDetails(
        val code: String,
        val name: String,
        val symbol: String
    )

    private var currencySymbol: String = ""
    private var selectedType: TransactionType = TransactionType.INCOME
    private var selectedDate: Date = Date()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private lateinit var currencyDao: com.example.finwise_lab.database.CurrencyDao
    private lateinit var currencyDb: com.example.finwise_lab.database.CurrencyDatabase
    private lateinit var appDb: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetDao: BudgetDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currencyDb = com.example.finwise_lab.database.CurrencyDatabase.getDatabase(requireContext())
        currencyDao = currencyDb.currencyDao()
        appDb = AppDatabase.getDatabase(requireContext())
        transactionDao = appDb.transactionDao()
        budgetDao = appDb.budgetDao()
        setupToolbar(view)
        loadSummary(view)
        loadRecentTransactions(view)

        // Initialize notification badge
        updateNotificationBadge()

        // Personalized Greeting Logic
        val tvGreetingMain = view.findViewById<TextView>(R.id.tvGreetingMain)
        val tvGreetingMotivation = view.findViewById<TextView>(R.id.tvGreetingMotivation)
        if (tvGreetingMain == null || tvGreetingMotivation == null) {
            Log.e("HomeFragment", "Greeting TextViews not found in layout")
        } else {
            Log.d("HomeFragment", "Greeting TextViews found")
        }
        // Fetch logged-in user's email from SQLite session table
        val dbHelper = com.example.finwise_lab.database.DatabaseHelper(requireContext())
        val userEmail = dbHelper.getLoggedInUserEmail()
        var userName = "User"
        if (userEmail != null) {
            val user = dbHelper.getUser(userEmail)
            if (user != null) {
                userName = user.username
            }
        }

        // Get greeting based on time
        val greeting = getGreetingForTime(userName)
        val motivational = getMotivationalMessage()
        tvGreetingMain?.text = greeting
        tvGreetingMotivation?.text = motivational
        tvGreetingMain?.visibility = View.VISIBLE
        tvGreetingMotivation?.visibility = View.VISIBLE
        Log.d("HomeFragment", "Greeting set: $greeting | $motivational")

        view.findViewById<View>(R.id.fabAddTransaction).setOnClickListener {
            showAddTransactionDialog()
        }

        // --- Tips & Insights Section using Room ---
        val layoutTipsList = view.findViewById<LinearLayout>(R.id.layoutTipsList)
        layoutTipsList?.removeAllViews()
        viewLifecycleOwner.lifecycleScope.launch {
            val tips = withContext(Dispatchers.IO) { getDynamicTips() }
            val inflater = LayoutInflater.from(requireContext())
            layoutTipsList?.removeAllViews()
            for (tip in tips) {
                val tv = TextView(requireContext()).apply {
                    text = "\u2022  $tip"
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 8
                        bottomMargin = 16
                    }
                }
                layoutTipsList?.addView(tv)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    // Generate  tips
    private suspend fun getDynamicTips(): List<String> {
        // Fetch all transactions from Room
        val entities = withContext(Dispatchers.IO) { transactionDao.getAll() }
        val transactions = entities.map { e ->
            Transaction(
                id = e.id,
                title = e.title,
                amount = e.amount,
                date = Date(e.date),
                type = TransactionType.valueOf(e.type),
                category = e.category
            )
        }
        val now = Calendar.getInstance()
        val lastWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val prevWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }

        val thisWeekTx = transactions.filter { it.date.after(lastWeek.time) }
        val lastWeekTx = transactions.filter { it.date.after(prevWeek.time) && it.date.before(lastWeek.time) }
        val thisWeekSpending = thisWeekTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val lastWeekSpending = lastWeekTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        // Get current month transactions for tips
        val calMonth = Calendar.getInstance()
        val currentYear = calMonth.get(Calendar.YEAR)
        val currentMonth = calMonth.get(Calendar.MONTH)
        val monthTransactions = transactions.filter {
            val cal = Calendar.getInstance().apply { time = it.date }
            cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
        }

        val tips = mutableListOf<String>()
        // Weekly spending comparison
        if (lastWeekTx.isNotEmpty()) {
            val percent = ((lastWeekSpending - thisWeekSpending) / lastWeekSpending * 100).toInt()
            when {
                percent > 0 -> tips.add("You spent $percent% less this week than last week!")
                percent < 0 -> tips.add("You spent ${-percent}% more this week than last week.")
                else -> tips.add("You spent the same as last week.")
            }
        } else if (thisWeekSpending > 0) {
            tips.add("You spent ${"%.2f".format(thisWeekSpending)} this week.")
        } else {
            tips.add("No expenses recorded this week.")
        }

        tips.add("Tip: Set a monthly budget and track your expenses.")
        tips.add("Tip: Review your subscriptions and cancel unused ones.")
        tips.add("Tip: Try a no-spend day each week to boost savings.")
        tips.add("Tip: Categorize your expenses to find saving opportunities.")

        val biggestExpense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.maxByOrNull { it.amount }
        if (biggestExpense != null) tips.add("Biggest expense: ${biggestExpense.title} (${"%.2f".format(biggestExpense.amount)})")
        val incomeTx = monthTransactions.filter { it.type == TransactionType.INCOME }
        if (incomeTx.size > 1) tips.add("You had ${incomeTx.size} income transactions this month.")
        if (monthTransactions.size > 2) tips.add("Great job! You are actively tracking your finances this month.")
        return tips.take(8)
    }

    private fun getGreetingForTime(userName: String): String {

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 1..11 -> "Good Morning, $userName."
            in 12..16 -> "Good Afternoon, $userName."
            in 17..20 -> "Good Evening, $userName."
            else -> "Good Night, $userName."
        }
    }

    private fun getMotivationalMessage(): String {
        val messages = listOf(
            "Don’t stop, your future self will thank you!",
            "Small savings today, big dreams tomorrow!",
            "Stay on track with your goals!",
            "Your savings today create security tomorrow!",
            "Smart choices today, wealth tomorrow!"
        )
        return messages.random()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val title = view.findViewById<TextView>(R.id.tvTitle)
        val btnNotification = view.findViewById<ImageButton>(R.id.btnNotification)
        title.text = "Home"
        btnNotification.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationActivity::class.java))
            updateNotificationBadge()
        }
    }

    // Loads and displays summary (balance, income, expense, budget)
    private fun loadSummary(view: View) {
        val tvTotalBalance = view.findViewById<TextView>(R.id.tvTotalBalance)
        val tvIncomeAmount = view.findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpenseAmount = view.findViewById<TextView>(R.id.tvExpenseAmount)
        val tvBudgetUsed = view.findViewById<TextView>(R.id.tvBudgetUsed)
        val tvBudgetRemaining = view.findViewById<TextView>(R.id.tvBudgetRemaining)
        val tvBudgetLimit = view.findViewById<TextView>(R.id.tvBudgetLimit)
        val progressBudget = view.findViewById<ProgressBar>(R.id.progressBudget)
        val tvBudgetWarning = view.findViewById<TextView>(R.id.tvBudgetWarning)

        viewLifecycleOwner.lifecycleScope.launch {
            // Fetch transactions from Room
            val entities = transactionDao.getAll()
            val transactions = entities.map { e ->
                Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                )
            }
            val currentMonth = Calendar.getInstance().time
            val monthlyTx = transactions.filter { isSameMonth(it.date, currentMonth) }
            val income = monthlyTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = monthlyTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val balance = income - expense
            val budgetLimit = budgetDao.getBudget() ?: 0.0
            val used = expense
            val remaining = budgetLimit - used
            val progress = if (budgetLimit > 0) ((used / budgetLimit) * 100).toInt().coerceAtMost(100) else 0

            // Fetch currency and update UI
            val currencyDetails = currencyDao.getCurrency()
            val symbol = currencyDetails?.symbol ?: "Rs."
            tvTotalBalance.text = "$symbol${"%.2f".format(balance)}"
            tvIncomeAmount.text = "$symbol${"%.2f".format(income)}"
            tvExpenseAmount.text = "$symbol${"%.2f".format(expense)}"
            tvBudgetUsed.text = "$symbol${"%.2f".format(used)}"
            tvBudgetRemaining.text = "$symbol${"%.2f".format(remaining.coerceAtLeast(0.0))}"
            tvBudgetLimit.text = "$symbol${"%.2f".format(budgetLimit)}"
            progressBudget.progress = progress

            // Budget warning logic moved here
            val layoutBudgetWarning = view.findViewById<LinearLayout>(R.id.layoutBudgetWarning)
            val imgBudgetWarningIcon = view.findViewById<ImageView>(R.id.imgBudgetWarningIcon)
            if (tvBudgetWarning != null && layoutBudgetWarning != null && imgBudgetWarningIcon != null) {
                val percentUsed = if (budgetLimit > 0) (used / budgetLimit) else 0.0
                when {
                    percentUsed >= 1.0 -> {
                        layoutBudgetWarning.visibility = View.VISIBLE
                        layoutBudgetWarning.setBackgroundResource(R.drawable.bg_budget_warning)
                        imgBudgetWarningIcon.setImageResource(R.drawable.ic_warning)
                        tvBudgetWarning.text = "You've gone over your budget. Review your spending to get back on track!"
                        tvBudgetWarning.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    }
                    percentUsed >= 0.8 -> {
                        layoutBudgetWarning.visibility = View.VISIBLE
                        layoutBudgetWarning.setBackgroundResource(R.drawable.bg_budget_caution)
                        imgBudgetWarningIcon.setImageResource(R.drawable.ic_caution)
                        tvBudgetWarning.text = "You're close to reaching your budget limit. Consider adjusting your expenses."
                        tvBudgetWarning.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    }
                    else -> layoutBudgetWarning.visibility = View.GONE
                }
            }
        }
    }

    private fun loadRecentTransactions(view: View) {
        val rvRecentTransactions = view.findViewById<RecyclerView>(R.id.rvRecentTransactions)
        viewLifecycleOwner.lifecycleScope.launch {
            val entities = transactionDao.getAll()
            val transactions = entities.map { e ->
                Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                )
            }
            val items = transactions.sortedByDescending { it.date }.take(5).map { TransactionItem.Transaction(it) }
            val currencyDetails = currencyDao.getCurrency()
            val symbol = currencyDetails?.symbol ?: "Rs."
            withContext(Dispatchers.Main) {
                rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
                rvRecentTransactions.adapter = TransactionAdapter(symbol).apply { submitList(items) }
            }
        }
    }

    private fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    private fun setupAmountInput(editText: com.google.android.material.textfield.TextInputEditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) return

                // Remove the currency symbol if it exists
                var amount = s.toString().replace(currencySymbol, "").trim()

                // Remove any non-digit characters except decimal point
                amount = amount.replace(Regex("[^0-9.]"), "")

                // Ensure only one decimal point
                val parts = amount.split(".")
                if (parts.size > 2) {
                    amount = parts[0] + "." + parts[1]
                }

                // Format the amount with the currency symbol
                if (amount.isNotEmpty()) {
                    val formattedAmount = "$currencySymbol$amount"
                    if (formattedAmount != s.toString()) {
                        editText.removeTextChangedListener(this)
                        editText.setText(formattedAmount)
                        editText.setSelection(formattedAmount.length)
                        editText.addTextChangedListener(this)
                    }
                }
            }
        })
    }

    private fun saveTransaction(transaction: Transaction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newEntity = TransactionEntity(
                id = UUID.randomUUID().toString(),
                title = transaction.title,
                amount = transaction.amount,
                date = transaction.date.time,
                type = transaction.type.name,
                category = transaction.category
            )
            transactionDao.insert(newEntity)
            // budget notification logic on transaction add with state tracking
            val budgetLimit = budgetDao.getBudget() ?: 0.0
            val entities = transactionDao.getAll()
            val usedExpenses = entities.filter { e -> e.type == TransactionType.EXPENSE.name && isSameMonth(Date(e.date), Calendar.getInstance().time) }.sumOf { it.amount }
            val notificationHelper = NotificationHelper(requireContext())
            // track last state: 0=below,1=at limit,2=exceeded
            val budgetStatePrefs = requireContext().getSharedPreferences("BudgetState", Context.MODE_PRIVATE)
            val lastState = budgetStatePrefs.getInt("lastBudgetState", 0)
            when {
                usedExpenses == budgetLimit && lastState != 1 -> {
                    notificationHelper.showBudgetLimitReached()
                    budgetStatePrefs.edit().putInt("lastBudgetState", 1).apply()
                }
                usedExpenses > budgetLimit && lastState != 2 -> {
                    notificationHelper.showBudgetExceeded()
                    budgetStatePrefs.edit().putInt("lastBudgetState", 2).apply()
                }
                usedExpenses < budgetLimit && lastState != 0 -> {
                    budgetStatePrefs.edit().putInt("lastBudgetState", 0).apply()
                }
            }
            // Refresh UI
            loadSummary(requireView())
            loadRecentTransactions(requireView())
        }
    }

    private fun showAddTransactionDialog() {
        val dialog = Dialog(requireContext())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set dialog width and height
        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Setup categories
        val incomeCategories = arrayOf("Salary", "Bonus", "Investment", "Freelance", "Other")
        val expenseCategories = arrayOf("Food", "Transport", "Entertainment", "Bills", "Shopping", "Rent", "Medicine", "Other")

        // Create custom adapter for income categories
        val incomeAdapter = ArrayAdapter(requireContext(), R.layout.item_category_dropdown, incomeCategories).apply {
            setDropDownViewResource(R.layout.item_category_dropdown)
        }
        dialogBinding.spinnerIncomeCategory.setAdapter(incomeAdapter)

        // Create custom adapter for expense categories
        val expenseAdapter = ArrayAdapter(requireContext(), R.layout.item_category_dropdown, expenseCategories).apply {
            setDropDownViewResource(R.layout.item_category_dropdown)
        }
        dialogBinding.spinnerExpenseCategory.setAdapter(expenseAdapter)

        // Style the category dropdowns
        fun styleCategoryDropdown(inputLayout: TextInputLayout) {
            inputLayout.apply {
                // Set box stroke width
                boxStrokeWidth = 2
                boxStrokeWidthFocused = 2

                // Set corner radius
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(12f)
                    .build()

                // Set box stroke color
                setBoxStrokeColorStateList(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf()
                        ),
                        intArrayOf(
                            ContextCompat.getColor(requireContext(), R.color.blue),
                            ContextCompat.getColor(requireContext(), R.color.black)
                        )
                    )
                )

                // Set hint text color
                hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.blue)

                // Set dropdown background
                setBoxBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }

        // Apply styles to category dropdowns
        styleCategoryDropdown(dialogBinding.textInputLayoutIncomeCategory)
        styleCategoryDropdown(dialogBinding.textInputLayoutExpenseCategory)

        // Setup amount input fields with currency symbol
        setupAmountInput(dialogBinding.editTextIncomeAmount)
        setupAmountInput(dialogBinding.editTextExpenseAmount)

        // Setup input field focus listeners
        fun setupInputFieldFocus(inputLayout: TextInputLayout, isCategory: Boolean = false) {
            // Set text color to black
            inputLayout.editText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))

            // Set cursor color to black
            inputLayout.editText?.setHighlightColor(ContextCompat.getColor(requireContext(), R.color.black))

            // Set cursor drawable to null
            inputLayout.editText?.setTextCursorDrawable(null)

            // Set box stroke width
            inputLayout.boxStrokeWidth = 2
            inputLayout.boxStrokeWidthFocused = 2

            if (isCategory) {
                // For category fields, use black by default and blue when focused
                inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.black)
                inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.blue)

                // Set the default box stroke color to black
                inputLayout.setBoxStrokeColorStateList(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf()
                        ),
                        intArrayOf(
                            ContextCompat.getColor(requireContext(), R.color.blue),
                            ContextCompat.getColor(requireContext(), R.color.black)
                        )
                    )
                )

                inputLayout.editText?.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.blue)
                        inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.blue)
                    } else {
                        inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.black)
                        inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.blue)
                    }
                }
            } else {
                // For other fields, use black by default and blue when focused
                inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.black)
                inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.black)

                // Set the default box stroke color to black
                inputLayout.setBoxStrokeColorStateList(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf()
                        ),
                        intArrayOf(
                            ContextCompat.getColor(requireContext(), R.color.blue),
                            ContextCompat.getColor(requireContext(), R.color.black)
                        )
                    )
                )

                inputLayout.editText?.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.blue)
                        inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.blue)
                    } else {
                        inputLayout.boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.black)
                        inputLayout.hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.black)
                    }
                }
            }

            // Set box corner radius using shape appearance
            val shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(50f)
                .build()
            inputLayout.shapeAppearanceModel = shapeAppearanceModel
        }

        // Setup all input fields
        val incomeTitleLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutIncomeTitle)
        val incomeAmountLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutIncomeAmount)
        val incomeDateLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutIncomeDate)
        val expenseTitleLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutExpenseTitle)
        val expenseAmountLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutExpenseAmount)
        val expenseDateLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutExpenseDate)
        val incomeCategoryLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutIncomeCategory)
        val expenseCategoryLayout = dialogBinding.root.findViewById<TextInputLayout>(R.id.textInputLayoutExpenseCategory)

        setupInputFieldFocus(incomeTitleLayout)
        setupInputFieldFocus(incomeAmountLayout)
        setupInputFieldFocus(incomeDateLayout)
        setupInputFieldFocus(expenseTitleLayout)
        setupInputFieldFocus(expenseAmountLayout)
        setupInputFieldFocus(expenseDateLayout)
        setupInputFieldFocus(incomeCategoryLayout, true)
        setupInputFieldFocus(expenseCategoryLayout, true)

        // Setup tab selection
        fun updateTabSelection(isIncomeSelected: Boolean) {
            selectedType = if (isIncomeSelected) TransactionType.INCOME else TransactionType.EXPENSE

            // Update tab colors and backgrounds
            dialogBinding.tabIncome.apply {
                setTextColor(
                    ContextCompat.getColor(requireContext(),
                        if (isIncomeSelected) R.color.green else R.color.gray))
                setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                        if (isIncomeSelected) R.color.light_green else android.R.color.transparent))
            }
            dialogBinding.tabExpense.apply {
                setTextColor(
                    ContextCompat.getColor(requireContext(),
                        if (isIncomeSelected) R.color.gray else R.color.primary))
                setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                        if (isIncomeSelected) android.R.color.transparent else R.color.light_blue))
            }

            // Show/hide sections
            dialogBinding.incomeSection.visibility = if (isIncomeSelected) View.VISIBLE else View.GONE
            dialogBinding.expenseSection.visibility = if (isIncomeSelected) View.GONE else View.VISIBLE
        }

        dialogBinding.tabIncome.setOnClickListener {
            updateTabSelection(true)
        }

        dialogBinding.tabExpense.setOnClickListener {
            updateTabSelection(false)
        }

        // Set initial selection
        updateTabSelection(true)

        // Setup date pickers
        var currentCalendar = Calendar.getInstance()

        fun setupDatePicker(editText: com.google.android.material.textfield.TextInputEditText) {
            editText.setOnClickListener {
                val datePickerDialog = DatePickerDialog(
                    requireContext(),
                    { _, year, month, day ->
                        currentCalendar.set(year, month, day)
                        selectedDate = currentCalendar.time
                        editText.setText(dateFormat.format(selectedDate))
                    },
                    currentCalendar.get(Calendar.YEAR),
                    currentCalendar.get(Calendar.MONTH),
                    currentCalendar.get(Calendar.DAY_OF_MONTH)
                )

                // Set max date to today
                datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

                // Set min date to 1 year ago
                val minCalendar = Calendar.getInstance()
                minCalendar.add(Calendar.YEAR, -1)
                datePickerDialog.datePicker.minDate = minCalendar.timeInMillis

                datePickerDialog.show()
            }
        }

        setupDatePicker(dialogBinding.editTextIncomeDate)
        setupDatePicker(dialogBinding.editTextExpenseDate)

        // Setup save button
        dialogBinding.buttonSave.setOnClickListener {
            val title: String
            val amount: String
            val category: String
            val date: Date

            if (selectedType == TransactionType.INCOME) {
                title = dialogBinding.editTextIncomeTitle.text.toString().trim()
                amount = dialogBinding.editTextIncomeAmount.text.toString().replace(currencySymbol, "").trim()
                category = dialogBinding.spinnerIncomeCategory.text.toString().trim()
                date = selectedDate
            } else {
                title = dialogBinding.editTextExpenseTitle.text.toString().trim()
                amount = dialogBinding.editTextExpenseAmount.text.toString().replace(currencySymbol, "").trim()
                category = dialogBinding.spinnerExpenseCategory.text.toString().trim()
                date = selectedDate
            }

            if (title.isBlank() && category.isBlank() && amount.isBlank()) {
                Toast.makeText(requireContext(), "Please Fill all Field", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (title.length < 3) {
                Toast.makeText(requireContext(), "Title must be at least 3 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (title.length > 100) {
                Toast.makeText(requireContext(), "Title must not exceed 50 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate amount
            if (amount.isBlank()) {
                Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val amountValue = amount.toDouble()
                if (amountValue <= 0) {
                    Toast.makeText(requireContext(), "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (amountValue > 999999999) {
                    Toast.makeText(requireContext(), "Amount is too large", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Invalid amount format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate category
            if (category.isBlank()) {
                Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate date
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)

            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.time = date
            selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
            selectedCalendar.set(Calendar.MINUTE, 0)
            selectedCalendar.set(Calendar.SECOND, 0)
            selectedCalendar.set(Calendar.MILLISECOND, 0)

            if (selectedCalendar.after(today)) {
                Toast.makeText(requireContext(), "Cannot select future date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val transaction = Transaction(
                    title = title,
                    amount = amount.toDouble(),
                    category = category,
                    date = date,
                    type = selectedType
                )
                // Save transaction to SharedPreferences
                saveTransaction(transaction)
                loadSummary(requireView())
                loadRecentTransactions(requireView())
                dialog.dismiss()
                Toast.makeText(requireContext(), "Transaction added successfully", Toast.LENGTH_SHORT).show()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup cancel button
        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}