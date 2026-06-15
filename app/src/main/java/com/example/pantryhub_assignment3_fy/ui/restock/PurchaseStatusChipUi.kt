package com.example.pantryhub_assignment3_fy.ui.restock

import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.RestockStatus

internal fun RestockStatus.purchaseStatusLabel(): String = when (this) {
    RestockStatus.DRAFT, RestockStatus.TO_ORDER -> "Draft"
    RestockStatus.ORDERED, RestockStatus.IN_TRANSIT -> "Ordered"
    RestockStatus.PARTIALLY_RECEIVED -> "Partially Received"
    RestockStatus.RECEIVED -> "Received"
    RestockStatus.CANCELLED -> "Cancelled"
}

internal fun RestockStatus.purchaseStatusBackgroundColorRes(): Int = when (this) {
    RestockStatus.DRAFT, RestockStatus.TO_ORDER -> R.color.chip_neutral
    RestockStatus.ORDERED, RestockStatus.IN_TRANSIT -> R.color.chip_yellow
    RestockStatus.PARTIALLY_RECEIVED -> R.color.inventory_primary_container
    RestockStatus.RECEIVED -> R.color.inventory_success_container
    RestockStatus.CANCELLED -> R.color.inventory_coral_light
}
