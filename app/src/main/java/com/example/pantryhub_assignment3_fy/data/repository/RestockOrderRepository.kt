package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ActivityActionType
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.PurchaseReceivingEvent
import com.example.pantryhub_assignment3_fy.model.PurchaseReceivingEventItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.RestockStatus
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.model.remainingQuantity
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

data class PurchaseDraftInput(
    val supplierId: String = "",
    val supplierName: String = "",
    val receivingLocationId: String = "",
    val receivingLocationName: String = "",
    val orderDate: Long = 0L,
    val expectedDeliveryDate: Long = 0L,
    val memo: String = "",
    val items: List<PurchaseOrderItem> = emptyList()
)

/**
 * Owns purchase documents under `stores/{storeId}/restockOrders`.
 *
 * The collection name stays the same so existing Firebase data remains valid while the UI moves
 * from a single-line restock tracker to enterprise purchase orders.
 */
class RestockOrderRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    private val activityRepository = ActivityRepository(authManager, firestoreDataSource)
    private val inventoryRepository = InventoryRepository(authManager, firestoreDataSource)
    private val stockMovementRepository = StockMovementRepository(authManager, firestoreDataSource)

    fun observeRestockOrders(includeArchived: Boolean = false): Flow<Result<List<RestockOrder>>> = callbackFlow {
        val storeId = currentUserProfile()?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = restockOrdersCollection(storeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else {
                    val orders = snapshot?.documents.orEmpty()
                        .map { it.toRestockOrder() }
                        .filter { includeArchived || !it.isArchived }
                        .sortedByDescending { order ->
                            order.updatedAt.takeIf { it > 0L } ?: order.createdAt
                        }
                    trySend(Result.success(orders))
                }
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    suspend fun addRestockOrder(
        itemName: String,
        quantity: Double,
        unit: String,
        inventoryItemId: String? = null,
        supplierName: String = ""
    ): Result<String> = runCatching {
        require(itemName.trim().isNotEmpty()) { "Purchase item name is required." }
        require(quantity > 0.0) { "Purchase quantity must be greater than 0." }

        val item = PurchaseOrderItem(
            inventoryItemId = inventoryItemId.orEmpty(),
            itemName = itemName.trim(),
            orderedQuantity = quantity,
            unit = unit.trim(),
            supplierName = supplierName.trim()
        )
        savePurchaseDraft(
            orderId = null,
            input = PurchaseDraftInput(
                supplierName = supplierName.trim(),
                orderDate = System.currentTimeMillis(),
                items = listOf(item)
            ),
            status = RestockStatus.DRAFT
        ).getOrThrow()
    }

    suspend fun createPurchaseDraft(input: PurchaseDraftInput): Result<String> =
        savePurchaseDraft(orderId = null, input = input, status = RestockStatus.DRAFT)

    suspend fun updatePurchaseDraft(orderId: String, input: PurchaseDraftInput): Result<Unit> = runCatching {
        require(orderId.isNotBlank()) { "Purchase id is required." }
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val existing = getRestockOrder(storeId, orderId)
        require(existing.canEditInPhaseOne()) { "Only draft purchases can be edited right now." }

        val cleaned = input.cleaned()
        validatePurchaseInput(cleaned)
        val updated = existing.copy(
            supplierId = cleaned.supplierId,
            supplierNameRaw = cleaned.supplierName,
            receivingLocationId = cleaned.receivingLocationId,
            receivingLocationName = cleaned.receivingLocationName,
            orderDate = cleaned.orderDate,
            expectedDeliveryDate = cleaned.expectedDeliveryDate,
            memo = cleaned.memo,
            items = cleaned.items,
            itemNameRaw = cleaned.items.firstOrNull()?.itemName.orEmpty(),
            quantityRaw = cleaned.items.sumOf { it.orderedQuantity },
            unitRaw = cleaned.items.firstOrNull()?.unit.orEmpty(),
            inventoryItemIdRaw = cleaned.items.firstOrNull()?.inventoryItemId,
            updatedBy = userId,
            updatedAt = System.currentTimeMillis(),
            createdByName = existing.createdByName.ifBlank { userProfile.displayName.ifBlank { userProfile.email } },
            status = RestockStatus.DRAFT.name
        )
        restockOrdersCollection(storeId).document(orderId).set(updated.toFirestoreMap()).await()
    }

    suspend fun placePurchaseOrder(orderId: String): Result<Unit> = runCatching {
        require(orderId.isNotBlank()) { "Purchase id is required." }
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val existing = getRestockOrder(storeId, orderId)
        require(existing.normalizedStatus == RestockStatus.DRAFT) { "Only draft purchases can be placed." }
        val items = existing.purchaseItems.map { it.copy() }
        validatePurchaseInput(
            PurchaseDraftInput(
                supplierId = existing.supplierId,
                supplierName = existing.supplierName,
                receivingLocationId = existing.receivingLocationId,
                receivingLocationName = existing.receivingLocationName,
                orderDate = existing.orderDate,
                expectedDeliveryDate = existing.expectedDeliveryDate,
                memo = existing.memo,
                items = items
            )
        )

        restockOrdersCollection(storeId).document(orderId)
            .update(
                mapOf(
                    "status" to RestockStatus.ORDERED.name,
                    "orderedAt" to Timestamp.now(),
                    "updatedBy" to userId,
                    "updatedAt" to Timestamp.now()
                )
            )
            .await()

        activityRepository.addActivityLog(
            actionType = ActivityActionType.RESTOCK_ORDER_ORDERED,
            inventoryItemId = existing.inventoryItemId,
            itemName = existing.itemName,
            quantity = existing.quantity,
            unit = existing.unit,
            note = "Purchase order placed"
        ).getOrThrow()
    }

    suspend fun updateRestockOrder(order: RestockOrder): Result<Unit> = updatePurchaseDraft(
        orderId = order.id,
        input = PurchaseDraftInput(
            supplierId = order.supplierId,
            supplierName = order.supplierName,
            receivingLocationId = order.receivingLocationId,
            receivingLocationName = order.receivingLocationName,
            orderDate = order.orderDate,
            expectedDeliveryDate = order.expectedDeliveryDate,
            memo = order.memo,
            items = order.purchaseItems
        )
    )

    suspend fun updateRestockOrderSort(orders: List<RestockOrder>): Result<Unit> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val batch = firestoreDataSource.db.batch()
        orders.forEachIndexed { index, order ->
            batch.update(
                restockOrdersCollection(storeId).document(order.id),
                mapOf(
                    "sortOrder" to index,
                    "updatedBy" to userId,
                    "updatedAt" to Timestamp.now()
                )
            )
        }
        batch.commit().await()
    }

    suspend fun deleteRestockOrder(orderId: String): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        val order = getRestockOrder(storeId, orderId)
        require(order.normalizedStatus == RestockStatus.DRAFT) { "Only draft purchases can be deleted." }
        restockOrdersCollection(storeId).document(orderId).delete().await()
    }

    suspend fun cancelRestockOrder(orderId: String): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val order = getRestockOrder(storeId, orderId)
        require(order.normalizedStatus != RestockStatus.RECEIVED) { "Received purchases cannot be cancelled." }
        restockOrdersCollection(storeId).document(orderId)
            .update(
                mapOf(
                    "status" to RestockStatus.CANCELLED.name,
                    "cancelledAt" to Timestamp.now(),
                    "updatedBy" to userId,
                    "updatedAt" to Timestamp.now()
                )
            )
            .await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.RESTOCK_ORDER_CANCELLED,
            inventoryItemId = order.inventoryItemId,
            itemName = order.itemName,
            quantity = order.quantity,
            unit = order.unit,
            note = "Purchase cancelled"
        ).getOrThrow()
    }

    suspend fun advanceRestockStatus(orderId: String): Result<RestockStatus> = runCatching {
        val storeId = currentStoreId()
        val order = getRestockOrder(storeId, orderId)
        when (order.normalizedStatus) {
            RestockStatus.DRAFT -> {
                placePurchaseOrder(orderId).getOrThrow()
                RestockStatus.ORDERED
            }
            else -> error("This purchase cannot be advanced in Phase 1.")
        }
    }

    suspend fun archiveReceivedOrder(orderId: String): Result<Unit> = archiveRestockOrder(orderId, "Archived received purchase")

    // Breakpoint: inspect purchase archive metadata before the order is hidden from active lists.
    suspend fun archiveRestockOrder(orderId: String, reason: String = "Archived purchase"): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val order = getRestockOrder(storeId, orderId)
        require(order.normalizedStatus != RestockStatus.DRAFT) { "Draft purchases can be deleted instead of archived." }
        val now = System.currentTimeMillis()
        restockOrdersCollection(storeId).document(orderId).update(
            mapOf(
                "isArchived" to true,
                "archivedAt" to Timestamp(Date(now)),
                "archivedBy" to userId,
                "archiveReason" to reason,
                "updatedBy" to userId,
                "updatedAt" to Timestamp(Date(now))
            )
        ).await()
    }

    suspend fun restoreRestockOrder(orderId: String): Result<Unit> = runCatching {
        require(orderId.isNotBlank()) { "Purchase id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        // Restore only changes archive metadata; inventory quantities and receiving history stay untouched.
        restockOrdersCollection(currentStoreId()).document(orderId).update(
            mapOf(
                "isArchived" to false,
                "archivedAt" to 0L,
                "archivedBy" to "",
                "archiveReason" to "",
                "updatedBy" to userId,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // Breakpoint: inspect purchase id, status, selected receive quantities, and linked inventory items.
    suspend fun receivePurchaseOrder(
        orderId: String,
        receivedQuantities: Map<String, Double>,
        note: String = "",
        receivedAt: Long = System.currentTimeMillis()
    ): Result<String> = runCatching {
        require(orderId.isNotBlank()) { "Purchase id is required." }
        val storeId = currentStoreId()
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val order = getRestockOrder(storeId, orderId)
        require(!order.isArchived) { "Archived purchases cannot be received." }
        require(order.normalizedStatus != RestockStatus.CANCELLED) { "Cancelled purchases cannot be received." }
        require(order.normalizedStatus != RestockStatus.RECEIVED) { "This purchase is already fully received." }

        val keyedQuantities = receivedQuantities
            .mapKeys { it.key.trim() }
            .filterValues { it > 0.0 }
        require(keyedQuantities.isNotEmpty()) { "Enter at least one received quantity." }

        val updatedItems = mutableListOf<PurchaseOrderItem>()
        val receivingItems = mutableListOf<PurchaseReceivingEventItem>()
        val transactionId = UUID.randomUUID().toString()
        val cleanNote = note.trim()

        // Breakpoint: step through each purchase line to confirm remaining quantity and receive quantity.
        order.purchaseItems.forEach { item ->
            val itemKey = item.receiveKey()
            val quantityReceivedNow = keyedQuantities[itemKey] ?: 0.0
            val remaining = item.remainingQuantity()
            require(quantityReceivedNow <= remaining) {
                "Received quantity for ${item.itemName} cannot exceed the remaining order quantity."
            }

            var resolvedInventoryItemId = item.inventoryItemId
            if (quantityReceivedNow > 0.0) {
                require(item.inventoryItemId.isNotBlank()) {
                    "${item.itemName} is not linked to an inventory item yet."
                }
                // Breakpoint: inspect the linked inventory item before stock is increased by receiving.
                val inventoryItem = resolveReceivingInventoryItem(order, item)
                resolvedInventoryItemId = inventoryItem.id
                stockMovementRepository.applyManualMovement(
                    inventoryItem = inventoryItem,
                    movementType = StockMovementType.STOCK_IN,
                    quantity = quantityReceivedNow,
                    reason = order.supplierName,
                    note = listOfNotNull(
                        "Purchase receipt ${order.fallbackOrderLabel}",
                        cleanNote.takeIf { it.isNotBlank() }
                    ).joinToString(" - "),
                    transactionAt = receivedAt,
                    counterpartyId = order.supplierId,
                    counterpartyName = order.supplierName,
                    counterpartyType = "SUPPLIER",
                    transactionId = transactionId
                ).getOrThrow()
                val afterItem = inventoryRepository.getInventoryItem(inventoryItem.id).getOrThrow()
                receivingItems += PurchaseReceivingEventItem(
                    inventoryItemId = inventoryItem.id,
                    itemName = item.itemName,
                    sku = item.sku,
                    barcode = item.barcode,
                    receivedQuantity = quantityReceivedNow,
                    unit = item.unit,
                    expiryDate = afterItem.expiryDate,
                    quantityBefore = inventoryItem.quantity,
                    quantityAfter = afterItem.quantity
                )
            }

            updatedItems += item.copy(
                inventoryItemId = resolvedInventoryItemId,
                receivedQuantity = item.receivedQuantity + quantityReceivedNow
            )
        }

        require(receivingItems.isNotEmpty()) { "Enter at least one received quantity." }

        // Breakpoint: inspect the purchase status transition after received quantities are applied.
        val nextStatus = if (updatedItems.all { it.remainingQuantity() <= 0.0 }) {
            RestockStatus.RECEIVED
        } else {
            RestockStatus.PARTIALLY_RECEIVED
        }
        val receivingEvent = PurchaseReceivingEvent(
            id = UUID.randomUUID().toString(),
            purchaseOrderId = order.id,
            purchaseOrderNumber = order.fallbackOrderLabel,
            receivingLocationId = order.receivingLocationId,
            receivingLocationName = order.receivingLocationName,
            receivedAt = receivedAt,
            receivedBy = userProfile.uid,
            receivedByName = userProfile.displayName.ifBlank { userProfile.email },
            linkedStockInTransactionId = transactionId,
            receivedItems = receivingItems
        )

        val updatedOrder = order.copy(
            status = nextStatus.name,
            updatedBy = userProfile.uid,
            updatedAt = receivedAt,
            receivedAt = if (nextStatus == RestockStatus.RECEIVED) receivedAt else order.receivedAt,
            items = updatedItems
        )

        // Read the latest purchase document before saving the receive event so older partial
        // receive records are kept even when this screen is holding a stale order snapshot.
        val orderRef = restockOrdersCollection(storeId).document(orderId)
        firestoreDataSource.db.runTransaction { transaction ->
            val currentOrder = transaction.get(orderRef).toRestockOrder()
            val mergedEvents = (currentOrder.receivingEvents + receivingEvent)
                .distinctBy { it.id.ifBlank { it.linkedStockInTransactionId } }
                .sortedBy { it.receivedAt }
            val mergedOrder = updatedOrder.copy(receivingEvents = mergedEvents)
            transaction.update(orderRef, mergedOrder.toFirestoreMap())
            AppLogger.info(
                area = "Purchases",
                event = "purchase_receiving_events_saved",
                message = "Saved purchase receive event history.",
                "order" to order.fallbackOrderLabel,
                "previousEvents" to currentOrder.receivingEvents.size,
                "savedEvents" to mergedEvents.size,
                "newEventItems" to receivingItems.size
            )
            null
        }.await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.RESTOCK_ORDER_RECEIVED,
            inventoryItemId = order.inventoryItemId,
            itemName = order.itemName,
            quantity = receivingItems.sumOf { it.receivedQuantity },
            unit = order.unit,
            note = listOfNotNull(
                "${order.fallbackOrderLabel} received",
                cleanNote.takeIf { it.isNotBlank() }
            ).joinToString(" - ")
        ).getOrThrow()
        transactionId
    }

    private suspend fun resolveReceivingInventoryItem(
        order: RestockOrder,
        item: PurchaseOrderItem
    ): InventoryItem {
        val linkedItem = inventoryRepository.getInventoryItem(item.inventoryItemId).getOrThrow()
        val receivingLocationId = order.receivingLocationId
        if (receivingLocationId.isBlank() || linkedItem.branchId == receivingLocationId) {
            return linkedItem
        }

        val receivingItem = inventoryRepository.findMatchingInventoryItemForLocation(
            branchId = receivingLocationId,
            sku = item.sku,
            barcode = item.barcode,
            name = item.itemName,
            brand = item.brand,
            category = item.category
        ).getOrThrow()

        return receivingItem ?: error(
            "Could not find ${item.itemName} in ${order.receivingLocationName}. " +
                "Please edit the purchase item after selecting the receiving location."
        )
    }

    private suspend fun savePurchaseDraft(
        orderId: String?,
        input: PurchaseDraftInput,
        status: RestockStatus
    ): Result<String> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val cleaned = input.cleaned()
        validatePurchaseInput(cleaned)
        val now = System.currentTimeMillis()
        val document = orderId?.takeIf { it.isNotBlank() }?.let { restockOrdersCollection(storeId).document(it) }
            ?: restockOrdersCollection(storeId).document()
        val orderNumber = nextPurchaseOrderNumber(storeId)
        val purchase = RestockOrder(
            id = document.id,
            orderNumber = orderNumber,
            supplierId = cleaned.supplierId,
            supplierNameRaw = cleaned.supplierName,
            receivingLocationId = cleaned.receivingLocationId,
            receivingLocationName = cleaned.receivingLocationName,
            status = status.name,
            orderDate = cleaned.orderDate,
            expectedDeliveryDate = cleaned.expectedDeliveryDate,
            memo = cleaned.memo,
            createdBy = userId,
            createdByName = userProfile.displayName.ifBlank { userProfile.email },
            createdAt = now,
            updatedBy = userId,
            updatedAt = now,
            itemNameRaw = cleaned.items.firstOrNull()?.itemName.orEmpty(),
            quantityRaw = cleaned.items.sumOf { it.orderedQuantity },
            unitRaw = cleaned.items.firstOrNull()?.unit.orEmpty(),
            inventoryItemIdRaw = cleaned.items.firstOrNull()?.inventoryItemId,
            items = cleaned.items,
            sortOrder = Int.MAX_VALUE - ((now / 1000L) % Int.MAX_VALUE).toInt()
        )
        document.set(purchase.toFirestoreMap()).await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.RESTOCK_ORDER_CREATED,
            inventoryItemId = purchase.inventoryItemId,
            itemName = purchase.itemName,
            quantity = purchase.quantity,
            unit = purchase.unit,
            note = "Purchase draft created"
        ).getOrThrow()
        document.id
    }

    private fun validatePurchaseInput(input: PurchaseDraftInput) {
        require(input.supplierName.isNotBlank()) { "Supplier is required." }
        require(input.receivingLocationName.isNotBlank()) { "Receiving location is required." }
        require(input.orderDate > 0L) { "Order date is required." }
        require(input.items.isNotEmpty()) { "Add at least one item." }
        require(input.expectedDeliveryDate == 0L || input.expectedDeliveryDate >= input.orderDate) {
            "Expected delivery cannot be earlier than order date."
        }
        input.items.forEach { item ->
            require(item.itemName.isNotBlank()) { "Each item must have a name." }
            require(item.orderedQuantity > 0.0) { "Each item must have a quantity greater than 0." }
            require(item.unit.isNotBlank()) { "Each item must have a unit." }
        }
    }

    private suspend fun nextPurchaseOrderNumber(storeId: String): String {
        val storeReference = firestoreDataSource.db.collection(Constants.STORES_COLLECTION).document(storeId)
        val sequence = firestoreDataSource.db.runTransaction { transaction ->
            val snapshot = transaction.get(storeReference)
            // A store-level counter keeps order numbers unique and readable while document ids stay
            // random Firestore ids for safe writes and easier future migrations.
            val next = ((snapshot.getLong(PURCHASE_ORDER_COUNTER_FIELD) ?: 0L) + 1L).coerceAtLeast(1L)
            transaction.update(storeReference, PURCHASE_ORDER_COUNTER_FIELD, next)
            next
        }.await()
        AppLogger.info(
            area = "Purchases",
            event = "purchase_counter_updated",
            message = "Purchase order counter updated.",
            "counter" to sequence
        )
        return "PO-${sequence.toString().padStart(6, '0')}"
    }

    private suspend fun currentStoreId(): String =
        currentUserProfile()?.currentStoreId ?: error("No store selected.")

    private suspend fun currentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .toObject(UserProfile::class.java)
    }

    private fun restockOrdersCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.RESTOCK_ORDERS_COLLECTION)

    private suspend fun getRestockOrder(storeId: String, orderId: String): RestockOrder {
        val snapshot = restockOrdersCollection(storeId).document(orderId).get().await()
        if (!snapshot.exists()) error("Purchase not found.")
        return snapshot.toRestockOrder()
    }

    private fun DocumentSnapshot.toRestockOrder(): RestockOrder {
        val savedItems = (get("items") as? List<*>)?.mapNotNull { row ->
            val data = row as? Map<*, *> ?: return@mapNotNull null
            PurchaseOrderItem(
                inventoryItemId = data.string("inventoryItemId"),
                itemName = data.string("itemName"),
                sku = data.string("sku"),
                barcode = data.string("barcode"),
                brand = data.string("brand"),
                category = data.string("category"),
                orderedQuantity = data.number("orderedQuantity"),
                receivedQuantity = data.number("receivedQuantity"),
                unit = data.string("unit"),
                unitCost = data.number("unitCost"),
                imageUrl = data.string("imageUrl"),
                supplierId = data.string("supplierId"),
                supplierName = data.string("supplierName")
            )
        }.orEmpty()

        val savedReceivingEvents = (get("receivingEvents") as? List<*>)?.mapNotNull { row ->
            val data = row as? Map<*, *> ?: return@mapNotNull null
            val receivedItems = (data["receivedItems"] as? List<*>)?.mapNotNull { itemRow ->
                val itemData = itemRow as? Map<*, *> ?: return@mapNotNull null
                PurchaseReceivingEventItem(
                    inventoryItemId = itemData.string("inventoryItemId"),
                    itemName = itemData.string("itemName"),
                    sku = itemData.string("sku"),
                    barcode = itemData.string("barcode"),
                    receivedQuantity = itemData.number("receivedQuantity"),
                    unit = itemData.string("unit"),
                    expiryDate = itemData.long("expiryDate"),
                    quantityBefore = itemData.number("quantityBefore"),
                    quantityAfter = itemData.number("quantityAfter")
                )
            }.orEmpty()
            PurchaseReceivingEvent(
                id = data.string("id"),
                purchaseOrderId = data.string("purchaseOrderId"),
                purchaseOrderNumber = data.string("purchaseOrderNumber"),
                receivingLocationId = data.string("receivingLocationId"),
                receivingLocationName = data.string("receivingLocationName"),
                receivedAt = data.long("receivedAt"),
                receivedBy = data.string("receivedBy"),
                receivedByName = data.string("receivedByName"),
                linkedStockInTransactionId = data.string("linkedStockInTransactionId"),
                receivedItems = receivedItems
            )
        }.orEmpty()

        val rawStatus = getString("status").orEmpty()
        return RestockOrder(
            id = getString("id").orEmpty().ifBlank { id },
            orderNumber = getString("orderNumber").orEmpty(),
            supplierId = getString("supplierId").orEmpty(),
            supplierNameRaw = getString("supplierName").orEmpty(),
            receivingLocationId = getString("receivingLocationId").orEmpty(),
            receivingLocationName = getString("receivingLocationName").orEmpty(),
            status = rawStatus.ifBlank { RestockStatus.TO_ORDER.name },
            orderDate = dateMillis("orderDate").takeIf { it > 0L } ?: dateMillis("createdAt"),
            expectedDeliveryDate = dateMillis("expectedDeliveryDate"),
            memo = getString("memo").orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            createdByName = getString("createdByName").orEmpty(),
            createdAt = dateMillis("createdAt"),
            updatedBy = getString("updatedBy").orEmpty(),
            updatedAt = dateMillis("updatedAt"),
            orderedAt = dateMillis("orderedAt"),
            inTransitAt = dateMillis("inTransitAt"),
            receivedAt = dateMillis("receivedAt"),
            cancelledAt = dateMillis("cancelledAt"),
            sortOrder = (get("sortOrder") as? Number)?.toInt() ?: 0,
            isArchived = getBoolean("isArchived") ?: false,
            archivedAt = dateMillis("archivedAt"),
            archivedBy = getString("archivedBy").orEmpty(),
            archiveReason = getString("archiveReason").orEmpty(),
            inventoryItemIdRaw = getString("inventoryItemId"),
            itemNameRaw = getString("itemName").orEmpty(),
            quantityRaw = (get("quantity") as? Number)?.toDouble() ?: 0.0,
            unitRaw = getString("unit").orEmpty(),
            items = savedItems,
            receivingEvents = savedReceivingEvents
        )
    }

    private fun RestockOrder.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "orderNumber" to orderNumber,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "receivingLocationId" to receivingLocationId,
        "receivingLocationName" to receivingLocationName,
        "status" to status,
        "orderDate" to orderDate.toFirestoreDateValue(),
        "expectedDeliveryDate" to expectedDeliveryDate.toFirestoreDateValue(),
        "memo" to memo,
        "createdBy" to createdBy,
        "createdByName" to createdByName,
        "createdAt" to createdAt.toFirestoreDateValue(),
        "updatedBy" to updatedBy,
        "updatedAt" to updatedAt.toFirestoreDateValue(),
        "orderedAt" to orderedAt.toFirestoreDateValue(),
        "inTransitAt" to inTransitAt.toFirestoreDateValue(),
        "receivedAt" to receivedAt.toFirestoreDateValue(),
        "cancelledAt" to cancelledAt.toFirestoreDateValue(),
        "sortOrder" to sortOrder,
        "isArchived" to isArchived,
        "archivedAt" to archivedAt.toFirestoreDateValue(),
        "archivedBy" to archivedBy,
        "archiveReason" to archiveReason,
        "inventoryItemId" to inventoryItemId,
        "itemName" to itemName,
        "quantity" to quantity,
        "unit" to unit,
        "receivingEvents" to receivingEvents.map { event -> event.toFirestoreMap() },
        "items" to purchaseItems.map { item ->
            mapOf(
                "inventoryItemId" to item.inventoryItemId,
                "itemName" to item.itemName,
                "sku" to item.sku,
                "barcode" to item.barcode,
                "brand" to item.brand,
                "category" to item.category,
                "orderedQuantity" to item.orderedQuantity,
                "receivedQuantity" to item.receivedQuantity,
                "unit" to item.unit,
                "unitCost" to item.unitCost,
                "imageUrl" to item.imageUrl,
                "supplierId" to item.supplierId,
                "supplierName" to item.supplierName
            )
        }
    )

    private fun PurchaseReceivingEvent.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "purchaseOrderId" to purchaseOrderId,
        "purchaseOrderNumber" to purchaseOrderNumber,
        "receivingLocationId" to receivingLocationId,
        "receivingLocationName" to receivingLocationName,
        "receivedAt" to receivedAt.toFirestoreDateValue(),
        "receivedBy" to receivedBy,
        "receivedByName" to receivedByName,
        "linkedStockInTransactionId" to linkedStockInTransactionId,
        "receivedItems" to receivedItems.map { item ->
            mapOf(
                "inventoryItemId" to item.inventoryItemId,
                "itemName" to item.itemName,
                "sku" to item.sku,
                "barcode" to item.barcode,
                "receivedQuantity" to item.receivedQuantity,
                "unit" to item.unit,
                "expiryDate" to item.expiryDate.toFirestoreDateValue(),
                "quantityBefore" to item.quantityBefore,
                "quantityAfter" to item.quantityAfter
            )
        }
    )

    private fun PurchaseDraftInput.cleaned(): PurchaseDraftInput = copy(
        supplierId = supplierId.trim(),
        supplierName = supplierName.trim(),
        receivingLocationId = receivingLocationId.trim(),
        receivingLocationName = receivingLocationName.trim(),
        memo = memo.trim(),
        items = items.map { item ->
            item.copy(
                inventoryItemId = item.inventoryItemId.trim(),
                itemName = item.itemName.trim(),
                sku = item.sku.trim(),
                barcode = item.barcode.trim(),
                brand = item.brand.trim(),
                category = item.category.trim(),
                unit = item.unit.trim(),
                supplierId = item.supplierId.trim(),
                supplierName = item.supplierName.trim()
            )
        }
    )

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: ""

    private fun Map<*, *>.number(key: String): Double = (this[key] as? Number)?.toDouble() ?: 0.0

    private fun Map<*, *>.long(key: String): Long =
        when (val value = this[key]) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun DocumentSnapshot.dateMillis(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun Long.toFirestoreDateValue(): Any? =
        if (this > 0L) Timestamp(Date(this)) else null

    companion object {
        private const val PURCHASE_ORDER_COUNTER_FIELD = "purchaseOrderCounter"
    }
}

private fun PurchaseOrderItem.receiveKey(): String =
    inventoryItemId.ifBlank {
        listOf(itemName.trim(), sku.trim(), barcode.trim()).joinToString("|")
    }
