package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ActivityActionType
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class SupplierRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    private val activityRepository = ActivityRepository(authManager, firestoreDataSource)

    fun observeSuppliers(includeArchived: Boolean = false): Flow<Result<List<Supplier>>> = callbackFlow {
        val storeId = currentUserProfile()?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = suppliersCollection(storeId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else {
                    trySend(
                        Result.success(
                            snapshot?.documents.orEmpty()
                                .map { it.toSupplier() }
                                .filter { includeArchived || !it.isArchived }
                        )
                    )
                }
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    suspend fun addSupplier(input: Supplier): Result<String> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        require(input.name.trim().isNotEmpty()) { "Supplier name is required." }
        val duplicate = suppliersCollection(storeId).get().await().documents
            .map { it.toSupplier() }
            .firstOrNull { it.name.equals(input.name.trim(), ignoreCase = true) }
        require(duplicate == null) { "This partner already exists." }
        val document = suppliersCollection(storeId).document()
        val now = System.currentTimeMillis()
        val supplier = input.copy(
            id = document.id,
            name = input.name.trim(),
            partnerType = PartnerType.fromValue(input.partnerType).value,
            createdBy = userId,
            updatedBy = userId,
            createdAt = now,
            updatedAt = now
        )
        document.set(supplier.toFirestoreMap()).await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.SUPPLIER_CREATED,
            inventoryItemId = null,
            itemName = supplier.name,
            quantity = null,
            unit = null,
            note = "Supplier created"
        ).getOrThrow()
        document.id
    }

    suspend fun updateSupplier(input: Supplier): Result<Unit> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        require(input.id.isNotBlank()) { "Supplier id is required." }
        require(input.name.trim().isNotEmpty()) { "Supplier name is required." }
        val existing = getSupplier(input.id).getOrThrow()
        val updated = input.copy(
            name = input.name.trim(),
            partnerType = PartnerType.fromValue(input.partnerType).value,
            createdBy = existing.createdBy.ifBlank { userId },
            createdAt = existing.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            updatedBy = userId,
            updatedAt = System.currentTimeMillis()
        )
        suppliersCollection(currentStoreId()).document(input.id).set(updated.toFirestoreMap()).await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.SUPPLIER_UPDATED,
            inventoryItemId = null,
            itemName = updated.name,
            quantity = null,
            unit = null,
            note = "Supplier updated"
        ).getOrThrow()
    }

    // Breakpoint: inspect supplier archive metadata before the partner disappears from active lists.
    suspend fun archiveSupplier(supplierId: String, reason: String = "Archived partner"): Result<Unit> = runCatching {
        require(supplierId.isNotBlank()) { "Supplier id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        val supplier = getSupplier(supplierId).getOrThrow()
        val now = System.currentTimeMillis()
        suppliersCollection(storeId).document(supplierId).update(
            mapOf(
                "isArchived" to true,
                "archivedAt" to Timestamp(Date(now)),
                "archivedBy" to userId,
                "archiveReason" to reason,
                "updatedBy" to userId,
                "updatedAt" to Timestamp(Date(now))
            )
        ).await()
        activityRepository.addActivityLog(
            actionType = ActivityActionType.SUPPLIER_DELETED,
            inventoryItemId = null,
            itemName = supplier.name,
            quantity = null,
            unit = null,
            note = reason
        ).getOrThrow()
    }

    suspend fun restoreSupplier(supplierId: String): Result<Unit> = runCatching {
        require(supplierId.isNotBlank()) { "Supplier id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        suppliersCollection(currentStoreId()).document(supplierId).update(
            mapOf(
                "isArchived" to false,
                "archivedAt" to 0L,
                "archivedBy" to "",
                "archiveReason" to "",
                "updatedBy" to userId,
                "updatedAt" to System.currentTimeMillis().toFirestoreDateValue()
            )
        ).await()
    }

    suspend fun getSupplier(supplierId: String): Result<Supplier> = runCatching {
        val snapshot = suppliersCollection(currentStoreId()).document(supplierId).get().await()
        if (!snapshot.exists()) error("Supplier not found.")
        snapshot.toSupplier()
    }

    suspend fun updateFavorite(supplierId: String, isFavorite: Boolean): Result<Unit> = runCatching {
        require(supplierId.isNotBlank()) { "Partner id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        suppliersCollection(currentStoreId()).document(supplierId).update(
            mapOf(
                "isFavorite" to isFavorite,
                "updatedBy" to userId,
                "updatedAt" to System.currentTimeMillis().toFirestoreDateValue()
            )
        ).await()
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

    private fun suppliersCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.SUPPLIERS_COLLECTION)

    private fun inventoryItemsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun DocumentSnapshot.toSupplier(): Supplier = Supplier(
        id = getString("id").orEmpty().ifBlank { id },
        name = getString("name").orEmpty(),
        partnerType = PartnerType.fromValue(getString("partnerType")).value,
        isFavorite = getBoolean("isFavorite") ?: false,
        contactPerson = getString("contactPerson").orEmpty(),
        phone = getString("phone").orEmpty(),
        email = getString("email").orEmpty(),
        address = getString("address").orEmpty(),
        paymentTerms = getString("paymentTerms").orEmpty(),
        leadTimeDays = (get("leadTimeDays") as? Number)?.toInt() ?: 0,
        notes = getString("notes").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        updatedBy = getString("updatedBy").orEmpty(),
        createdAt = dateMillis("createdAt"),
        updatedAt = dateMillis("updatedAt"),
        isArchived = getBoolean("isArchived") ?: false,
        archivedAt = dateMillis("archivedAt"),
        archivedBy = getString("archivedBy").orEmpty(),
        archiveReason = getString("archiveReason").orEmpty()
    )

    private fun Supplier.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name.trim(),
        "partnerType" to PartnerType.fromValue(partnerType).value,
        "isFavorite" to isFavorite,
        "contactPerson" to contactPerson.trim(),
        "phone" to phone.trim(),
        "email" to email.trim(),
        "address" to address.trim(),
        "paymentTerms" to paymentTerms.trim(),
        "leadTimeDays" to leadTimeDays,
        "notes" to notes.trim(),
        "createdBy" to createdBy,
        "updatedBy" to updatedBy,
        "createdAt" to createdAt.toFirestoreDateValue(),
        "updatedAt" to updatedAt.toFirestoreDateValue(),
        "isArchived" to isArchived,
        "archivedAt" to archivedAt.toFirestoreDateValue(),
        "archivedBy" to archivedBy,
        "archiveReason" to archiveReason
    )

    private fun DocumentSnapshot.dateMillis(field: String): Long =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }

    private fun Long.toFirestoreDateValue(): Any =
        if (this > 0L) Timestamp(Date(this)) else this
}
