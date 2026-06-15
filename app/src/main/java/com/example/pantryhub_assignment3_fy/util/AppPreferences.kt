package com.example.pantryhub_assignment3_fy.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Stores local-only app settings in SharedPreferences.
 *
 * These values are device preferences, not store data, so they are not saved
 * to Firestore and do not sync to other staff.
 */
object AppPreferences {
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val PREFS_NAME = "inventoryhub_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_EXPIRY_REMINDERS_ENABLED = "expiry_reminders_enabled"
    private const val KEY_NOTIFICATION_LOCATIONS = "notification_locations"
    private const val KEY_NOTIFICATION_REMINDER_HOUR = "notification_reminder_hour"

    const val LOCATION_ALL = "__all_locations__"

    /**
     * Returns the saved appearance mode: system, light, or dark.
     */
    fun themeMode(context: Context): String {
        return prefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    /**
     * Saves the user's chosen appearance mode locally on this device.
     */
    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    /**
     * Returns whether this device should schedule expiry reminders.
     */
    fun expiryRemindersEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EXPIRY_REMINDERS_ENABLED, true)
    }

    /**
     * Saves the local notification reminder toggle.
     */
    fun setExpiryRemindersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXPIRY_REMINDERS_ENABLED, enabled).apply()
    }

    fun notificationLocations(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_NOTIFICATION_LOCATIONS, setOf(LOCATION_ALL))?.toSet()
            ?: setOf(LOCATION_ALL)
    }

    fun setNotificationLocations(context: Context, locations: Set<String>) {
        val safeLocations = if (locations.isEmpty()) setOf(LOCATION_ALL) else locations
        prefs(context).edit().putStringSet(KEY_NOTIFICATION_LOCATIONS, safeLocations).apply()
    }

    fun reminderHour(context: Context): Int {
        return prefs(context).getInt(KEY_NOTIFICATION_REMINDER_HOUR, 9)
    }

    fun setReminderHour(context: Context, hour: Int) {
        prefs(context).edit().putInt(KEY_NOTIFICATION_REMINDER_HOUR, hour.coerceIn(0, 23)).apply()
    }

    /**
     * Applies the stored theme before layouts inflate so screens open in the right mode.
     */
    fun applySavedTheme(context: Context) {
        // Theme is a local UI preference, so SharedPreferences decides AppCompat's night mode
        // before screens are inflated. This keeps it separate from shared store Firestore data.
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode(context)) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
