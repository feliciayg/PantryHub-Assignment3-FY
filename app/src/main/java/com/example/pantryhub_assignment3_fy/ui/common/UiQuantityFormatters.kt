package com.example.pantryhub_assignment3_fy.ui.common

import java.util.Locale

/**
 * Shared quantity formatting helpers for dashboards and dialogs outside Storage.
 */
fun Double.toCompactQuantityText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

// Dashboard cards prefer stable two-decimal summaries, while quantity dialogs keep raw decimals.
fun Double.toSummaryQuantityText(locale: Locale = Locale.getDefault()): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(locale, "%.2f", this)
