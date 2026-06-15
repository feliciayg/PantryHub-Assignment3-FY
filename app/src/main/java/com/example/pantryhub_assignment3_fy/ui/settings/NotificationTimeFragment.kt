package com.example.pantryhub_assignment3_fy.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentNotificationTimeBinding
import com.example.pantryhub_assignment3_fy.util.AppPreferences
import com.google.android.material.checkbox.MaterialCheckBox

class NotificationTimeFragment : Fragment() {
    private var _binding: FragmentNotificationTimeBinding? = null
    private val binding get() = _binding!!
    private var selectedHour: Int = 9

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedHour = AppPreferences.reminderHour(requireContext())
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.saveButton.setOnClickListener { saveSelection() }
        renderRows()
    }

    private fun renderRows() {
        binding.timeRowsContainer.removeAllViews()
        (0..23).forEach { hour ->
            val checkBox = MaterialCheckBox(requireContext()).apply {
                text = hour.toReminderLabel()
                isChecked = hour == selectedHour
                tag = hour
                setTextColor(resources.getColor(R.color.inventory_text_primary, null))
                setOnCheckedChangeListener { _, isChecked ->
                    if (!isChecked) return@setOnCheckedChangeListener
                    selectedHour = hour
                    binding.timeRowsContainer.children.filterIsInstance<MaterialCheckBox>()
                        .forEach { box -> if (box !== this) box.isChecked = false }
                }
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.page_horizontal_padding)
                    marginEnd = resources.getDimensionPixelSize(R.dimen.page_horizontal_padding)
                }
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.space_sm),
                    resources.getDimensionPixelSize(R.dimen.space_md),
                    resources.getDimensionPixelSize(R.dimen.space_sm),
                    resources.getDimensionPixelSize(R.dimen.space_md)
                )
            }
            binding.timeRowsContainer.addView(checkBox)
            binding.timeRowsContainer.addView(View(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.secondary_button_stroke_width))
                setBackgroundColor(resources.getColor(R.color.inventory_outline, null))
            })
        }
    }

    private fun saveSelection() {
        AppPreferences.setReminderHour(requireContext(), selectedHour)
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

internal fun Int.toReminderLabel(): String {
    val normalizedHour = when {
        this == 0 -> 12
        this > 12 -> this - 12
        else -> this
    }
    val suffix = if (this < 12) "AM" else "PM"
    return "$normalizedHour $suffix"
}
