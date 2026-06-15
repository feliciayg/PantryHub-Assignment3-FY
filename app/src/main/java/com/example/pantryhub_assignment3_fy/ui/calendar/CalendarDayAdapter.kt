package com.example.pantryhub_assignment3_fy.ui.calendar

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemCalendarDayBinding

class CalendarDayAdapter(
    private val onDateClick: (CalendarDayUi) -> Unit
) : ListAdapter<CalendarDayUi, CalendarDayAdapter.CalendarDayViewHolder>(CalendarDayDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarDayViewHolder {
        return CalendarDayViewHolder(
            ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onDateClick
        )
    }

    override fun onBindViewHolder(holder: CalendarDayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CalendarDayViewHolder(
        private val binding: ItemCalendarDayBinding,
        private val onDateClick: (CalendarDayUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: CalendarDayUi) {
            val context = binding.root.context
            binding.dayTextView.text = day.date.dayOfMonth.toString()
            binding.dayTextView.alpha = if (day.isInVisibleMonth) 1f else 0.28f
            binding.root.isSelected = day.isSelected
            binding.dayTextView.setTextColor(
                ContextCompat.getColor(context, if (day.isSelected) R.color.white else R.color.inventory_text_primary)
            )
            val markerColors = day.markerColorResList.take(4).ifEmpty {
                if (day.marker == CalendarMarker.NONE) emptyList() else listOf(day.markerColorRes ?: day.marker.colorRes())
            }
            val markerViews = listOf(
                binding.markerOneView,
                binding.markerTwoView,
                binding.markerThreeView,
                binding.markerFourView
            )
            binding.markerContainer.isVisible = markerColors.isNotEmpty()
            markerViews.forEachIndexed { index, markerView ->
                markerView.isVisible = index < markerColors.size
                if (index < markerColors.size) {
                    markerView.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, markerColors[index])
                    )
                }
            }
            binding.root.setOnClickListener { onDateClick(day) }
        }

        private fun CalendarMarker.colorRes(): Int {
            return when (this) {
                CalendarMarker.RED -> R.color.inventory_coral
                CalendarMarker.AMBER -> R.color.status_low_stock
                CalendarMarker.UPCOMING -> R.color.inventory_primary
                CalendarMarker.NONE -> R.color.inventory_outline
            }
        }
    }
}

private object CalendarDayDiff : DiffUtil.ItemCallback<CalendarDayUi>() {
    override fun areItemsTheSame(oldItem: CalendarDayUi, newItem: CalendarDayUi): Boolean = oldItem.date == newItem.date
    override fun areContentsTheSame(oldItem: CalendarDayUi, newItem: CalendarDayUi): Boolean = oldItem == newItem
}
