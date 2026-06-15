package com.example.pantryhub_assignment3_fy.ui.common

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pantryhub_assignment3_fy.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.radiobutton.MaterialRadioButton

data class RadioSheetOption(
    val key: String,
    val title: String,
    val subtitle: String? = null
)

/**
 * Shared radio bottom sheet used by filter and sort selectors so the selection UI stays consistent.
 */
fun Fragment.showRadioSheet(
    title: String,
    options: List<RadioSheetOption>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    val dialog = BottomSheetDialog(requireContext())
    val container = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
        setPadding(padding, padding, padding, padding)
        setBackgroundResource(R.drawable.bg_bottom_sheet)
    }
    container.addView(TextView(requireContext()).apply {
        text = title
        setTextColor(resources.getColor(R.color.inventory_text_primary, requireContext().theme))
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
    })
    options.forEach { option ->
        val radio = MaterialRadioButton(requireContext()).apply {
            text = if (option.subtitle.isNullOrBlank()) option.title else "${option.title}\n${option.subtitle}"
            isChecked = option.key == selectedKey
            minHeight = resources.getDimensionPixelSize(R.dimen.form_field_height)
            setTextColor(resources.getColor(R.color.inventory_text_primary, requireContext().theme))
            setOnClickListener {
                onSelected(option.key)
                dialog.dismiss()
            }
        }
        container.addView(radio)
    }
    dialog.setContentView(container)
    dialog.show()
}
