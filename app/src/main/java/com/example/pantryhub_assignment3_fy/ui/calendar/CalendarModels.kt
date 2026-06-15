package com.example.pantryhub_assignment3_fy.ui.calendar

import androidx.annotation.ColorRes
import java.time.LocalDate

data class CalendarDayUi(
    val date: LocalDate,
    val isInVisibleMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val marker: CalendarMarker = CalendarMarker.NONE,
    @ColorRes val markerColorRes: Int? = null,
    @ColorRes val markerColorResList: List<Int> = emptyList()
)

enum class CalendarMarker {
    NONE,
    UPCOMING,
    AMBER,
    RED
}
