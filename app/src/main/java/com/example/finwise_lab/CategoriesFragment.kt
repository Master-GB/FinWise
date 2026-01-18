package com.example.finwise_lab

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.finwise_lab.R

class CategoriesFragment : BaseFragment() {
    private lateinit var categoryGrid: GridLayout
    private lateinit var tabLayout: TabLayout
    private var currentType = CategoryType.EXPENSE

    companion object {
        private var lastSelectedType = CategoryType.EXPENSE
    }

    private val categories: List<Category> = listOf(

        Category(name = "Salary", iconResourceId = R.drawable.ic_category_salary, type = CategoryType.INCOME),
        Category(name = "Bonus", iconResourceId = R.drawable.ic_category_bonus, type = CategoryType.INCOME),
        Category(name = "Investment", iconResourceId = R.drawable.ic_category_investment, type = CategoryType.INCOME),
        Category(name = "Freelance", iconResourceId = R.drawable.ic_category_freelance, type = CategoryType.INCOME),
        Category(name = "Other", iconResourceId = R.drawable.ic_category_other, type = CategoryType.INCOME),


        Category(name = "Food", iconResourceId = R.drawable.ic_food, type = CategoryType.EXPENSE),
        Category(name = "Transport", iconResourceId = R.drawable.ic_category_transport, type = CategoryType.EXPENSE),
        Category(name = "Entertainment", iconResourceId = R.drawable.ic_category_entertainment, type = CategoryType.EXPENSE),
        Category(name = "Bills", iconResourceId = R.drawable.ic_category_bills, type = CategoryType.EXPENSE),
        Category(name = "Shopping", iconResourceId = R.drawable.ic_category_shopping, type = CategoryType.EXPENSE),
        Category(name = "Rent", iconResourceId = R.drawable.ic_category_rent, type = CategoryType.EXPENSE),
        Category(name = "Medicine", iconResourceId = R.drawable.ic_category_medicine, type = CategoryType.EXPENSE),
        Category(name = "Other", iconResourceId = R.drawable.ic_category_other, type = CategoryType.EXPENSE)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentType = if (savedInstanceState?.getInt("currentType", 0) == 0) 
            CategoryType.EXPENSE else CategoryType.INCOME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }


    private fun updateNotificationBadge(view: View) {
        val sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
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

        // Initialize views
        categoryGrid = view.findViewById(R.id.categoryGrid)
        tabLayout = view.findViewById(R.id.tabLayout)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        // Set up back button
        btnBack.setOnClickListener {
            // Navigate via BottomNavigationView to ensure correct selection
            (requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation))?.apply {
                selectedItemId = R.id.navigation_home
            }
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

        // Set up tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentType = if (tab?.position == 0) CategoryType.EXPENSE else CategoryType.INCOME
                lastSelectedType = currentType
                displayCategories()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Set the initial tab selection and display
        if (savedInstanceState == null) {
            // First time opening the fragment
            currentType = lastSelectedType
            tabLayout.getTabAt(if (currentType == CategoryType.EXPENSE) 0 else 1)?.select()
        } else {
            // Restoring from saved state
            val savedType = savedInstanceState.getInt("currentType", 0)
            currentType = if (savedType == 0) CategoryType.EXPENSE else CategoryType.INCOME
            tabLayout.getTabAt(savedType)?.select()
        }
        
        // Initial display
        displayCategories()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the current type
        outState.putInt("currentType", if (currentType == CategoryType.EXPENSE) 0 else 1)
    }

    private fun displayCategories() {
        // Clear existing categories
        categoryGrid.removeAllViews()

        // Filter categories by type
        val filteredCategories = categories.filter { it.type == currentType }

        // Add category items to grid
        for (category in filteredCategories) {
            val categoryView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category, categoryGrid, false)

            // Set icon
            categoryView.findViewById<ImageView>(R.id.categoryIcon)
                .setImageResource(category.iconResourceId)

            // Set name
            categoryView.findViewById<TextView>(R.id.categoryName)
                .text = category.name

            // Set click listener on the container
            categoryView.findViewById<View>(R.id.categoryContainer).setOnClickListener {
                onCategoryClicked(category)
            }

            // Add to grid
            categoryGrid.addView(categoryView)
        }
    }

    private fun onCategoryClicked(category: Category) {
        // Save the current type before navigating
        currentType = category.type
        lastSelectedType = currentType
        
        // Navigate to category details
        val categoryDetailsFragment = CategoryDetailsFragment.newInstance(category)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, categoryDetailsFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateNotificationBadge(it) }
        // Restore the correct tab when returning from category details
        currentType = lastSelectedType
        tabLayout.getTabAt(if (currentType == CategoryType.EXPENSE) 0 else 1)?.select()
        displayCategories()
    }
} 