package com.example.finwise_lab

import android.content.Context

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.example.finwise_lab.databinding.FragmentAnalyticsBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

import com.example.finwise_lab.database.CurrencyDatabase as AppDatabase
import com.example.finwise_lab.database.TransactionDao
import com.example.finwise_lab.database.TransactionEntity
import com.example.finwise_lab.Transaction
import com.example.finwise_lab.TransactionType

class AnalyticsFragment : BaseFragment() {
    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()
    private val transactionsKey = "transactions"
    private var currencySymbol: String = ""
    private var selectedPeriod: Period = Period.MONTHLY
    private var allTransactions: List<Transaction> = emptyList()
    private lateinit var currencyDao: com.example.finwise_lab.database.CurrencyDao
    private lateinit var currencyDb: com.example.finwise_lab.database.CurrencyDatabase
    private lateinit var appDb: AppDatabase
    private lateinit var transactionDao: TransactionDao

    // Views for sticky header logic
    private lateinit var nestedScrollView: NestedScrollView
    private lateinit var originalPeriodSelectorCard: com.google.android.material.card.MaterialCardView
    private lateinit var stickyPeriodSelectorCard: com.google.android.material.card.MaterialCardView


    enum class Period {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }

    private lateinit var incomePieChartView: PieChartView
    private lateinit var expensePieChartView: PieChartView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        currencyDb = com.example.finwise_lab.database.CurrencyDatabase.getDatabase(requireContext())
        currencyDao = currencyDb.currencyDao()
        appDb = AppDatabase.getDatabase(requireContext())
        transactionDao = appDb.transactionDao()
        loadCurrencySymbol()
        loadTransactions()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateNotificationBadge(it) }
    }

    private fun updateNotificationBadge(view: View) {
        val badge = view.findViewById<android.widget.TextView>(R.id.tvNotificationBadge)
        val sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
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

        // Initialize views for sticky header
        nestedScrollView = binding.nestedScrollView
        originalPeriodSelectorCard = binding.originalPeriodSelectorCard
        stickyPeriodSelectorCard = binding.stickyPeriodSelectorCard

        binding.btnBack.setOnClickListener {
            (activity as? HomeActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.navigation_home
        }

        val btnNotification = view.findViewById<View>(R.id.btnNotification)
        updateNotificationBadge(view)
        btnNotification.setOnClickListener {
            val intent = android.content.Intent(requireContext(), NotificationActivity::class.java)
            startActivity(intent)
        }

        // Always load currency symbol before updating UI
        viewLifecycleOwner.lifecycleScope.launch {
            val currencyDetails = currencyDao.getCurrency()
            currencySymbol = currencyDetails?.symbol ?: "Rs."
            updateSummarySection()
            updateButtonStates()
            updateCharts()
        }

        // Setup period selector buttons (Original and Sticky)
        setupButtonClickListeners()

        // Setup scroll listener for sticky header
        setupScrollListener()
        setupTimeRangeButtons()
        setupPieCharts()
    }

    private fun setupButtonClickListeners() {
        val originalButtons = mapOf(
            Period.DAILY to binding.btnDaily,
            Period.WEEKLY to binding.btnWeekly,
            Period.MONTHLY to binding.btnMonthly,
            Period.YEARLY to binding.btnYearly
        )

        val stickyButtons = mapOf(
            Period.DAILY to binding.btnDailySticky,
            Period.WEEKLY to binding.btnWeeklySticky,
            Period.MONTHLY to binding.btnMonthlySticky,
            Period.YEARLY to binding.btnYearlySticky
        )

        val allButtons = originalButtons.entries + stickyButtons.entries

        allButtons.forEach { (period, button) ->
            button.setOnClickListener {
                if (selectedPeriod != period) {
                    selectedPeriod = period
                    updateButtonStates()
                    updateCharts()
                }
            }
        }
    }

    private fun setupScrollListener() {
        nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener {
            _, _, scrollY, _, _ ->

            // Get the top position of the original card relative to the ScrollView's top
            val originalCardTop = originalPeriodSelectorCard.top

            // Determine if the sticky header should be shown
            val showStickyHeader = scrollY >= originalCardTop

            // Toggle visibility
            stickyPeriodSelectorCard.visibility = if (showStickyHeader) View.VISIBLE else View.GONE
            originalPeriodSelectorCard.visibility = if (showStickyHeader) View.INVISIBLE else View.VISIBLE
        })
    }

    private fun updateButtonStates() {
        // Default states
        val defaultColor = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        val defaultTextColor = ContextCompat.getColor(requireContext(), R.color.black)

        // Selected states
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.selected_green_background)
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.white)

        // Button maps
        val originalButtons = mapOf(
            Period.DAILY to binding.btnDaily,
            Period.WEEKLY to binding.btnWeekly,
            Period.MONTHLY to binding.btnMonthly,
            Period.YEARLY to binding.btnYearly
        )
        val stickyButtons = mapOf(
            Period.DAILY to binding.btnDailySticky,
            Period.WEEKLY to binding.btnWeeklySticky,
            Period.MONTHLY to binding.btnMonthlySticky,
            Period.YEARLY to binding.btnYearlySticky
        )


        // Apply states to all buttons
        Period.values().forEach { period ->
            val isSelected = selectedPeriod == period
            val bgColor = if (isSelected) selectedColor else defaultColor
            val textColor = if (isSelected) selectedTextColor else defaultTextColor

            originalButtons[period]?.setBackgroundColor(bgColor)
            originalButtons[period]?.setTextColor(textColor)

            stickyButtons[period]?.setBackgroundColor(bgColor)
            stickyButtons[period]?.setTextColor(textColor)
        }
    }

    private fun loadCurrencySymbol() {
        viewLifecycleOwner.lifecycleScope.launch {
            val currencyDetails = currencyDao.getCurrency()
            currencySymbol = currencyDetails?.symbol ?: "Rs."
            // If you need to update the UI after fetching symbol, do it here
        }
    }

    private fun updateSummarySection() {
        val totalIncome = allTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = allTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }


        binding.tvTotalIncome.text = "$currencySymbol${String.format("%,.2f", totalIncome)}"
        binding.tvTotalExpense.text = "$currencySymbol${String.format("%,.2f", totalExpense)}"


        val expensePercentage = if (totalIncome > 0) {
            ((totalExpense.toFloat() / totalIncome.toFloat()) * 100).toInt()
        } else {
            0
        }

        binding.expenseProgress.progress = expensePercentage


        val message = when {
            expensePercentage <= 30 -> "Looks Good"
            expensePercentage <= 50 -> "Be Careful"
            expensePercentage <= 80 -> "High"
            else -> "Too High"
        }
        binding.tvExpensePercentage.text = "$expensePercentage% Of Your Expenses, $message"
    }

    private fun loadTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entities = transactionDao.getAll().sortedBy { it.date }
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
            updateSummarySection()
            updateCharts()
        }
    }

    private fun updateCharts() {
        setupBarChart()
        setupPieCharts()
        setupLineChart()
    }

    private fun setupBarChart() {
        binding.barChartContainer.removeAllViews()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.firstDayOfWeek = Calendar.MONDAY

        val incomeData = ArrayList<Float>()
        val expenseData = ArrayList<Float>()
        val labels = ArrayList<String>()

        when (selectedPeriod) {
            Period.DAILY -> {
                labels.addAll(arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))

                calendar.time = Date() // Start from today
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
                val startOfWeek = calendar.time

                for (i in 0..6) {
                    val dayStart = calendar.time
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = calendar.time // End is start of next day

                    val dayTransactions = allTransactions.filter { it.date.after(dayStart) && it.date.before(dayEnd) }
                    incomeData.add(dayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                    expenseData.add(dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                    // We set calendar back one day in the loop, so next iteration starts correctly
                    // but for the last iteration, we need to reset it for next chart calculations
                    if (i < 6) {
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                         calendar.time = Date() // Reset calendar for safety
                    }
                     calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to next day for next iteration
                }
            }
            Period.WEEKLY -> {
                calendar.time = Date() // Start from today
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
                val startOfMonth = calendar.time
                val maxWeeks = calendar.getActualMaximum(Calendar.WEEK_OF_MONTH)

                val weekLabels = mutableListOf<String>()
                val weeklyIncome = mutableListOf<Float>()
                val weeklyExpense = mutableListOf<Float>()

                for (weekNum in 1..maxWeeks) {
                    weekLabels.add("${weekNum}${getOrdinalSuffix(weekNum)} Week")
                    // Find start of the week (Monday)
                    calendar.time = startOfMonth
                    calendar.set(Calendar.WEEK_OF_MONTH, weekNum)
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                     // Ensure it's still within the current month
                    if (calendar.get(Calendar.MONTH) != startOfMonth.month) {
                         calendar.time = startOfMonth // Reset to start of month if week calculation pushed it out
                         calendar.set(Calendar.WEEK_OF_MONTH, weekNum) // Try setting week again
                         calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) // Set to Monday
                         // If still not in the right month, maybe it's the first week starting previous month
                         if (calendar.get(Calendar.MONTH) != startOfMonth.month) {
                              calendar.time = startOfMonth // Use start of month as start of week
                         }
                    }
                    val weekStart = calendar.time

                    // Find end of the week (Sunday night)
                    calendar.add(Calendar.DAY_OF_YEAR, 6)
                    calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
                    val weekEnd = calendar.time

                    val weekTransactions = allTransactions.filter { it.date in weekStart..weekEnd && it.date.month == startOfMonth.month } // Filter for week AND month
                    weeklyIncome.add(weekTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                    weeklyExpense.add(weekTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                }
                labels.addAll(weekLabels)
                incomeData.addAll(weeklyIncome)
                expenseData.addAll(weeklyExpense)
            }
            Period.MONTHLY -> {
                labels.addAll(arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))
                val transactionsThisYear = allTransactions.filter {
                    calendar.time = it.date
                    calendar.get(Calendar.YEAR) == currentYear
                }
                val monthlyIncome = FloatArray(12)
                val monthlyExpense = FloatArray(12)

                for (transaction in transactionsThisYear) {
                    calendar.time = transaction.date
                    val month = calendar.get(Calendar.MONTH) // 0-11
                    if (transaction.type == TransactionType.INCOME) {
                        monthlyIncome[month] += transaction.amount.toFloat()
                    } else {
                        monthlyExpense[month] += transaction.amount.toFloat()
                    }
                }
                incomeData.addAll(monthlyIncome.toList())
                expenseData.addAll(monthlyExpense.toList())
            }
            Period.YEARLY -> {

                val startYear = allTransactions.minByOrNull { it.date }?.let { calendar.apply { time = it.date }.get(Calendar.YEAR) } ?: currentYear
                val endYear = currentYear
                if (startYear <= endYear) {
                     for (year in startYear..endYear) {
                        labels.add(year.toString())
                        val transactionsInYear = allTransactions.filter {
                            calendar.time = it.date
                            calendar.get(Calendar.YEAR) == year
                        }
                        incomeData.add(transactionsInYear.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                        expenseData.add(transactionsInYear.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                    }
                } else {
                     // Handle case where there are no transactions or only future ones
                     labels.add(currentYear.toString())
                     incomeData.add(0f)
                     expenseData.add(0f)
                }
            }
        }

        if (incomeData.isNotEmpty() || expenseData.isNotEmpty()) {
            val barChartView = BarChartView(requireContext(), incomeData, expenseData, labels, currencySymbol, selectedPeriod)
            binding.barChartContainer.addView(barChartView)

            // Calculate and display totals for the period shown in the bar chart
            val periodTotalIncome = incomeData.sum()
            val periodTotalExpense = expenseData.sum()
            binding.tvBarChartIncomeTotal.text = "$currencySymbol${String.format("%,.2f", periodTotalIncome)}"
            binding.tvBarChartExpenseTotal.text = "$currencySymbol${String.format("%,.2f", periodTotalExpense)}" // Show as positive value here

        } else {
            showNoDataMessage(binding.barChartContainer)
            // Also clear the period totals if no data
            binding.tvBarChartIncomeTotal.text = "$currencySymbol${String.format("%,.2f", 0f)}"
            binding.tvBarChartExpenseTotal.text = "$currencySymbol${String.format("%,.2f", 0f)}"
        }
    }

    private fun setupPieCharts() {
        incomePieChartView = PieChartView(requireContext())
        expensePieChartView = PieChartView(requireContext())

        view?.findViewById<FrameLayout>(R.id.incomePieChartContainer)?.addView(incomePieChartView)
        view?.findViewById<FrameLayout>(R.id.expensePieChartContainer)?.addView(expensePieChartView)

        updatePieCharts()
    }

    private fun updatePieCharts() {
        val transactions = when (selectedPeriod) {
            Period.DAILY -> {
                val calendar = Calendar.getInstance()
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                val startOfWeek = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                val endOfWeekExclusive = calendar.time
                allTransactions.filter { it.date.after(startOfWeek) && it.date.before(endOfWeekExclusive) }
            }
            Period.WEEKLY -> {
                val calendar = Calendar.getInstance()
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                val startOfMonth = calendar.time
                calendar.add(Calendar.MONTH, 1)
                val endOfMonthExclusive = calendar.time
                allTransactions.filter { it.date.after(startOfMonth) && it.date.before(endOfMonthExclusive) }
            }
            Period.MONTHLY -> {
                val calendar = Calendar.getInstance()
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                val startOfYear = calendar.time
                calendar.add(Calendar.YEAR, 1)
                val endOfYearExclusive = calendar.time
                allTransactions.filter { it.date.after(startOfYear) && it.date.before(endOfYearExclusive) }
            }
            Period.YEARLY -> allTransactions
        }
        
        // Process income categories
        val incomeData = transactions
            .filter { it.type == TransactionType.INCOME }
            .groupBy { it.category }
            .map { (category, transactions) ->
                val total = transactions.sumOf { it.amount.toDouble() }.toFloat()
                PieChartData(
                    category = category,
                    value = total,
                    color = getCategoryColor(category, true)
                )
            }
            .filter { it.value > 0 }

        // Process expense categories
        val expenseData = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { (category, transactions) ->
                val total = transactions.sumOf { it.amount.toDouble() }.toFloat()
                PieChartData(
                    category = category,
                    value = total,
                    color = getCategoryColor(category, false)
                )
            }
            .filter { it.value > 0 }

        incomePieChartView.setData(incomeData, true)
        expensePieChartView.setData(expenseData, false)
    }

    private fun getCategoryColor(category: String, isIncome: Boolean): Int {
        return if (isIncome) {

            when (category.lowercase()) {
                "salary" -> Color.parseColor("#1B5E20")      // Darkest Green
                "bonus" -> Color.parseColor("#2E7D32")       // Dark Green
                "investment" -> Color.parseColor("#388E3C")  // Green
                "freelance" -> Color.parseColor("#43A047")   // Light Green
                "other" -> Color.parseColor("#4CAF50")       // Lighter Green
                else -> Color.parseColor("#388E3C")          // Default Green
            }
        } else {

            when (category.lowercase()) {
                "food" -> Color.parseColor("#B71C1C")        // Deep Red
                "transport" -> Color.parseColor("#D32F2F")   // Bright Red
                "entertainment" -> Color.parseColor("#E53935") // Vibrant Red
                "bills" -> Color.parseColor("#C62828")       // Dark Red
                "shopping" -> Color.parseColor("#D50000")    // Pure Red
                "rent" -> Color.parseColor("#FF1744")        // Intense Red
                "medicine" -> Color.parseColor("#FF5252")    // Light Red
                "other" -> Color.parseColor("#FF8A80")       // Pale Red
                else -> Color.parseColor("#C62828")          // Default Red
            }
        }
    }

    private fun setupLineChart() {
        binding.lineChartContainer.removeAllViews()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.firstDayOfWeek = Calendar.MONDAY // Ensure consistency

        val incomeData = ArrayList<Float>()
        val expenseData = ArrayList<Float>()
        val labels = ArrayList<String>()

         when (selectedPeriod) {
            Period.DAILY -> {
                labels.addAll(arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)

                for (i in 0..6) {
                    val dayStart = calendar.time
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = calendar.time
                    val dayTransactions = allTransactions.filter { it.date.after(dayStart) && it.date.before(dayEnd) }
                    incomeData.add(dayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                    expenseData.add(dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                    // Reset calendar for next iteration (already advanced)
                    calendar.add(Calendar.DAY_OF_YEAR, -1) // Go back to start of the day we just processed
                    calendar.add(Calendar.DAY_OF_YEAR, 1) // Advance to next day
                }
            }
            Period.WEEKLY -> {
                calendar.time = Date()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
                val startOfMonth = calendar.time
                val maxWeeks = calendar.getActualMaximum(Calendar.WEEK_OF_MONTH)

                val weekLabels = mutableListOf<String>()
                val weeklyIncome = mutableListOf<Float>()
                val weeklyExpense = mutableListOf<Float>()

                 for (weekNum in 1..maxWeeks) {
                    weekLabels.add("${weekNum}${getOrdinalSuffix(weekNum)} Week")
                    calendar.time = startOfMonth
                    calendar.set(Calendar.WEEK_OF_MONTH, weekNum)
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                     if (calendar.get(Calendar.MONTH) != startOfMonth.month) {
                         calendar.time = startOfMonth
                         calendar.set(Calendar.WEEK_OF_MONTH, weekNum)
                         calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                         if (calendar.get(Calendar.MONTH) != startOfMonth.month) {
                              calendar.time = startOfMonth // Corrected typo here
                         }
                    }
                    val weekStart = calendar.time
                    calendar.add(Calendar.DAY_OF_YEAR, 7) // Go to start of next week
                    val weekEndExclusive = calendar.time

                    val weekTransactions = allTransactions.filter { it.date.after(weekStart) && it.date.before(weekEndExclusive) && it.date.month == startOfMonth.month }
                    weeklyIncome.add(weekTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                    weeklyExpense.add(weekTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                }
                labels.addAll(weekLabels)
                incomeData.addAll(weeklyIncome)
                expenseData.addAll(weeklyExpense)
            }
            Period.MONTHLY -> {
                // Keep existing Monthly logic
                 labels.addAll(arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))
                val transactionsThisYear = allTransactions.filter {
                    calendar.time = it.date
                    calendar.get(Calendar.YEAR) == currentYear
                }
                val monthlyIncome = FloatArray(12)
                val monthlyExpense = FloatArray(12)
                for (transaction in transactionsThisYear) {
                    calendar.time = transaction.date
                    val month = calendar.get(Calendar.MONTH) // 0-11
                    if (transaction.type == TransactionType.INCOME) {
                        monthlyIncome[month] += transaction.amount.toFloat()
                    } else {
                        monthlyExpense[month] += transaction.amount.toFloat()
                    }
                }
                incomeData.addAll(monthlyIncome.toList())
                expenseData.addAll(monthlyExpense.toList())
            }
            Period.YEARLY -> {
                // Keep existing Yearly logic
                val startYear = allTransactions.minByOrNull { it.date }?.let { calendar.apply { time = it.date }.get(Calendar.YEAR) } ?: currentYear
                val endYear = currentYear
                 if (startYear <= endYear) {
                    for (year in startYear..endYear) {
                        labels.add(year.toString())
                        val transactionsInYear = allTransactions.filter {
                            calendar.time = it.date
                            calendar.get(Calendar.YEAR) == year
                        }
                        incomeData.add(transactionsInYear.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat())
                        expenseData.add(transactionsInYear.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat())
                    }
                } else {
                     labels.add(currentYear.toString())
                     incomeData.add(0f)
                     expenseData.add(0f)
                }
            }
        }

        if (incomeData.isNotEmpty() || expenseData.isNotEmpty()) {
            val lineChartView = LineChartView(requireContext(), incomeData, expenseData, labels, currencySymbol, selectedPeriod)
            binding.lineChartContainer.addView(lineChartView)
        } else {
            showNoDataMessage(binding.lineChartContainer)
        }
    }

     private fun showNoDataMessage(container: ViewGroup) {
        val textView = android.widget.TextView(requireContext()).apply {
            text = "No data available for this period"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = android.view.Gravity.CENTER
            textSize = 16f
            setTextColor(Color.GRAY)
        }
        container.addView(textView)
    }

    // Helper function for weekly labels
    private fun getOrdinalSuffix(n: Int): String {
        return when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    private fun setupTimeRangeButtons() {
        // Implementation of setupTimeRangeButtons method
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Custom Bar Chart View
class BarChartView(
    context: Context,
    private val incomeData: List<Float>,
    private val expenseData: List<Float>,
    private val labels: List<String>,
    private val currencySymbol: String,
    private val period: AnalyticsFragment.Period
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // Enable Anti-aliasing
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG) // Enable Anti-aliasing
    private val incomeColor = ContextCompat.getColor(context, R.color.secondary) // Use secondary color
    private val expenseColor = ContextCompat.getColor(context, R.color.primary)
    private val labelColor = Color.BLACK
    private val gridColor = Color.LTGRAY
    private val barWidth = 20f // Significantly decreased bar width
    private val barCornerRadius = 15f // User-set radius
    private val spacing = 10f // Reduced space between income/expense bars
    private val startPadding = 90f // Increased left padding for wide Y-axis labels
    private val endPadding = 20f   // Keep reduced padding
    private val topPadding = 60f
    private val bottomPadding = 70f
    private val labelTextSize = 25f

    init {
        textPaint.color = labelColor
        textPaint.textSize = labelTextSize
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val chartDrawWidth = width - startPadding - endPadding
        val chartDrawHeight = height - topPadding - bottomPadding

        if (labels.isEmpty() || (incomeData.isEmpty() && expenseData.isEmpty())) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No data", width / 2f, height / 2f, textPaint)
            return
        }

        // Find max value for scaling
        val maxIncome = incomeData.maxOrNull() ?: 0f
        val maxExpense = expenseData.maxOrNull() ?: 0f
        var maxValue = maxOf(maxIncome, maxExpense)
        if (maxValue == 0f) maxValue = 1f // Prevent division by zero

        // --- Draw Grid and Y-Axis Labels ---
        paint.color = gridColor
        paint.strokeWidth = 1f
        val numGridLines = 5
        textPaint.textAlign = Paint.Align.RIGHT // Align Y labels to the right of the axis line
        for (i in 0..numGridLines) {
            val y = topPadding + (chartDrawHeight * i / numGridLines)
            canvas.drawLine(startPadding, y, width - endPadding, y, paint)

            // Format Y-axis labels
            val value = maxValue * (numGridLines - i) / numGridLines
            val labelText = if (value >= 1000) {
                String.format("%.0fk", value / 1000f)
            } else {
                String.format("%.0f", value)
            }
            canvas.drawText(labelText, startPadding - 15, y + (textPaint.textSize / 3), textPaint) // Adjust position
        }

        // --- Draw X-Axis Labels and Bars ---
        textPaint.textAlign = Paint.Align.CENTER

        // Determine spacing based on period
        val singleGroupWidth = barWidth * 2 + spacing // Width of one income+expense pair
        val desiredInterGroupSpacing = when (period) {
            AnalyticsFragment.Period.WEEKLY -> 90f  // Further increased Weekly gap
            AnalyticsFragment.Period.DAILY -> 60f   // Keep Daily medium
            AnalyticsFragment.Period.MONTHLY -> 80f // Keep Monthly target high
            else -> 50f                           // Keep Yearly tight
        }
        
        // Calculate total required width
        val totalBarPairWidth = singleGroupWidth * labels.size
        val totalSpacingWidth = if (labels.size > 1) desiredInterGroupSpacing * (labels.size - 1) else 0f
        val requiredTotalWidth = totalBarPairWidth + totalSpacingWidth

        // Adjust spacing if required width exceeds drawable width
        var actualInterGroupSpacing = desiredInterGroupSpacing
        if (requiredTotalWidth > chartDrawWidth && labels.size > 1) {
            val availableSpacing = chartDrawWidth - totalBarPairWidth
            actualInterGroupSpacing = max(0f, availableSpacing / (labels.size - 1))
        }

        // Calculate starting position for the first bar group center
        // Center the whole content within the chartDrawWidth
        val actualTotalContentWidth = totalBarPairWidth + (if (labels.size > 1) actualInterGroupSpacing * (labels.size - 1) else 0f)
        val startOffset = startPadding + (chartDrawWidth - actualTotalContentWidth) / 2f
        val firstGroupCenterX = startOffset + singleGroupWidth / 2f

        for (i in labels.indices) {
            // Calculate the center X position for the current group
            val groupCenterX = firstGroupCenterX + i * (singleGroupWidth + actualInterGroupSpacing)

            // Draw label
            canvas.drawText(labels[i], groupCenterX, height - bottomPadding + 40, textPaint)

            // --- Calculate Bar Heights and Positions ---
            val incomeBarHeight = (incomeData.getOrElse(i) { 0f } / maxValue) * chartDrawHeight
            val expenseBarHeight = (expenseData.getOrElse(i) { 0f } / maxValue) * chartDrawHeight

            // Adjust positions relative to groupCenterX
            val incomeLeft = groupCenterX - singleGroupWidth / 2f
            val incomeTop = height - bottomPadding - incomeBarHeight
            val incomeRight = incomeLeft + barWidth
            val incomeBottom = height - bottomPadding

            val expenseLeft = incomeRight + spacing
            val expenseTop = height - bottomPadding - expenseBarHeight
            val expenseRight = expenseLeft + barWidth
            val expenseBottom = height - bottomPadding

            // --- Draw Income Bar (Top Rounded) ---
            paint.color = incomeColor
            paint.style = Paint.Style.FILL
            if (incomeBarHeight > 0) {
                val incomePath = Path()
                val safeRadius = min(barCornerRadius, min(barWidth / 2f, incomeBarHeight / 2f))
                incomePath.moveTo(incomeLeft, incomeBottom)
                incomePath.lineTo(incomeLeft, incomeTop + safeRadius)
                incomePath.arcTo(RectF(incomeLeft, incomeTop, incomeLeft + 2 * safeRadius, incomeTop + 2 * safeRadius), 180f, 90f, false)
                incomePath.lineTo(incomeRight - safeRadius, incomeTop)
                incomePath.arcTo(RectF(incomeRight - 2 * safeRadius, incomeTop, incomeRight, incomeTop + 2 * safeRadius), 270f, 90f, false)
                incomePath.lineTo(incomeRight, incomeBottom)
                incomePath.close()
                canvas.drawPath(incomePath, paint)
            }

            // --- Draw Expense Bar (Top Rounded) ---
            paint.color = expenseColor
            paint.style = Paint.Style.FILL
             if (expenseBarHeight > 0) {
                 val expensePath = Path()
                 val safeRadius = min(barCornerRadius, min(barWidth / 2f, expenseBarHeight / 2f))
                 expensePath.moveTo(expenseLeft, expenseBottom)
                 expensePath.lineTo(expenseLeft, expenseTop + safeRadius)
                 expensePath.arcTo(RectF(expenseLeft, expenseTop, expenseLeft + 2 * safeRadius, expenseTop + 2 * safeRadius), 180f, 90f, false)
                 expensePath.lineTo(expenseRight - safeRadius, expenseTop)
                 expensePath.arcTo(RectF(expenseRight - 2 * safeRadius, expenseTop, expenseRight, expenseTop + 2 * safeRadius), 270f, 90f, false)
                 expensePath.lineTo(expenseRight, expenseBottom)
                 expensePath.close()
                 canvas.drawPath(expensePath, paint)
            }
        }

        // --- Draw Legend (Use Rectangles) ---
        val legendY = topPadding - 25 // Position legend above the chart
        val legendItemWidth = 150f // Increased width/spacing for legend items
        val legendRectSize = 25f
        val legendTextSize = 20f
        textPaint.textSize = legendTextSize
        textPaint.textAlign = Paint.Align.LEFT

        // Calculate starting X to center the legend
        val legendTotalWidth = (legendItemWidth * 2)
        var legendX = (width - legendTotalWidth) / 2

        // Income legend
        paint.color = incomeColor
        paint.style = Paint.Style.FILL // Set style for rectangle
        canvas.drawRect(legendX, legendY - legendRectSize / 2, legendX + legendRectSize, legendY + legendRectSize / 2, paint)
        canvas.drawText("Income", legendX + legendRectSize + 10, legendY + (legendTextSize / 3), textPaint)

        // Expense legend
        legendX += legendItemWidth
        paint.color = expenseColor
        paint.style = Paint.Style.FILL // Set style for rectangle
        canvas.drawRect(legendX, legendY - legendRectSize / 2, legendX + legendRectSize, legendY + legendRectSize / 2, paint)
        canvas.drawText("Expense", legendX + legendRectSize + 10, legendY + (legendTextSize / 3), textPaint)
    }
}

// Custom Pie Chart View
class PieChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var data: List<PieChartData> = emptyList()
    private var total: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f
    private var isIncome: Boolean = false

    init {
        textPaint.textSize = 30f
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
    }

    fun setData(newData: List<PieChartData>, isIncomeChart: Boolean = false) {
        data = newData
        total = data.sumOf { it.value.toDouble() }.toFloat()
        isIncome = isIncomeChart
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) / 2.2f // Adjusted radius for better display
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Clear the entire canvas with white background
        canvas.drawColor(Color.WHITE)
        
        if (data.isEmpty() || total == 0f) {
            textPaint.textSize = 40f
            canvas.drawText("No data available", centerX, centerY, textPaint)
            return
        }

        var startAngle = 0f
        data.forEach { pieData ->
            val sweepAngle = (pieData.value / total) * 360f
            paint.color = pieData.color
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)

            // Draw label and percentage
            val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val labelRadius = radius * 0.85f // Adjusted label radius
            val labelX = centerX + (labelRadius * Math.cos(midAngle)).toFloat()
            val labelY = centerY + (labelRadius * Math.sin(midAngle)).toFloat()

            // Draw category name
            textPaint.textSize = 30f // Adjusted text size
            canvas.drawText(pieData.category, labelX, labelY - 20, textPaint)

            // Draw percentage
            val percentage = (pieData.value / total * 100).toInt()
            textPaint.textSize = 26f // Adjusted text size
            canvas.drawText("$percentage%", labelX, labelY + 20, textPaint)

            startAngle += sweepAngle
        }
    }
}

data class PieChartData(val category: String, val value: Float, val color: Int)

// Custom Line Chart View
class LineChartView(
    context: Context,
    private val incomeData: List<Float>,
    private val expenseData: List<Float>,
    private val labels: List<String>,
    private val currencySymbol: String,
    private val period: AnalyticsFragment.Period
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val incomeColor = ContextCompat.getColor(context, R.color.secondary)
    private val expenseColor = ContextCompat.getColor(context, R.color.primary)
    private val labelColor = Color.BLACK
    private val gridColor = Color.LTGRAY
    private val startPadding = 60f
    private val endPadding = 20f
    private val topPadding = 60f
    private val bottomPadding = 70f
    private val pointRadius = 8f
    private val lineWidth = 3f
    private val labelTextSize = 22f // Reduced text size for better fit
    private val xAxisLabelPadding = 25f // Increased padding for better spacing

    init {
        textPaint.color = labelColor
        textPaint.textSize = labelTextSize
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val chartWidth = width - startPadding - endPadding
        val chartHeight = height - topPadding - bottomPadding
        
        // Find max value for scaling
        val maxIncome = incomeData.maxOrNull() ?: 0f
        val maxExpense = expenseData.maxOrNull() ?: 0f
        val maxValue = maxOf(maxIncome, maxExpense)
        if (maxValue == 0f) return
        
        // Draw grid lines and Y-axis labels
        paint.color = gridColor
        paint.strokeWidth = 1f
        val numGridLines = 5
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..numGridLines) {
            val y = topPadding + (chartHeight * i / numGridLines)
            canvas.drawLine(startPadding, y, width - endPadding, y, paint)
            
            // Format Y-axis labels
            val value = maxValue * (numGridLines - i) / numGridLines
            val labelText = if (value >= 1000) {
                String.format("%.0fk", value / 1000f)
            } else {
                String.format("%.0f", value)
            }
            canvas.drawText(labelText, startPadding - 15, y + (textPaint.textSize / 3), textPaint)
        }
        
        // Draw X-axis labels with adjusted spacing
        textPaint.textAlign = Paint.Align.CENTER
        val xStep = if (labels.size > 1) (chartWidth - 2 * xAxisLabelPadding) / (labels.size - 1) else 0f
        for (i in labels.indices) {
            val x = startPadding + xAxisLabelPadding + (xStep * i)
            // Draw label with smaller text size for better fit
            textPaint.textSize = labelTextSize
            canvas.drawText(labels[i], x, height - bottomPadding + 40, textPaint)
        }
        
        // Draw income line
        if (incomeData.isNotEmpty()) {
            paint.color = incomeColor
            paint.strokeWidth = lineWidth
            paint.style = Paint.Style.STROKE
            
            val path = Path()
            val firstX = if (labels.size > 1) startPadding + xAxisLabelPadding else startPadding + xAxisLabelPadding + chartWidth / 2
            val firstY = height - bottomPadding - (incomeData[0] / maxValue * chartHeight)
            path.moveTo(firstX, firstY)
            
            for (i in 1 until incomeData.size) {
                val x = if (labels.size > 1) startPadding + xAxisLabelPadding + (xStep * i) else firstX
                val y = height - bottomPadding - (incomeData[i] / maxValue * chartHeight)
                path.lineTo(x, y)
            }
            
            canvas.drawPath(path, paint)
            
            // Draw points
            paint.style = Paint.Style.FILL
            for (i in incomeData.indices) {
                val x = if (labels.size > 1) startPadding + xAxisLabelPadding + (xStep * i) else firstX
                val y = height - bottomPadding - (incomeData[i] / maxValue * chartHeight)
                canvas.drawCircle(x, y, pointRadius, paint)
            }
        }
        
        // Draw expense line
        if (expenseData.isNotEmpty()) {
            paint.color = expenseColor
            paint.strokeWidth = lineWidth
            paint.style = Paint.Style.STROKE
            
            val path2 = Path()
            val firstX2 = if (labels.size > 1) startPadding + xAxisLabelPadding else startPadding + xAxisLabelPadding + chartWidth / 2
            val firstY2 = height - bottomPadding - (expenseData[0] / maxValue * chartHeight)
            path2.moveTo(firstX2, firstY2)
            
            for (i in 1 until expenseData.size) {
                val x = if (labels.size > 1) startPadding + xAxisLabelPadding + (xStep * i) else firstX2
                val y = height - bottomPadding - (expenseData[i] / maxValue * chartHeight)
                path2.lineTo(x, y)
            }
            
            canvas.drawPath(path2, paint)
            
            // Draw points
            paint.style = Paint.Style.FILL
            for (i in expenseData.indices) {
                val x = if (labels.size > 1) startPadding + xAxisLabelPadding + (xStep * i) else firstX2
                val y = height - bottomPadding - (expenseData[i] / maxValue * chartHeight)
                canvas.drawCircle(x, y, pointRadius, paint)
            }
        }
        
        // Draw legend
        val legendY = topPadding - 25
        val legendItemWidth = 150f
        val legendRectSize = 25f
        val legendTextSize = 20f
        textPaint.textSize = legendTextSize
        textPaint.textAlign = Paint.Align.LEFT

        // Calculate starting X to center the legend
        val legendTotalWidth = (legendItemWidth * 2)
        var legendX = (width - legendTotalWidth) / 2

        // Income legend
        paint.color = incomeColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(legendX, legendY - legendRectSize / 2, legendX + legendRectSize, legendY + legendRectSize / 2, paint)
        canvas.drawText("Income", legendX + legendRectSize + 10, legendY + (legendTextSize / 3), textPaint)

        // Expense legend
        legendX += legendItemWidth
        paint.color = expenseColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(legendX, legendY - legendRectSize / 2, legendX + legendRectSize, legendY + legendRectSize / 2, paint)
        canvas.drawText("Expense", legendX + legendRectSize + 10, legendY + (legendTextSize / 3), textPaint)
    }
} 