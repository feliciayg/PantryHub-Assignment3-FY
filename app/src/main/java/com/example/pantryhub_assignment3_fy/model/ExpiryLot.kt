package com.example.pantryhub_assignment3_fy.model

data class ExpiryLot(
    val id: String = "",
    val inventoryItemId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val expiryDate: Long? = null,
    val quantity: Double = 0.0,
    val receivedAt: Long = 0L,
    val sourceTransactionId: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
