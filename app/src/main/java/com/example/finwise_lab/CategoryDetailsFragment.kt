package com.example.finwise_lab

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import com.example.finwise_lab.database.CurrencyDatabase as AppDatabase
import com.example.finwise_lab.database.TransactionDao
import kotlinx.coroutines.launch

class CategoryDetailsFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var categoryIcon: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvTransactionCount: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private var currencySymbol: String = "Rs"
    private lateinit var transactionAdapter: TransactionAdapter

    private var categoryName: String = ""
    private var categoryIconRes: Int = 0
    private var categoryType: CategoryType = CategoryType.EXPENSE

    private lateinit var appDb: AppDatabase
    private lateinit var transactionDao: TransactionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryName = it.getString(ARG_CATEGORY_NAME, "")
            categoryIconRes = it.getInt(ARG_CATEGORY_ICON)
            categoryType = if (it.getBoolean(ARG_IS_INCOME)) CategoryType.INCOME else CategoryType.EXPENSE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_category_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)

        appDb = AppDatabase.getDatabase(requireContext())
        transactionDao = appDb.transactionDao()

        categoryIcon = view.findViewById(R.id.categoryIcon)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        tvTransactionCount = view.findViewById(R.id.tvTransactionCount)
        rvTransactions = view.findViewById(R.id.rvTransactions)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }

        transactionAdapter = TransactionAdapter(currencySymbol)
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = transactionAdapter

        loadCurrencySymbol()

        setupUI()

        loadTransactions()
    }

    private fun setupUI() {
        categoryIcon.setImageResource(categoryIconRes)
        tvTitle.text = categoryName
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
            rvTransactions.adapter = transactionAdapter
            loadTransactions() // Ensure transactions use the updated symbol
        }
    }

    private fun loadTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entities = transactionDao.getAll()
            val transactionType = if (categoryType == CategoryType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
            val categoryTransactions = entities.filter { it.category.equals(categoryName, ignoreCase = true) && TransactionType.valueOf(it.type) == transactionType }
                .map { e -> Transaction(id=e.id, title=e.title, amount=e.amount, date=Date(e.date), type=TransactionType.valueOf(e.type), category=e.category) }

            if (categoryTransactions.isEmpty()) {
                rvTransactions.visibility = View.GONE
                tvEmptyState.visibility = View.VISIBLE
            } else {
                rvTransactions.visibility = View.VISIBLE
                tvEmptyState.visibility = View.GONE

                val total = categoryTransactions.sumOf { it.amount }
                tvTotalAmount.text = "$currencySymbol${String.format("%.2f", total)}"
                tvTransactionCount.text = "${categoryTransactions.size} transactions"

                val transactionItems = categoryTransactions
                    .groupBy {
                        Calendar.getInstance().apply {
                            time = it.date
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time
                    }
                    .flatMap { (date, transactions) ->
                        listOf(TransactionItem.MonthHeader(date)) +
                                transactions.map { TransactionItem.Transaction(it) }
                    }

                transactionAdapter.submitList(transactionItems)
            }
        }
    }

    companion object {
        private const val ARG_CATEGORY_NAME = "category_name"
        private const val ARG_CATEGORY_ICON = "category_icon"
        private const val ARG_IS_INCOME = "is_income"

        fun newInstance(category: Category) = CategoryDetailsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CATEGORY_NAME, category.name)
                putInt(ARG_CATEGORY_ICON, category.iconResourceId)
                putBoolean(ARG_IS_INCOME, category.type == CategoryType.INCOME)
            }
        }
    }
}