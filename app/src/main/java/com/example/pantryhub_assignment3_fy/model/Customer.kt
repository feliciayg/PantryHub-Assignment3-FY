package com.example.pantryhub_assignment3_fy.model

data class Customer(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0L,
    val archivedBy: String = "",
    val archiveReason: String = ""
)
