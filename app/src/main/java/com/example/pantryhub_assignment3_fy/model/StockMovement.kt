package com.example.pantryhub_assignment3_fy.model

data class StockMovement(
    val id: String = "",
    val transactionId: String = "",
    val inventoryItemId: String = "",
    val itemName: String = "",
    val sku: String = "",
    val barcode: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val movementType: String = StockMovementType.STOCK_OUT.name,
    val quantity: Double = 0.0,
    val unit: String = "",
    val quantityBefore: Double = 0.0,
    val quantityAfter: Double = 0.0,
    val reason: String = "",
    val note: String = "",
    val performedBy: String = "",
    val performedByName: String = "",
    val counterpartyId: String = "",
    val counterpartyName: String = "",
    val counterpartyType: String = "",
    val expiryDate: Long = 0L,
    val costPrice: Double = 0.0,
    val imageUrl: String = "",
    val transactionAt: Long = 0L,
    val createdAt: Long = 0L
)
