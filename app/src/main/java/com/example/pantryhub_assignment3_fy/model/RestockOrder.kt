package com.example.pantryhub_assignment3_fy.model

data class PurchaseOrderItem(
    val inventoryItemId: String = "",
    val itemName: String = "",
    val sku: String = "",
    val barcode: String = "",
    val brand: String = "",
    val category: String = "",
    val orderedQuantity: Double = 0.0,
    val receivedQuantity: Double = 0.0,
    val unit: String = "",
    val unitCost: Double = 0.0,
    val imageUrl: String = "",
    val supplierId: String = "",
    val supplierName: String = ""
)

data class PurchaseReceivingEventItem(
    val inventoryItemId: String = "",
    val itemName: String = "",
    val sku: String = "",
    val barcode: String = "",
    val receivedQuantity: Double = 0.0,
    val unit: String = "",
    val expiryDate: Long = 0L,
    val quantityBefore: Double = 0.0,
    val quantityAfter: Double = 0.0
)

data class PurchaseReceivingEvent(
    val id: String = "",
    val purchaseOrderId: String = "",
    val purchaseOrderNumber: String = "",
    val receivingLocationId: String = "",
    val receivingLocationName: String = "",
    val receivedAt: Long = 0L,
    val receivedBy: String = "",
    val receivedByName: String = "",
    val linkedStockInTransactionId: String = "",
    val receivedItems: List<PurchaseReceivingEventItem> = emptyList()
)

/**
 * Keeps the old restock shape and the new enterprise purchase shape in one model so Phase 1 can
 * upgrade the UI without breaking existing Firebase records or older inventory flows.
 */
data class RestockOrder(
    val id: String = "",
    val orderNumber: String = "",
    val supplierId: String = "",
    val supplierNameRaw: String = "",
    val receivingLocationId: String = "",
    val receivingLocationName: String = "",
    val status: String = RestockStatus.DRAFT.name,
    val orderDate: Long = 0L,
    val expectedDeliveryDate: Long = 0L,
    val memo: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0L,
    val updatedBy: String = "",
    val updatedAt: Long = 0L,
    val orderedAt: Long = 0L,
    val inTransitAt: Long = 0L,
    val receivedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0L,
    val archivedBy: String = "",
    val archiveReason: String = "",
    val inventoryItemIdRaw: String? = null,
    val itemNameRaw: String = "",
    val quantityRaw: Double = 0.0,
    val unitRaw: String = "",
    val items: List<PurchaseOrderItem> = emptyList(),
    val receivingEvents: List<PurchaseReceivingEvent> = emptyList()
) {
    // Old single-item records are surfaced as a synthetic purchase item so list/detail code can
    // read both old and new records through the same interface.
    val purchaseItems: List<PurchaseOrderItem>
        get() = if (items.isNotEmpty()) items else listOfNotNull(legacyPurchaseItem())

    val inventoryItemId: String?
        get() = purchaseItems.firstOrNull()?.inventoryItemId?.takeIf { it.isNotBlank() } ?: inventoryItemIdRaw

    val itemName: String
        get() = purchaseItems.firstOrNull()?.itemName?.takeIf { it.isNotBlank() } ?: itemNameRaw

    val quantity: Double
        get() = purchaseItems.sumOf { it.orderedQuantity }.takeIf { it > 0.0 } ?: quantityRaw

    val unit: String
        get() = purchaseItems.firstOrNull()?.unit?.takeIf { it.isNotBlank() } ?: unitRaw

    val supplierName: String
        get() = supplierNameRaw.ifBlank {
            purchaseItems.firstOrNull()?.supplierName.orEmpty()
        }

    val normalizedStatus: RestockStatus
        get() = when (status) {
            RestockStatus.TO_ORDER.name -> RestockStatus.DRAFT
            RestockStatus.IN_TRANSIT.name -> RestockStatus.ORDERED
            RestockStatus.PARTIALLY_RECEIVED.name -> RestockStatus.PARTIALLY_RECEIVED
            RestockStatus.RECEIVED.name -> RestockStatus.RECEIVED
            RestockStatus.CANCELLED.name -> RestockStatus.CANCELLED
            RestockStatus.ORDERED.name -> RestockStatus.ORDERED
            else -> RestockStatus.DRAFT
        }

    val totalCost: Double
        get() = purchaseItems.sumOf { it.orderedQuantity * it.unitCost }

    val totalReceivedQuantity: Double
        get() = purchaseItems.sumOf { it.receivedQuantity.coerceAtMost(it.orderedQuantity) }

    val totalRemainingQuantity: Double
        get() = purchaseItems.sumOf { it.remainingQuantity() }

    val itemCount: Int
        get() = purchaseItems.size

    val fallbackOrderLabel: String
        get() = orderNumber.ifBlank { "Draft Purchase" }

    fun isDraft(): Boolean = normalizedStatus == RestockStatus.DRAFT

    fun isOpenOrder(): Boolean = normalizedStatus == RestockStatus.DRAFT ||
        normalizedStatus == RestockStatus.ORDERED ||
        normalizedStatus == RestockStatus.PARTIALLY_RECEIVED

    fun isFullyReceived(): Boolean =
        purchaseItems.isNotEmpty() && purchaseItems.all { it.remainingQuantity() <= 0.0 }

    fun canEditInPhaseOne(): Boolean = normalizedStatus == RestockStatus.DRAFT

    private fun legacyPurchaseItem(): PurchaseOrderItem? {
        if (itemNameRaw.isBlank()) return null
        return PurchaseOrderItem(
            inventoryItemId = inventoryItemIdRaw.orEmpty(),
            itemName = itemNameRaw,
            orderedQuantity = quantityRaw,
            unit = unitRaw,
            supplierName = supplierNameRaw
        )
    }

}

fun PurchaseOrderItem.remainingQuantity(): Double =
    (orderedQuantity - receivedQuantity).coerceAtLeast(0.0)
