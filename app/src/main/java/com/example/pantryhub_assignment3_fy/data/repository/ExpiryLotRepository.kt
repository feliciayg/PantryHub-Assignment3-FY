package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.RestockStatus
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.example.pantryhub_assignment3_fy.util.InventoryStatusCalculator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.tasks.await

data class LotMutationResult(
    val quantityBefore: Double,
    val quantityAfter: Double,
    val affectedLots: List<ExpiryLot>
)

data class LotTransferResult(
    val sourceBefore: Double,
    val sourceAfter: Double,
    val destinationBefore: Double,
    val destinationAfter: Double,
    val transferredLots: List<ExpiryLot>
)

/**
 * Owns expiry-lot persistence and FEFO allocation.
 *
 * Deterministic lot ids make same-expiry stock-in operations merge safely and make
 * legacy initialization idempotent. The parent inventory quantity remains the summary total.
 */
class ExpiryLotRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    suspend fun loadLots(inventoryItem: InventoryItem): List<ExpiryLot> {
        if (inventoryItem.id.isBlank()) return emptyList()
        val storeId = currentStoreId()
        val documents = expiryLotsCollection(storeId, inventoryItem.id).get().await().documents
        val persisted = documents.map { it.toExpiryLot(inventoryItem.id) }.filter { it.quantity > 0.0 }
        if (persisted.isNotEmpty() || inventoryItem.quantity <= 0.0) return persisted.sortedFefo()

        // Old records remain readable before their first mutation; no Firestore write occurs on read.
        return listOf(
            ExpiryLot(
                id = lotId(inventoryItem.expiryDate.takeIf { it > 0L }),
                inventoryItemId = inventoryItem.id,
                branchId = inventoryItem.branchId,
                branchName = inventoryItem.branchName,
                expiryDate = inventoryItem.expiryDate.takeIf { it > 0L },
                quantity = inventoryItem.quantity,
                receivedAt = inventoryItem.createdAt,
                createdBy = inventoryItem.createdBy,
                createdAt = inventoryItem.createdAt,
                updatedAt = inventoryItem.updatedAt
            )
        )
    }

    suspend fun createItemWithInitialLot(itemReference: DocumentReference, inventoryItem: InventoryItem) {
        val lot = ExpiryLot(
            id = lotId(inventoryItem.expiryDate.takeIf { it > 0L }),
            inventoryItemId = inventoryItem.id,
            branchId = inventoryItem.branchId,
            branchName = inventoryItem.branchName,
            expiryDate = inventoryItem.expiryDate.takeIf { it > 0L },
            quantity = inventoryItem.quantity,
            receivedAt = inventoryItem.createdAt,
            createdBy = inventoryItem.createdBy,
            createdAt = inventoryItem.createdAt,
            updatedAt = inventoryItem.updatedAt
        )
        firestoreDataSource.db.runTransaction { transaction ->
            transaction.set(itemReference, inventoryItem.copy(expiryLotsInitialized = true).toParentMap())
            if (lot.quantity > 0.0) {
                transaction.set(itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(lot.id), lot.toMap())
            }
        }.await()
    }

    /** Corrects one branch-level expiry batch without changing the item's total quantity. */
    suspend fun updateExpiryDate(
        inventoryItemId: String,
        currentExpiryDate: Long?,
        newExpiryDate: Long
    ): Result<Unit> = runCatching {
        require(inventoryItemId.isNotBlank()) { "Inventory item is required." }
        require(newExpiryDate > 0L) { "Select a valid expiry date." }

        val storeId = currentStoreId()
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItemId)
        val initialSnapshot = itemReference.get().await()
        require(initialSnapshot.exists()) { "Inventory item not found." }
        val initialItem = initialSnapshot.toInventoryItem()
        val effectiveLots = loadLots(initialItem)
        val sourceId = lotId(currentExpiryDate)
        val targetId = lotId(newExpiryDate)
        if (sourceId == targetId) return@runCatching

        val knownLotsById = effectiveLots.associateBy { it.id }
        val lotIds = (effectiveLots.map { it.id } + sourceId + targetId).distinct()
        val lotReferences = lotIds.associateWith {
            itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it)
        }
        val now = System.currentTimeMillis()

        firestoreDataSource.db.runTransaction { transaction ->
            val itemSnapshot = transaction.get(itemReference)
            require(itemSnapshot.exists()) { "Inventory item not found." }
            val item = itemSnapshot.toInventoryItem()
            val lots = lotReferences.mapValues { (id, reference) ->
                val snapshot = transaction.get(reference)
                snapshot.takeIf { it.exists() }?.toExpiryLot(item.id) ?: knownLotsById[id]
            }.values.filterNotNull().associateBy { it.id }.toMutableMap()

            val source = lots[sourceId] ?: error("Expiry batch no longer exists.")
            require(source.quantity > 0.0) { "Expiry batch has no stock." }
            val existingTarget = lots[targetId]
            val updatedTarget = source.copy(
                id = targetId,
                expiryDate = newExpiryDate,
                quantity = source.quantity + (existingTarget?.quantity ?: 0.0),
                receivedAt = listOf(source.receivedAt, existingTarget?.receivedAt ?: 0L)
                    .filter { it > 0L }
                    .minOrNull() ?: now,
                sourceTransactionId = existingTarget?.sourceTransactionId
                    ?.takeIf { it.isNotBlank() }
                    ?: source.sourceTransactionId,
                createdAt = listOf(source.createdAt, existingTarget?.createdAt ?: 0L)
                    .filter { it > 0L }
                    .minOrNull() ?: now,
                updatedAt = now
            )

            lots.remove(sourceId)
            lots[targetId] = updatedTarget
            transaction.delete(lotReferences.getValue(sourceId))
            transaction.set(lotReferences.getValue(targetId), updatedTarget.toMap())

            val nearestExpiry = lots.values
                .filter { it.quantity > 0.0 }
                .mapNotNull { it.expiryDate }
                .minOrNull()
            transaction.update(
                itemReference,
                parentQuantityUpdates(item, item.quantity, nearestExpiry, now)
            )
        }.await()

        AppLogger.info(
            area = "Expiry",
            event = "expiry_date_corrected",
            message = "Expiry batch date corrected without changing stock quantity.",
            "itemId" to inventoryItemId,
            "from" to (currentExpiryDate ?: 0L),
            "to" to newExpiryDate
        )
    }

    suspend fun addStock(
        inventoryItemId: String,
        quantity: Double,
        expiryDate: Long?,
        sourceTransactionId: String = ""
    ): LotMutationResult {
        require(quantity > 0.0) { "Stock-in quantity must be greater than 0." }
        val storeId = currentStoreId()
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItemId)
        val targetLotReference = itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(lotId(expiryDate))
        val initialSnapshot = itemReference.get().await()
        require(initialSnapshot.exists()) { "Inventory item not found." }
        val initialItem = initialSnapshot.toInventoryItem()
        val effectiveLots = loadLots(initialItem)
        val knownLotsById = effectiveLots.associateBy { it.id }
        val lotReferences = (effectiveLots.map { it.id } + lotId(expiryDate))
            .distinct()
            .associateWith { itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it) }
        val now = System.currentTimeMillis()
        var result: LotMutationResult? = null

        firestoreDataSource.db.runTransaction { transaction ->
            val itemSnapshot = transaction.get(itemReference)
            require(itemSnapshot.exists()) { "Inventory item not found." }
            val item = itemSnapshot.toInventoryItem()
            val lots = lotReferences.mapValues { (id, reference) ->
                val snapshot = transaction.get(reference)
                snapshot.takeIf { it.exists() }?.toExpiryLot(item.id) ?: knownLotsById[id]
            }.values.filterNotNull().associateBy { it.id }.toMutableMap()

            val targetId = lotId(expiryDate)
            val existingTarget = lots[targetId]
            val updatedTarget = ExpiryLot(
                id = targetId,
                inventoryItemId = item.id,
                branchId = item.branchId,
                branchName = item.branchName,
                expiryDate = expiryDate?.takeIf { it > 0L },
                quantity = (existingTarget?.quantity ?: 0.0) + quantity,
                receivedAt = existingTarget?.receivedAt?.takeIf { it > 0L } ?: now,
                sourceTransactionId = existingTarget?.sourceTransactionId
                    ?.takeIf { it.isNotBlank() }
                    ?: sourceTransactionId,
                createdBy = existingTarget?.createdBy?.takeIf { it.isNotBlank() } ?: authManager.currentUserId.orEmpty(),
                createdAt = existingTarget?.createdAt?.takeIf { it > 0L } ?: now,
                updatedAt = now
            )
            lots[targetId] = updatedTarget
            lots.values.forEach { lot ->
                transaction.set(
                    itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(lot.id),
                    lot.toMap()
                )
            }
            val quantityAfter = item.quantity + quantity
            val nearestExpiry = lots.values.filter { it.quantity > 0.0 }.mapNotNull { it.expiryDate }.minOrNull()
            transaction.update(
                itemReference,
                parentQuantityUpdates(item, quantityAfter, nearestExpiry, now)
            )
            result = LotMutationResult(item.quantity, quantityAfter, listOf(updatedTarget))
        }.await()
        return (result ?: error("Could not add expiry stock.")).also { mutation ->
            AppLogger.info(
                area = "Expiry",
                event = "expiry_lot_saved",
                message = "Expiry lot saved for stock in.",
                "quantity" to quantity,
                "expiryDate" to (expiryDate ?: "none"),
                "before" to mutation.quantityBefore,
                "after" to mutation.quantityAfter
            )
        }
    }

    suspend fun receiveRestockStock(
        inventoryItemId: String,
        quantity: Double,
        orderReference: DocumentReference
    ): LotMutationResult {
        require(quantity > 0.0) { "Received quantity must be greater than 0." }
        val storeId = currentStoreId()
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItemId)
        val targetLotReference = itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(NO_EXPIRY_LOT_ID)
        val initialSnapshot = itemReference.get().await()
        require(initialSnapshot.exists()) { "Linked inventory item no longer exists." }
        val initialItem = initialSnapshot.toInventoryItem()
        val effectiveLots = loadLots(initialItem)
        val knownLotsById = effectiveLots.associateBy { it.id }
        val lotReferences = (effectiveLots.map { it.id } + NO_EXPIRY_LOT_ID)
            .distinct()
            .associateWith { itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it) }
        val now = System.currentTimeMillis()
        var result: LotMutationResult? = null
        firestoreDataSource.db.runTransaction { transaction ->
            val orderSnapshot = transaction.get(orderReference)
            require(orderSnapshot.getString("status") != RestockStatus.RECEIVED.name) {
                "This restock order has already been received."
            }
            val itemSnapshot = transaction.get(itemReference)
            require(itemSnapshot.exists()) { "Linked inventory item no longer exists." }
            val item = itemSnapshot.toInventoryItem()
            val lots = lotReferences.mapValues { (id, reference) ->
                val snapshot = transaction.get(reference)
                snapshot.takeIf { it.exists() }?.toExpiryLot(item.id) ?: knownLotsById[id]
            }.values.filterNotNull().associateBy { it.id }.toMutableMap()
            val existingNoExpiry = lots[NO_EXPIRY_LOT_ID]
            val updatedLot = ExpiryLot(
                id = NO_EXPIRY_LOT_ID,
                inventoryItemId = item.id,
                branchId = item.branchId,
                branchName = item.branchName,
                expiryDate = null,
                quantity = (existingNoExpiry?.quantity ?: 0.0) + quantity,
                receivedAt = existingNoExpiry?.receivedAt?.takeIf { it > 0L } ?: now,
                sourceTransactionId = existingNoExpiry?.sourceTransactionId?.takeIf { it.isNotBlank() } ?: orderReference.id,
                createdBy = existingNoExpiry?.createdBy?.takeIf { it.isNotBlank() } ?: authManager.currentUserId.orEmpty(),
                createdAt = existingNoExpiry?.createdAt?.takeIf { it > 0L } ?: now,
                updatedAt = now
            )
            lots[NO_EXPIRY_LOT_ID] = updatedLot
            lots.values.forEach { lot ->
                transaction.set(
                    itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(lot.id),
                    lot.toMap()
                )
            }
            val quantityAfter = item.quantity + quantity
            val nearestExpiry = lots.values.mapNotNull { it.expiryDate }.minOrNull()
            transaction.update(itemReference, parentQuantityUpdates(item, quantityAfter, nearestExpiry, now))
            transaction.update(
                orderReference,
                mapOf(
                    "status" to RestockStatus.RECEIVED.name,
                    "receivedAt" to Timestamp(Date(now)),
                    "updatedBy" to authManager.currentUserId.orEmpty(),
                    "updatedAt" to Timestamp(Date(now))
                )
            )
            result = LotMutationResult(item.quantity, quantityAfter, listOf(updatedLot))
        }.await()
        return result ?: error("Could not receive restock stock.")
    }

    suspend fun deductStock(inventoryItemId: String, quantity: Double): LotMutationResult {
        require(quantity > 0.0) { "Stock-out quantity must be greater than 0." }
        val storeId = currentStoreId()
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItemId)
        val initialItemSnapshot = itemReference.get().await()
        require(initialItemSnapshot.exists()) { "Inventory item not found." }
        val initialItem = initialItemSnapshot.toInventoryItem()
        val effectiveLots = loadLots(initialItem)
        require(effectiveLots.sumOf { it.quantity } + EPSILON >= quantity) {
            "Movement cannot reduce stock below 0."
        }
        val lotReferences = effectiveLots.associateWith {
            itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it.id)
        }
        val now = System.currentTimeMillis()
        var result: LotMutationResult? = null

        firestoreDataSource.db.runTransaction { transaction ->
            val item = transaction.get(itemReference).toInventoryItem()
            val currentLots = effectiveLots.map { fallback ->
                val snapshot = transaction.get(lotReferences.getValue(fallback))
                snapshot.takeIf { it.exists() }?.toExpiryLot(item.id) ?: fallback
            }.filter { it.quantity > 0.0 }.sortedFefo()
            require(currentLots.sumOf { it.quantity } + EPSILON >= quantity) {
                "Movement cannot reduce stock below 0."
            }

            var remaining = quantity
            val deductions = mutableListOf<ExpiryLot>()
            val resultingLots = mutableListOf<ExpiryLot>()
            currentLots.forEach { lot ->
                val deducted = minOf(lot.quantity, remaining)
                remaining -= deducted
                val nextQuantity = (lot.quantity - deducted).coerceAtLeast(0.0)
                if (deducted > 0.0) deductions += lot.copy(quantity = deducted, updatedAt = now)
                val reference = lotReferences.getValue(lot)
                if (nextQuantity <= EPSILON) {
                    transaction.delete(reference)
                } else {
                    val updatedLot = lot.copy(quantity = nextQuantity, updatedAt = now)
                    resultingLots += updatedLot
                    transaction.set(reference, updatedLot.toMap())
                }
            }
            val quantityAfter = (item.quantity - quantity).coerceAtLeast(0.0)
            val nearestExpiry = resultingLots.mapNotNull { it.expiryDate }.minOrNull()
            transaction.update(itemReference, parentQuantityUpdates(item, quantityAfter, nearestExpiry, now))
            result = LotMutationResult(item.quantity, quantityAfter, deductions)
        }.await()
        return (result ?: error("Could not deduct expiry stock.")).also { mutation ->
            AppLogger.info(
                area = "Expiry",
                event = "expiry_lot_deducted",
                message = "Expiry lots deducted using FEFO.",
                "quantity" to quantity,
                "before" to mutation.quantityBefore,
                "after" to mutation.quantityAfter,
                "lots" to mutation.affectedLots.size
            )
        }
    }

    suspend fun deductExpiredStock(inventoryItemId: String, quantity: Double): LotMutationResult {
        require(quantity > 0.0) { "Expired quantity must be greater than 0." }
        val storeId = currentStoreId()
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItemId)
        val initialItemSnapshot = itemReference.get().await()
        require(initialItemSnapshot.exists()) { "Inventory item not found." }
        val initialItem = initialItemSnapshot.toInventoryItem()
        val effectiveLots = loadLots(initialItem)
        val today = LocalDate.now()
        val expiredLots = effectiveLots.filter { lot ->
            lot.quantity > 0.0 && lot.expiryDate?.let { expiry ->
                Instant.ofEpochMilli(expiry).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(today)
            } == true
        }
        require(expiredLots.sumOf { it.quantity } + EPSILON >= quantity) {
            "Expired quantity cannot be more than the expired stock available."
        }
        val lotReferences = effectiveLots.associateWith {
            itemReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it.id)
        }
        val expiredLotIds = expiredLots.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        var result: LotMutationResult? = null

        firestoreDataSource.db.runTransaction { transaction ->
            val item = transaction.get(itemReference).toInventoryItem()
            val currentLots = effectiveLots.map { fallback ->
                val snapshot = transaction.get(lotReferences.getValue(fallback))
                snapshot.takeIf { it.exists() }?.toExpiryLot(item.id) ?: fallback
            }.filter { it.quantity > 0.0 }.sortedFefo()
            val currentExpiredLots = currentLots.filter { it.id in expiredLotIds }
            require(currentExpiredLots.sumOf { it.quantity } + EPSILON >= quantity) {
                "Expired quantity cannot be more than the expired stock available."
            }

            var remaining = quantity
            val deductions = mutableListOf<ExpiryLot>()
            val resultingLots = mutableListOf<ExpiryLot>()
            currentLots.forEach { lot ->
                val deductible = if (lot.id in expiredLotIds) minOf(lot.quantity, remaining) else 0.0
                remaining -= deductible
                val nextQuantity = (lot.quantity - deductible).coerceAtLeast(0.0)
                val reference = lotReferences.getValue(lot)
                if (deductible > 0.0) deductions += lot.copy(quantity = deductible, updatedAt = now)
                when {
                    nextQuantity <= EPSILON -> transaction.delete(reference)
                    deductible > 0.0 -> {
                        val updatedLot = lot.copy(quantity = nextQuantity, updatedAt = now)
                        resultingLots += updatedLot
                        transaction.set(reference, updatedLot.toMap())
                    }
                    else -> resultingLots += lot
                }
            }
            val quantityAfter = (item.quantity - quantity).coerceAtLeast(0.0)
            val nearestExpiry = resultingLots.mapNotNull { it.expiryDate }.minOrNull()
            transaction.update(itemReference, parentQuantityUpdates(item, quantityAfter, nearestExpiry, now))
            result = LotMutationResult(item.quantity, quantityAfter, deductions)
        }.await()
        return result ?: error("Could not deduct expired stock.")
    }

    suspend fun transferStock(
        sourceItem: InventoryItem,
        destinationItem: InventoryItem,
        quantity: Double
    ): LotTransferResult {
        require(quantity > 0.0) { "Transfer quantity must be greater than 0." }
        val storeId = currentStoreId()
        val sourceReference = inventoryItemsCollection(storeId).document(sourceItem.id)
        val destinationReference = inventoryItemsCollection(storeId).document(destinationItem.id)
        val effectiveSourceLots = loadLots(sourceItem)
        require(effectiveSourceLots.sumOf { it.quantity } + EPSILON >= quantity) {
            "Source item does not have enough stock."
        }
        val sourceLotReferences = effectiveSourceLots.associateWith {
            sourceReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it.id)
        }
        val destinationLots = if (destinationItem.quantity > 0.0) loadLots(destinationItem) else emptyList()
        val destinationLotsById = destinationLots.associateBy { it.id }
        val possibleTransferLotIds = effectiveSourceLots.map { it.id }
        val destinationLotReferences = (destinationLots.map { it.id } + possibleTransferLotIds)
            .distinct()
            .associateWith { destinationReference.collection(Constants.EXPIRY_LOTS_COLLECTION).document(it) }
        val now = System.currentTimeMillis()
        var result: LotTransferResult? = null

        firestoreDataSource.db.runTransaction { transaction ->
            val currentSource = transaction.get(sourceReference).toInventoryItem()
            val destinationSnapshot = transaction.get(destinationReference)
            val currentDestination = if (destinationSnapshot.exists()) {
                destinationItem.copy(
                    quantity = (destinationSnapshot.get("quantity") as? Number)?.toDouble() ?: destinationItem.quantity,
                    expiryDate = destinationSnapshot.dateMillisOrNull("expiryDate") ?: 0L,
                    expiryLotsInitialized = destinationSnapshot.getBoolean("expiryLotsInitialized") ?: false
                )
            } else {
                destinationItem.copy(quantity = 0.0, expiryDate = 0L, expiryLotsInitialized = true)
            }
            val sourceLots = effectiveSourceLots.map { fallback ->
                val snapshot = transaction.get(sourceLotReferences.getValue(fallback))
                snapshot.takeIf { it.exists() }?.toExpiryLot(sourceItem.id) ?: fallback
            }.filter { it.quantity > 0.0 }.sortedFefo()
            val currentDestinationLots = destinationLotReferences.mapValues { (id, reference) ->
                val snapshot = transaction.get(reference)
                snapshot.takeIf { it.exists() }?.toExpiryLot(destinationItem.id) ?: destinationLotsById[id]
            }.values.filterNotNull().associateBy { it.id }.toMutableMap()

            var remaining = quantity
            val transferred = mutableListOf<ExpiryLot>()
            val sourceRemainingLots = mutableListOf<ExpiryLot>()
            val sourceLotUpdates = mutableListOf<Pair<DocumentReference, ExpiryLot?>>()
            sourceLots.forEach { lot ->
                val moved = minOf(lot.quantity, remaining)
                remaining -= moved
                val nextSourceQuantity = (lot.quantity - moved).coerceAtLeast(0.0)
                val sourceLotReference = sourceLotReferences.getValue(lot)
                if (nextSourceQuantity <= EPSILON) {
                    sourceLotUpdates += sourceLotReference to null
                } else {
                    val updatedSourceLot = lot.copy(quantity = nextSourceQuantity, updatedAt = now)
                    sourceRemainingLots += updatedSourceLot
                    sourceLotUpdates += sourceLotReference to updatedSourceLot
                }
                if (moved > 0.0) {
                    val movedLot = lot.copy(
                        inventoryItemId = destinationItem.id,
                        branchId = destinationItem.branchId,
                        branchName = destinationItem.branchName,
                        quantity = moved,
                        createdAt = now,
                        updatedAt = now
                    )
                    transferred += movedLot
                }
            }

            transferred.forEach { movedLot ->
                val currentLot = currentDestinationLots[movedLot.id]
                currentDestinationLots[movedLot.id] = movedLot.copy(
                    quantity = (currentLot?.quantity ?: 0.0) + movedLot.quantity,
                    createdAt = currentLot?.createdAt?.takeIf { it > 0L } ?: now,
                    updatedAt = now
                )
            }

            val sourceAfter = (currentSource.quantity - quantity).coerceAtLeast(0.0)
            val destinationAfter = currentDestination.quantity + quantity
            val sourceNearest = sourceRemainingLots.mapNotNull { it.expiryDate }.minOrNull()
            val destinationNearest = currentDestinationLots.values
                .filter { it.quantity > 0.0 }
                .mapNotNull { it.expiryDate }
                .minOrNull()
            sourceLotUpdates.forEach { (reference, lot) ->
                if (lot == null) transaction.delete(reference) else transaction.set(reference, lot.toMap())
            }
            currentDestinationLots.values.forEach { lot ->
                transaction.set(destinationLotReferences.getValue(lot.id), lot.toMap())
            }
            transaction.update(sourceReference, parentQuantityUpdates(currentSource, sourceAfter, sourceNearest, now))
            if (destinationSnapshot.exists()) {
                transaction.update(destinationReference, parentQuantityUpdates(currentDestination, destinationAfter, destinationNearest, now))
            } else {
                transaction.set(
                    destinationReference,
                    destinationItem.copy(
                        quantity = destinationAfter,
                        expiryDate = destinationNearest ?: 0L,
                        expiryLotsInitialized = true,
                        updatedAt = now
                    ).toParentMap()
                )
            }
            result = LotTransferResult(
                sourceBefore = currentSource.quantity,
                sourceAfter = sourceAfter,
                destinationBefore = currentDestination.quantity,
                destinationAfter = destinationAfter,
                transferredLots = transferred
            )
        }.await()
        return result ?: error("Could not transfer expiry stock.")
    }

    private fun parentQuantityUpdates(
        item: InventoryItem,
        quantity: Double,
        nearestExpiry: Long?,
        now: Long
    ): Map<String, Any> {
        val status = InventoryStatusCalculator.calculate(item.copy(quantity = quantity, expiryDate = nearestExpiry ?: 0L)).name
        return mapOf(
            "quantity" to quantity,
            "expiryDate" to (nearestExpiry?.let { Timestamp(Date(it)) } ?: 0L),
            "expiryLotsInitialized" to true,
            "status" to status,
            "updatedBy" to authManager.currentUserId.orEmpty(),
            "updatedAt" to Timestamp(Date(now))
        )
    }

    private fun legacyLot(item: InventoryItem): ExpiryLot = ExpiryLot(
        id = lotId(item.expiryDate.takeIf { it > 0L }),
        inventoryItemId = item.id,
        branchId = item.branchId,
        branchName = item.branchName,
        expiryDate = item.expiryDate.takeIf { it > 0L },
        quantity = item.quantity,
        receivedAt = item.createdAt,
        createdBy = item.createdBy,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt
    )

    private fun lotId(expiryDate: Long?): String {
        if (expiryDate == null || expiryDate <= 0L) return NO_EXPIRY_LOT_ID
        val date = Instant.ofEpochMilli(expiryDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return "expiry_${date.format(LOT_ID_DATE)}"
    }

    private fun List<ExpiryLot>.sortedFefo(): List<ExpiryLot> = sortedWith(
        compareBy<ExpiryLot> { it.expiryDate == null }
            .thenBy { it.expiryDate ?: Long.MAX_VALUE }
            .thenBy { it.createdAt }
    )

    private suspend fun currentStoreId(): String {
        val uid = authManager.currentUserId ?: error("You must be logged in.")
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .getString("currentStoreId")
            ?.takeIf { it.isNotBlank() }
            ?: error("No store selected.")
    }

    private fun inventoryItemsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun expiryLotsCollection(storeId: String, inventoryItemId: String) =
        inventoryItemsCollection(storeId).document(inventoryItemId)
            .collection(Constants.EXPIRY_LOTS_COLLECTION)

    private fun DocumentSnapshot.toExpiryLot(inventoryItemId: String): ExpiryLot = ExpiryLot(
        id = getString("id").orEmpty().ifBlank { id },
        inventoryItemId = getString("inventoryItemId").orEmpty().ifBlank { inventoryItemId },
        branchId = getString("branchId").orEmpty(),
        branchName = getString("branchName").orEmpty(),
        expiryDate = dateMillisOrNull("expiryDate"),
        quantity = (get("quantity") as? Number)?.toDouble() ?: 0.0,
        receivedAt = dateMillisOrNull("receivedAt") ?: 0L,
        sourceTransactionId = getString("sourceTransactionId").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = dateMillisOrNull("createdAt") ?: 0L,
        updatedAt = dateMillisOrNull("updatedAt") ?: 0L
    )

    private fun DocumentSnapshot.toInventoryItem(): InventoryItem = InventoryItem(
        id = getString("id").orEmpty().ifBlank { id },
        branchId = getString("branchId").orEmpty(),
        branchName = getString("branchName").orEmpty(),
        quantity = (get("quantity") as? Number)?.toDouble() ?: 0.0,
        expiryDate = dateMillisOrNull("expiryDate") ?: 0L,
        reorderPoint = (get("reorderPoint") as? Number)?.toInt() ?: 0,
        reorderThreshold = (get("reorderThreshold") as? Number)?.toDouble() ?: 0.0,
        maximumStockLevel = (get("maximumStockLevel") as? Number)?.toInt() ?: 0,
        status = getString("status").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        updatedBy = getString("updatedBy").orEmpty(),
        createdAt = dateMillisOrNull("createdAt") ?: 0L,
        updatedAt = dateMillisOrNull("updatedAt") ?: 0L,
        expiryLotsInitialized = getBoolean("expiryLotsInitialized") ?: false
    )

    private fun ExpiryLot.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "inventoryItemId" to inventoryItemId,
        "branchId" to branchId,
        "branchName" to branchName,
        "expiryDate" to expiryDate?.let { Timestamp(Date(it)) },
        "quantity" to quantity,
        "receivedAt" to Timestamp(Date(receivedAt.takeIf { it > 0L } ?: createdAt)),
        "sourceTransactionId" to sourceTransactionId,
        "createdBy" to createdBy,
        "createdAt" to Timestamp(Date(createdAt)),
        "updatedAt" to Timestamp(Date(updatedAt))
    )

    private fun InventoryItem.toParentMap(): Map<String, Any> = mapOf(
        "id" to id,
        "sku" to sku,
        "barcode" to barcode,
        "name" to name,
        "brand" to brand,
        "category" to category,
        "branchId" to branchId,
        "branchName" to branchName,
        "storageLocation" to storageLocation,
        "quantity" to quantity,
        "unit" to unit,
        "costPrice" to costPrice,
        "sellingPrice" to sellingPrice,
        "minimumStockLevel" to minimumStockLevel,
        "reorderPoint" to reorderPoint,
        "maximumStockLevel" to maximumStockLevel,
        "reorderThreshold" to reorderThreshold,
        "aisle" to aisle,
        "shelf" to shelf,
        "addedDate" to itemDateValue(addedDate),
        "expiryDate" to itemDateValue(expiryDate),
        "batchNumber" to batchNumber,
        "shelfLifeDays" to shelfLifeDays,
        "reminderDaysBefore" to reminderDaysBefore,
        "notes" to notes,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "supplierPhone" to supplierPhone,
        "supplierEmail" to supplierEmail,
        "imageUrl" to imageUrl,
        "tags" to tags,
        "status" to status,
        "createdBy" to createdBy,
        "updatedBy" to updatedBy,
        "createdAt" to itemDateValue(createdAt),
        "updatedAt" to itemDateValue(updatedAt),
        "expiryLotsInitialized" to true
    )

    private fun itemDateValue(value: Long): Any = if (value > 0L) Timestamp(Date(value)) else value

    private fun DocumentSnapshot.dateMillisOrNull(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong().takeIf { it > 0L }
        else -> null
    }

    companion object {
        private const val NO_EXPIRY_LOT_ID = "no_expiry"
        private const val EPSILON = 0.000001
        private val LOT_ID_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
