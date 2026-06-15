package com.example.pantryhub_assignment3_fy.model

import com.google.firebase.Timestamp

data class Store(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val registrationNumber: String = "",
    val address: String = "",
    val contactName: String = "",
    val phone: String = "",
    val imageUrl: String = "",
    val inviteCode: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
