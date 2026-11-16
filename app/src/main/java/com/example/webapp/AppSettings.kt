package com.example.webapp

import android.content.Context

object AppSettings {
    private const val DEFAULT_REMINDER_TIME = "13:00"
    private val TIME = Regex("([01]\\d|2[0-3]):[0-5]\\d")

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun reminderTime(context: Context): String =
        prefs(context).getString("reminder_time", null) ?: DEFAULT_REMINDER_TIME

    fun setReminderTime(context: Context, value: String): Boolean {
        if (!value.matches(TIME)) return false
        prefs(context).edit().putString("reminder_time", value).apply()
        return true
    }
}
