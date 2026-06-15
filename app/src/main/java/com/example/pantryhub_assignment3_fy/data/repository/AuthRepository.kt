package com.example.pantryhub_assignment3_fy.data.repository

import com.example.pantryhub_assignment3_fy.data.firebase.FirebaseAuthManager
import com.example.pantryhub_assignment3_fy.data.firebase.FirestoreDataSource
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.Constants
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * Handles authentication and the InventoryHub user profile document.
 *
 * Firebase Auth stores the login credential, while Firestore `users/{uid}` stores
 * app-specific data such as email, display name, and current store/team id.
 */
class AuthRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) {
    /**
     * Creates a Firebase Auth account and matching Firestore user profile.
     */
    suspend fun signUp(email: String, displayName: String, password: String): Result<UserProfile> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            // Records the moment account creation starts so failed sign-up attempts can be traced.
            AppLogger.info(
                area = "Auth",
                event = "sign_up_start",
                message = "Creating Firebase account.",
                "email" to normalizedEmail
            )
            require(android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                "Enter a valid email address."
            }
            require(displayName.trim().isNotEmpty()) { "Display name is required." }
            require(password.length >= 6) { "Password must be at least 6 characters." }

            val authResult = authManager.firebaseAuth()
                .createUserWithEmailAndPassword(normalizedEmail, password)
                .await()
            val firebaseUser = authResult.user ?: error("Could not create account.")
            val uid = firebaseUser.uid

            // Fresh accounts sometimes hit Firestore before the newest auth token is fully ready on device.
            authManager.refreshCurrentUserToken()
            createOrUpdateUserProfile(
                firebaseUser = firebaseUser,
                displayName = displayName.trim(),
                overwriteDisplayName = true
            ).getOrThrow()

            loadUserProfile(uid)?.also {
                // Confirms sign-up reached a valid InventoryHub profile and whether store setup is still needed.
                AppLogger.info(
                    area = "Auth",
                    event = "sign_up_success",
                    message = "Account created successfully.",
                    "uid" to uid,
                    "hasStore" to !it.currentStoreId.isNullOrBlank()
                )
            } ?: error("Could not load created profile.")
        }
            .onFailure { error ->
                // Captures the exact sign-up failure path with the attempted email for Logcat debugging.
                AppLogger.error(
                    area = "Auth",
                    event = "sign_up_failed",
                    message = "Could not create account.",
                    throwable = error,
                    "email" to email.trim().lowercase()
                )
            }
    }

    /**
     * Logs in directly with the user's real email address.
     */
    suspend fun login(email: String, password: String): Result<UserProfile> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            // Marks the start of an email/password login attempt before Firebase is called.
            AppLogger.info(
                area = "Auth",
                event = "login_start",
                message = "Signing in with email/password.",
                "email" to normalizedEmail
            )
            require(normalizedEmail.isNotEmpty()) { "Email is required." }
            require(password.isNotEmpty()) { "Password is required." }

            val authResult = authManager.firebaseAuth()
                .signInWithEmailAndPassword(normalizedEmail, password)
                .await()
            val firebaseUser = authResult.user ?: error("Login failed.")
            val uid = firebaseUser.uid

            authManager.refreshCurrentUserToken()

            // If an account exists in Firebase Auth but its Firestore profile was blocked earlier,
            // rebuild the missing profile so the user can continue into the app.
            if (loadUserProfile(uid) == null) {
                createOrUpdateUserProfile(
                    firebaseUser = firebaseUser,
                    displayName = firebaseUser.email.orEmpty().substringBefore("@").ifBlank { "InventoryHub User" },
                    overwriteDisplayName = false
                ).getOrThrow()
            }

            loadUserProfile(uid)?.also {
                // Confirms login succeeded and shows whether the user already belongs to a store.
                AppLogger.info(
                    area = "Auth",
                    event = "login_success",
                    message = "Signed in successfully.",
                    "uid" to uid,
                    "hasStore" to !it.currentStoreId.isNullOrBlank()
                )
            } ?: error("Could not load your InventoryHub profile.")
        }
            .onFailure { error ->
                // Stores the login failure details so auth problems can be matched with UI behavior.
                AppLogger.error(
                    area = "Auth",
                    event = "login_failed",
                    message = "Sign in failed.",
                    throwable = error,
                    "email" to email.trim().lowercase()
                )
            }
    }

    /**
     * Sends Firebase's secure password reset email for a valid email address.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            AppLogger.info(
                area = "Auth",
                event = "password_reset_start",
                message = "Requesting Firebase password reset email.",
                "email" to normalizedEmail
            )
            require(android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                "Enter a valid email address."
            }

            authManager.firebaseAuth()
                .sendPasswordResetEmail(normalizedEmail)
                .await()

            AppLogger.info(
                area = "Auth",
                event = "password_reset_success",
                message = "Password reset request submitted.",
                "email" to normalizedEmail
            )
        }.onFailure { error ->
            AppLogger.error(
                area = "Auth",
                event = "password_reset_failed",
                message = "Password reset request failed.",
                throwable = error,
                "email" to email.trim().lowercase()
            )
        }
    }

    /**
     * Loads the profile for the currently signed-in Firebase user.
     */
    suspend fun loadCurrentUserProfile(): UserProfile? {
        val uid = authManager.currentUserId ?: return null
        return loadUserProfile(uid)
    }

    /**
     * Clears the local Firebase Auth session.
     */
    fun signOut() {
        authManager.signOut()
    }

    private suspend fun loadUserProfile(uid: String): UserProfile? {
        // Login decisions depend on this read because currentStoreId controls the next screen.
        val snapshot = firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
        return snapshot.toObject(UserProfile::class.java)
    }

    private suspend fun createOrUpdateUserProfile(
        firebaseUser: FirebaseUser,
        displayName: String,
        overwriteDisplayName: Boolean
    ): Result<Unit> = runCatching {
        val uid = firebaseUser.uid
        val existing = loadUserProfile(uid)
        val resolvedEmail = firebaseUser.email?.trim()?.lowercase().orEmpty()
        val resolvedDisplayName = when {
            displayName.isNotBlank() -> displayName.trim()
            existing?.displayName?.isNotBlank() == true -> existing.displayName
            resolvedEmail.isNotBlank() -> resolvedEmail.substringBefore("@")
            else -> "InventoryHub User"
        }

        val userData = hashMapOf<String, Any?>(
            "uid" to uid,
            "email" to resolvedEmail,
            "displayName" to if (overwriteDisplayName || existing?.displayName.isNullOrBlank()) resolvedDisplayName else existing?.displayName,
            "currentStoreId" to existing?.currentStoreId,
            "createdAt" to if (existing == null) FieldValue.serverTimestamp() else existing.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestoreDataSource.db.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .set(userData)
            .await()
    }
}
