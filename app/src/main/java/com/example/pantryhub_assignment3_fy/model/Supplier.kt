package com.example.pantryhub_assignment3_fy.model

data class Supplier(
    val id: String = "",
    val name: String = "",
    val partnerType: String = PartnerType.SUPPLIER.value,
    val isFavorite: Boolean = false,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val paymentTerms: String = "",
    val leadTimeDays: Int = 0,
    val notes: String = "",
    val createdBy: String = "",
    val updatedBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0L,
    val archivedBy: String = "",
    val archiveReason: String = ""
)
