package com.example.finwise_lab

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.room.Entity
import androidx.room.PrimaryKey

class CurrencySelectActivity : AppCompatActivity() {
    private lateinit var rvCurrencies: RecyclerView
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnContinue: MaterialButton
    private lateinit var btnBack: ImageButton
    private var selectedCurrency: String? = null
    private var selectedPosition: Int = -1
    private lateinit var currencyDao: com.example.finwise_lab.database.CurrencyDao
    private lateinit var currencyDb: com.example.finwise_lab.database.CurrencyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_currency_select)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.currencySelect)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currencyDb = com.example.finwise_lab.database.CurrencyDatabase.getDatabase(this)
        currencyDao = currencyDb.currencyDao()


        rvCurrencies = findViewById(R.id.rvCurrencies)
        etSearch = findViewById(R.id.etSearch)
        btnContinue = findViewById(R.id.btnContinue)
        btnBack = findViewById(R.id.btnBackC)

        findViewById<View>(R.id.btnBackC).setOnClickListener {
            startActivity(Intent(this, GetStartActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }


        rvCurrencies.layoutManager = LinearLayoutManager(this)
        rvCurrencies.adapter = CurrencyAdapter(getCurrencyList()) { currency, position ->
            selectedCurrency = currency
            selectedPosition = position
            btnContinue.isEnabled = true
            btnContinue.alpha = 1f
            (rvCurrencies.adapter as CurrencyAdapter).notifyDataSetChanged()
            

            val currencyDetails = CurrencyDetails(
                code = currency,
                name = getCurrencyList().find { it.code == currency }?.name ?: "",
                symbol = getCurrencySymbol(currency)
            )
            lifecycleScope.launch {
                currencyDao.insertCurrency(currencyDetails)
            }
        }


        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                (rvCurrencies.adapter as CurrencyAdapter).filter(s.toString())
            }
        })


        btnContinue.isEnabled = false
        btnContinue.alpha = 0.5f
        btnContinue.setOnClickListener {
            if (selectedCurrency != null) {
                startActivity(Intent(this, SuccessfullyCreatedActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }
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
}

data class Currency(
    val code: String,
    val name: String
)

class CurrencyAdapter(
    private var currencies: List<Currency>,
    private val onCurrencySelected: (String, Int) -> Unit
) : RecyclerView.Adapter<CurrencyAdapter.CurrencyViewHolder>() {

    private var filteredCurrencies: List<Currency> = currencies
    private var selectedPosition: Int = -1

    class CurrencyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCurrencyCode: TextView = itemView.findViewById(R.id.tvCurrencyCode)
        val tvCurrencyName: TextView = itemView.findViewById(R.id.tvCurrencyName)
        val ivSelected: View = itemView.findViewById(R.id.ivSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_currency, parent, false)
        return CurrencyViewHolder(view)
    }

    override fun onBindViewHolder(holder: CurrencyViewHolder, position: Int) {
        val currency = filteredCurrencies[position]
        holder.tvCurrencyCode.text = currency.code
        holder.tvCurrencyName.text = currency.name

        // Set content descriptions
        holder.itemView.contentDescription = "Select ${currency.name} (${currency.code})"
        holder.tvCurrencyCode.contentDescription = "Currency code: ${currency.code}"
        holder.tvCurrencyName.contentDescription = "Currency name: ${currency.name}"
        holder.ivSelected.contentDescription = if (position == selectedPosition) 
            "Selected" else "Not selected"

        // Update selection state
        if (position == selectedPosition) {
            holder.tvCurrencyCode.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.primary))
            holder.tvCurrencyName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.primary))
            holder.ivSelected.visibility = View.VISIBLE
        } else {
            holder.tvCurrencyCode.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
            holder.tvCurrencyName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
            holder.ivSelected.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            selectedPosition = position
            onCurrencySelected(currency.code, position)
        }
    }

    override fun getItemCount() = filteredCurrencies.size

    fun filter(query: String) {
        filteredCurrencies = if (query.isEmpty()) {
            currencies
        } else {
            currencies.filter {
                it.code.contains(query, ignoreCase = true) ||
                it.name.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}

@Entity
data class CurrencyDetails(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String
)