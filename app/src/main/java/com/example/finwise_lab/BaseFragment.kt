package com.example.finwise_lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.Gson

abstract class BaseFragment : Fragment(), NotificationUpdateListener {
    private lateinit var notificationBadge: TextView
    private val gson = Gson()

    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "notification_updated") {
                updateNotificationBadge()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Register broadcast receiver
        requireActivity().registerReceiver(
            notificationUpdateReceiver,
            IntentFilter("notification_updated")
        )
    }

    override fun onDestroyView() {
        requireActivity().unregisterReceiver(notificationUpdateReceiver)
        super.onDestroyView()
    }

    override fun updateNotificationBadge() {
        view?.let { view ->
            val badge = view.findViewById<TextView>(R.id.tvNotificationBadge)
            val sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
            val notificationsJson = sharedPreferences.getString("push_notifications", "[]")
            val type = object : com.google.gson.reflect.TypeToken<List<Any>>() {}.type
            val notifications: List<Any> = try {
                gson.fromJson(notificationsJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            badge?.apply {
                visibility = if (notifications.isNotEmpty()) View.VISIBLE else View.GONE
                text = if (notifications.size > 9) "9+" else notifications.size.toString()
            }
        }
    }
}
