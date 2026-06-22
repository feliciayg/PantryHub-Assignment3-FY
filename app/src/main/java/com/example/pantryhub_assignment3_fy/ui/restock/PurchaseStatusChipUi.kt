package com.example.pantryhub_assignment3_fy.ui.restock

import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.RestockStatus

internal fun RestockStatus.purchaseStatusLabelRes(): Int = when (this) {
    RestockStatus.DRAFT, RestockStatus.TO_ORDER -> R.string.draft
    RestockStatus.ORDERED, RestockStatus.IN_TRANSIT -> R.string.ordered
    RestockStatus.PARTIALLY_RECEIVED -> R.string.partially_received
    RestockStatus.RECEIVED -> R.string.received
    RestockStatus.CANCELLED -> R.string.cancelled
}

internal fun RestockStatus.purchaseStatusBackgroundColorRes(): Int = when (this) {
    RestockStatus.DRAFT, RestockStatus.TO_ORDER -> R.color.chip_neutral
    RestockStatus.ORDERED, RestockStatus.IN_TRANSIT -> R.color.chip_yellow
    RestockStatus.PARTIALLY_RECEIVED -> R.color.inventory_primary_container
    RestockStatus.RECEIVED -> R.color.inventory_success_container
    RestockStatus.CANCELLED -> R.color.inventory_coral_light
}
