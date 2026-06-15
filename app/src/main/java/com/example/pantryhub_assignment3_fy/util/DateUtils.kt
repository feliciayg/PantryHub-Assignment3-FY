package com.example.pantryhub_assignment3_fy.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

object DateUtils {
    fun formatDisplayDate(date: Date): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }

    fun formatDisplayDate(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
    }

    fun formatInputDate(millis: Long): String {
        return millisToLocalDate(millis).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun parseInputDate(value: String): Long {
        return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    fun todayMillis(): Long {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun daysBetween(startMillis: Long, endMillis: Long): Int {
        return ChronoUnit.DAYS.between(millisToLocalDate(startMillis), millisToLocalDate(endMillis)).toInt()
    }

    fun countdownText(expiryMillis: Long): String {
        val days = ChronoUnit.DAYS.between(LocalDate.now(), millisToLocalDate(expiryMillis))
        return when {
            days < 0 -> "Expired"
            days == 0L -> "Today"
            days == 1L -> "1 day"
            days < 31 -> "$days days"
            days < 365 -> "${days / 30} months"
            else -> "${days / 365} years"
        }
    }

    private fun millisToLocalDate(value: Long): LocalDate {
        return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
