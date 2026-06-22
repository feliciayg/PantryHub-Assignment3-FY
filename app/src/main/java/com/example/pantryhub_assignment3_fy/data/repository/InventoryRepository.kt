package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ActivityActionType
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.example.pantryhub_assignment3_fy.util.InventoryStatusCalculator
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import com.example.pantryhub_assignment3_fy.util.SkuGenerator
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import java.security.MessageDigest
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Repository for shared inventory items.
 *
 * All Firebase reads/writes for `stores/{storeId}/inventoryItems` live here so
 * Fragments and ViewModels do not need to know Firestore paths or document mapping.
 */
class InventoryRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    private val activityRepository = ActivityRepository(authManager, firestoreDataSource)
    private val stockMovementRepository = StockMovementRepository(authManager, firestoreDataSource)
    private val expiryLotRepository = ExpiryLotRepository(authManager, firestoreDataSource)

    /**
     * Streams live inventoryItem updates for the signed-in user's current store.
     */
    fun observeInventoryItems(
        includeArchived: Boolean = true,
        includeExpiryLots: Boolean = false
    ): Flow<Result<List<InventoryItem>>> = callbackFlow {
        val userProfile = currentUserProfile()
        val storeId = userProfile?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = firestoreDataSource.db
            .collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                launch {
                    val inventoryItems = snapshot?.documents.orEmpty()
                        .map { it.toInventoryItem() }
                        .filter { includeArchived || !it.isArchived }
                        .map { inventoryItem ->
                            val lots = if (includeExpiryLots) {
                                runCatching { expiryLotRepository.loadLots(inventoryItem) }.getOrDefault(emptyList())
                            } else {
                                inventoryItem.lightweightExpiryLots()
                            }
                            val nearestExpiry = lots.mapNotNull { it.expiryDate }.minOrNull()
                                ?: inventoryItem.expiryDate.takeIf { it > 0L }
                                ?: 0L
                            val withLots = inventoryItem.copy(expiryLots = lots, expiryDate = nearestExpiry)
                            val calculatedStatus = InventoryStatusCalculator.calculate(withLots)
                            if (calculatedStatus.name == withLots.status) withLots else withLots.copy(status = calculatedStatus.name)
                        }
                    trySend(Result.success(inventoryItems))
                    }
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    /**
     * Reads one inventoryItem document from the current store.
     */
    suspend fun getInventoryItem(inventoryItemId: String): Result<InventoryItem> = runCatching {
        val storeId = currentStoreId()
        val snapshot = inventoryItemsCollection(storeId).document(inventoryItemId).get().await()
        if (!snapshot.exists()) error("Stock item not found.")
        snapshot.toInventoryItem()
    }

    suspend fun findMatchingInventoryItemForLocation(
        branchId: String,
        sku: String,
        barcode: String,
        name: String,
        brand: String,
        category: String
    ): Result<InventoryItem?> = runCatching {
        if (branchId.isBlank()) return@runCatching null
        val probe = InventoryItem(
            sku = sku,
            barcode = barcode,
            name = name,
            brand = brand,
            category = category
        )
        loadInventoryItems(currentStoreId())
            .filterNot { it.isArchived }
            .firstOrNull { item ->
                item.branchId == branchId && ProductIdentity.sameProduct(item, probe)
            }
    }

    suspend fun generateUniqueDisplaySku(): Result<String> = runCatching {
        SkuGenerator.generateRandomSku(loadInventoryItems(currentStoreId()).map { it.sku })
    }

    suspend fun validateSkuForProduct(
        sku: String,
        currentItemId: String?,
        branchId: String,
        name: String,
        brand: String,
        category: String,
        reusedProductSku: String?
    ): Result<Unit> = runCatching {
        require(sku.matches(SKU_FORMAT)) { "Use only A-Z, 0-9, and hyphens." }
        val matching = loadInventoryItems(currentStoreId()).filter {
            it.id != currentItemId && it.sku.equals(sku, ignoreCase = true)
        }
        if (matching.isEmpty()) return@runCatching
        if (branchId.isNotBlank() && matching.any { it.branchId == branchId }) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate SKU blocked in selected location.",
                "sku" to sku,
                "locationId" to branchId
            )
            error("This SKU is already used by another product.")
        }
        val explicitlyReused = reusedProductSku?.equals(sku, ignoreCase = true) == true
        val sameProduct = name.isNotBlank() && matching.all {
            it.name.equals(name, ignoreCase = true) &&
                it.brand.equals(brand, ignoreCase = true) &&
                it.category.equals(category, ignoreCase = true)
        }
        if (!explicitlyReused && !sameProduct) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate SKU blocked for another product.",
                "sku" to sku
            )
            error("This SKU is already used by another product.")
        }
    }

    suspend fun validateBarcodeForProduct(
        barcode: String,
        currentItemId: String?,
        branchId: String,
        name: String,
        brand: String,
        category: String
    ): Result<Unit> = runCatching {
        val normalized = barcode.trim()
        if (normalized.isBlank()) return@runCatching
        require(normalized.matches(BARCODE_FORMAT)) { "Use digits only." }
        val matching = loadInventoryItems(currentStoreId()).filter {
            it.id != currentItemId && it.barcode.equals(normalized, ignoreCase = true)
        }
        if (matching.isEmpty()) return@runCatching
        if (branchId.isNotBlank() && matching.any { it.branchId == branchId }) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate barcode blocked in selected location.",
                "locationId" to branchId
            )
            error("This barcode is already used by another product.")
        }
        val sameProduct = name.isNotBlank() && matching.all {
            it.name.equals(name, ignoreCase = true) &&
                it.brand.equals(brand, ignoreCase = true) &&
                it.category.equals(category, ignoreCase = true)
        }
        if (!sameProduct) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate barcode blocked for another product."
            )
            error("This barcode is already used by another product.")
        }
    }

    /**
     * Creates a new inventoryItem document and writes an activity log for the action.
     */
    suspend fun addInventoryItem(input: InventoryItem, receivedFromRestockOrder: Boolean = false): Result<String> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val existingItems = loadInventoryItems(storeId)
        val finalSku = input.sku.trim().ifBlank {
            SkuGenerator.generateUniqueSku(input, existingItems.map { it.sku })
        }
        validateIdentifierUniqueness(
            candidate = input.copy(sku = finalSku),
            sku = finalSku,
            barcode = input.barcode.trim(),
            branchId = input.branchId,
            existingItems = existingItems
        )
        val now = System.currentTimeMillis()
        val productTemplate = input.copy(
            sku = finalSku,
            barcode = input.barcode.trim(),
            reorderThreshold = StockLevelRules.effectiveReorderPoint(input),
            shelfLifeDays = input.shelfLifeDays,
            createdBy = userId,
            updatedBy = userId,
            createdAt = now,
            updatedAt = now
        )
        val existingSameProduct = existingItems.filter { ProductIdentity.sameProduct(it, productTemplate) }
        val branches = loadBranches(storeId).ifEmpty {
            listOf(Branch(id = input.branchId, name = input.branchName))
        }.filter { it.id.isNotBlank() }
        val createdByBranch = createMissingProductLocationRecords(
            storeId = storeId,
            template = productTemplate,
            branches = branches,
            existingItems = existingItems,
            userId = userId,
            now = now
        )
        val zeroStockCount = createdByBranch.values.count { it.branchId != input.branchId && it.quantity == 0.0 }
        if (zeroStockCount > 0) {
            AppLogger.info(
                area = "Items",
                event = "zero_stock_records_created",
                message = "Created zero-stock records for other locations.",
                "item" to productTemplate.name,
                "count" to zeroStockCount
            )
        }
        val selectedRecord = existingSameProduct.firstOrNull { it.branchId == input.branchId }
            ?: createdByBranch[input.branchId]
            ?: error("Could not create inventory record for the selected location.")
        if (input.quantity > 0.0) {
            expiryLotRepository.addStock(selectedRecord.id, input.quantity, input.expiryDate.takeIf { it > 0L })
        }
        activityRepository.addActivityLog(
            actionType = if (receivedFromRestockOrder) ActivityActionType.RESTOCK_ORDER_RECEIVED else ActivityActionType.INVENTORY_ITEM_CREATED,
            inventoryItemId = selectedRecord.id,
            itemName = productTemplate.name,
            quantity = input.quantity,
            unit = productTemplate.unit,
            note = if (receivedFromRestockOrder) "Restocked from restock list" else "Added to inventory"
        ).getOrThrow()
        selectedRecord.id
    }

    /**
     * CSV import creates the selected product/location row with its imported quantity directly.
     * This avoids leaving bulk-imported rows at zero if later stock-lot setup is not required.
     */
    suspend fun importInventoryItem(input: InventoryItem): Result<String> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val existingItems = loadInventoryItems(storeId)
        val finalSku = input.sku.trim().ifBlank {
            SkuGenerator.generateUniqueSku(input, existingItems.map { it.sku })
        }
        val productTemplate = input.copy(
            sku = finalSku,
            barcode = input.barcode.trim(),
            reorderThreshold = StockLevelRules.effectiveReorderPoint(input),
            createdBy = userId,
            updatedBy = userId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val existingSelectedRecord = existingItems.firstOrNull {
            it.branchId == productTemplate.branchId && ProductIdentity.sameProduct(it, productTemplate)
        }
        if (existingSelectedRecord != null) {
            val now = System.currentTimeMillis()
            val updated = productTemplate.copy(
                id = existingSelectedRecord.id,
                branchId = existingSelectedRecord.branchId,
                branchName = existingSelectedRecord.branchName.ifBlank { productTemplate.branchName },
                quantity = productTemplate.quantity,
                status = InventoryStatusCalculator.calculate(productTemplate.copy(quantity = productTemplate.quantity)).name,
                createdBy = existingSelectedRecord.createdBy.ifBlank { userId },
                createdAt = existingSelectedRecord.createdAt.takeIf { it > 0L } ?: now,
                updatedBy = userId,
                updatedAt = now,
                expiryLotsInitialized = existingSelectedRecord.expiryLotsInitialized
            )
            inventoryItemsCollection(storeId).document(existingSelectedRecord.id).set(updated.toFirestoreMap()).await()
            activityRepository.addActivityLog(
                actionType = ActivityActionType.INVENTORY_ITEM_CREATED,
                inventoryItemId = updated.id,
                itemName = updated.name,
                quantity = updated.quantity,
                unit = updated.unit,
                note = "Updated from inventory CSV"
            ).getOrThrow()
            return@runCatching updated.id
        }
        val branches = loadBranches(storeId).ifEmpty {
            listOf(Branch(id = productTemplate.branchId, name = productTemplate.branchName))
        }.filter { it.id.isNotBlank() }
        val sameProductRecords = existingItems.filter { ProductIdentity.sameProduct(it, productTemplate) }
        val existingBranchIds = sameProductRecords.map { it.branchId }.toSet()
        val selectedBranchId = productTemplate.branchId.ifBlank { error("CSV row must resolve to a location.") }
        val selectedBranch = branches.firstOrNull { it.id == selectedBranchId }
            ?: Branch(id = selectedBranchId, name = productTemplate.branchName)
        val missingBranches = branches
            .filter { it.id.isNotBlank() && it.id !in existingBranchIds }
            .distinctBy { it.id }
        val branchesToWrite = (missingBranches + selectedBranch).distinctBy { it.id }

        val now = System.currentTimeMillis()
        val records = branchesToWrite.associate { branch ->
            val document = inventoryItemsCollection(storeId).document(productLocationDocumentId(productTemplate, branch.id))
            val importedQuantity = if (branch.id == selectedBranchId) productTemplate.quantity else 0.0
            val record = productTemplate.copy(
                id = document.id,
                branchId = branch.id,
                branchName = branch.name,
                quantity = importedQuantity,
                expiryDate = if (importedQuantity > 0.0) productTemplate.expiryDate else 0L,
                status = InventoryStatusCalculator.calculate(productTemplate.copy(quantity = importedQuantity)).name,
                createdBy = userId,
                createdAt = now,
                updatedBy = userId,
                updatedAt = now,
                expiryLotsInitialized = false
            )
            branch.id to record
        }

        records.values.chunked(FIRESTORE_BATCH_WRITE_LIMIT).forEach { chunk ->
            val batch = firestoreDataSource.db.batch()
            chunk.forEach { record ->
                batch.set(inventoryItemsCollection(storeId).document(record.id), record.toFirestoreMap())
            }
            batch.commit().await()
        }

        val zeroStockCount = records.values.count { it.branchId != selectedBranchId && it.quantity == 0.0 }
        if (zeroStockCount > 0) {
            AppLogger.info(
                area = "Items",
                event = "zero_stock_records_created",
                message = "Created zero-stock records for other locations during CSV import.",
                "item" to productTemplate.name,
                "count" to zeroStockCount
            )
        }
        val selectedRecord = records[selectedBranchId] ?: error("Could not create imported inventory record.")
        activityRepository.addActivityLog(
            actionType = ActivityActionType.INVENTORY_ITEM_CREATED,
            inventoryItemId = selectedRecord.id,
            itemName = selectedRecord.name,
            quantity = selectedRecord.quantity,
            unit = selectedRecord.unit,
            note = "Imported from inventory CSV"
        ).getOrThrow()
        selectedRecord.id
    }

    /**
     * Replaces an existing inventoryItem document while preserving the original creator metadata.
     */
    suspend fun updateInventoryItem(inventoryItem: InventoryItem): Result<Unit> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val existing = getInventoryItem(inventoryItem.id).getOrThrow()
        val now = System.currentTimeMillis()
        // Editing never auto-generates or clears an existing SKU. Identifier uniqueness is checked
        // only when the user actually changes SKU/barcode, which keeps transferred branch records editable.
        val finalSku = inventoryItem.sku.trim().ifBlank { existing.sku }
        val finalBarcode = inventoryItem.barcode.trim()
        val existingItems = loadInventoryItems(storeId)
        val branchChanged = inventoryItem.branchId != existing.branchId
        validateIdentifierUniqueness(
            candidate = inventoryItem.copy(sku = finalSku),
            sku = finalSku.takeIf { branchChanged || !it.equals(existing.sku, ignoreCase = true) }.orEmpty(),
            barcode = finalBarcode.takeIf { branchChanged || it != existing.barcode }.orEmpty(),
            branchId = inventoryItem.branchId,
            existingItems = existingItems,
            excludingInventoryItemId = existing.id
        )
        val quantityDelta = inventoryItem.quantity - existing.quantity
        if (quantityDelta > 0.0) {
            expiryLotRepository.addStock(existing.id, quantityDelta, inventoryItem.expiryDate.takeIf { it > 0L })
        } else if (quantityDelta < 0.0) {
            expiryLotRepository.deductStock(existing.id, -quantityDelta)
        }
        val lotSummaryItem = getInventoryItem(existing.id).getOrThrow()
        val updated = inventoryItem.copy(
            quantity = lotSummaryItem.quantity,
            expiryDate = lotSummaryItem.expiryDate,
            sku = finalSku,
            barcode = finalBarcode,
            reorderThreshold = StockLevelRules.effectiveReorderPoint(inventoryItem),
            status = InventoryStatusCalculator.calculate(
                inventoryItem.copy(
                    quantity = lotSummaryItem.quantity,
                    expiryDate = lotSummaryItem.expiryDate
                )
            ).name,
            createdBy = existing.createdBy.ifBlank { userId },
            createdAt = existing.createdAt.takeIf { it > 0L }
                ?: existing.updatedAt.takeIf { it > 0L }
                ?: now,
            updatedBy = userId,
            updatedAt = now,
            expiryLotsInitialized = true
        )

        // Creator metadata is immutable; only updater metadata changes when inventoryItem is edited.
        inventoryItemsCollection(storeId).document(inventoryItem.id).set(
            updated.copy(expiryLotsInitialized = true).toFirestoreMap()
        ).await()
        synchronizeSharedProductFields(storeId, existing, updated, existingItems)
    }

    /**
     * Idempotently creates only missing product/location combinations with quantity zero.
     * Existing records and quantities are never overwritten.
     */
    suspend fun backfillMissingProductLocationRecords(): Result<Int> = runCatching {
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val items = loadInventoryItems(storeId)
        val branches = loadBranches(storeId)
        val representatives = distinctProductRepresentatives(items)
        var created = 0
        representatives.forEach { template ->
            created += createMissingProductLocationRecords(
                storeId,
                template,
                branches,
                items,
                userId,
                System.currentTimeMillis()
            ).size
        }
        created
    }

    suspend fun ensureRecordsForBranch(branch: Branch): Result<Int> = runCatching {
        require(branch.id.isNotBlank()) { "Location id is required." }
        val storeId = currentStoreId()
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val items = loadInventoryItems(storeId)
        val representatives = distinctProductRepresentatives(items)
        var created = 0
        representatives.forEach { template ->
            created += createMissingProductLocationRecords(
                storeId,
                template,
                listOf(branch),
                items,
                userId,
                System.currentTimeMillis()
            ).size
        }
        created
    }

    /**
     * Adds quantity into an existing matching inventoryItem item instead of creating a duplicate card.
     */
    suspend fun mergeInventoryQuantity(
        existingItemId: String,
        quantityToAdd: Double,
        expiryDate: Long? = null,
        receivedFromRestockOrder: Boolean = false
    ): Result<Unit> = runCatching {
        require(quantityToAdd > 0.0) { "Quantity to add must be greater than 0." }
        expiryLotRepository.addStock(existingItemId, quantityToAdd, expiryDate)
        val inventoryItem = getInventoryItem(existingItemId).getOrThrow()
        activityRepository.addActivityLog(
            actionType = if (receivedFromRestockOrder) ActivityActionType.RESTOCK_ORDER_RECEIVED else ActivityActionType.INVENTORY_ITEM_CREATED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = quantityToAdd,
            unit = inventoryItem.unit,
            note = if (receivedFromRestockOrder) "Merged restock into existing inventory item" else "Merged duplicate inventory item"
        ).getOrThrow()
    }

    /**
     * Archives one inventory item document without deleting stock history or expiry lots.
     */
    // Breakpoint: inspect archived state and matching records before inventory items are hidden from active lists.
    suspend fun archiveInventoryItem(inventoryItemId: String, reason: String = "Archived from inventory"): Result<Unit> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val inventoryItem = getInventoryItem(inventoryItemId).getOrThrow()
        val now = System.currentTimeMillis()
        val matchingItems = loadInventoryItems(storeId).filter { ProductIdentity.sameProduct(it, inventoryItem) }
        require(matchingItems.isNotEmpty()) { "Stock item not found." }
        // Breakpoint: inspect the batch update payload for every matching product-location record.
        val batch = firestoreDataSource.db.batch()
        matchingItems.forEach { item ->
            batch.update(
                inventoryItemsCollection(storeId).document(item.id),
                mapOf(
                    "isArchived" to true,
                    "archivedAt" to Timestamp(Date(now)),
                    "archivedBy" to userId,
                    "archiveReason" to reason,
                    "updatedBy" to userId,
                    "updatedAt" to Timestamp(Date(now))
                )
            )
        }
        batch.commit().await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.INVENTORY_ITEM_DELETED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = matchingItems.sumOf { it.quantity },
            unit = inventoryItem.unit,
            note = reason
        ).getOrThrow()
    }

    /**
     * Restores an archived inventory item without touching quantity, expiry lots, or history.
     */
    suspend fun restoreInventoryItem(inventoryItemId: String): Result<Unit> = runCatching {
        require(inventoryItemId.isNotBlank()) { "Stock item id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val inventoryItem = getInventoryItem(inventoryItemId).getOrThrow()
        val now = System.currentTimeMillis()
        val matchingItems = loadInventoryItems(storeId).filter { ProductIdentity.sameProduct(it, inventoryItem) }
        require(matchingItems.isNotEmpty()) { "Stock item not found." }
        val batch = firestoreDataSource.db.batch()
        matchingItems.forEach { item ->
            batch.update(
                inventoryItemsCollection(storeId).document(item.id),
                mapOf(
                    "isArchived" to false,
                    "archivedAt" to 0L,
                    "archivedBy" to "",
                    "archiveReason" to "",
                    "updatedBy" to userId,
                    "updatedAt" to Timestamp(Date(now))
                )
            )
        }
        batch.commit().await()
    }

    suspend fun deductSalesImport(inventoryItemId: String, quantitySold: Double, note: String? = null): Result<Unit> = runCatching {
        require(quantitySold > 0.0) { "Quantity sold must be greater than 0." }
        val beforeItem = getInventoryItem(inventoryItemId).getOrThrow()
        val lotResult = expiryLotRepository.deductStock(inventoryItemId, quantitySold)
        val inventoryItem = beforeItem.copy(quantity = lotResult.quantityAfter)
        activityRepository.addActivityLog(
            actionType = ActivityActionType.STOCK_REDUCED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = quantitySold,
            unit = inventoryItem.unit,
            note = listOfNotNull("Sales import deducted ${quantitySold.toCleanString()} ${inventoryItem.unit}", note?.takeIf { it.isNotBlank() })
                .joinToString(" - ")
        ).getOrThrow()
        stockMovementRepository.recordMovement(
            inventoryItem = inventoryItem,
            movementType = StockMovementType.SALES_DEDUCTION,
            quantity = quantitySold,
            quantityBefore = lotResult.quantityBefore,
            quantityAfter = inventoryItem.quantity,
            note = listOfNotNull("Sales CSV deduction", note?.takeIf { it.isNotBlank() }).joinToString(" - ")
        ).getOrThrow()
    }

    /**
     * Decreases inventoryItem quantity for waste, stores the reason, and records the waste without gamification.
     */
    suspend fun logWaste(inventoryItemId: String, quantityWasted: Double, reason: String): Result<Unit> = runCatching {
        val inventoryItem = getInventoryItem(inventoryItemId).getOrThrow()
        require(reason.isNotBlank()) { "Waste reason is required." }
        require(quantityWasted > 0) { "Quantity wasted must be greater than 0." }
        require(quantityWasted <= inventoryItem.quantity) { "Quantity wasted cannot be more than current quantity." }

        val lotResult = expiryLotRepository.deductStock(inventoryItemId, quantityWasted)
        val remaining = lotResult.quantityAfter
        val existingNotes = inventoryItem.notes.trim()
        val wasteNote = "Waste logged: $quantityWasted ${inventoryItem.unit} ($reason)"
        val notes = if (existingNotes.isBlank()) wasteNote else "$existingNotes\n$wasteNote"

        // Log waste decreases stock and records the reason in notes until the Activity Log module exists.
        inventoryItemsCollection(currentStoreId()).document(inventoryItemId)
            .update(
                mapOf(
                    "notes" to notes,
                    "updatedBy" to authManager.currentUserId.orEmpty(),
                    "updatedAt" to Timestamp.now()
                )
            )
            .await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.WASTE_RECORDED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = quantityWasted,
            unit = inventoryItem.unit,
            reason = reason,
            note = "Waste recorded"
        ).getOrThrow()
        stockMovementRepository.recordMovement(
            inventoryItem = inventoryItem,
            movementType = reason.toWasteMovementType(),
            quantity = quantityWasted,
            quantityBefore = inventoryItem.quantity,
            quantityAfter = remaining,
            reason = reason,
            note = "Waste/damage movement recorded"
        ).getOrThrow()
    }

    suspend fun markExpiredStock(inventoryItemId: String, quantityExpired: Double): Result<Unit> = runCatching {
        val inventoryItem = getInventoryItem(inventoryItemId).getOrThrow()
        require(quantityExpired > 0.0) { "Expired quantity must be greater than 0." }

        val lotResult = expiryLotRepository.deductExpiredStock(inventoryItemId, quantityExpired)
        activityRepository.addActivityLog(
            actionType = ActivityActionType.WASTE_RECORDED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = quantityExpired,
            unit = inventoryItem.unit,
            reason = "Expired",
            note = "Expired stock removed"
        ).getOrThrow()
        stockMovementRepository.recordMovement(
            inventoryItem = inventoryItem,
            movementType = StockMovementType.EXPIRED,
            quantity = quantityExpired,
            quantityBefore = lotResult.quantityBefore,
            quantityAfter = lotResult.quantityAfter,
            reason = "Expired",
            note = "Marked expired stock"
        ).getOrThrow()
    }

    private suspend fun currentStoreId(): String {
        return currentUserProfile()?.currentStoreId ?: error("No store selected.")
    }

    private suspend fun currentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        val snapshot = firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
        return snapshot.toObject(UserProfile::class.java)
    }

    private fun inventoryItemsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun branchesCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.BRANCHES_COLLECTION)

    private suspend fun loadInventoryItems(storeId: String): List<InventoryItem> =
        inventoryItemsCollection(storeId).get().await().documents.map { it.toInventoryItem() }

    private suspend fun loadBranches(storeId: String): List<Branch> =
        branchesCollection(storeId).get().await().documents.map { snapshot ->
            Branch(
                id = snapshot.getString("id").orEmpty().ifBlank { snapshot.id },
                name = snapshot.getString("name").orEmpty()
            )
        }

    /**
     * Keeps list/dashboard streams fast by avoiding an expiryLots subcollection read
     * for every inventory row. Exact lot breakdowns can opt in with includeExpiryLots=true.
     */
    private fun InventoryItem.lightweightExpiryLots(): List<ExpiryLot> {
        val expiry = expiryDate.takeIf { it > 0L } ?: return emptyList()
        if (quantity <= 0.0) return emptyList()
        return listOf(
            ExpiryLot(
                id = "summary-$id",
                inventoryItemId = id,
                branchId = branchId,
                branchName = branchName,
                expiryDate = expiry,
                quantity = quantity,
                receivedAt = createdAt,
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        )
    }

    private suspend fun createMissingProductLocationRecords(
        storeId: String,
        template: InventoryItem,
        branches: List<Branch>,
        existingItems: List<InventoryItem>,
        userId: String,
        now: Long
    ): Map<String, InventoryItem> {
        val sameProductRecords = existingItems.filter { ProductIdentity.sameProduct(it, template) }
        val existingBranchIds = sameProductRecords.map { it.branchId }.toSet()
        val missingBranches = branches
            .filter { it.id.isNotBlank() && it.id !in existingBranchIds }
            .distinctBy { it.id }
        if (missingBranches.isEmpty()) return emptyMap()

        val records = missingBranches.associate { branch ->
            val document = inventoryItemsCollection(storeId).document(productLocationDocumentId(template, branch.id))
            branch.id to template.copy(
                id = document.id,
                branchId = branch.id,
                branchName = branch.name,
                quantity = 0.0,
                expiryDate = 0L,
                expiryLotsInitialized = true,
                status = InventoryStatus.OUT_OF_STOCK.name,
                createdBy = userId,
                createdAt = now,
                updatedBy = userId,
                updatedAt = now
            )
        }
        records.values.chunked(FIRESTORE_BATCH_WRITE_LIMIT).forEach { chunk ->
            val batch = firestoreDataSource.db.batch()
            chunk.forEach { record ->
                batch.set(inventoryItemsCollection(storeId).document(record.id), record.toFirestoreMap())
            }
            batch.commit().await()
        }
        return records
    }

    private fun distinctProductRepresentatives(items: List<InventoryItem>): List<InventoryItem> {
        val representatives = mutableListOf<InventoryItem>()
        items.sortedByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.createdAt }
            .forEach { item ->
                if (representatives.none { ProductIdentity.sameProduct(it, item) }) representatives += item
            }
        return representatives
    }

    private suspend fun synchronizeSharedProductFields(
        storeId: String,
        oldIdentityItem: InventoryItem,
        updatedItem: InventoryItem,
        existingItems: List<InventoryItem>
    ) {
        val siblingRecords = existingItems.filter {
            it.id != updatedItem.id && ProductIdentity.sameProduct(it, oldIdentityItem)
        }
        if (siblingRecords.isEmpty()) return
        val sharedUpdates = updatedItem.sharedProductFields()
        siblingRecords.chunked(FIRESTORE_BATCH_WRITE_LIMIT).forEach { chunk ->
            val batch = firestoreDataSource.db.batch()
            chunk.forEach { sibling ->
                val siblingWithSharedFields = sibling.copy(
                    sku = updatedItem.sku,
                    barcode = updatedItem.barcode,
                    name = updatedItem.name,
                    brand = updatedItem.brand,
                    category = updatedItem.category,
                    unit = updatedItem.unit,
                    costPrice = updatedItem.costPrice,
                    sellingPrice = updatedItem.sellingPrice,
                    reorderPoint = updatedItem.reorderPoint,
                    reorderThreshold = updatedItem.reorderThreshold,
                    maximumStockLevel = updatedItem.maximumStockLevel,
                    imageUrl = updatedItem.imageUrl
                )
                batch.update(
                    inventoryItemsCollection(storeId).document(sibling.id),
                    sharedUpdates + ("status" to InventoryStatusCalculator.calculate(siblingWithSharedFields).name)
                )
            }
            batch.commit().await()
        }
    }

    private fun productLocationDocumentId(template: InventoryItem, branchId: String): String {
        val raw = "${ProductIdentity.key(template)}|$branchId"
        return "product_location_" + MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private fun InventoryItem.sharedProductFields(): Map<String, Any> = mapOf(
        "sku" to sku,
        "barcode" to barcode,
        "name" to name,
        "brand" to brand,
        "category" to category,
        "unit" to unit,
        "costPrice" to costPrice,
        "sellingPrice" to sellingPrice,
        "reorderPoint" to reorderPoint,
        "reorderThreshold" to reorderThreshold,
        "maximumStockLevel" to maximumStockLevel,
        "supplierId" to supplierId,
        "supplierName" to supplierName,
        "supplierPhone" to supplierPhone,
        "supplierEmail" to supplierEmail,
        "imageUrl" to imageUrl,
        "tags" to tags,
        "updatedBy" to updatedBy,
        "updatedAt" to updatedAt.toFirestoreDateValue()
    )

    private fun validateIdentifierUniqueness(
        candidate: InventoryItem,
        sku: String,
        barcode: String,
        branchId: String,
        existingItems: List<InventoryItem>,
        excludingInventoryItemId: String? = null
    ) {
        // Product identifiers can repeat across branches, but not within the destination branch.
        // Blank identifiers remain compatible and the current record is excluded during edit.
        val comparableItems = existingItems.filter {
            it.id != excludingInventoryItemId && it.branchId == branchId
        }
        if (sku.isNotBlank() && comparableItems.any { it.sku.isNotBlank() && it.sku.equals(sku, ignoreCase = true) }) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate SKU blocked in selected location.",
                "sku" to sku,
                "locationId" to branchId
            )
            error("Duplicate SKU: $sku is already used in this branch.")
        }
        if (barcode.isNotBlank() && comparableItems.any { it.barcode.isNotBlank() && it.barcode.equals(barcode, ignoreCase = true) }) {
            AppLogger.warn(
                area = "Items",
                event = "identifier_duplicate_blocked",
                message = "Duplicate barcode blocked in selected location.",
                "locationId" to branchId
            )
            error("Duplicate barcode: $barcode is already used in this branch.")
        }
        if (sku.isNotBlank()) {
            val otherBranchMatches = existingItems.filter {
                it.id != excludingInventoryItemId &&
                    it.branchId != branchId &&
                    it.sku.equals(sku, ignoreCase = true)
            }
            val sameProduct = otherBranchMatches.all {
                it.name.equals(candidate.name, ignoreCase = true) &&
                    it.brand.equals(candidate.brand, ignoreCase = true) &&
                    it.category.equals(candidate.category, ignoreCase = true)
            }
            if (otherBranchMatches.isNotEmpty() && !sameProduct) {
                AppLogger.warn(
                    area = "Items",
                    event = "identifier_duplicate_blocked",
                    message = "Duplicate SKU blocked for another product.",
                    "sku" to sku
                )
                error("This SKU is already used by another product.")
            }
        }
        if (barcode.isNotBlank()) {
            val otherBranchMatches = existingItems.filter {
                it.id != excludingInventoryItemId &&
                    it.branchId != branchId &&
                    it.barcode.equals(barcode, ignoreCase = true)
            }
            val sameProduct = otherBranchMatches.all {
                it.name.equals(candidate.name, ignoreCase = true) &&
                    it.brand.equals(candidate.brand, ignoreCase = true) &&
                    it.category.equals(candidate.category, ignoreCase = true)
            }
            if (otherBranchMatches.isNotEmpty() && !sameProduct) {
                AppLogger.warn(
                    area = "Items",
                    event = "identifier_duplicate_blocked",
                    message = "Duplicate barcode blocked for another product."
                )
                error("This barcode is already used by another product.")
            }
        }
    }

    /**
     * Converts a Firestore document into the app's InventoryItem model.
     */
    private fun DocumentSnapshot.toInventoryItem(): InventoryItem {
        return InventoryItem(
            id = getString("id").orEmpty().ifBlank { id },
            sku = getString("sku").orEmpty(),
            barcode = getString("barcode").orEmpty(),
            name = getString("name").orEmpty(),
            brand = getString("brand").orEmpty(),
            category = getString("category").orEmpty(),
            branchId = getString("branchId").orEmpty(),
            branchName = getString("branchName").orEmpty(),
            storageLocation = getString("storageLocation").orEmpty(),
            quantity = number("quantity")?.toDouble() ?: 0.0,
            unit = getString("unit").orEmpty(),
            costPrice = number("costPrice")?.toDouble() ?: 0.0,
            sellingPrice = number("sellingPrice")?.toDouble() ?: 0.0,
            minimumStockLevel = number("minimumStockLevel")?.toInt() ?: 0,
            reorderPoint = number("reorderPoint")?.toInt()?.takeIf { it > 0 } ?: number("reorderThreshold")?.toInt() ?: 0,
            maximumStockLevel = number("maximumStockLevel")?.toInt() ?: 0,
            reorderThreshold = number("reorderThreshold")?.toDouble() ?: 0.0,
            aisle = getString("aisle").orEmpty(),
            shelf = getString("shelf").orEmpty(),
            addedDate = dateMillis("addedDate"),
            expiryDate = dateMillis("expiryDate"),
            batchNumber = getString("batchNumber").orEmpty(),
            shelfLifeDays = number("shelfLifeDays")?.toInt() ?: 0,
            reminderDaysBefore = number("reminderDaysBefore")?.toInt() ?: 0,
            notes = getString("notes").orEmpty(),
            supplierId = getString("supplierId").orEmpty(),
            supplierName = getString("supplierName").orEmpty(),
            supplierPhone = getString("supplierPhone").orEmpty(),
            supplierEmail = getString("supplierEmail").orEmpty(),
            imageUrl = getString("imageUrl").orEmpty(),
            tags = stringList("tags"),
            status = getString("status").orEmpty().ifBlank { InventoryStatus.FRESH.name },
            createdBy = getString("createdBy").orEmpty(),
            updatedBy = getString("updatedBy").orEmpty(),
            createdAt = dateMillis("createdAt"),
            updatedAt = dateMillis("updatedAt"),
            isArchived = getBoolean("isArchived") ?: false,
            archivedAt = dateMillis("archivedAt"),
            archivedBy = getString("archivedBy").orEmpty(),
            archiveReason = getString("archiveReason").orEmpty(),
            expiryLotsInitialized = getBoolean("expiryLotsInitialized") ?: false
        )
    }

    private fun InventoryItem.toFirestoreMap(): Map<String, Any> = mapOf(
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
        "addedDate" to addedDate.toFirestoreDateValue(),
        "expiryDate" to expiryDate.toFirestoreDateValue(),
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
        "createdAt" to createdAt.toFirestoreDateValue(),
        "updatedAt" to updatedAt.toFirestoreDateValue(),
        "isArchived" to isArchived,
        "archivedAt" to archivedAt.toFirestoreDateValue(),
        "archivedBy" to archivedBy,
        "archiveReason" to archiveReason,
        "expiryLotsInitialized" to expiryLotsInitialized
    )

    private fun DocumentSnapshot.dateMillis(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun Long.toFirestoreDateValue(): Any =
        if (this > 0L) Timestamp(Date(this)) else this

    private fun DocumentSnapshot.number(field: String): Number? = get(field) as? Number

    private fun DocumentSnapshot.stringList(field: String): List<String> {
        val value = get(field)
        return when (value) {
            is List<*> -> value.filterIsInstance<String>().map { it.trim() }.filter { it.isNotBlank() }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun Double.toCleanString(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun String.toWasteMovementType(): StockMovementType = when {
        equals("Spoiled", ignoreCase = true) -> StockMovementType.DAMAGE
        else -> StockMovementType.WASTE
    }

    companion object {
        private val SKU_FORMAT = Regex("^[A-Z0-9-]+$")
        private val BARCODE_FORMAT = Regex("^\\d+$")
        private const val FIRESTORE_BATCH_WRITE_LIMIT = 450
    }

}
