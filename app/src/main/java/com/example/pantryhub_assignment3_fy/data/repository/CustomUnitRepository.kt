package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

/**
 * Stores only user-created units. Standard units remain fixed in Kotlin because changing their
 * meaning would make historical inventory quantities ambiguous.
 */
class CustomUnitRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    suspend fun loadUnits(): Result<List<InventoryOption>> = runCatching {
        val storeId = currentStoreId()
        ensureExistingCustomUnitsImported(storeId)
        unitsCollection(storeId).get().await().documents
            .map { it.toInventoryOption() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun addUnit(name: String): Result<InventoryOption> = runCatching {
        val storeId = currentStoreId()
        val cleanedName = name.trim()
        require(cleanedName.isNotBlank()) { "Unit is required." }
        require(cleanedName.none { it.isWhitespace() }) { "Use a short unit symbol without spaces." }
        require(STANDARD_UNITS.none { it.equals(cleanedName, ignoreCase = true) }) {
            "This standard unit is already available."
        }
        val existing = unitsCollection(storeId).get().await().documents
            .map { it.toInventoryOption() }
            .firstOrNull { it.name.equals(cleanedName, ignoreCase = true) }
        require(existing == null) { "This custom unit already exists." }

        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val now = System.currentTimeMillis()
        val document = unitsCollection(storeId).document()
        val unit = InventoryOption(
            id = document.id,
            name = cleanedName,
            createdBy = userId,
            createdAt = now,
            updatedBy = userId,
            updatedAt = now
        )
        document.set(unit.toFirestoreMap()).await()
        unit
    }

    suspend fun deleteUnusedUnit(unit: InventoryOption): Result<Unit> = runCatching {
        val storeId = currentStoreId()
        val usageCount = inventoryCollection(storeId).get().await().documents.count {
            it.getString("unit").orEmpty().equals(unit.name, ignoreCase = true)
        }
        require(usageCount == 0) {
            "This unit is used by $usageCount inventory item${if (usageCount == 1) "" else "s"}. Change those items before deleting it."
        }
        unitsCollection(storeId).document(unit.id).delete().await()
    }

    private suspend fun ensureExistingCustomUnitsImported(storeId: String) {
        val marker = settingsCollection(storeId).document(UNIT_INITIALIZATION_DOCUMENT)
        if (marker.get().await().exists()) return
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val now = System.currentTimeMillis()
        val existingCustomUnits = inventoryCollection(storeId).get().await().documents
            .mapNotNull { it.getString("unit") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { candidate -> STANDARD_UNITS.none { it.equals(candidate, ignoreCase = true) } }
            .distinctBy { it.lowercase() }
        if (existingCustomUnits.isNotEmpty()) {
            val batch = firestoreDataSource.db.batch()
            existingCustomUnits.forEach { name ->
                val document = unitsCollection(storeId).document()
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

    private fun unitsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.UNITS_COLLECTION)

    private fun inventoryCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun settingsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_OPTION_SETTINGS_COLLECTION)

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

    companion object {
        val STANDARD_UNITS = listOf("pcs", "box", "pack", "carton", "bottle", "can", "kg", "g", "L", "ml")
        private const val UNIT_INITIALIZATION_DOCUMENT = "customUnitDefaults"
    }
}
