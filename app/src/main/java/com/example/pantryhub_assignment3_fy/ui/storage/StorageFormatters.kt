package com.example.pantryhub_assignment3_fy.ui.storage

import java.text.NumberFormat
import java.util.Locale

/**
 * Shared quantity and money formatting for Storage screens and CSV flows.
 */
fun Double.toStorageQuantityText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

fun Double.toStorageMoneyText(): String =
    if (this <= 0.0) "RM0.00" else NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ms-MY")).format(this)
