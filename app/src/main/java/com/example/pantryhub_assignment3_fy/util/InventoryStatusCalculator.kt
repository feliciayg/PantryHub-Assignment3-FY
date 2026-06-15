package com.example.pantryhub_assignment3_fy.util

import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Central place for deriving a inventoryItem item's status from expiry date and quantity.
 */
object InventoryStatusCalculator {
    /**
     * Calculates status from an existing InventoryItem.
     */
    fun calculate(item: InventoryItem): InventoryStatus {
        return calculate(
            expiryDate = item.expiryDate,
            quantity = item.quantity,
            reorderPoint = StockLevelRules.effectiveReorderPoint(item),
            maximumStockLevel = item.maximumStockLevel,
            currentStatus = item.status
        )
    }

    /**
     * Calculates freshness, expiry urgency, or low-stock state without changing Firestore.
     */
    fun calculate(
        expiryDate: Long,
        quantity: Double,
        reorderPoint: Double,
        maximumStockLevel: Int = 0,
        currentStatus: String = InventoryStatus.FRESH.name
    ): InventoryStatus {
        if (currentStatus == InventoryStatus.WASTED.name) return InventoryStatus.WASTED

        val daysUntilExpiry = if (expiryDate > 0L) {
            ChronoUnit.DAYS.between(LocalDate.now(), millisToLocalDate(expiryDate))
        } else {
            Long.MAX_VALUE
        }

        // Simplified quantity rules use reorderPoint; reorderThreshold is mapped before reaching here.
        return when {
            quantity <= 0.0 -> InventoryStatus.OUT_OF_STOCK
            daysUntilExpiry < 0 -> InventoryStatus.EXPIRED
            daysUntilExpiry == 0L -> InventoryStatus.USE_TODAY
            daysUntilExpiry in 1..3 -> InventoryStatus.EXPIRING_SOON
            quantity <= reorderPoint -> InventoryStatus.LOW_STOCK
            maximumStockLevel > 0 && quantity > maximumStockLevel -> InventoryStatus.OVERSTOCK
            else -> InventoryStatus.FRESH
        }
    }

    private fun millisToLocalDate(value: Long): LocalDate {
        return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
