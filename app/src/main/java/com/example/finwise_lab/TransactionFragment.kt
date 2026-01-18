package com.example.finwise_lab

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.finwise_lab.databinding.FragmentTransactionBinding
import com.example.finwise_lab.databinding.DialogAddTransactionBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import androidx.recyclerview.widget.DividerItemDecoration
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.finwise_lab.database.TransactionDao
import com.example.finwise_lab.database.TransactionEntity
import com.example.finwise_lab.database.CurrencyDatabase as AppDatabase
import com.example.finwise_lab.NotificationHelper
import com.example.finwise_lab.database.BudgetDao
import kotlinx.coroutines.runBlocking

class TransactionFragment : BaseFragment() {
    private var _binding: FragmentTransactionBinding? = null
    private val binding get() = _binding!!
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var selectedType: TransactionType = TransactionType.INCOME
    private var selectedDate: Date = Date()
    private lateinit var sharedPreferences: SharedPreferences
    private var currencySymbol: String = ""
    private lateinit var budgetDao: BudgetDao

    private val dateFormatForStorage = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    private lateinit var transactionAdapter: TransactionAdapter
    private var allTransactions: List<Transaction> = emptyList()
    private var searchQuery: String = ""
    private var selectedFilterDate: Date? = null
    private var selectedTransactionType: TransactionType? = null
    private lateinit var appDb: AppDatabase
    private lateinit var transactionDao: TransactionDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)

        appDb = AppDatabase.getDatabase(requireContext())
        transactionDao = appDb.transactionDao()
        budgetDao = appDb.budgetDao()
        loadCurrencySymbol()
        setupRecyclerView()
        loadTransactions()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateNotificationBadge(it) }
    }

    private fun updateNotificationBadge(view: View) {
        val badge = view.findViewById<TextView>(R.id.tvNotificationBadge)
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

        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }

        val btnNotification = view.findViewById<View>(R.id.btnNotification)
        // Add badge if not already present
        if (view.findViewById<View>(R.id.tvNotificationBadge) == null) {
            val badge = layoutInflater.inflate(R.layout.notification_badge, null)
            val parent = btnNotification.parent as ViewGroup
            val index = parent.indexOfChild(btnNotification)
            parent.addView(badge, index + 1)
            // Position badge over btnNotification (adjust as needed)
            badge.translationX = btnNotification.width * 0.6f
            badge.translationY = btnNotification.height * -0.2f
        }
        updateNotificationBadge(view)
        btnNotification.setOnClickListener {
            val intent = android.content.Intent(requireContext(), NotificationActivity::class.java)
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener {
            if (selectedTransactionType != null) {
                // If a transaction type is selected, clear the filter
                selectedTransactionType = null
                binding.cardIncome.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                binding.cardExpense.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                filterTransactions()
            } else {
                // If no transaction type is selected, navigate to HomeFragment using bottom navigation
                (activity as? HomeActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.navigation_home
            }
        }

        // Setup income card click listener
        binding.cardIncome.setOnClickListener {
            if (selectedTransactionType == TransactionType.INCOME) {
                // If already selected, clear the filter
                selectedTransactionType = null
                binding.cardIncome.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                // Select income filter
                selectedTransactionType = TransactionType.INCOME
                binding.cardIncome.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_green))
                binding.cardExpense.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
            filterTransactions()
        }

        // Setup expense card click listener
        binding.cardExpense.setOnClickListener {
            if (selectedTransactionType == TransactionType.EXPENSE) {
                // If already selected, clear the filter
                selectedTransactionType = null
                binding.cardExpense.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                // Select expense filter
                selectedTransactionType = TransactionType.EXPENSE
                binding.cardExpense.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_blue))
                binding.cardIncome.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
            filterTransactions()
        }

        // Setup search bar styling
        binding.textInputLayoutSearch.apply {
            // Set corner radius
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(50f)
                .build()

            // Set box stroke color (black when not focused, blue when focused)
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

            // Set hint text color to black
            hintTextColor = ContextCompat.getColorStateList(requireContext(), R.color.black)

            editText?.apply {
                // Set text color to black
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))

                // Remove cursor drawable
                setTextCursorDrawable(null)

                // Handle keyboard visibility and hint
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        binding.textInputLayoutSearch.hint = ""
                    } else {
                        // Only show hint when search is empty
                        if (text.isNullOrEmpty()) {
                            binding.textInputLayoutSearch.hint = "Search transactions"
                        }
                        // Hide keyboard when focus is lost
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(windowToken, 0)
                    }
                }
            }
        }

        // Setup search functionality
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                // Only show hint when search is empty
                binding.textInputLayoutSearch.hint = if (searchQuery.isEmpty()) "Search transactions" else ""
                filterTransactions()
            }
        })

        // Setup calendar button
        binding.buttonCalendar.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(currencySymbol)
        binding.rvTransactions.apply {
            adapter = transactionAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

            // Add custom divider
            val dividerDrawable = ContextCompat.getDrawable(context, R.drawable.divider_transaction)
            val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                dividerDrawable?.let { setDrawable(it) }
            }
            addItemDecoration(divider)

            // Add padding for better visual appearance
            setPadding(0, 16, 0, 16)
            clipToPadding = false

            // Setup swipe functionality
            val swipeCallback = TransactionSwipeCallback(
                requireContext(),
                onDelete = { position ->
                    // Get the transaction at this position
                    val item = transactionAdapter.currentList[position]
                    if (item is TransactionItem.Transaction) {
                        val transaction = item.transaction
                        removeTransaction(transaction.id)
                        showUndoSnackbar(transaction)
                    } else {
                        transactionAdapter.notifyItemChanged(position)
                    }
                },
                onUpdate = { position ->
                    // Get the transaction at this position
                    val item = transactionAdapter.currentList[position]
                    if (item is TransactionItem.Transaction) {
                        val transaction = item.transaction
                        showUpdateTransactionDialog(transaction)
                    } else {
                        transactionAdapter.notifyItemChanged(position)
                    }
                }
            )

            ItemTouchHelper(swipeCallback).attachToRecyclerView(this)
        }
    }

    private lateinit var currencyDao: com.example.finwise_lab.database.CurrencyDao
    private lateinit var currencyDb: com.example.finwise_lab.database.CurrencyDatabase

    private fun loadCurrencySymbol() {

        if (!::currencyDb.isInitialized) {
            currencyDb = com.example.finwise_lab.database.CurrencyDatabase.getDatabase(requireContext())
            currencyDao = currencyDb.currencyDao()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val currencyDetails = currencyDao.getCurrency()
            currencySymbol = currencyDetails?.symbol ?: "Rs."
            transactionAdapter = TransactionAdapter(currencySymbol)
            binding.rvTransactions.adapter = transactionAdapter
            loadTransactions()
        }
    }

    private fun loadTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entities = transactionDao.getAll()
            allTransactions = entities.map { e -> 
                Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                )
            }
            updateTransactionSummary(allTransactions)
            filterTransactions()
        }
    }

    private fun saveTransaction(transaction: Transaction) {
        val entity = TransactionEntity(
            id = transaction.id.ifEmpty { UUID.randomUUID().toString() },
            title = transaction.title,
            amount = transaction.amount,
            date = transaction.date.time,
            type = transaction.type.name,
            category = transaction.category
        )
        viewLifecycleOwner.lifecycleScope.launch {
            transactionDao.insert(entity)
            val entities = transactionDao.getAll()
            val transactionsList = entities.map { e ->
                Transaction(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    date = Date(e.date),
                    type = TransactionType.valueOf(e.type),
                    category = e.category
                )
            }
            allTransactions = transactionsList
            if (transaction.type == TransactionType.EXPENSE) checkBudgetLimits(transactionsList)
            updateTransactionSummary(transactionsList)
            filterTransactions()
        }
    }

    private fun updateTransactionSummary(transactions: List<Transaction>) {
        var totalIncome = 0.0
        var totalExpense = 0.0

        transactions.forEach { transaction ->
            when (transaction.type) {
                TransactionType.INCOME -> totalIncome += transaction.amount
                TransactionType.EXPENSE -> totalExpense += transaction.amount
            }
        }

        val savings = totalIncome - totalExpense


        binding.tvIncomeAmount.text = "$currencySymbol${String.format("%.2f", totalIncome)}"
        binding.tvExpenseAmount.text = "$currencySymbol${String.format("%.2f", totalExpense)}"
        binding.tvSavingsAmount.text = "$currencySymbol${String.format("%.2f", savings)}"
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
                setTextColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.green else R.color.gray))
                setBackgroundColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.light_green else android.R.color.transparent))
            }
            dialogBinding.tabExpense.apply {
                setTextColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.gray else R.color.primary))
                setBackgroundColor(ContextCompat.getColor(requireContext(),
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

            if(title.isBlank() && amount.isBlank() && category.isBlank()){
                Toast.makeText(requireContext(), "Please Fill all field", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Title must not exceed 100 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


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


            if (category.isBlank()) {
                Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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

                saveTransaction(transaction)
                dialog.dismiss()
                Toast.makeText(requireContext(), "Transaction added successfully", Toast.LENGTH_SHORT).show()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
            }
        }


        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        selectedFilterDate?.let { calendar.time = it }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                selectedFilterDate = calendar.time
                filterTransactions()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.setButton(
            DatePickerDialog.BUTTON_NEUTRAL,
            "Clear",
            { _, _ ->
                selectedFilterDate = null
                filterTransactions()
            }
        )

        datePickerDialog.show()
    }

    private fun filterTransactions() {
        val filteredTransactions = allTransactions.filter { transaction ->
            val matchesSearch = if (searchQuery.isNotEmpty()) {
                transaction.title.contains(searchQuery, ignoreCase = true)
            } else {
                true
            }

            val matchesDate = if (selectedFilterDate != null) {
                val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
                val filterCalendar = Calendar.getInstance().apply { time = selectedFilterDate!! }

                transactionCalendar.get(Calendar.YEAR) == filterCalendar.get(Calendar.YEAR) &&
                        transactionCalendar.get(Calendar.MONTH) == filterCalendar.get(Calendar.MONTH) &&
                        transactionCalendar.get(Calendar.DAY_OF_MONTH) == filterCalendar.get(Calendar.DAY_OF_MONTH)
            } else {
                true
            }

            val matchesType = if (selectedTransactionType != null) {
                transaction.type == selectedTransactionType
            } else {
                true
            }

            matchesSearch && matchesDate && matchesType
        }


        val groupedTransactions = filteredTransactions
            .sortedByDescending { it.date }
            .groupBy { transaction ->
                val calendar = Calendar.getInstance().apply { time = transaction.date }
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.time
            }
            .flatMap { (month, monthTransactions) ->
                listOf(TransactionItem.MonthHeader(month)) +
                        monthTransactions.map { TransactionItem.Transaction(it) }
            }

        // Ensure the adapter is properly updated
        transactionAdapter.submitList(groupedTransactions.toList())
    }

    private fun showUndoSnackbar(transaction: Transaction) {
        val snackbar = Snackbar.make(
            binding.root,
            "Transaction deleted",
            Snackbar.LENGTH_LONG
        )

        snackbar.setAction("UNDO") {
            saveTransaction(transaction)
        }

        snackbar.show()
    }

    private fun removeTransaction(transactionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            transactionDao.deleteById(transactionId)
            loadTransactions()
        }
    }

    private fun showUpdateTransactionDialog(transaction: Transaction) {
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
                setTextColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.green else R.color.gray))
                setBackgroundColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.light_green else android.R.color.transparent))
            }
            dialogBinding.tabExpense.apply {
                setTextColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) R.color.gray else R.color.red))
                setBackgroundColor(ContextCompat.getColor(requireContext(),
                    if (isIncomeSelected) android.R.color.transparent else R.color.light_red))
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

        // Set initial selection based on transaction type
        updateTabSelection(transaction.type == TransactionType.INCOME)

        // Pre-fill the form with transaction data
        if (transaction.type == TransactionType.INCOME) {
            dialogBinding.editTextIncomeTitle.setText(transaction.title)
            dialogBinding.editTextIncomeAmount.setText("$currencySymbol${transaction.amount}")
            dialogBinding.editTextIncomeDate.setText(dateFormat.format(transaction.date))
            dialogBinding.spinnerIncomeCategory.setText(transaction.category, false)
        } else {
            dialogBinding.editTextExpenseTitle.setText(transaction.title)
            dialogBinding.editTextExpenseAmount.setText("$currencySymbol${transaction.amount}")
            dialogBinding.editTextExpenseDate.setText(dateFormat.format(transaction.date))
            dialogBinding.spinnerExpenseCategory.setText(transaction.category, false)
        }

        // Setup date pickers
        var currentCalendar = Calendar.getInstance()
        currentCalendar.time = transaction.date
        selectedDate = transaction.date // Set the selected date to the transaction's date

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
                Toast.makeText(requireContext(), "Title must not exceed 100 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


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


            if (category.isBlank()) {
                Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


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

                val updatedTransaction = Transaction(
                    id = transaction.id,
                    title = title,
                    amount = amount.toDouble(),
                    category = category,
                    date = date,
                    type = selectedType
                )


                updateTransaction(updatedTransaction)
                dialog.dismiss()
                Toast.makeText(requireContext(), "Transaction updated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
            }
        }


        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()

            transactionAdapter.submitList(null)
            filterTransactions()
        }

        dialog.show()
    }

    private fun updateTransaction(updatedTransaction: Transaction) {
        val entity = TransactionEntity(
            id = updatedTransaction.id,
            title = updatedTransaction.title,
            amount = updatedTransaction.amount,
            date = updatedTransaction.date.time,
            type = updatedTransaction.type.name,
            category = updatedTransaction.category
        )
        viewLifecycleOwner.lifecycleScope.launch {
            transactionDao.update(entity)
            loadTransactions()
            if (updatedTransaction.type == TransactionType.EXPENSE) checkBudgetLimits(allTransactions)
        }
    }

    private fun checkBudgetLimits(transactions: List<Transaction>) {
        val sharedPreferences = requireContext().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("budget_alerts", true)) {
            return
        }

        val currentMonth = Calendar.getInstance().time
        val monthlyExpenses = transactions.filter { transaction ->
            isSameMonth(transaction.date, currentMonth) && transaction.type == TransactionType.EXPENSE
        }

        val totalSpent = monthlyExpenses.sumOf { it.amount }
        val budgetLimit = getCurrentBudget()

        val notificationHelper = NotificationHelper(requireContext())
        val budgetStatePrefs = requireContext().getSharedPreferences("BudgetState", Context.MODE_PRIVATE)
        val lastBudgetState = budgetStatePrefs.getInt("lastBudgetState", 0) // 0: below, 1: at limit, 2: exceeded

        if (totalSpent == budgetLimit && lastBudgetState != 1) {
            // Only show when exactly at the limit
            notificationHelper.showBudgetLimitReached()
            budgetStatePrefs.edit().putInt("lastBudgetState", 1).apply()
        } else if (totalSpent > budgetLimit && lastBudgetState != 2) {
            // Only show when exceeded
            notificationHelper.showBudgetExceeded()
            budgetStatePrefs.edit().putInt("lastBudgetState", 2).apply()
        } else if (totalSpent < budgetLimit && lastBudgetState != 0) {
            // Reset state, do not show any notification
            budgetStatePrefs.edit().putInt("lastBudgetState", 0).apply()
        }
    }

    private fun getCurrentBudget(): Double {
        return runBlocking { budgetDao.getBudget() ?: 0.0 }
    }

    private fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}