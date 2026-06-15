package com.example.pantryhub_assignment3_fy.model

import com.google.firebase.Timestamp

data class StaffMember(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "",
    val joinedAt: Timestamp? = null
)
