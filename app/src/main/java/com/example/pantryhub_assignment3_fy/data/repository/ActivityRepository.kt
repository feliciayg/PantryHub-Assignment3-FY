package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.ActivityActionType
import com.example.pantryhub_assignment3_fy.model.ActivityLog
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Owns append-only activity records under `stores/{storeId}/activityLogs`.
 */
class ActivityRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    fun observeActivityLogs(): Flow<Result<List<ActivityLog>>> = callbackFlow {
        val storeId = currentUserProfile()?.currentStoreId
        if (storeId.isNullOrBlank()) {
            trySend(Result.failure(IllegalStateException("No store selected.")))
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = activityCollection(storeId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else {
                    trySend(Result.success(snapshot?.documents.orEmpty().map { it.toActivityLog() }))
                }
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    suspend fun addActivityLog(
        actionType: ActivityActionType,
        inventoryItemId: String?,
        itemName: String,
        quantity: Double?,
        unit: String?,
        reason: String? = null,
        note: String? = null
    ): Result<String> = runCatching {
        val userProfile = currentUserProfile() ?: error("You must be logged in.")
        val storeId = userProfile.currentStoreId ?: error("No store selected.")
        val document = activityCollection(storeId).document()
        val data = mapOf(
            "id" to document.id,
            "inventoryItemId" to inventoryItemId,
            "itemName" to itemName.trim(),
            "actionType" to actionType.name,
            "quantity" to quantity,
            "unit" to unit?.trim(),
            "reason" to reason?.trim()?.takeIf { it.isNotBlank() },
            "note" to note?.trim()?.takeIf { it.isNotBlank() },
            "performedBy" to userProfile.uid,
            "performedByName" to userProfile.displayName.ifBlank { userProfile.email },
            "createdAt" to FieldValue.serverTimestamp()
        )
        document.set(data).await()
        document.id
    }

    private suspend fun currentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .toObject(UserProfile::class.java)
    }

    private fun activityCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
            .document(storeId)
            .collection(Constants.ACTIVITY_LOGS_COLLECTION)

    private fun DocumentSnapshot.toActivityLog(): ActivityLog = ActivityLog(
        id = getString("id").orEmpty().ifBlank { id },
        inventoryItemId = getString("inventoryItemId"),
        itemName = getString("itemName").orEmpty(),
        actionType = getString("actionType").orEmpty(),
        quantity = (get("quantity") as? Number)?.toDouble(),
        unit = getString("unit"),
        reason = getString("reason"),
        note = getString("note"),
        performedBy = getString("performedBy").orEmpty(),
        performedByName = getString("performedByName").orEmpty(),
        createdAt = (get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L
    )
}
