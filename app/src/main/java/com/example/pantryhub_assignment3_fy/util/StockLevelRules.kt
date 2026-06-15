package com.example.pantryhub_assignment3_fy.util

import com.example.pantryhub_assignment3_fy.model.InventoryItem

/**
 * Shared stock-level rules. reorderThreshold remains a legacy Firestore compatibility field,
 * while reorderPoint is the user-facing and business calculation value.
 */
object StockLevelRules {
    fun effectiveReorderPoint(reorderPoint: Int, reorderThreshold: Double): Double =
        reorderPoint.takeIf { it > 0 }?.toDouble() ?: reorderThreshold.coerceAtLeast(0.0)

    fun effectiveReorderPoint(item: InventoryItem): Double =
        effectiveReorderPoint(item.reorderPoint, item.reorderThreshold)

    fun isLowStock(item: InventoryItem): Boolean =
        item.quantity <= effectiveReorderPoint(item)

    fun isOutOfStock(item: InventoryItem): Boolean =
        item.quantity <= 0.0

    fun isOverstock(item: InventoryItem): Boolean =
        item.maximumStockLevel > 0 && item.quantity > item.maximumStockLevel

    fun restockUrgency(item: InventoryItem): Double =
        (effectiveReorderPoint(item) - item.quantity).coerceAtLeast(0.0)

    // Suggested restock fills stock to the configured maximum and is zero when no refill is needed.
    fun suggestedRestockQuantity(item: InventoryItem): Double =
        if (item.maximumStockLevel > item.quantity) item.maximumStockLevel - item.quantity else 0.0
}
