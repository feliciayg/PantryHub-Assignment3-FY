package com.example.pantryhub_assignment3_fy.util

import com.example.pantryhub_assignment3_fy.model.InventoryItem

/**
 * Stable product identity shared by repository persistence and UI aggregation.
 * SKU, then barcode, then normalized name + brand + category.
 */
object ProductIdentity {
    fun key(item: InventoryItem): String {
        val sku = item.sku.trim().lowercase()
        if (sku.isNotBlank()) return "sku:$sku"
        val barcode = item.barcode.trim().lowercase()
        if (barcode.isNotBlank()) return "barcode:$barcode"
        return "named:${normalize(item.name)}|${normalize(item.brand)}|${normalize(item.category)}"
    }

    fun sameProduct(first: InventoryItem, second: InventoryItem): Boolean {
        val firstSku = first.sku.trim()
        val secondSku = second.sku.trim()
        if (firstSku.isNotBlank() && secondSku.isNotBlank()) {
            return firstSku.equals(secondSku, ignoreCase = true)
        }
        val firstBarcode = first.barcode.trim()
        val secondBarcode = second.barcode.trim()
        if (firstBarcode.isNotBlank() && secondBarcode.isNotBlank()) {
            return firstBarcode.equals(secondBarcode, ignoreCase = true)
        }
        return normalize(first.name) == normalize(second.name) &&
            normalize(first.brand) == normalize(second.brand) &&
            normalize(first.category) == normalize(second.category)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}
