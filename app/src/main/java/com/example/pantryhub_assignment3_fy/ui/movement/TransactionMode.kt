package com.example.pantryhub_assignment3_fy.ui.movement

import androidx.annotation.StringRes
import com.example.pantryhub_assignment3_fy.R

enum class TransactionMode {
    STOCK_IN,
    STOCK_OUT,
    MOVE_STOCK,
    ADJUST_STOCK;

    val quantityPrefix: String get() = when (this) {
        STOCK_IN -> "+"
        STOCK_OUT -> "-"
        MOVE_STOCK -> ""
        ADJUST_STOCK -> "-> "
    }

    @get:StringRes
    val partnerLabelRes: Int get() = when (this) {
        STOCK_IN -> R.string.partner
        STOCK_OUT -> R.string.customer
        MOVE_STOCK -> R.string.to
        ADJUST_STOCK -> R.string.partner
    }

    @get:StringRes
    val partnerPlaceholderRes: Int get() = when (this) {
        STOCK_IN -> R.string.select_a_partner
        STOCK_OUT -> R.string.select_a_customer
        MOVE_STOCK -> R.string.select_destination
        ADJUST_STOCK -> R.string.select_a_partner
    }
}
