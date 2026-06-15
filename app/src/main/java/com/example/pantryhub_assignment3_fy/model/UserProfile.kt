package com.example.pantryhub_assignment3_fy.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val currentStoreId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
