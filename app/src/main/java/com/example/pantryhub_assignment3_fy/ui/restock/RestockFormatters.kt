package com.example.pantryhub_assignment3_fy.ui.restock

import com.example.pantryhub_assignment3_fy.util.DateUtils
import java.util.Date

/**
 * Shared Purchases formatting helpers keep list, detail, and editor screens consistent while
 * trimming duplicate local extension functions across the restock package.
 */
internal fun Long.toPurchaseDateTimeText(emptyValue: String = "-"): String {
    if (this <= 0L) return emptyValue
    return "${DateUtils.formatDisplayDate(this)} ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(Date(this))}"
}

internal fun Double.toPurchaseMoneyText(): String = "RM%.2f".format(this)

internal fun Double.toPurchaseQuantityText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

internal fun Int.toPurchaseItemLabel(): String = if (this == 1) "1 item" else "$this items"
