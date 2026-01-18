package com.example.finwise_lab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finwise_lab.databinding.ItemTransactionBinding
import com.example.finwise_lab.databinding.ItemMonthHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(private val currencySymbol: String) : 
    ListAdapter<TransactionItem, RecyclerView.ViewHolder>(TransactionDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_MONTH_HEADER = 0
        private const val VIEW_TYPE_TRANSACTION = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TransactionItem.MonthHeader -> VIEW_TYPE_MONTH_HEADER
            is TransactionItem.Transaction -> VIEW_TYPE_TRANSACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MONTH_HEADER -> {
                val binding = ItemMonthHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MonthHeaderViewHolder(binding)
            }
            VIEW_TYPE_TRANSACTION -> {
                val binding = ItemTransactionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TransactionViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MonthHeaderViewHolder -> holder.bind(getItem(position) as TransactionItem.MonthHeader)
            is TransactionViewHolder -> holder.bind((getItem(position) as TransactionItem.Transaction).transaction)
        }
    }

    inner class MonthHeaderViewHolder(
        private val binding: ItemMonthHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        fun bind(header: TransactionItem.MonthHeader) {
            binding.tvMonthHeader.text = monthFormat.format(header.date)
        }
    }

    inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(transaction: Transaction) {
            binding.apply {
                // Set title and date
                tvTransactionTitle.text = transaction.title
                tvTransactionDate.text = dateFormat.format(transaction.date)
                
                // Set amount with color
                val amount = "$currencySymbol${String.format("%.2f", transaction.amount)}"
                tvTransactionAmount.text = amount
                val colorRes = if (transaction.type == TransactionType.INCOME) R.color.secondary else R.color.primary
                tvTransactionAmount.setTextColor(ContextCompat.getColor(itemView.context, colorRes))

                // Set arrow icon
                val arrowRes = if (transaction.type == TransactionType.INCOME) R.drawable.ic_income_arrow else R.drawable.ic_expense_arrow
                ivTransactionArrow.setImageResource(arrowRes)
                ivTransactionArrow.contentDescription = if (transaction.type == TransactionType.INCOME) "Income arrow" else "Expense arrow"
                
                // Set category icon
                val iconRes = when (transaction.category.lowercase()) {
                    "salary" -> R.drawable.ic_category_salary
                    "bonus" -> R.drawable.ic_category_bonus
                    "investment" -> R.drawable.ic_category_investment
                    "freelance" -> R.drawable.ic_category_freelance
                    "food" -> R.drawable.ic_food
                    "transport" -> R.drawable.ic_category_transport
                    "entertainment" -> R.drawable.ic_category_entertainment
                    "bills" -> R.drawable.ic_category_bills
                    "shopping" -> R.drawable.ic_category_shopping
                    "rent" -> R.drawable.ic_category_rent
                    "medicine" -> R.drawable.ic_category_medicine
                    else -> R.drawable.ic_category_other
                }
                ivCategoryIcon.setImageResource(iconRes)
            }
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionItem>() {
        override fun areItemsTheSame(oldItem: TransactionItem, newItem: TransactionItem): Boolean {
            return when {
                oldItem is TransactionItem.MonthHeader && newItem is TransactionItem.MonthHeader -> {
                    val oldDate = Calendar.getInstance().apply { time = oldItem.date }
                    val newDate = Calendar.getInstance().apply { time = newItem.date }
                    oldDate.get(Calendar.YEAR) == newDate.get(Calendar.YEAR) &&
                    oldDate.get(Calendar.MONTH) == newDate.get(Calendar.MONTH)
                }
                oldItem is TransactionItem.Transaction && newItem is TransactionItem.Transaction -> {
                    oldItem.transaction.id == newItem.transaction.id
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TransactionItem, newItem: TransactionItem): Boolean {
            return oldItem == newItem
        }
    }
} 