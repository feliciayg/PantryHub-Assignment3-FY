package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.model.InventoryOptionType
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

data class InventoryOptionUsage(
    val count: Int,
    val affectedItems: List<String>
)

/**
 * Owns category and brand master data and the cross-record updates required by rename/delete.
 *
 * Completed movements are intentionally not rewritten: they preserve the item description that
 * was recorded when the transaction happened.
 */
class InventoryOptionRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    suspend fun loadOptions(type: InventoryOptionType): Result<List<InventoryOption>> = runCatching {
        val storeId = currentStoreId()
        ensureInitialized(storeId)
        optionCollection(storeId, type).get().await().documents
            .map { it.toInventoryOption() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun addOption(type: InventoryOptionType, name: String): Result<InventoryOption> = runCatching {
        val storeId = currentStoreId()
        ensureInitialized(storeId)
        val cleanedName = name.trim()
        require(cleanedName.isNotBlank()) { "A name is required." }
        require(findByName(storeId, type, cleanedName) == null) { "This choice already exists." }

        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val now = System.currentTimeMillis()
        val document = optionCollection(storeId, type).document()
        val option = InventoryOption(
            id = document.id,
            name = cleanedName,
            createdBy = userId,
            createdAt = now,
            updatedBy = userId,
            updatedAt = now
        )
        document.set(option.toFirestoreMap()).await()
        option
    }

    suspend fun usageCount(type: InventoryOptionType, name: String): Result<Int> = runCatching {
        inventoryDocuments(currentStoreId()).count {
            it.getString(type.inventoryField).orEmpty().equals(name.trim(), ignoreCase = true)
        }
    }

    suspend fun usageDetails(type: InventoryOptionType, name: String): Result<InventoryOptionUsage> =
        runCatching {
            val matchingDocuments = matchingInventoryDocuments(currentStoreId(), type, name)
            InventoryOptionUsage(
                count = matchingDocuments.size,
                affectedItems = matchingDocuments.map { document ->
                    val itemName = document.getString("name").orEmpty().ifBlank { "Unnamed item" }
                    val branchName = document.getString("branchName").orEmpty()
                    if (branchName.isBlank()) itemName else "$itemName - $branchName"
                }.distinct().sorted()
            )
        }

    suspend fun renameOption(
        type: InventoryOptionType,
        option: InventoryOption,
        newName: String
    ): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        val cleanedName = newName.trim()
        require(option.id.isNotBlank()) { "Choice id is required." }
        require(cleanedName.isNotBlank()) { "A name is required." }
        val duplicate = findByName(storeId, type, cleanedName)
        require(duplicate == null || duplicate.id == option.id) { "This choice already exists." }

        updateInventoryField(storeId, type, option.name, cleanedName)
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        optionCollection(storeId, type).document(option.id).update(
            mapOf(
                "name" to cleanedName,
                "updatedBy" to userId,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    suspend fun deleteOption(
        type: InventoryOptionType,
        option: InventoryOption,
        replacementName: String?
    ): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        require(option.id.isNotBlank()) { "Choice id is required." }
        val affectedDocuments = matchingInventoryDocuments(storeId, type, option.name)
        if (affectedDocuments.isNotEmpty()) {
            require(replacementName != null) { "Select a replacement before deleting this choice." }
            val cleanedReplacement = replacementName.trim()
            if (type == InventoryOptionType.CATEGORY) {
                require(cleanedReplacement.isNotBlank()) { "A replacement category is required." }
                ensureReplacementOption(storeId, type, cleanedReplacement)
            } else if (cleanedReplacement.isNotBlank()) {
                ensureReplacementOption(storeId, type, cleanedReplacement)
            }
            updateDocumentsField(affectedDocuments, type.inventoryField, cleanedReplacement)
        }

        // Delete only after every dependent inventory record has been updated successfully.
        optionCollection(storeId, type).document(option.id).delete().await()
    }

    private suspend fun ensureInitialized(storeId: String) {
        val marker = settingsCollection(storeId).document(OPTIONS_INITIALIZATION_DOCUMENT)
        if (marker.get().await().exists()) return

        val inventory = inventoryDocuments(storeId)
        val categories = (DEFAULT_CATEGORIES + inventory.mapNotNull { it.getString("category") })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val brands = inventory.mapNotNull { it.getString("brand") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val now = System.currentTimeMillis()
        val writes = buildList {
            categories.forEach { add(InventoryOptionType.CATEGORY to it) }
            brands.forEach { add(InventoryOptionType.BRAND to it) }
        }
        writes.chunked(FIRESTORE_BATCH_WRITE_LIMIT).forEach { chunk ->
            val batch = firestoreDataSource.db.batch()
            chunk.forEach { (type, name) ->
                val document = optionCollection(storeId, type).document()
                batch.set(
                    document,
                    InventoryOption(
                        id = document.id,
                        name = name,
                        createdBy = userId,
                        createdAt = now,
                        updatedBy = userId,
                        updatedAt = now
                    ).toFirestoreMap()
                )
            }
            batch.commit().await()
        }
        marker.set(
            mapOf(
                "initialized" to true,
                "initializedBy" to userId,
                "initializedAt" to Timestamp.now()
            )
        ).await()
    }

    private suspend fun ensureReplacementOption(
        storeId: String,
        type: InventoryOptionType,
        name: String
    ) {
        if (findByName(storeId, type, name) != null) return
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val document = optionCollection(storeId, type).document()
        val now = System.currentTimeMillis()
        document.set(
            InventoryOption(
                id = document.id,
                name = name,
                createdBy = userId,
                createdAt = now,
                updatedBy = userId,
                updatedAt = now
            ).toFirestoreMap()
        ).await()
    }

    private suspend fun updateInventoryField(
        storeId: String,
        type: InventoryOptionType,
        oldName: String,
        newName: String
    ) {
        updateDocumentsField(
            matchingInventoryDocuments(storeId, type, oldName),
            type.inventoryField,
            newName
        )
    }

    private suspend fun updateDocumentsField(
        documents: List<DocumentSnapshot>,
        field: String,
        newValue: String
    ) {
        if (documents.isEmpty()) return
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        documents.chunked(FIRESTORE_BATCH_WRITE_LIMIT).forEach { chunk ->
            val batch = firestoreDataSource.db.batch()
            chunk.forEach { document ->
                batch.update(
                    document.reference,
                    mapOf(
                        field to newValue,
                        "updatedBy" to userId,
                        "updatedAt" to Timestamp.now()
                    )
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun matchingInventoryDocuments(
        storeId: String,
        type: InventoryOptionType,
        name: String
    ): List<DocumentSnapshot> = inventoryDocuments(storeId).filter {
        it.getString(type.inventoryField).orEmpty().equals(name.trim(), ignoreCase = true)
    }

    private suspend fun findByName(
        storeId: String,
        type: InventoryOptionType,
        name: String
    ): InventoryOption? = optionCollection(storeId, type).get().await().documents
        .map { it.toInventoryOption() }
        .firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private suspend fun inventoryDocuments(storeId: String): List<DocumentSnapshot> =
        inventoryCollection(storeId).get().await().documents

    private suspend fun currentStoreId(): String {
        val uid = authManager.currentUserId ?: error("You must be logged in.")
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .toObject(UserProfile::class.java)
            ?.currentStoreId
            ?.takeIf { it.isNotBlank() }
            ?: error("No store selected.")
    }

    private fun optionCollection(storeId: String, type: InventoryOptionType) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(type.collectionName)

    private fun settingsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_OPTION_SETTINGS_COLLECTION)

    private fun inventoryCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun InventoryOption.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "createdBy" to createdBy,
        "createdAt" to Timestamp(createdAt / 1000, ((createdAt % 1000) * 1_000_000).toInt()),
        "updatedBy" to updatedBy,
        "updatedAt" to Timestamp(updatedAt / 1000, ((updatedAt % 1000) * 1_000_000).toInt())
    )

    private fun DocumentSnapshot.toInventoryOption(): InventoryOption = InventoryOption(
        id = getString("id").orEmpty().ifBlank { id },
        name = getString("name").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        updatedBy = getString("updatedBy").orEmpty(),
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L
    )

    private companion object {
        const val OPTIONS_INITIALIZATION_DOCUMENT = "categoryBrandDefaults"
        const val FIRESTORE_BATCH_WRITE_LIMIT = 450
        val DEFAULT_CATEGORIES = listOf(
            "Vegetable",
            "Dairy",
            "Drinks",
            "Meat",
            "Fruit",
            "Frozen",
            "Canned",
            "Snacks",
            "Other"
        )
    }
}
