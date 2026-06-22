package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.annotation.StringRes
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.util.ExpiryLotRules

data class InventoryUiState(
    val isLoading: Boolean = true,
    val inventoryItems: List<InventoryItem> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val visibleInventoryItems: List<InventoryItem> = emptyList(),
    val visibleInventoryRows: List<InventoryDisplayRow> = emptyList(),
    val groupSummaries: List<GroupSummaryUiModel> = emptyList(),
    val selectedLocation: String = FilterOptions.ALL_LOCATION,
    val selectedCategory: String = FilterOptions.ALL_CATEGORY,
    val selectedBrand: String = FilterOptions.ALL_BRAND,
    val selectedSupplier: String = FilterOptions.ALL_SUPPLIER,
    val selectedStatus: String = FilterOptions.ALL_STATUS,
    val archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE,
    val selectedBranch: String = FilterOptions.ALL_BRANCH,
    val groupOption: GroupOption = GroupOption.NONE,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val groupSortOption: GroupSortOption = GroupSortOption.VALUE_ASC,
    val searchQuery: String = "",
    val csvExportContent: String? = null,
    val csvSummaryTitle: String? = null,
    val csvSummaryMessage: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class ArchiveFilter(@StringRes val labelRes: Int) {
    ACTIVE(R.string.active),
    ARCHIVED(R.string.archived),
    ALL(R.string.all)
}

data class InventoryDisplayRow(
    val id: String,
    val representativeItem: InventoryItem,
    val realInventoryItemId: String?,
    val matchingRecords: List<InventoryItem>,
    val quantity: Double,
    val branchId: String = "",
    val branchName: String = "",
    val isAggregate: Boolean = false
) {
    val unit: String get() = representativeItem.unit
    val name: String get() = representativeItem.name
    val brand: String get() = representativeItem.brand
    val category: String get() = representativeItem.category
    val supplierName: String get() = representativeItem.supplierName
    val imageUrl: String get() = representativeItem.imageUrl
    val reorderPoint: Int get() = representativeItem.reorderPoint
    val reorderThreshold: Double get() = representativeItem.reorderThreshold
    val isArchived: Boolean get() = matchingRecords.all { it.isArchived }
    val expiryLots: List<ExpiryLot>
        get() = ExpiryLotRules.combineByExpiry(matchingRecords.flatMap { it.expiryLots })
    val expiryDate: Long get() = matchingRecords.mapNotNull { it.expiryDate.takeIf { date -> date > 0 } }.minOrNull() ?: representativeItem.expiryDate
    val updatedAt: Long get() = matchingRecords.maxOfOrNull { it.updatedAt.takeIf { timestamp -> timestamp > 0 } ?: it.createdAt } ?: representativeItem.updatedAt
}

data class InventoryItemFormData(
    val id: String = "",
    val sku: String = "",
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val storageLocation: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val minimumStockLevel: Int = 0,
    val reorderPoint: Int = 0,
    val maximumStockLevel: Int = 0,
    val reorderThreshold: Double = 0.0,
    val aisle: String = "",
    val shelf: String = "",
    val addedDate: Long = 0L,
    val expiryDate: Long = 0L,
    val batchNumber: String = "",
    val shelfLifeDays: Int = 0,
    val reminderDaysBefore: Int = 0,
    val notes: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val supplierPhone: String = "",
    val supplierEmail: String = "",
    val imageUrl: String = "",
    val tags: List<String> = emptyList()
)

enum class SortOption(@StringRes val labelRes: Int) {
    EXPIRY_SOONEST(R.string.sort_expiry_soonest),
    NAME_ASC(R.string.sort_name_az),
    NAME_DESC(R.string.sort_name_za),
    QUANTITY_LOW(R.string.sort_quantity_low),
    QUANTITY_HIGH(R.string.sort_quantity_high),
    SAFETY_STOCK_LOW(R.string.sort_safety_stock_low),
    RESTOCK_URGENCY(R.string.sort_restock_urgency),
    RECENTLY_UPDATED(R.string.sort_recently_updated)
}

enum class GroupOption(@StringRes val labelRes: Int) {
    NONE(R.string.group_none_all),
    NAME(R.string.name),
    COST(R.string.cost),
    PRICE(R.string.price),
    CATEGORY(R.string.category),
    BRAND(R.string.brand),
    SAFETY_STOCK(R.string.safety_stock),
    EXPIRY_DATE(R.string.expiry_date)
}

enum class GroupSortOption(@StringRes val labelRes: Int) {
    VALUE_ASC(R.string.group_sort_value_az),
    VALUE_DESC(R.string.group_sort_value_za),
    QUANTITY_HIGH(R.string.group_sort_quantity_high),
    QUANTITY_LOW(R.string.group_sort_quantity_low),
    ITEM_COUNT_HIGH(R.string.group_sort_item_count_high),
    ITEM_COUNT_LOW(R.string.group_sort_item_count_low)
}

data class GroupSummaryUiModel(
    val groupKey: String,
    val displayName: String,
    val itemCount: Int,
    val totalQuantity: Double,
    val percentage: Int,
    val previewImageUrls: List<String>,
    val groupedItems: List<InventoryDisplayRow>
)

object FilterOptions {
    const val IN_STOCK_STATUS = "In Stock"
    const val ALL_BRANCH = "All Locations"
    const val ALL_LOCATION = "All"
    const val ALL_CATEGORY = "All Categories"
    const val ALL_BRAND = "All Brands"
    const val ALL_SUPPLIER = "All Suppliers"
    const val ALL_STATUS = "All"
    const val LOW_STOCK_STATUS = "Low Stock"

    val locations = listOf(ALL_LOCATION, "Dry Storage", "Fridge", "Freezer", "Crisper")
    val categories = listOf(ALL_CATEGORY, "Vegetable", "Dairy", "Drinks", "Meat", "Fruit", "Frozen", "Canned", "Snacks", "Other")
    // Used and Wasted are explicit filters for archived zero-stock items hidden from the default Storage list.
    val statuses = listOf(ALL_STATUS, IN_STOCK_STATUS, "Priority Today", "Expiring Soon", "Expired", "Out of Stock", LOW_STOCK_STATUS, "Overstock", "Wasted")
    val units = listOf("pack", "g", "kg", "bottle", "can", "pcs", "ml", "L")
}
