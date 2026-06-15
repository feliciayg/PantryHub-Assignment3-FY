package com.example.pantryhub_assignment3_fy.ui.storage

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

enum class ArchiveFilter(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ALL("All")
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

enum class SortOption(val label: String) {
    EXPIRY_SOONEST("Expiry Date: Soonest First"),
    NAME_ASC("Name: A-Z"),
    NAME_DESC("Name: Z-A"),
    QUANTITY_LOW("Quantity: Low to High"),
    QUANTITY_HIGH("Quantity: High to Low"),
    SAFETY_STOCK_LOW("Safety Stock: Low to High"),
    RESTOCK_URGENCY("Restock Urgency"),
    RECENTLY_UPDATED("Recently Updated")
}

enum class GroupOption(val label: String) {
    NONE("None (All)"),
    NAME("Name"),
    COST("Cost"),
    PRICE("Price"),
    CATEGORY("Category"),
    BRAND("Brand"),
    SAFETY_STOCK("Safety Stock"),
    EXPIRY_DATE("Expiry Date")
}

enum class GroupSortOption(val label: String) {
    VALUE_ASC("Group Value: A-Z"),
    VALUE_DESC("Group Value: Z-A"),
    QUANTITY_HIGH("Total Quantity: High to Low"),
    QUANTITY_LOW("Total Quantity: Low to High"),
    ITEM_COUNT_HIGH("Item Count: High to Low"),
    ITEM_COUNT_LOW("Item Count: Low to High")
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
