package com.example.pantryhub_assignment3_fy.ui.common

import android.text.format.DateFormat
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object TransactionDateTimePicker {
    fun show(
        fragmentManager: FragmentManager,
        initialMillis: Long,
        use24HourTime: Boolean,
        onSelected: (Long) -> Unit,
        onInvalidFuture: () -> Unit
    ) {
        val zone = ZoneId.systemDefault()
        val initial = Instant.ofEpochMilli(initialMillis).atZone(zone)
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointBackward.now())
                    .build()
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { selectedUtcMillis ->
            val selectedDate = Instant.ofEpochMilli(selectedUtcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            showTimePicker(
                fragmentManager = fragmentManager,
                selectedDate = selectedDate,
                initialHour = initial.hour,
                initialMinute = initial.minute,
                use24HourTime = use24HourTime,
                onSelected = onSelected,
                onInvalidFuture = onInvalidFuture
            )
        }
        datePicker.show(fragmentManager, DATE_PICKER_TAG)
    }

    private fun showTimePicker(
        fragmentManager: FragmentManager,
        selectedDate: LocalDate,
        initialHour: Int,
        initialMinute: Int,
        use24HourTime: Boolean,
        onSelected: (Long) -> Unit,
        onInvalidFuture: () -> Unit
    ) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (use24HourTime) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .build()

        picker.addOnPositiveButtonClickListener {
            val selectedMillis = selectedDate
                .atTime(picker.hour, picker.minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (selectedMillis > System.currentTimeMillis()) {
                onInvalidFuture()
                showTimePicker(
                    fragmentManager,
                    selectedDate,
                    picker.hour,
                    picker.minute,
                    use24HourTime,
                    onSelected,
                    onInvalidFuture
                )
            } else {
                onSelected(selectedMillis)
            }
        }
        picker.show(fragmentManager, TIME_PICKER_TAG)
    }

    fun is24HourTime(context: android.content.Context): Boolean = DateFormat.is24HourFormat(context)

    private const val DATE_PICKER_TAG = "transaction_date_picker"
    private const val TIME_PICKER_TAG = "transaction_time_picker"
}
