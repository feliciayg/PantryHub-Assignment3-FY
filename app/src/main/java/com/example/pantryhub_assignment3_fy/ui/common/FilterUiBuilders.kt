package com.example.pantryhub_assignment3_fy.ui.common

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetFilterSelectorBinding
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.google.android.material.bottomsheet.BottomSheetDialog

data class FilterSelectorOptionUi(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val trailingText: String = "",
    val searchTokens: List<String> = emptyList(),
    val imageUrl: String = "",
    val iconRes: Int = 0
)

/**
 * Shared row used by transaction and purchase filter pages so their form-like filter screens
 * stay visually aligned after future design tweaks.
 */
fun Fragment.addInteractiveFilterRow(
    container: LinearLayout,
    label: String,
    value: String,
    @ColorRes valueColorRes: Int,
    onClick: () -> Unit
) {
    val row = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
        setPadding(
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            0,
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            0
        )
        background = selectableItemBackground()
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    row.addView(TextView(requireContext()).apply {
        text = label
        setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    row.addView(TextView(requireContext()).apply {
        text = value.ifBlank { getString(R.string.all) }
        setTextColor(ContextCompat.getColor(requireContext(), valueColorRes))
        textSize = 16f
        maxLines = 1
    })
    row.addView(ImageView(requireContext()).apply {
        setImageResource(R.drawable.ic_chevron_right)
        setColorFilter(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
        layoutParams = LinearLayout.LayoutParams(24, 24).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.space_sm)
        }
    })
    container.addView(row)
    container.addView(createFilterDivider())
}

fun Fragment.showFilterSelectorBottomSheet(
    title: String,
    searchHint: String,
    options: List<FilterSelectorOptionUi>,
    selectedId: String,
    onSelected: (FilterSelectorOptionUi?) -> Unit
) {
    val dialog = BottomSheetDialog(requireContext())
    val sheetBinding = BottomSheetFilterSelectorBinding.inflate(layoutInflater)
    dialog.setContentView(sheetBinding.root)
    sheetBinding.titleTextView.text = title
    sheetBinding.searchEditText.hint = searchHint

    fun render(query: String) {
        sheetBinding.rowsContainer.removeAllViews()
        var visibleCount = 0

        // The selector no longer renders a separate "clear" row; callers show a real "All" option
        // when that option is part of the filter domain, such as transaction type.
        options.filter { it.matchesQuery(query) }
            .forEachIndexed { index, option ->
                if (visibleCount > 0 || index > 0) {
                    sheetBinding.rowsContainer.addView(createFilterDivider())
                }
                sheetBinding.rowsContainer.addView(
                    if (option.imageUrl.isNotBlank() || option.trailingText.isNotBlank()) {
                        createRichOptionRow(option, option.id == selectedId) {
                            dialog.dismiss()
                            onSelected(option)
                        }
                    } else {
                        createSimpleOptionRow(
                            title = option.title,
                            subtitle = option.subtitle,
                            trailingText = option.trailingText,
                            iconRes = option.iconRes,
                            useInitialsAvatar = option.iconRes == R.drawable.ic_person || option.iconRes == R.drawable.ic_restock,
                            selected = option.id == selectedId
                        ) {
                            dialog.dismiss()
                            onSelected(option)
                        }
                    }
                )
                visibleCount += 1
            }

        sheetBinding.emptyTextView.isVisible = visibleCount == 0
    }

    sheetBinding.searchEditText.doAfterTextChanged { editable ->
        render(editable?.toString().orEmpty().trim())
    }
    render("")
    dialog.show()
}

fun formatOptionalDateRange(startMillis: Long?, endMillis: Long?): String {
    if (startMillis == null || endMillis == null) return ""
    val normalizedStart = minOf(startMillis, endMillis)
    val normalizedEnd = maxOf(startMillis, endMillis)
    val start = DateUtils.formatDisplayDate(normalizedStart)
    val end = DateUtils.formatDisplayDate(normalizedEnd)
    return if (normalizedStart == normalizedEnd) start else "$start - $end"
}

private fun Fragment.createSimpleOptionRow(
    title: String,
    subtitle: String,
    trailingText: String,
    iconRes: Int,
    useInitialsAvatar: Boolean = false,
    selected: Boolean,
    onClick: () -> Unit
): View {
    val row = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
        setPadding(
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            resources.getDimensionPixelSize(R.dimen.space_sm),
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            resources.getDimensionPixelSize(R.dimen.space_sm)
        )
        background = selectableItemBackground()
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    if (useInitialsAvatar) {
        row.addView(createInitialsAvatar(title, selected))
    } else {
        row.addView(ImageView(requireContext()).apply {
            if (iconRes != 0) setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.inventory_primary else R.color.inventory_outline
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.home_action_icon_size),
                resources.getDimensionPixelSize(R.dimen.home_action_icon_size)
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_md)
            }
        })
    }
    row.addView(LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 16f
        })
        addView(TextView(requireContext()).apply {
            text = subtitle
            isVisible = subtitle.isNotBlank()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            textSize = 13f
        })
    })
    row.addView(TextView(requireContext()).apply {
        text = trailingText
        isVisible = trailingText.isNotBlank()
        setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
        textSize = 16f
    })
    return row
}

private fun Fragment.createInitialsAvatar(title: String, selected: Boolean): TextView {
    val size = resources.getDimensionPixelSize(R.dimen.items_row_image_size) - resources.getDimensionPixelSize(R.dimen.space_md)
    return TextView(requireContext()).apply {
        text = title.toInitials()
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.inventory_primary else R.color.inventory_text_secondary
            )
        )
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = resources.getDimensionPixelSize(R.dimen.space_md)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.inventory_primary_container else R.color.image_placeholder
                )
            )
        }
    }
}

private fun Fragment.createRichOptionRow(
    option: FilterSelectorOptionUi,
    selected: Boolean,
    onClick: () -> Unit
): View {
    val row = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
        setPadding(
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            resources.getDimensionPixelSize(R.dimen.space_md),
            resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
            resources.getDimensionPixelSize(R.dimen.space_md)
        )
        background = selectableItemBackground()
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    // People and partners usually do not have product images, so initials keep those rows useful
    // without adding one-off avatar logic in each filter screen.
    val useInitialsAvatar = option.imageUrl.isBlank() &&
        (option.iconRes == R.drawable.ic_person || option.iconRes == R.drawable.ic_restock)
    if (useInitialsAvatar) {
        row.addView(createInitialsAvatar(option.title, selected))
    } else {
        row.addView(ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.item_form_image_size),
                resources.getDimensionPixelSize(R.dimen.item_form_image_size)
            ).apply {
                width = resources.getDimensionPixelSize(R.dimen.item_form_image_size)
                height = resources.getDimensionPixelSize(R.dimen.item_form_image_size)
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_md)
            }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_inventory_image_placeholder)
            clipToOutline = true
            if (option.imageUrl.isNotBlank()) {
                loadInventoryImage(option.imageUrl, R.drawable.ic_leaf)
            } else {
                setImageResource(R.drawable.ic_leaf)
                val padding = resources.getDimensionPixelSize(R.dimen.space_md)
                setPadding(padding, padding, padding, padding)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.inventory_outline))
            }
        })
    }
    row.addView(LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(requireContext()).apply {
            text = option.title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 17f
        })
        addView(TextView(requireContext()).apply {
            text = option.subtitle
            isVisible = option.subtitle.isNotBlank()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            textSize = 14f
        })
    })
    row.addView(TextView(requireContext()).apply {
        text = option.trailingText
        isVisible = option.trailingText.isNotBlank()
        setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.inventory_primary else R.color.inventory_text_primary
            )
        )
        textSize = 18f
    })
    return row
}

private fun Fragment.createFilterDivider(): View =
    View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding)
        }
        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.inventory_outline))
    }

private fun Fragment.selectableItemBackground() =
    TypedValue().let { value ->
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        ContextCompat.getDrawable(requireContext(), value.resourceId)
    }

private fun FilterSelectorOptionUi.matchesQuery(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true
    // Search against hidden tokens as well as visible text so SKU, barcode, location, or memo can
    // still find the correct row without overcrowding the UI.
    return buildList {
        add(title)
        add(subtitle)
        addAll(searchTokens)
    }.any { token ->
        token.contains(normalizedQuery, ignoreCase = true)
    }
}

private fun String.toInitials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
