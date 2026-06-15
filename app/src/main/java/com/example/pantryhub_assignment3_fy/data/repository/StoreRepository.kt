package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.model.Store
import com.example.pantryhub_assignment3_fy.model.StoreDetails
import com.example.pantryhub_assignment3_fy.model.StaffMember
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

/**
 * Repository for store setup, joining, staff profile data, and drawer/profile details.
 */
class StoreRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    /**
     * Creates a store, adds the current user as owner, and links the user profile to it.
     */
    suspend fun createStore(name: String): Result<String> {
        return runCatching {
            require(name.trim().isNotEmpty()) { "Store name is required." }
            val userProfile = currentUserProfile() ?: error("You must be logged in first.")
            val storeRef = firestoreDataSource.db.collection(Constants.STORES_COLLECTION).document()
            val inviteCode = reserveUniqueInviteCode(storeRef.id, userProfile.uid)

            val storeData = hashMapOf(
                "id" to storeRef.id,
                "name" to name.trim(),
                "description" to "",
                "registrationNumber" to "",
                "address" to "",
                "contactName" to userProfile.displayName,
                "phone" to "",
                "imageUrl" to "",
                "inviteCode" to inviteCode,
                "createdBy" to userProfile.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val staffData = hashMapOf(
                "uid" to userProfile.uid,
                "email" to userProfile.email,
                "displayName" to userProfile.displayName,
                "role" to Constants.OWNER_ROLE,
                "joinedAt" to FieldValue.serverTimestamp()
            )

            // Store creation is batched so the store, owner staff record, and user profile stay consistent.
            val batch = firestoreDataSource.db.batch()
            batch.set(storeRef, storeData)
            batch.set(storeRef.collection(Constants.STAFF_COLLECTION).document(userProfile.uid), staffData)
            batch.update(
                firestoreDataSource.db.collection(Constants.USERS_COLLECTION).document(userProfile.uid),
                mapOf(
                    "currentStoreId" to storeRef.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            storeRef.id
        }
    }

    /**
     * Finds a store by invite code and adds the current user as staff.
     */
    suspend fun joinStore(inviteCode: String): Result<String> {
        return runCatching {
            val normalizedCode = inviteCode.trim().uppercase(Locale.getDefault())
            require(normalizedCode.isNotEmpty()) { "Invite code is required." }
            val userProfile = currentUserProfile() ?: error("You must be logged in first.")

            val storeSnapshot = firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
                .whereEqualTo("inviteCode", normalizedCode)
                .limit(1)
                .get()
                .await()
            val storeDocument = storeSnapshot.documents.firstOrNull()
                ?: error("Store not found.")
            val storeId = storeDocument.id
            val storeRef = firestoreDataSource.db.collection(Constants.STORES_COLLECTION).document(storeId)
            val staffData = hashMapOf(
                "uid" to userProfile.uid,
                "email" to userProfile.email,
                "displayName" to userProfile.displayName,
                "role" to Constants.STAFF_ROLE,
                "joinedAt" to FieldValue.serverTimestamp()
            )

            val batch = firestoreDataSource.db.batch()
            batch.set(storeRef.collection(Constants.STAFF_COLLECTION).document(userProfile.uid), staffData)
            batch.update(
                firestoreDataSource.db.collection(Constants.USERS_COLLECTION).document(userProfile.uid),
                mapOf(
                    "currentStoreId" to storeId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            storeId
        }
    }

    /**
     * Loads the store, current user, current staff role, and full staff list together.
     */
    suspend fun loadCurrentStoreDetails(): Result<StoreDetails> {
        return runCatching {
            val userProfile = currentUserProfile() ?: error("You must be logged in first.")
            val storeId = userProfile.currentStoreId ?: error("No store selected.")
            val storeRef = firestoreDataSource.db.collection(Constants.STORES_COLLECTION).document(storeId)
            val store = storeRef.get().await().toObject(Store::class.java)
                ?: error("Store not found.")
            val staff = storeRef.collection(Constants.STAFF_COLLECTION)
                .get()
                .await()
                .toObjects(StaffMember::class.java)
                .sortedWith(compareBy<StaffMember> { it.role != Constants.OWNER_ROLE }.thenBy { it.displayName })
            val currentStaff = staff.firstOrNull { it.uid == userProfile.uid }
                ?: StaffMember(uid = userProfile.uid, email = userProfile.email, displayName = userProfile.displayName)
            StoreDetails(store, userProfile, currentStaff, staff)
        }
    }

    /**
     * Updates display name in both the user profile and store staff record.
     */
    suspend fun updateCurrentUserDisplayName(displayName: String): Result<Unit> {
        return runCatching {
            val normalizedName = displayName.trim()
            require(normalizedName.isNotEmpty()) { "Display name is required." }
            val userProfile = currentUserProfile() ?: error("You must be logged in first.")
            val storeId = userProfile.currentStoreId ?: error("No store selected.")
            val batch = firestoreDataSource.db.batch()
            batch.update(
                firestoreDataSource.db.collection(Constants.USERS_COLLECTION).document(userProfile.uid),
                mapOf(
                    "displayName" to normalizedName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
                    .document(storeId)
                    .collection(Constants.STAFF_COLLECTION)
                    .document(userProfile.uid),
                "displayName",
                normalizedName
            )
            batch.commit().await()
        }
    }

    suspend fun updateCurrentStoreDetails(
        name: String,
        description: String,
        registrationNumber: String,
        address: String,
        contactName: String,
        phone: String,
        imageUrl: String
    ): Result<Unit> {
        return runCatching {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty()) { "Store name is required." }
            val userProfile = currentUserProfile() ?: error("You must be logged in first.")
            val storeId = userProfile.currentStoreId ?: error("No store selected.")
            firestoreDataSource.db.collection(Constants.STORES_COLLECTION)
                .document(storeId)
                .update(
                    mapOf(
                        "name" to normalizedName,
                        "description" to description.trim(),
                        "registrationNumber" to registrationNumber.trim(),
                        "address" to address.trim(),
                        "contactName" to contactName.trim(),
                        "phone" to phone.trim(),
                        "imageUrl" to imageUrl.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }
    }

    private suspend fun currentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        val snapshot = firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
        return snapshot.toObject(UserProfile::class.java)
    }

    /**
     * Reserves an invite code in a dedicated collection before the store is created so
     * two stores can never commit the same join code, even if they are created at the same time.
     */
    private suspend fun reserveUniqueInviteCode(storeId: String, createdBy: String): String {
        repeat(MAX_INVITE_CODE_ATTEMPTS) {
            val inviteCode = generateInviteCode()
            val reservationRef = firestoreDataSource.db
                .collection(Constants.INVITE_CODES_COLLECTION)
                .document(inviteCode)

            val reserved = firestoreDataSource.db.runTransaction { transaction ->
                val existingReservation = transaction.get(reservationRef)
                if (existingReservation.exists()) {
                    false
                } else {
                    transaction.set(
                        reservationRef,
                        mapOf(
                            "code" to inviteCode,
                            "storeId" to storeId,
                            "createdBy" to createdBy,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    true
                }
            }.await()

            if (reserved) return inviteCode
        }
        error("Could not generate a unique invite code. Please try again.")
    }

    /**
     * Generates a short human-readable invite code candidate for joining stores.
     */
    private fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    companion object {
        private const val MAX_INVITE_CODE_ATTEMPTS = 12
    }
}
