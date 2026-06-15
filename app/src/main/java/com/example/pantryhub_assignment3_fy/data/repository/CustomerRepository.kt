package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.Customer
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CustomerRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    fun observeCustomers(): Flow<Result<List<Customer>>> = callbackFlow {
        val registration = customersCollection(currentStoreId())
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) trySend(Result.failure(error))
                else trySend(Result.success(snapshot?.documents.orEmpty().map { it.toCustomer() }.filterNot { it.isArchived }))
            }
        awaitClose { registration.remove() }
    }

    suspend fun addCustomer(name: String): Result<String> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Customer name is required." }
        val collection = customersCollection(currentStoreId())
        require(collection.get().await().documents.none { it.getString("name").orEmpty().equals(cleanName, true) }) {
            "This customer already exists."
        }
        val document = collection.document()
        val now = System.currentTimeMillis()
        document.set(Customer(document.id, cleanName, createdAt = now, updatedAt = now).toFirestoreMap()).await()
        AppLogger.info(
            area = "Customers",
            event = "customer_create_success",
            message = "Customer created.",
            "name" to cleanName
        )
        document.id
    }

    private suspend fun currentStoreId(): String {
        val uid = authManager.currentUserId ?: error("You must be logged in.")
        return firestoreDataSource.db.collection(Constants.USERS_COLLECTION).document(uid).get().await()
            .toObject(UserProfile::class.java)?.currentStoreId ?: error("No store selected.")
    }

    private fun customersCollection(storeId: String) =
        firestoreDataSource.db.collection(Constants.STORES_COLLECTION).document(storeId)
            .collection(Constants.CUSTOMERS_COLLECTION)

    private fun DocumentSnapshot.toCustomer() = Customer(
        id = getString("id").orEmpty().ifBlank { id },
        name = getString("name").orEmpty(),
        phone = getString("phone").orEmpty(),
        email = getString("email").orEmpty(),
        notes = getString("notes").orEmpty(),
        createdAt = dateMillis("createdAt"),
        updatedAt = dateMillis("updatedAt"),
        isArchived = getBoolean("isArchived") ?: false,
        archivedAt = dateMillis("archivedAt"),
        archivedBy = getString("archivedBy").orEmpty(),
        archiveReason = getString("archiveReason").orEmpty()
    )

    private fun Customer.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id, "name" to name, "phone" to phone, "email" to email, "notes" to notes,
        "createdAt" to createdAt.asDate(), "updatedAt" to updatedAt.asDate(),
        "isArchived" to isArchived,
        "archivedAt" to archivedAt.asDate(),
        "archivedBy" to archivedBy,
        "archiveReason" to archiveReason
    )

    private fun DocumentSnapshot.dateMillis(field: String): Long = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
    }

    private fun Long.asDate(): Any = if (this > 0L) Timestamp(Date(this)) else this
}
