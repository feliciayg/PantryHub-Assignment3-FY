package com.example.pantryhub_assignment3_fy.model

data class ActivityLog(
    val id: String = "",
    val inventoryItemId: String? = null,
    val itemName: String = "",
    val actionType: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val reason: String? = null,
    val note: String? = null,
    val performedBy: String = "",
    val performedByName: String = "",
    val createdAt: Long = 0L
)
