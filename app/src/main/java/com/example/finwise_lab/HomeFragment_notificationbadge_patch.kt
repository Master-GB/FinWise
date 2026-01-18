package com.example.finwise_lab

import android.view.View
import android.widget.TextView
import android.content.SharedPreferences

fun updateNotificationBadge(view: View, sharedPreferences: SharedPreferences) {
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
