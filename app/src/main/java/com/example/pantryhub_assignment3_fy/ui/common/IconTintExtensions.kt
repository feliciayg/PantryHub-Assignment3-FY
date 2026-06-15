package com.example.pantryhub_assignment3_fy.ui.common

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.pantryhub_assignment3_fy.R
import com.google.android.material.appbar.MaterialToolbar

fun MaterialToolbar.tintMenuIcons(
    context: Context,
    @ColorRes colorRes: Int = R.color.inventory_text_primary
) {
    val iconColor = ContextCompat.getColor(context, colorRes)
    for (index in 0 until menu.size()) {
        val item = menu.getItem(index)
        item.icon = item.icon?.mutate()?.apply { setTint(iconColor) }
    }
}
