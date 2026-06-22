package com.example.pantryhub_assignment3_fy.model

/**
 * Store-specific master data used by item forms.
 *
 * Categories and brands are documents instead of hardcoded UI values so each store can
 * add, rename, and safely delete the choices that fit its own inventory.
 */
data class InventoryOption(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedBy: String = "",
    val updatedAt: Long = 0L
)

enum class InventoryOptionType(
    val collectionName: String,
    val inventoryField: String
) {
    CATEGORY("categories", "category"),
    BRAND("brands", "brand")
}
