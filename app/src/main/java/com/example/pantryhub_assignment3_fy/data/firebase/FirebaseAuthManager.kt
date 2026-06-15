package com.example.pantryhub_assignment3_fy.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Small wrapper around FirebaseAuth so repositories can share one login helper.
 */
class FirebaseAuthManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Signs the current user out of Firebase Auth.
     */
    fun signOut() {
        auth.signOut()
    }

    suspend fun currentUser(): FirebaseUser? = auth.currentUser

    /**
     * Refreshes the current Firebase ID token before first-write flows that depend on Firestore rules.
     */
    suspend fun refreshCurrentUserToken() {
        auth.currentUser?.getIdToken(true)?.await()
    }

    /**
     * Exposes FirebaseAuth for operations that need Firebase Tasks, such as sign up/login.
     */
    fun firebaseAuth(): FirebaseAuth = auth
}
