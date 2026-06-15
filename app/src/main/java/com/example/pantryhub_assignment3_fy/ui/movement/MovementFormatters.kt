package com.example.pantryhub_assignment3_fy.ui.movement

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared movement formatting helpers reduce repeated quantity/date extensions across
 * transaction list and form screens while keeping output consistent.
 */
internal fun Double.toMovementQuantityText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

internal fun Double.toMovementQuantityWithUnit(unit: String): String =
    "${toMovementQuantityText()}${unit.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"

internal fun Long.toMovementDateTimeText(emptyValue: String = "Pending time"): String =
    if (this <= 0L) emptyValue else MOVEMENT_DATE_FORMAT.format(Date(this))

private val MOVEMENT_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
