package com.example.pantryhub_assignment3_fy.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.example.pantryhub_assignment3_fy.util.ExpiryLotRules
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules local device reminders for inventoryItem expiry dates.
 *
 * This uses AlarmManager on the current phone only. Firestore stores the inventoryItem data,
 * but each staffMember's device schedules its own reminders after loading that data.
 */
object InventoryReminderScheduler {
    const val CHANNEL_ID = "inventory_expiry_reminders"
    const val EXTRA_INVENTORY_ITEM_ID = "extra_inventory_item_id"
    const val EXTRA_INVENTORY_ITEM_NAME = "extra_inventory_item_name"
    const val EXTRA_EXPIRY_DATE = "extra_expiry_date"
    private const val REMINDER_HOUR = 9

    /**
     * Creates the notification channel required before Android can show expiry reminders.
     */
    fun ensureNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.inventory_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.inventory_reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Schedules or replaces one reminder alarm for a inventoryItem item.
     */
    fun schedule(context: Context, inventoryItem: InventoryItem) {
        cancel(context, inventoryItem)
        if (!AppPreferences.expiryRemindersEnabled(context)) {
            return
        }

        if (!inventoryItem.canHaveReminder()) return
        ExpiryLotRules.sorted(inventoryItem.expiryLots)
            .filter { it.expiryDate != null && it.quantity > 0.0 }
            .forEach { lot ->
                scheduleLot(context, inventoryItem, lot.id, lot.expiryDate ?: return@forEach)
            }
    }

    private fun scheduleLot(context: Context, inventoryItem: InventoryItem, lotId: String, expiryDate: Long) {
        val reminderAt = calculateReminderTimeMillis(expiryDate, inventoryItem.reminderDaysBefore)
        if (reminderAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, InventoryReminderReceiver::class.java).apply {
            putExtra(EXTRA_INVENTORY_ITEM_ID, inventoryItem.id)
            putExtra(EXTRA_INVENTORY_ITEM_NAME, inventoryItem.name)
            putExtra(EXTRA_EXPIRY_DATE, expiryDate)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "${inventoryItem.id}:$lotId".stableReminderRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // AlarmManager stores the reminder locally on this device. Reusing the same request code
        // replaces older reminders for the same inventoryItem after the user edits the expiry/reminder date.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, pendingIntent)
    }

    /**
     * Rebuilds reminder alarms for all currently loaded inventoryItem items.
     */
    fun scheduleAll(context: Context, inventoryItems: List<InventoryItem>) {
        if (!AppPreferences.expiryRemindersEnabled(context)) {
            inventoryItems.forEach { cancel(context, it) }
            return
        }
        inventoryItems.forEach { schedule(context, it) }
    }

    /**
     * Cancels the local reminder alarm for one inventoryItem item.
     */
    fun cancel(context: Context, inventoryItemId: String) {
        if (inventoryItemId.isBlank()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, InventoryReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            inventoryItemId.stableReminderRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancel(context: Context, inventoryItem: InventoryItem) {
        cancel(context, inventoryItem.id)
        inventoryItem.expiryLots.forEach { lot ->
            cancelRequest(context, "${inventoryItem.id}:${lot.id}".stableReminderRequestCode())
        }
    }

    private fun cancelRequest(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, InventoryReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Checks whether this app currently has notification permission.
     */
    fun canPostNotifications(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun InventoryItem.canHaveReminder(): Boolean {
        val archived = status == InventoryStatus.USED.name || status == InventoryStatus.WASTED.name
        // A value of 0 means the Add/Edit Inventory Item checkbox is off.
        return reminderDaysBefore > 0 && id.isNotBlank() && name.isNotBlank() &&
            expiryLots.any { it.expiryDate != null && it.quantity > 0.0 } && quantity > 0.0 && !archived
    }

    private fun calculateReminderTimeMillis(expiryMillis: Long, reminderDaysBefore: Int): Long {
        val zone = ZoneId.systemDefault()
        val expiryDate = Instant.ofEpochMilli(expiryMillis).atZone(zone).toLocalDate()

        // The stored expiry date is a calendar day, so reminders are scheduled at 9 AM on
        // the user-selected reminder day instead of midnight, which is easier to notice.
        return expiryDate
            .minusDays(reminderDaysBefore.coerceAtLeast(0).toLong())
            .atTime(LocalTime.of(REMINDER_HOUR, 0))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun String.stableReminderRequestCode(): Int = hashCode()
}
