package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BranchRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    private val inventoryRepository = InventoryRepository(authManager, firestoreDataSource)

    fun observeBranches(includeArchived: Boolean = false): Flow<Result<List<Branch>>> = callbackFlow {
        val storeId = currentUserProfile()?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        ensureDefaultBranch(storeId).onFailure { trySend(Result.failure(it)) }
        var registration: ListenerRegistration? = branchesCollection(storeId)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else {
                    trySend(
                        Result.success(
                            snapshot?.documents.orEmpty()
                                .map { it.toBranch() }
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

    suspend fun addBranch(input: Branch): Result<String> = runCatching {
        AppLogger.info(
            area = "Locations",
            event = "location_create_start",
            message = "Creating location.",
            "location" to input.name.trim()
        )
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val storeId = currentStoreId()
        require(input.name.trim().isNotEmpty()) { "Branch name is required." }
        val existing = branchesCollection(storeId).get().await().documents
            .map { it.toBranch() }
            .firstOrNull { it.name.equals(input.name.trim(), ignoreCase = true) }
        if (existing != null) {
            inventoryRepository.ensureRecordsForBranch(existing).getOrThrow()
            return@runCatching existing.id
        }
        val document = branchesCollection(storeId).document()
        val now = System.currentTimeMillis()
        val branch = input.copy(
            id = document.id,
            name = input.name.trim(),
            address = input.address.trim(),
            notes = input.notes.trim(),
            createdBy = userId,
            createdAt = now,
            updatedBy = userId,
            updatedAt = now
        )
        document.set(branch.toFirestoreMap()).await()
        val createdRecords = inventoryRepository.ensureRecordsForBranch(branch).getOrThrow()
        AppLogger.info(
            area = "Locations",
            event = "location_create_success",
            message = "Location created.",
            "location" to branch.name
        )
        if (createdRecords > 0) {
            AppLogger.info(
                area = "Locations",
                event = "zero_stock_records_created",
                message = "Created missing zero-stock records for new location.",
                "location" to branch.name,
                "count" to createdRecords
            )
        }
        document.id
    }

    suspend fun updateBranch(input: Branch): Result<Unit> = runCatching {
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        require(input.id.isNotBlank()) { "Branch id is required." }
        require(input.name.trim().isNotEmpty()) { "Branch name is required." }
        val existing = getBranch(input.id).getOrThrow()
        val updated = input.copy(
            name = input.name.trim(),
            address = input.address.trim(),
            notes = input.notes.trim(),
            createdBy = existing.createdBy.ifBlank { userId },
            createdAt = existing.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            updatedBy = userId,
            updatedAt = System.currentTimeMillis()
        )
        branchesCollection(currentStoreId()).document(input.id).set(updated.toFirestoreMap()).await()
        AppLogger.info(
            area = "Locations",
            event = "location_update_success",
            message = "Location updated.",
            "location" to updated.name
        )
    }

    // Breakpoint: inspect location archive metadata before the branch is hidden from active selectors.
    suspend fun archiveBranch(branchId: String, reason: String = "Archived location"): Result<Unit> = runCatching {
        require(branchId.isNotBlank()) { "Branch id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        val now = System.currentTimeMillis()
        branchesCollection(currentStoreId()).document(branchId).update(
            mapOf(
                "isArchived" to true,
                "archivedAt" to Timestamp(Date(now)),
                "archivedBy" to userId,
                "archiveReason" to reason,
                "updatedBy" to userId,
                "updatedAt" to Timestamp(Date(now))
            )
        ).await()
        AppLogger.info(
            area = "Locations",
            event = "location_archive_success",
            message = "Location archived.",
            "locationId" to branchId
        )
    }

    suspend fun restoreBranch(branchId: String): Result<Unit> = runCatching {
        require(branchId.isNotBlank()) { "Branch id is required." }
        val userId = authManager.currentUserId ?: error("You must be logged in.")
        branchesCollection(currentStoreId()).document(branchId).update(
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

    suspend fun getBranch(branchId: String): Result<Branch> = runCatching {
        val snapshot = branchesCollection(currentStoreId()).document(branchId).get().await()
        if (!snapshot.exists()) error("Branch not found.")
        snapshot.toBranch()
    }

    private suspend fun ensureDefaultBranch(storeId: String): Result<Unit> = runCatching {
        val snapshot = branchesCollection(storeId).limit(1).get().await()
        if (!snapshot.isEmpty) return@runCatching

        val userId = authManager.currentUserId.orEmpty()
        val document = branchesCollection(storeId).document(DEFAULT_BRANCH_ID)
        val now = System.currentTimeMillis()
        val branch = Branch(
            id = document.id,
            name = MAIN_BRANCH_NAME,
            createdBy = userId,
            createdAt = now,
            updatedBy = userId,
            updatedAt = now
        )
        // Default branch creation only happens for an empty store branch list; old inventory records are not mass-updated.
        document.set(branch.toFirestoreMap()).await()
        inventoryRepository.ensureRecordsForBranch(branch).getOrThrow()
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

    private fun branchesCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.BRANCHES_COLLECTION)

    private fun inventoryItemsCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.INVENTORY_ITEMS_COLLECTION)

    private fun DocumentSnapshot.toBranch(): Branch = Branch(
        id = getString("id").orEmpty().ifBlank { id },
        name = getString("name").orEmpty(),
        address = getString("address").orEmpty(),
        notes = getString("notes").orEmpty(),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = dateMillis("createdAt"),
        updatedBy = getString("updatedBy").orEmpty(),
        updatedAt = dateMillis("updatedAt"),
        isArchived = getBoolean("isArchived") ?: false,
        archivedAt = dateMillis("archivedAt"),
        archivedBy = getString("archivedBy").orEmpty(),
        archiveReason = getString("archiveReason").orEmpty()
    )

    private fun Branch.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name.trim(),
        "address" to address.trim(),
        "notes" to notes.trim(),
        "createdBy" to createdBy,
        "createdAt" to createdAt.toFirestoreDateValue(),
        "updatedBy" to updatedBy,
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

    companion object {
        private const val DEFAULT_BRANCH_ID = "main_branch"
        const val MAIN_BRANCH_NAME = "Main Branch"
    }
}
