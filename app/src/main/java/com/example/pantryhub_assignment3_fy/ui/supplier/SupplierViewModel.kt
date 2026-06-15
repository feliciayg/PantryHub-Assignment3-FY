package com.example.pantryhub_assignment3_fy.ui.supplier

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.data.repository.SupplierRepository
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

class SupplierViewModel(
    private val supplierRepository: SupplierRepository = SupplierRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(SupplierUiState())
    val uiState: LiveData<SupplierUiState> = _uiState

    init {
        observeSuppliers()
    }

    private fun observeSuppliers() {
        viewModelScope.launch {
            supplierRepository.observeSuppliers(includeArchived = true).collect { result ->
                result
                    .onSuccess { suppliers ->
                        _uiState.update {
                            val next = it.copy(isLoading = false, suppliers = suppliers, errorMessage = null)
                            next.copy(visibleSuppliers = applySearch(next))
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = throwable.message ?: "Could not load suppliers.")
                        }
                    }
            }
        }
    }

    fun search(query: String) {
        _uiState.update {
            val next = it.copy(searchQuery = query)
            next.copy(visibleSuppliers = applySearch(next))
        }
    }

    fun selectTab(tab: PartnerTab) {
        _uiState.update {
            val next = it.copy(selectedTab = tab)
            next.copy(visibleSuppliers = applySearch(next))
        }
    }

    fun setFilterMode(filterMode: PartnerFilterMode) {
        _uiState.update {
            val next = it.copy(filterMode = filterMode)
            next.copy(visibleSuppliers = applySearch(next))
        }
    }

    fun setArchiveFilter(archiveFilter: PartnerArchiveFilter) {
        _uiState.update {
            val next = it.copy(archiveFilter = archiveFilter)
            next.copy(visibleSuppliers = applySearch(next))
        }
    }

    fun addSupplier(supplier: Supplier) {
        viewModelScope.launch {
            supplierRepository.addSupplier(supplier)
                .onSuccess {
                    val type = PartnerType.fromValue(supplier.partnerType)
                    AppLogger.info(
                        area = if (type == PartnerType.CUSTOMER) "Customers" else "Suppliers",
                        event = if (type == PartnerType.CUSTOMER) "customer_create_success" else "supplier_create_success",
                        message = "Partner created.",
                        "name" to supplier.name
                    )
                    showSuccess(if (type == PartnerType.CUSTOMER) "Customer saved." else "Supplier saved.")
                }
                .onFailure {
                    val type = PartnerType.fromValue(supplier.partnerType)
                    AppLogger.error(
                        area = if (type == PartnerType.CUSTOMER) "Customers" else "Suppliers",
                        event = if (type == PartnerType.CUSTOMER) "customer_create_failed" else "supplier_create_failed",
                        message = "Could not create partner.",
                        throwable = it,
                        "name" to supplier.name
                    )
                    showError(it.message ?: "Could not save partner.")
                }
        }
    }

    fun updateSupplier(supplier: Supplier) {
        viewModelScope.launch {
            supplierRepository.updateSupplier(supplier)
                .onSuccess {
                    val type = PartnerType.fromValue(supplier.partnerType)
                    AppLogger.info(
                        area = if (type == PartnerType.CUSTOMER) "Customers" else "Suppliers",
                        event = if (type == PartnerType.CUSTOMER) "customer_update_success" else "supplier_update_success",
                        message = "Partner updated.",
                        "name" to supplier.name
                    )
                    showSuccess(if (type == PartnerType.CUSTOMER) "Customer updated." else "Supplier updated.")
                }
                .onFailure {
                    val type = PartnerType.fromValue(supplier.partnerType)
                    AppLogger.error(
                        area = if (type == PartnerType.CUSTOMER) "Customers" else "Suppliers",
                        event = if (type == PartnerType.CUSTOMER) "customer_update_failed" else "supplier_update_failed",
                        message = "Could not update partner.",
                        throwable = it,
                        "name" to supplier.name
                    )
                    showError(it.message ?: "Could not update partner.")
                }
        }
    }

    fun archiveSupplier(supplierId: String) {
        viewModelScope.launch {
            supplierRepository.archiveSupplier(supplierId)
                .onSuccess {
                    AppLogger.info(
                        area = "Suppliers",
                        event = "supplier_archive_success",
                        message = "Partner archived.",
                        "partnerId" to supplierId
                    )
                    showSuccess("Partner archived.")
                }
                .onFailure {
                    AppLogger.error(
                        area = "Suppliers",
                        event = "supplier_archive_failed",
                        message = "Could not archive partner.",
                        throwable = it,
                        "partnerId" to supplierId
                    )
                    showError(it.message ?: "Could not archive partner.")
                }
        }
    }

    fun restoreSupplier(supplierId: String) {
        viewModelScope.launch {
            supplierRepository.restoreSupplier(supplierId)
                .onSuccess { showSuccess("Partner restored.") }
                .onFailure { showError(it.message ?: "Could not restore partner.") }
        }
    }

    fun toggleFavorite(partner: Supplier) {
        viewModelScope.launch {
            supplierRepository.updateFavorite(partner.id, !partner.isFavorite)
                .onFailure { showError(it.message ?: "Could not update favorite.") }
        }
    }

    fun findSupplier(supplierId: String): Supplier? =
        _uiState.value?.suppliers?.firstOrNull { it.id == supplierId }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun applySearch(state: SupplierUiState): List<Supplier> {
        val query = state.searchQuery.trim()
        val partners = state.suppliers
            .asSequence()
            .filter {
                when (state.archiveFilter) {
                    PartnerArchiveFilter.ACTIVE -> !it.isArchived
                    PartnerArchiveFilter.ARCHIVED -> it.isArchived
                    PartnerArchiveFilter.ALL -> true
                }
            }
            .filter { state.selectedTab.includes(it) }
            .filter {
                when (state.filterMode) {
                    PartnerFilterMode.ALL -> true
                    PartnerFilterMode.FAVORITES_FIRST -> true
                    PartnerFilterMode.FAVORITES_ONLY -> it.isFavorite
                }
            }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.contactPerson.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true) ||
                    it.email.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true) ||
                    it.notes.contains(query, ignoreCase = true)
            }
            .toList()
        return if (state.filterMode == PartnerFilterMode.FAVORITES_FIRST) {
            partners.sortedWith(compareByDescending<Supplier> { it.isFavorite }.thenBy { it.name.lowercase() })
        } else {
            partners.sortedBy { it.name.lowercase() }
        }
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
