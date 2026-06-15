package com.example.pantryhub_assignment3_fy.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.pantryhub_assignment3_fy.MainActivity
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.util.DateUtils

/**
 * Receives AlarmManager reminder broadcasts and turns them into visible notifications.
 */
class InventoryReminderReceiver : BroadcastReceiver() {
    /**
     * Builds the notification and deep-link intent when a scheduled expiry reminder fires.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (!InventoryReminderScheduler.canPostNotifications(context)) return

        val inventoryItemId =
            intent.getStringExtra(InventoryReminderScheduler.EXTRA_INVENTORY_ITEM_ID).orEmpty()
        val itemName =
            intent.getStringExtra(InventoryReminderScheduler.EXTRA_INVENTORY_ITEM_NAME).orEmpty()
        val expiryDate = intent.getLongExtra(InventoryReminderScheduler.EXTRA_EXPIRY_DATE, 0L)
        if (inventoryItemId.isBlank() || itemName.isBlank()) return

        InventoryReminderScheduler.ensureNotificationChannel(context)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(InventoryReminderScheduler.EXTRA_INVENTORY_ITEM_ID, inventoryItemId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            inventoryItemId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The notification only carries enough data to alert the user and deep-link back
        // to InventoryItemDetailFragment. Fresh inventoryItem data is still loaded from Firestore in the app.
        val notification = NotificationCompat.Builder(context, InventoryReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_storage)
            .setContentTitle(context.getString(R.string.inventory_reminder_title, itemName))
            .setContentText(context.getString(R.string.inventory_reminder_body, DateUtils.countdownText(expiryDate)))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.inventory_reminder_body_detailed,
                        itemName,
                        DateUtils.formatDisplayDate(expiryDate)
                    )
                )
            )
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(inventoryItemId.hashCode(), notification)
    }
}
