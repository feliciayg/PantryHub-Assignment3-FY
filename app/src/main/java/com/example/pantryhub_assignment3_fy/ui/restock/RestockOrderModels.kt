package com.example.pantryhub_assignment3_fy.ui.restock

import androidx.annotation.StringRes
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.Supplier

data class RestockOrdersUiState(
    val isLoading: Boolean = true,
    val isSavingPurchase: Boolean = false,
    val restockOrders: List<RestockOrder> = emptyList(),
    val visibleOrders: List<RestockOrder> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val selectedFilter: PurchaseStatusFilter = PurchaseStatusFilter.ALL,
    val appliedFilters: PurchaseHistoryFilter = PurchaseHistoryFilter(),
    val hasActiveFilters: Boolean = false,
    val searchQuery: String = "",
    val receiveDraft: PurchaseReceiveDraft? = null,
    val receivePickerQuery: String = "",
    val receiveCompletion: PurchaseReceiveCompletion? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class PurchaseFormState(
    val purchaseId: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val receivingLocationId: String = "",
    val receivingLocationName: String = "",
    val orderDate: Long = 0L,
    val expectedDeliveryDate: Long = 0L,
    val memo: String = "",
    val items: List<PurchaseOrderItem> = emptyList(),
    val pendingCreatedSupplierName: String = "",
    val pendingCreatedLocationName: String = ""
) {
    val isEditing: Boolean
        get() = purchaseId.isNotBlank()
}

enum class PurchaseStatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.all),
    DRAFT(R.string.draft),
    ORDERED(R.string.ordered),
    PARTIALLY_RECEIVED(R.string.partially_received),
    RECEIVED(R.string.received),
    CANCELLED(R.string.cancelled)
}

data class PurchaseHistoryFilter(
    val dateSelection: PurchaseDateSelection? = null,
    val orderNumberQuery: String = "",
    val itemKey: String = "",
    val itemLabel: String = "",
    val memberName: String = "",
    val partnerName: String = ""
) {
    fun hasActiveFilters(): Boolean {
        return dateSelection != null ||
            orderNumberQuery.isNotBlank() ||
            itemKey.isNotBlank() ||
            memberName.isNotBlank() ||
            partnerName.isNotBlank()
    }
}

data class PurchaseDateSelection(
    val startMillis: Long,
    val endMillis: Long
) {
    val normalizedStartMillis: Long
        get() = minOf(startMillis, endMillis)

    val normalizedEndMillis: Long
        get() = maxOf(startMillis, endMillis)
}

data class PurchaseReceiveDraft(
    val purchaseId: String,
    val orderLabel: String,
    val locationName: String,
    val memo: String = "",
    val selectedQuantities: Map<String, Double> = emptyMap(),
    val isSubmitting: Boolean = false
)

data class PurchaseReceiveCompletion(
    val transactionId: String,
    val itemCount: Int,
    val totalQuantity: Double
)
