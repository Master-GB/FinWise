package com.example.finwise_lab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.finwise_lab.NotificationHelper.PushNotification
import com.example.finwise_lab.NotificationAdapter

class NotificationActivity : AppCompatActivity() {
    private lateinit var adapter: NotificationAdapter
    private lateinit var notifications: MutableList<PushNotification>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)
        // Set status bar color to match HomeFragment
        window.statusBarColor = resources.getColor(R.color.primary, theme)
        val toolbar = findViewById<Toolbar>(R.id.toolbarNotification)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val tvTitle = findViewById<TextView>(R.id.tvTitleNotification)
        tvTitle.text = "Notifications"
        toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, theme))
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val rvNotifications = findViewById<RecyclerView>(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)
        notifications = loadPushNotifications().toMutableList()
        adapter = NotificationAdapter(notifications)
        // No longer needed, handled in swipe callback
        
        rvNotifications.adapter = adapter

        // Swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                notifications.removeAt(position)
                adapter.notifyItemRemoved(position)
                saveNotifications()
                // Broadcast notification update
                sendBroadcast(Intent("notification_updated"))
                android.util.Log.d("NotificationSwipe", "onSwiped called for position $position and removed from notifications list")
            }
        })
        itemTouchHelper.attachToRecyclerView(rvNotifications)

        findViewById<Button>(R.id.btnClearNotifications).setOnClickListener {
            notifications.clear()
            adapter.notifyDataSetChanged()
            saveNotifications()
            // Broadcast notification update
            sendBroadcast(Intent("notification_updated"))
        }
    }

    private fun loadPushNotifications(): List<PushNotification> {
        val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("push_notifications", "[]")
        val type = object : TypeToken<List<PushNotification>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }

    // No longer needed, handled in swipe callback
    

    private fun saveNotifications() {
        val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val gson = Gson()
        sharedPreferences.edit().putString("push_notifications", gson.toJson(notifications)).apply()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Data class for notifications
data class PushNotification(val title: String, val message: String, val timestamp: Long)

// Adapter for RecyclerView

