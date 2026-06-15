package com.example.pantryhub_assignment3_fy.model

data class Branch(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedBy: String = "",
    val updatedAt: Long = 0L,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0L,
    val archivedBy: String = "",
    val archiveReason: String = ""
)
