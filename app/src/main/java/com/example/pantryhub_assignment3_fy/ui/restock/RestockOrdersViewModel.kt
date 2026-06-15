package com.example.pantryhub_assignment3_fy.ui.restock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.RestockOrderRepository
import com.example.pantryhub_assignment3_fy.data.repository.SupplierRepository
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.RestockStatus
import com.example.pantryhub_assignment3_fy.model.remainingQuantity
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

class RestockOrdersViewModel(
    private val restockOrderRepository: RestockOrderRepository = RestockOrderRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val supplierRepository: SupplierRepository = SupplierRepository(),
    private val branchRepository: BranchRepository = BranchRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(RestockOrdersUiState())
    val uiState: LiveData<RestockOrdersUiState> = _uiState

    private var latestInventoryItems: List<InventoryItem> = emptyList()
    private val receivingOrderIds = mutableSetOf<String>()

    init {
        observeRestockOrders()
        observeInventoryItems()
        observeSuppliers()
        observeBranches()
    }

    fun search(query: String) {
        _uiState.update {
            val next = it.copy(searchQuery = query)
            deriveState(next)
        }
    }

    fun filter(filter: PurchaseStatusFilter) {
        _uiState.update {
            val next = it.copy(selectedFilter = filter)
            deriveState(next)
        }
    }

    fun applyPurchaseFilters(filters: PurchaseHistoryFilter) {
        _uiState.update {
            val next = it.copy(appliedFilters = filters)
            deriveState(next)
        }
    }

    fun placeOrder(order: RestockOrder) {
        viewModelScope.launch {
            restockOrderRepository.placePurchaseOrder(order.id)
                .onSuccess {
                    AppLogger.info(
                        area = "Purchases",
                        event = "purchase_place_success",
                        message = "Purchase marked ordered.",
                        "order" to order.fallbackOrderLabel
                    )
                    showSuccess("${order.fallbackOrderLabel} marked ordered.")
                }
                .onFailure {
                    AppLogger.error(
                        area = "Purchases",
                        event = "purchase_save_failed",
                        message = "Could not place purchase.",
                        throwable = it,
                        "order" to order.fallbackOrderLabel
                    )
                    showError(it.message ?: "Could not place purchase order.")
                }
        }
    }

    fun deleteDraft(order: RestockOrder) {
        viewModelScope.launch {
            restockOrderRepository.deleteRestockOrder(order.id)
                .onSuccess { showSuccess("${order.fallbackOrderLabel} deleted.") }
                .onFailure { showError(it.message ?: "Could not delete purchase.") }
        }
    }

    fun archivePurchase(order: RestockOrder) {
        viewModelScope.launch {
            restockOrderRepository.archiveRestockOrder(order.id)
                .onSuccess {
                    AppLogger.info(
                        area = "Purchases",
                        event = "purchase_archive_success",
                        message = "Purchase archived.",
                        "order" to order.fallbackOrderLabel
                    )
                    showSuccess("${order.fallbackOrderLabel} archived.")
                }
                .onFailure { showError(it.message ?: "Could not archive purchase.") }
        }
    }

    fun restorePurchase(order: RestockOrder) {
        viewModelScope.launch {
            restockOrderRepository.restoreRestockOrder(order.id)
                .onSuccess { showSuccess("${order.fallbackOrderLabel} restored.") }
                .onFailure { showError(it.message ?: "Could not restore purchase.") }
        }
    }

    fun cancelOrder(order: RestockOrder) {
        viewModelScope.launch {
            restockOrderRepository.cancelRestockOrder(order.id)
                .onSuccess {
                    AppLogger.info(
                        area = "Purchases",
                        event = "purchase_cancel_success",
                        message = "Purchase cancelled.",
                        "order" to order.fallbackOrderLabel
                    )
                    showSuccess("${order.fallbackOrderLabel} cancelled.")
                }
                .onFailure { showError(it.message ?: "Could not cancel purchase.") }
        }
    }

    fun startPartialReceive(orderId: String) {
        val order = _uiState.value?.restockOrders.orEmpty().firstOrNull { it.id == orderId } ?: return
        _uiState.update {
            it.copy(
                receiveDraft = PurchaseReceiveDraft(
                    purchaseId = order.id,
                    orderLabel = order.fallbackOrderLabel,
                    locationName = order.receivingLocationName.ifBlank { "Receiving" }
                ),
                receivePickerQuery = "",
                errorMessage = null,
                successMessage = null,
                receiveCompletedMessage = null
            )
        }
    }

    fun updateReceiveMemo(memo: String) {
        _uiState.update { state ->
            state.copy(
                receiveDraft = state.receiveDraft?.copy(
                    memo = normalizeReceiveMemo(state.receiveDraft.orderLabel, memo)
                )
            )
        }
    }

    fun setReceivePickerQuery(query: String) {
        _uiState.update { it.copy(receivePickerQuery = query) }
    }

    fun setReceiveQuantity(itemKey: String, quantity: Double) {
        _uiState.update { state ->
            val draft = state.receiveDraft ?: return@update state
            val updated = draft.selectedQuantities.toMutableMap()
            if (quantity > 0.0) updated[itemKey] = quantity else updated.remove(itemKey)
            state.copy(receiveDraft = draft.copy(selectedQuantities = updated))
        }
    }

    fun clearReceiveDraft() {
        _uiState.update {
            it.copy(
                receiveDraft = null,
                receivePickerQuery = "",
                receiveCompletedMessage = null
            )
        }
    }

    fun consumeReceiveCompletedMessage() {
        _uiState.update { it.copy(receiveCompletedMessage = null) }
    }

    fun currentReceiveOrder(): RestockOrder? {
        val draft = _uiState.value?.receiveDraft ?: return null
        return _uiState.value?.restockOrders.orEmpty().firstOrNull { it.id == draft.purchaseId }
    }

    fun receivePickerItems(): List<PurchaseReceivePickerRow> {
        val state = _uiState.value ?: return emptyList()
        val draft = state.receiveDraft ?: return emptyList()
        val query = state.receivePickerQuery.trim()
        return receiveRowsForDraft(state, draft)
            .filter { row ->
                val item = row.item
                query.isBlank() ||
                    item.itemName.contains(query, ignoreCase = true) ||
                    item.sku.contains(query, ignoreCase = true) ||
                    item.barcode.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true) ||
                    item.brand.contains(query, ignoreCase = true)
            }
            .sortedBy { it.item.itemName.lowercase() }
    }

    fun selectedReceiveRows(): List<PurchaseReceivePickerRow> =
        _uiState.value
            ?.let { state -> state.receiveDraft?.let { draft -> receiveRowsForDraft(state, draft) } }
            .orEmpty()
            .filter { it.selectedQuantity > 0.0 }

    private fun receiveRowsForDraft(
        state: RestockOrdersUiState,
        draft: PurchaseReceiveDraft
    ): List<PurchaseReceivePickerRow> {
        val order = state.restockOrders.firstOrNull { it.id == draft.purchaseId } ?: return emptyList()
        return order.purchaseItems
            .filter { it.remainingQuantity() > 0.0 }
            .map { item ->
                PurchaseReceivePickerRow(
                    item = item,
                    remainingQuantity = item.remainingQuantity(),
                    availableQuantity = latestInventoryItems.firstOrNull { it.id == item.inventoryItemId }?.quantity ?: 0.0,
                    selectedQuantity = draft.selectedQuantities[item.receiveKey()] ?: 0.0
                )
            }
    }

    fun submitPartialReceive() {
        val state = _uiState.value ?: return
        val draft = state.receiveDraft ?: return
        val order = state.restockOrders.firstOrNull { it.id == draft.purchaseId } ?: run {
            showError("Purchase could not be found.")
            return
        }
        if (draft.isSubmitting) return
        if (draft.selectedQuantities.isEmpty()) {
            showError("Select at least one item to stock in.")
            return
        }

        _uiState.update {
            it.copy(
                receiveDraft = draft.copy(isSubmitting = true),
                errorMessage = null,
                successMessage = null,
                receiveCompletedMessage = null
            )
        }
        AppLogger.info(
            area = "Purchases",
            event = "purchase_partial_receive_start",
            message = "Partial receive started.",
            "order" to order.fallbackOrderLabel
        )
        viewModelScope.launch {
            restockOrderRepository.receivePurchaseOrder(order.id, draft.selectedQuantities, draft.memo)
                .onSuccess {
                    AppLogger.info(
                        area = "Purchases",
                        event = "purchase_partial_receive_success",
                        message = "Partial receive completed.",
                        "order" to order.fallbackOrderLabel,
                        "items" to draft.selectedQuantities.size,
                        "quantity" to draft.selectedQuantities.values.sum()
                    )
                    _uiState.update {
                        it.copy(
                            receiveDraft = null,
                            receivePickerQuery = "",
                            receiveCompletedMessage = "Stocked in"
                        )
                    }
                }
                .onFailure { error ->
                    AppLogger.error(
                        area = "Purchases",
                        event = "purchase_partial_receive_failed",
                        message = "Could not receive purchase.",
                        throwable = error,
                        "order" to order.fallbackOrderLabel
                    )
                    _uiState.update {
                        it.copy(
                            receiveDraft = draft.copy(isSubmitting = false),
                            errorMessage = error.message ?: "Could not receive purchase."
                        )
                    }
                }
        }
    }

    fun receivePurchase(order: RestockOrder, receivedQuantities: Map<String, Double>, note: String = "") {
        if (!receivingOrderIds.add(order.id)) {
            AppLogger.warn(
                area = "Purchases",
                event = "purchase_receive_duplicate_blocked",
                message = "Duplicate receive tap ignored.",
                "order" to order.fallbackOrderLabel
            )
            return
        }
        AppLogger.info(
            area = "Purchases",
            event = "purchase_receive_all_start",
            message = "Receive purchase started.",
            "order" to order.fallbackOrderLabel,
            "items" to receivedQuantities.size,
            "quantity" to receivedQuantities.values.sum()
        )
        viewModelScope.launch {
            try {
                restockOrderRepository.receivePurchaseOrder(order.id, receivedQuantities, note)
                    .onSuccess {
                        val statusLabel = if (order.purchaseItems.all {
                                val receivedNow = receivedQuantities[it.inventoryItemId.ifBlank { listOf(it.itemName.trim(), it.sku.trim(), it.barcode.trim()).joinToString("|") }] ?: 0.0
                                it.remainingQuantity() - receivedNow <= 0.0
                            }
                        ) {
                            "received."
                        } else {
                            "partially received."
                        }
                        AppLogger.info(
                            area = "Purchases",
                            event = "purchase_receive_all_success",
                            message = "Receive purchase completed.",
                            "order" to order.fallbackOrderLabel,
                            "status" to statusLabel.removeSuffix(".")
                        )
                        showSuccess("${order.fallbackOrderLabel} $statusLabel")
                    }
                    .onFailure {
                        AppLogger.error(
                            area = "Purchases",
                            event = "purchase_receive_all_failed",
                            message = "Could not receive purchase.",
                            throwable = it,
                            "order" to order.fallbackOrderLabel
                        )
                        showError(it.message ?: "Could not receive purchase.")
                    }
            } finally {
                receivingOrderIds.remove(order.id)
            }
        }
    }

    fun receiveAll(order: RestockOrder) {
        val remaining = order.purchaseItems.associate { item ->
            item.inventoryItemId.ifBlank { listOf(item.itemName.trim(), item.sku.trim(), item.barcode.trim()).joinToString("|") } to item.remainingQuantity()
        }.filterValues { it > 0.0 }
        if (remaining.isEmpty()) {
            showError("This purchase is already fully received.")
            return
        }
        receivePurchase(order, remaining)
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, receiveCompletedMessage = null) }
    }

    private fun observeRestockOrders() {
        viewModelScope.launch {
            restockOrderRepository.observeRestockOrders(includeArchived = true).collect { result ->
                result
                    .onSuccess { orders ->
                        AppLogger.info(
                            area = "Purchases",
                            event = "purchase_list_loaded",
                            message = "Purchase list refreshed.",
                            "count" to orders.size
                        )
                        _uiState.update {
                            val next = it.copy(isLoading = false, restockOrders = orders, errorMessage = null)
                            deriveState(next)
                        }
                    }
                    .onFailure { error ->
                        showError(error.message ?: "Could not load purchases.")
                    }
            }
        }
    }

    private fun observeInventoryItems() {
        viewModelScope.launch {
            inventoryRepository.observeInventoryItems().collect { result ->
                result
                    .onSuccess { items ->
                        latestInventoryItems = items.filterNot { it.isArchived }
                        _uiState.update { deriveState(it.copy(errorMessage = null)) }
                    }
                    .onFailure { error ->
                        showError(error.message ?: "Could not load purchase items.")
                    }
            }
        }
    }

    private fun observeSuppliers() {
        viewModelScope.launch {
            supplierRepository.observeSuppliers().collect { result ->
                result.onSuccess { suppliers ->
                    _uiState.update { it.copy(suppliers = suppliers) }
                }
            }
        }
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches().collect { result ->
                result.onSuccess { branches ->
                    _uiState.update { it.copy(branches = branches) }
                }
            }
        }
    }

    private fun deriveState(state: RestockOrdersUiState): RestockOrdersUiState {
        val query = state.searchQuery.trim()
        val purchaseFilters = state.appliedFilters
        val matchingOrders = state.restockOrders
            // Archived purchases live on the dedicated archive page so the normal tab stays clean.
            .filterNot { it.isArchived }
            .filter { order ->
                query.isBlank() ||
                    order.fallbackOrderLabel.contains(query, ignoreCase = true) ||
                    order.itemName.contains(query, ignoreCase = true) ||
                    order.supplierName.contains(query, ignoreCase = true) ||
                    order.receivingLocationName.contains(query, ignoreCase = true)
            }
            .filter { order -> order.matchesFilters(purchaseFilters) }
            .sortedByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.createdAt }

        val visibleOrders = when (state.selectedFilter) {
            PurchaseStatusFilter.ALL -> matchingOrders.filter { it.normalizedStatus != RestockStatus.CANCELLED }
            PurchaseStatusFilter.DRAFT -> matchingOrders.filter { it.normalizedStatus == RestockStatus.DRAFT }
            PurchaseStatusFilter.ORDERED -> matchingOrders.filter { it.normalizedStatus == RestockStatus.ORDERED }
            PurchaseStatusFilter.PARTIALLY_RECEIVED ->
                matchingOrders.filter { it.normalizedStatus == RestockStatus.PARTIALLY_RECEIVED }
            PurchaseStatusFilter.RECEIVED -> matchingOrders.filter { it.normalizedStatus == RestockStatus.RECEIVED }
            PurchaseStatusFilter.CANCELLED -> matchingOrders.filter { it.normalizedStatus == RestockStatus.CANCELLED }
        }

        return state.copy(
            visibleOrders = visibleOrders,
            hasActiveFilters = purchaseFilters.hasActiveFilters()
        )
    }

    private fun normalizeReceiveMemo(orderLabel: String, memo: String): String {
        val clean = memo.trim()
        val defaultMemo = defaultReceiveMemo(orderLabel)
        if (clean.equals(defaultMemo, ignoreCase = true)) return ""
        if (clean.startsWith(defaultMemo, ignoreCase = true)) {
            return clean.removePrefix(defaultMemo)
                .trim()
                .removePrefix("-")
                .trim()
        }
        return clean
    }

    fun defaultReceiveMemo(orderLabel: String): String = "Stock in from purchase $orderLabel"

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    // Extra purchase filters sit alongside the status tabs so users can combine both safely.
    private fun RestockOrder.matchesFilters(filters: PurchaseHistoryFilter): Boolean {
        if (filters.dateSelection != null) {
            val orderTime = orderDate.takeIf { it > 0L } ?: createdAt
            if (orderTime < filters.dateSelection.normalizedStartMillis || orderTime > filters.dateSelection.normalizedEndMillis) {
                return false
            }
        }

        if (filters.orderNumberQuery.isNotBlank()) {
            val query = filters.orderNumberQuery.trim()
            val matchesOrderNumber = orderNumber.contains(query, ignoreCase = true) ||
                fallbackOrderLabel.contains(query, ignoreCase = true)
            if (!matchesOrderNumber) return false
        }

        if (filters.itemKey.isNotBlank() && purchaseItems.none { it.filterKey() == filters.itemKey }) {
            return false
        }

        if (filters.memberName.isNotBlank()) {
            val memberMatches = createdByName.equals(filters.memberName, ignoreCase = true) ||
                createdBy.equals(filters.memberName, ignoreCase = true)
            if (!memberMatches) return false
        }

        if (filters.partnerName.isNotBlank() && !supplierName.equals(filters.partnerName, ignoreCase = true)) {
            return false
        }

        return true
    }
}

data class PurchaseReceivePickerRow(
    val item: com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem,
    val remainingQuantity: Double,
    val availableQuantity: Double,
    val selectedQuantity: Double
)

private fun PurchaseOrderItem.filterKey(): String =
    inventoryItemId.ifBlank { listOf(itemName.trim(), sku.trim(), barcode.trim()).joinToString("|") }
