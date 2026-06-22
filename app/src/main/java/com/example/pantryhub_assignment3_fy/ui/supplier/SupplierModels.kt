package com.example.pantryhub_assignment3_fy.ui.supplier

import androidx.annotation.StringRes
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier

enum class PartnerTab(@StringRes val labelRes: Int) {
    ALL(R.string.all),
    SUPPLIERS(R.string.suppliers),
    CUSTOMERS(R.string.customers);

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

enum class PartnerArchiveFilter(@StringRes val labelRes: Int) {
    ACTIVE(R.string.active),
    ARCHIVED(R.string.archived),
    ALL(R.string.all)
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
