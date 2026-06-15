package com.example.pantryhub_assignment3_fy.ui.supplier

import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier

enum class PartnerTab(val label: String) {
    ALL("All"),
    SUPPLIERS("Suppliers"),
    CUSTOMERS("Customers");

    fun includes(partner: Supplier): Boolean = when (this) {
        ALL -> true
        SUPPLIERS -> PartnerType.fromValue(partner.partnerType) == PartnerType.SUPPLIER
        CUSTOMERS -> PartnerType.fromValue(partner.partnerType) == PartnerType.CUSTOMER
    }
}

enum class PartnerFilterMode {
    ALL,
    FAVORITES_FIRST,
    FAVORITES_ONLY
}

enum class PartnerArchiveFilter(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ALL("All")
}

data class SupplierUiState(
    val isLoading: Boolean = true,
    val suppliers: List<Supplier> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: PartnerTab = PartnerTab.ALL,
    val filterMode: PartnerFilterMode = PartnerFilterMode.ALL,
    val archiveFilter: PartnerArchiveFilter = PartnerArchiveFilter.ACTIVE,
    val visibleSuppliers: List<Supplier> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)
