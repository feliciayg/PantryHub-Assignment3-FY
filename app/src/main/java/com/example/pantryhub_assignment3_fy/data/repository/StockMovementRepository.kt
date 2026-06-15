package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ActivityActionType
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.example.pantryhub_assignment3_fy.util.InventoryStatusCalculator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionMode
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class StockMovementRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    private val activityRepository = ActivityRepository(authManager, firestoreDataSource)
    private val expiryLotRepository = ExpiryLotRepository(authManager, firestoreDataSource)

    data class TransactionDeleteAssessment(
        val canDelete: Boolean,
        val title: String,
        val message: String
    )

    fun observeStockMovements(): Flow<Result<List<StockMovement>>> = callbackFlow {
        val storeId = currentUserProfile()?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = stockMovementsCollection(storeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else {
                    val movements = snapshot?.documents.orEmpty()
                        .map { it.toStockMovement() }
                        .sortedByDescending { movement ->
                            movement.transactionAt.takeIf { it > 0L } ?: movement.createdAt
                        }
                    trySend(Result.success(movements))
                }
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    suspend fun getTransactionMovements(transactionId: String): Result<List<StockMovement>> = runCatching {
        require(transactionId.isNotBlank()) { "Transaction ID is required." }
        val storeId = currentUserProfile()?.currentStoreId ?: error("No store selected.")
        val collection = stockMovementsCollection(storeId)
        resolveTransactionMovements(
            allMovements = collection.get().await().documents.map { it.toStockMovement() },
            requestedId = transactionId
        )
    }

    suspend fun assessTransactionDelete(transactionId: String): Result<TransactionDeleteAssessment> = runCatching {
        require(transactionId.isNotBlank()) { "Transaction ID is required." }
        TransactionDeleteAssessment(
            canDelete = false,
            title = "Transaction history is read-only",
            message = "Completed stock transactions cannot be deleted or edited. Create an Adjust Stock transaction to correct stock."
        )
    }

    suspend fun deleteTransactionSafely(transactionId: String): Result<Unit> = runCatching {
        require(transactionId.isNotBlank()) { "Transaction ID is required." }
        error("Completed stock transactions cannot be deleted. Create an Adjust Stock transaction to correct stock.")
    }

    // Breakpoint: inspect the final StockMovement payload before Firestore writes the transaction record.
    suspend fun recordMovement(
        inventoryItem: InventoryItem,
        movementType: StockMovementType,
        quantity: Double,
        quantityBefore: Double,
        quantityAfter: Double,
        reason: String = "",
        note: String = "",
        transactionAt: Long = System.currentTimeMillis(),
        counterpartyId: String = "",
        counterpartyName: String = "",
        counterpartyType: String = "",
        transactionId: String = "",
        expiryDate: Long? = null
    ): Result<String> = runCatching {
        require(quantity > 0.0) { "Movement quantity must be greater than 0." }
        require(quantityAfter >= 0.0) { "Quantity after movement cannot be below 0." }
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val storeId = userProfile.currentStoreId ?: error("No store selected.")
        val document = stockMovementsCollection(storeId).document()
        val stableTransactionId = transactionId.ifBlank { document.id }
        val movement = buildMovement(
            id = document.id,
            transactionId = stableTransactionId,
            inventoryItem = inventoryItem,
            movementType = movementType,
            quantity = quantity,
            quantityBefore = quantityBefore,
            quantityAfter = quantityAfter,
            reason = reason,
            note = note,
            userProfile = userProfile,
            counterpartyId = counterpartyId,
            counterpartyName = counterpartyName,
            counterpartyType = counterpartyType,
            expiryDate = expiryDate,
            transactionAt = transactionAt,
            createdAt = System.currentTimeMillis()
        )
        document.set(movement.toFirestoreMap()).await()
        document.id
    }

    // Breakpoint: inspect quantityBefore, movement quantity, expiryDate, and quantityAfter for stock in/out.
    suspend fun applyManualMovement(
        inventoryItem: InventoryItem,
        movementType: StockMovementType,
        quantity: Double,
        reason: String,
        note: String,
        expiryDate: Long? = null,
        transactionAt: Long = System.currentTimeMillis(),
        counterpartyId: String = "",
        counterpartyName: String = "",
        counterpartyType: String = "",
        transactionId: String = ""
    ): Result<Unit> = runCatching {
        require(quantity > 0.0) { "Movement quantity must be greater than 0." }
        // Breakpoint: pause here to compare the item quantity before and after the manual movement.
        val lotResult = if (movementType.increasesStock()) {
            expiryLotRepository.addStock(inventoryItem.id, quantity, expiryDate, transactionId)
        } else {
            expiryLotRepository.deductStock(inventoryItem.id, quantity)
        }
        AppLogger.info(
            area = "Transactions",
            event = "transaction_quantity_changed",
            message = "Inventory quantity changed for stock transaction.",
            "type" to movementType.name,
            "item" to inventoryItem.name,
            "location" to inventoryItem.branchName,
            "before" to lotResult.quantityBefore,
            "quantity" to quantity,
            "after" to lotResult.quantityAfter
        )
        recordMovement(
            inventoryItem = inventoryItem,
            movementType = movementType,
            quantity = quantity,
            quantityBefore = lotResult.quantityBefore,
            quantityAfter = lotResult.quantityAfter,
            reason = reason,
            note = note,
            transactionAt = transactionAt,
            counterpartyId = counterpartyId,
            counterpartyName = counterpartyName,
            counterpartyType = counterpartyType,
            transactionId = transactionId,
            expiryDate = expiryDate
        ).getOrThrow()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.STOCK_MOVEMENT_RECORDED,
            inventoryItemId = inventoryItem.id,
            itemName = inventoryItem.name,
            quantity = quantity,
            unit = inventoryItem.unit,
            reason = reason,
            note = "Manual stock movement: ${movementType.name.lowercase().replace('_', ' ')}"
        ).getOrThrow()
    }

    // Breakpoint: inspect source, destination, and transfer quantity before Move Stock updates both locations.
    suspend fun transferBetweenBranches(
        sourceInventoryItem: InventoryItem,
        destinationBranch: Branch,
        quantity: Double,
        note: String,
        transactionAt: Long = System.currentTimeMillis(),
        transactionId: String = ""
    ): Result<Unit> = runCatching {
        require(sourceInventoryItem.id.isNotBlank()) { "Source inventory item is required." }
        require(destinationBranch.id.isNotBlank()) { "Destination branch is required." }
        require(sourceInventoryItem.branchId != destinationBranch.id) { "Source and destination branches cannot be the same." }
        require(quantity > 0.0) { "Transfer quantity must be greater than 0." }

        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val storeId = userProfile.currentStoreId ?: error("No store selected.")
        val stableTransactionId = transactionId.ifBlank { stockMovementsCollection(storeId).document().id }
        val inventoryCollection = inventoryItemsCollection(storeId)
        val sourceReference = inventoryCollection.document(sourceInventoryItem.id)
        val destinationMatch = inventoryCollection
            .whereEqualTo("branchId", destinationBranch.id)
            .get()
            .await()
            .documents
            .map { it.toInventoryItem() }
            .firstOrNull { it.matchesTransferDestination(sourceInventoryItem) }
        val destinationReference = destinationMatch?.let { inventoryCollection.document(it.id) }
            ?: inventoryCollection.document()
        val now = System.currentTimeMillis()
        val destinationItem = destinationMatch ?: sourceInventoryItem.copy(
            id = destinationReference.id,
            branchId = destinationBranch.id,
            branchName = destinationBranch.name,
            quantity = 0.0,
            expiryDate = 0L,
            expiryLotsInitialized = true,
            createdBy = userProfile.uid,
            createdAt = now,
            updatedBy = userProfile.uid,
            updatedAt = now
        )
        // Breakpoint: inspect sourceBefore/sourceAfter and destinationBefore/destinationAfter for the transfer.
        val transfer = expiryLotRepository.transferStock(sourceInventoryItem, destinationItem, quantity)
        AppLogger.info(
            area = "Transactions",
            event = "transaction_quantity_changed",
            message = "Move Stock quantities calculated.",
            "item" to sourceInventoryItem.name,
            "from" to sourceInventoryItem.branchName,
            "to" to destinationBranch.name,
            "quantity" to quantity,
            "sourceBefore" to transfer.sourceBefore,
            "sourceAfter" to transfer.sourceAfter,
            "destinationBefore" to transfer.destinationBefore,
            "destinationAfter" to transfer.destinationAfter
        )
        recordMovement(
            sourceInventoryItem,
            StockMovementType.BRANCH_TRANSFER_OUT,
            quantity,
            transfer.sourceBefore,
            transfer.sourceAfter,
            "Branch transfer",
            "Transferred to ${destinationBranch.name}${note.transferNoteSuffix()}",
            transactionAt = transactionAt,
            transactionId = stableTransactionId
        ).getOrThrow()
        recordMovement(
            destinationItem,
            StockMovementType.BRANCH_TRANSFER_IN,
            quantity,
            transfer.destinationBefore,
            transfer.destinationAfter,
            "Branch transfer",
            "Transferred from ${sourceInventoryItem.branchName.ifBlank { "Unassigned branch" }}${note.transferNoteSuffix()}",
            transactionAt = transactionAt,
            transactionId = stableTransactionId
        ).getOrThrow()

        val sourceItem = sourceInventoryItem
        activityRepository.addActivityLog(
            actionType = ActivityActionType.BRANCH_TRANSFER_RECORDED,
            inventoryItemId = sourceItem.id,
            itemName = sourceItem.name,
            quantity = quantity,
            unit = sourceItem.unit,
            note = "Transferred ${quantity.toCleanString()} ${sourceItem.unit} ${sourceItem.name} from ${sourceItem.branchName.ifBlank { "Unassigned branch" }} to ${destinationBranch.name}"
        ).getOrThrow()
    }

    // Breakpoint: inspect old quantity, new final quantity, and reason before Adjust Stock writes the final count.
    suspend fun adjustStock(
        inventoryItem: InventoryItem,
        newQuantity: Double,
        note: String,
        transactionAt: Long = System.currentTimeMillis(),
        transactionId: String = ""
    ): Result<Unit> = runCatching {
        require(inventoryItem.id.isNotBlank()) { "Inventory item is required." }
        require(newQuantity >= 0.0) { "Adjusted quantity cannot be below 0." }
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val storeId = userProfile.currentStoreId ?: error("No store selected.")
        val itemReference = inventoryItemsCollection(storeId).document(inventoryItem.id)
        val now = System.currentTimeMillis()
        // Breakpoint: pause inside the Firestore transaction to confirm the exact final quantity being saved.
        val adjustedSnapshot = firestoreDataSource.db.runTransaction { transaction ->
            val currentItem = transaction.get(itemReference).toInventoryItem()
            require(currentItem.id.isNotBlank()) { "Inventory item could not be found." }
            require(currentItem.quantity != newQuantity) { "Adjusted quantity must be different from current stock." }
            val status = InventoryStatusCalculator.calculate(currentItem.copy(quantity = newQuantity)).name
            // Adjust Stock corrects the counted item total only. Expiry-lot redistribution needs a
            // dedicated lot-counting flow, so existing lot documents are left untouched here.
            transaction.update(
                itemReference,
                mapOf(
                    "quantity" to newQuantity,
                    "status" to status,
                    "updatedBy" to userProfile.uid,
                    "updatedAt" to Timestamp(Date(now))
                )
            )
            currentItem to currentItem.copy(
                quantity = newQuantity,
                status = status,
                updatedBy = userProfile.uid,
                updatedAt = now
            )
        }.await()

        val beforeItem = adjustedSnapshot.first
        val afterItem = adjustedSnapshot.second
        // Breakpoint: inspect the calculated difference that will be logged as the adjustment record.
        val difference = afterItem.quantity - beforeItem.quantity
        AppLogger.info(
            area = "Transactions",
            event = "transaction_quantity_changed",
            message = "Adjust Stock quantity calculated.",
            "item" to afterItem.name,
            "location" to afterItem.branchName,
            "oldQuantity" to beforeItem.quantity,
            "newQuantity" to afterItem.quantity,
            "difference" to difference
        )
        recordMovement(
            inventoryItem = afterItem,
            movementType = StockMovementType.ADJUST_STOCK,
            quantity = abs(difference),
            quantityBefore = beforeItem.quantity,
            quantityAfter = afterItem.quantity,
            reason = "Stock adjustment",
            note = note,
            transactionAt = transactionAt,
            transactionId = transactionId
        ).getOrThrow()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.STOCK_MOVEMENT_RECORDED,
            inventoryItemId = afterItem.id,
            itemName = afterItem.name,
            quantity = abs(difference),
            unit = afterItem.unit,
            reason = "Stock adjustment",
            note = "Adjusted ${afterItem.name} stock from ${beforeItem.quantity.toCleanString()} ${afterItem.unit} to ${afterItem.quantity.toCleanString()} ${afterItem.unit} in ${afterItem.branchName.ifBlank { "Unassigned branch" }}"
        ).getOrThrow()
    }

    private fun StockMovementType.applyTo(quantityBefore: Double, quantity: Double): Double {
        // Direction rules live in one place so every manual movement applies quantity consistently.
        return when (this) {
            StockMovementType.STOCK_IN,
            StockMovementType.RETURN,
            StockMovementType.RESTOCK_RECEIVED,
            StockMovementType.BRANCH_TRANSFER_IN -> quantityBefore + quantity
            StockMovementType.STOCK_OUT,
            StockMovementType.DAMAGE,
            StockMovementType.EXPIRED,
            StockMovementType.WASTE,
            StockMovementType.SALES_DEDUCTION,
            StockMovementType.BRANCH_TRANSFER_OUT -> quantityBefore - quantity
            StockMovementType.ADJUST_STOCK -> quantityBefore
        }
    }

    private fun StockMovementType.increasesStock(): Boolean = this in setOf(
        StockMovementType.STOCK_IN,
        StockMovementType.RETURN,
        StockMovementType.RESTOCK_RECEIVED,
        StockMovementType.BRANCH_TRANSFER_IN
    )

    private suspend fun reversePositiveMovement(movement: StockMovement) {
        expiryLotRepository.deductStock(movement.inventoryItemId, movement.quantity)
    }

    private suspend fun reverseNegativeMovement(movement: StockMovement) {
        expiryLotRepository.addStock(
            inventoryItemId = movement.inventoryItemId,
            quantity = movement.quantity,
            expiryDate = movement.expiryDate.takeIf { it > 0L }
        )
    }

    private suspend fun reverseAdjustStock(storeId: String, movement: StockMovement) {
        val itemReference = inventoryItemsCollection(storeId).document(movement.inventoryItemId)
        val snapshot = itemReference.get().await()
        require(snapshot.exists()) { "${movement.itemName.ifBlank { "Inventory item" }} no longer exists." }
        val currentItem = snapshot.toInventoryItem()
        val restoredQuantity = movement.quantityBefore
        val now = System.currentTimeMillis()
        val restoredStatus = InventoryStatusCalculator.calculate(
            currentItem.copy(quantity = restoredQuantity)
        ).name
        itemReference.update(
            mapOf(
                "quantity" to restoredQuantity,
                "status" to restoredStatus,
                "updatedBy" to authManager.currentUserId.orEmpty(),
                "updatedAt" to Timestamp(Date(now))
            )
        ).await()
    }

    private suspend fun reverseTransferTransaction(storeId: String, transactionMovements: List<StockMovement>) {
        val outgoing = transactionMovements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name }
        val incoming = transactionMovements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name }

        when {
            outgoing != null && incoming != null -> {
                val sourceItem = getInventoryItemOrThrow(storeId, outgoing.inventoryItemId)
                val destinationItem = getInventoryItemOrThrow(storeId, incoming.inventoryItemId)
                expiryLotRepository.transferStock(
                    sourceItem = destinationItem,
                    destinationItem = sourceItem,
                    quantity = outgoing.quantity
                )
            }
            outgoing != null -> reverseNegativeMovement(outgoing)
            incoming != null -> reversePositiveMovement(incoming)
            else -> error("Transfer transaction could not be reversed.")
        }
    }

    private fun statusAfterMovement(item: InventoryItem, movementType: StockMovementType, quantityAfter: Double): InventoryStatus {
        return when {
            movementType == StockMovementType.WASTE && quantityAfter == 0.0 -> InventoryStatus.WASTED
            movementType == StockMovementType.EXPIRED && quantityAfter == 0.0 -> InventoryStatus.EXPIRED
            movementType in listOf(StockMovementType.STOCK_OUT, StockMovementType.DAMAGE, StockMovementType.SALES_DEDUCTION) && quantityAfter == 0.0 -> InventoryStatus.OUT_OF_STOCK
            else -> InventoryStatusCalculator.calculate(item.copy(quantity = quantityAfter))
        }
    }

    private fun InventoryItem.matchesTransferDestination(source: InventoryItem): Boolean {
        // Destination matching intentionally stays exact: SKU first, then barcode, then name+brand.
        // This avoids accidental fuzzy matches between similar supermarket products.
        return when {
            source.sku.isNotBlank() -> sku.equals(source.sku, ignoreCase = true)
            source.barcode.isNotBlank() -> barcode.equals(source.barcode, ignoreCase = true)
            else -> name.equals(source.name, ignoreCase = true) && brand.equals(source.brand, ignoreCase = true)
        }
    }

    private suspend fun currentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .toObject(UserProfile::class.java)
    }

    private fun inventoryItemsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private suspend fun getInventoryItemOrThrow(storeId: String, inventoryItemId: String): InventoryItem {
        val snapshot = inventoryItemsCollection(storeId).document(inventoryItemId).get().await()
        require(snapshot.exists()) { "Inventory item no longer exists." }
        return snapshot.toInventoryItem()
    }

    private fun stockMovementsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.STOCK_MOVEMENTS_COLLECTION)

    private fun buildMovement(
        id: String,
        transactionId: String,
        inventoryItem: InventoryItem,
        movementType: StockMovementType,
        quantity: Double,
        quantityBefore: Double,
        quantityAfter: Double,
        reason: String,
        note: String,
        userProfile: UserProfile,
        counterpartyId: String,
        counterpartyName: String,
        counterpartyType: String,
        expiryDate: Long?,
        transactionAt: Long,
        createdAt: Long
    ): StockMovement = StockMovement(
        id = id,
        transactionId = transactionId,
        inventoryItemId = inventoryItem.id,
        itemName = inventoryItem.name,
        sku = inventoryItem.sku,
        barcode = inventoryItem.barcode,
        branchId = inventoryItem.branchId,
        branchName = inventoryItem.branchName,
        movementType = movementType.name,
        quantity = quantity,
        unit = inventoryItem.unit,
        quantityBefore = quantityBefore,
        quantityAfter = quantityAfter,
        reason = reason.trim(),
        note = note.trim(),
        performedBy = userProfile.uid,
        performedByName = userProfile.displayName.ifBlank { userProfile.email },
        counterpartyId = counterpartyId,
        counterpartyName = counterpartyName,
        counterpartyType = counterpartyType,
        expiryDate = expiryDate?.takeIf { it > 0L } ?: 0L,
        costPrice = inventoryItem.costPrice,
        imageUrl = inventoryItem.imageUrl,
        transactionAt = transactionAt,
        createdAt = createdAt
    )

    private fun DocumentSnapshot.toInventoryItem(): InventoryItem = InventoryItem(
        id = getString("id").orEmpty().ifBlank { id },
        sku = getString("sku").orEmpty(),
        barcode = getString("barcode").orEmpty(),
        name = getString("name").orEmpty(),
        brand = getString("brand").orEmpty(),
        category = getString("category").orEmpty(),
        branchId = getString("branchId").orEmpty(),
        branchName = getString("branchName").orEmpty(),
        storageLocation = getString("storageLocation").orEmpty(),
        quantity = (get("quantity") as? Number)?.toDouble() ?: 0.0,
        unit = getString("unit").orEmpty(),
        costPrice = number("costPrice")?.toDouble() ?: 0.0,
        sellingPrice = number("sellingPrice")?.toDouble() ?: 0.0,
        minimumStockLevel = number("minimumStockLevel")?.toInt() ?: 0,
        reorderPoint = number("reorderPoint")?.toInt()?.takeIf { it > 0 } ?: number("reorderThreshold")?.toInt() ?: 0,
        maximumStockLevel = number("maximumStockLevel")?.toInt() ?: 0,
        reorderThreshold = (get("reorderThreshold") as? Number)?.toDouble() ?: 0.0,
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
        createdBy = getString("createdBy").orEmpty(),
        updatedBy = getString("updatedBy").orEmpty(),
        createdAt = dateMillis("createdAt"),
        updatedAt = dateMillis("updatedAt"),
        status = getString("status").orEmpty().ifBlank { InventoryStatus.FRESH.name }
    )

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
        "updatedAt" to updatedAt.toFirestoreDateValue()
    )

    private fun DocumentSnapshot.toStockMovement(): StockMovement = StockMovement(
        id = getString("id").orEmpty().ifBlank { id },
        transactionId = getString("transactionId").orEmpty(),
        inventoryItemId = getString("inventoryItemId").orEmpty(),
        itemName = getString("itemName").orEmpty(),
        sku = getString("sku").orEmpty(),
        barcode = getString("barcode").orEmpty(),
        branchId = getString("branchId").orEmpty(),
        branchName = getString("branchName").orEmpty(),
        movementType = getString("movementType").orEmpty(),
        quantity = (get("quantity") as? Number)?.toDouble() ?: 0.0,
        unit = getString("unit").orEmpty(),
        quantityBefore = (get("quantityBefore") as? Number)?.toDouble() ?: 0.0,
        quantityAfter = (get("quantityAfter") as? Number)?.toDouble() ?: 0.0,
        reason = getString("reason").orEmpty(),
        note = getString("note").orEmpty(),
        performedBy = getString("performedBy").orEmpty(),
        performedByName = getString("performedByName").orEmpty(),
        counterpartyId = getString("counterpartyId").orEmpty(),
        counterpartyName = getString("counterpartyName").orEmpty(),
        counterpartyType = getString("counterpartyType").orEmpty(),
        expiryDate = dateMillis("expiryDate"),
        costPrice = (get("costPrice") as? Number)?.toDouble() ?: 0.0,
        imageUrl = getString("imageUrl").orEmpty(),
        transactionAt = dateMillis("transactionAt").takeIf { it > 0L } ?: dateMillis("createdAt"),
        createdAt = dateMillis("createdAt")
    )

    private fun StockMovement.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "transactionId" to transactionId,
        "inventoryItemId" to inventoryItemId,
        "itemName" to itemName,
        "sku" to sku,
        "barcode" to barcode,
        "branchId" to branchId,
        "branchName" to branchName,
        "movementType" to movementType,
        "quantity" to quantity,
        "unit" to unit,
        "quantityBefore" to quantityBefore,
        "quantityAfter" to quantityAfter,
        "reason" to reason,
        "note" to note,
        "performedBy" to performedBy,
        "performedByName" to performedByName,
        "counterpartyId" to counterpartyId,
        "counterpartyName" to counterpartyName,
        "counterpartyType" to counterpartyType,
        "expiryDate" to expiryDate.toFirestoreDateValue(),
        "costPrice" to costPrice,
        "imageUrl" to imageUrl,
        "transactionAt" to transactionAt.toFirestoreDateValue(),
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.dateMillis(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun DocumentSnapshot.number(field: String): Number? = get(field) as? Number

    private fun DocumentSnapshot.stringList(field: String): List<String> {
        val value = get(field)
        return when (value) {
            is List<*> -> value.filterIsInstance<String>().map { it.trim() }.filter { it.isNotBlank() }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun Long.toFirestoreDateValue(): Any =
        if (this > 0L) Timestamp(Date(this)) else this

    private fun String.transferNoteSuffix(): String =
        trim().takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()

    private fun findLaterDependencies(
        transactionMovements: List<StockMovement>,
        allMovements: List<StockMovement>
    ): List<StockMovement> {
        val transactionIds = transactionMovements.map { it.id }.toSet()
        return transactionMovements
            .flatMap { currentMovement ->
                val movementOccurredAt = currentMovement.occurredAt()
                // Latest-only delete keeps quantity reversal safe because no newer transaction has
                // already changed this same inventory record after the transaction being removed.
                allMovements.filter { candidate ->
                    candidate.id !in transactionIds &&
                        candidate.inventoryItemId == currentMovement.inventoryItemId &&
                        candidate.occurredAt() > movementOccurredAt
                }
            }
            .distinctBy { it.transactionId.ifBlank { it.id } }
            .sortedBy { it.occurredAt() }
    }

    private fun resolveTransactionMovements(
        allMovements: List<StockMovement>,
        requestedId: String
    ): List<StockMovement> {
        val groupedMatches = allMovements
            .filter { it.transactionId == requestedId }
            .sortedBy { it.occurredAt() }
        if (groupedMatches.isNotEmpty()) return groupedMatches

        val primary = allMovements.firstOrNull { it.id == requestedId } ?: return emptyList()
        if (primary.movementType !in TRANSFER_TYPES || primary.transactionId.isNotBlank()) {
            return listOf(primary)
        }

        // Older transfer records were stored as two separate documents with no shared transaction id.
        val counterpartType = when (primary.movementType) {
            StockMovementType.BRANCH_TRANSFER_OUT.name -> StockMovementType.BRANCH_TRANSFER_IN.name
            StockMovementType.BRANCH_TRANSFER_IN.name -> StockMovementType.BRANCH_TRANSFER_OUT.name
            else -> return listOf(primary)
        }
        val pair = allMovements
            .asSequence()
            .filter { it.id != primary.id }
            .filter { it.transactionId.isBlank() }
            .filter { it.movementType == counterpartType }
            .filter { it.quantity == primary.quantity }
            .filter { it.productKey() == primary.productKey() }
            .filter { abs(it.occurredAt() - primary.occurredAt()) <= LEGACY_TRANSFER_PAIR_WINDOW_MS }
            .minByOrNull { abs(it.occurredAt() - primary.occurredAt()) }

        return listOfNotNull(primary, pair).sortedBy { it.occurredAt() }
    }

    private fun List<StockMovement>.transactionMode(): TransactionMode = when {
        any { it.movementType in TRANSFER_TYPES } -> TransactionMode.MOVE_STOCK
        any { it.movementType == StockMovementType.ADJUST_STOCK.name } -> TransactionMode.ADJUST_STOCK
        any { it.movementType in OUT_TYPES } -> TransactionMode.STOCK_OUT
        else -> TransactionMode.STOCK_IN
    }

    private fun TransactionMode.displayName(): String = when (this) {
        TransactionMode.STOCK_IN -> "Stock In"
        TransactionMode.STOCK_OUT -> "Stock Out"
        TransactionMode.MOVE_STOCK -> "Move Stock"
        TransactionMode.ADJUST_STOCK -> "Adjust Stock"
    }

    private fun StockMovement.occurredAt(): Long = transactionAt.takeIf { it > 0L } ?: createdAt

    private fun StockMovement.productKey(): String = when {
        sku.isNotBlank() -> "sku:${sku.trim().lowercase()}"
        barcode.isNotBlank() -> "barcode:${barcode.trim().lowercase()}"
        else -> "name:${itemName.trim().lowercase()}"
    }

    private fun StockMovement.summaryLine(): String = buildString {
        append(movementType.toDisplayName())
        occurredAt().takeIf { it > 0L }?.let {
            append(" on ")
            append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))
        }
    }

    private fun String.toDisplayName(): String = lowercase()
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun Double.toCleanString(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()

    companion object {
        private val TRANSFER_TYPES = setOf(
            StockMovementType.BRANCH_TRANSFER_OUT.name,
            StockMovementType.BRANCH_TRANSFER_IN.name
        )
        private val OUT_TYPES = setOf(
            StockMovementType.STOCK_OUT.name,
            StockMovementType.DAMAGE.name,
            StockMovementType.EXPIRED.name,
            StockMovementType.WASTE.name,
            StockMovementType.SALES_DEDUCTION.name
        )
        private const val LEGACY_TRANSFER_PAIR_WINDOW_MS = 2 * 60 * 1000L
    }
}
