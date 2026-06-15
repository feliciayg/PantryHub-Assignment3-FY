package com.example.pantryhub_assignment3_fy.ui.movement

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

    val partnerLabel: String get() = when (this) {
        STOCK_IN -> "Partner"
        STOCK_OUT -> "Customer"
        MOVE_STOCK -> "To"
        ADJUST_STOCK -> ""
    }

    val partnerPlaceholder: String get() = when (this) {
        STOCK_IN -> "Select"
        STOCK_OUT -> "Select a customer"
        MOVE_STOCK -> "Select destination"
        ADJUST_STOCK -> ""
    }
}
