package com.example.pantryhub_assignment3_fy.ui.restock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.PurchaseDraftInput
import com.example.pantryhub_assignment3_fy.data.repository.RestockOrderRepository
import com.example.pantryhub_assignment3_fy.data.repository.SupplierRepository
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.ui.storage.SortOption
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

data class PurchaseEditorUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val suppliers: List<Supplier> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
    val pickerQuery: String = "",
    val pickerInStockOnly: Boolean = false,
    val pickerSortOption: SortOption = SortOption.NAME_ASC,
    val form: PurchaseFormState = PurchaseFormState(orderDate = System.currentTimeMillis()),
    val closeAfterSave: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class PurchaseEditorViewModel(
    private val restockOrderRepository: RestockOrderRepository = RestockOrderRepository(),
    private val supplierRepository: SupplierRepository = SupplierRepository(),
    private val branchRepository: BranchRepository = BranchRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(PurchaseEditorUiState())
    val uiState: LiveData<PurchaseEditorUiState> = _uiState

    init {
        observeSuppliers()
        observeBranches()
        observeInventory()
        startFreshDraft()
    }

    fun startFreshDraft() {
        // Marks when the purchase form is intentionally reset to a brand-new draft.
        AppLogger.debug(
            area = "PurchaseEditor",
            event = "draft_start_fresh",
            message = "Starting a fresh purchase draft."
        )
        _uiState.update {
            it.copy(
                form = PurchaseFormState(
                    orderDate = System.currentTimeMillis()
                ),
                closeAfterSave = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun loadDraftForEdit(order: RestockOrder) {
        // Shows when an existing purchase is loaded back into the form for editing.
        AppLogger.info(
            area = "PurchaseEditor",
            event = "draft_load_edit",
            message = "Loading purchase draft for edit.",
            "purchaseId" to order.id,
            "itemCount" to order.purchaseItems.size
        )
        // The detail screen hands the selected purchase back into the shared form state so edit
        // can reuse the current New Purchase UI instead of maintaining a second purchase form.
        _uiState.update {
            it.copy(
                form = PurchaseFormState(
                    purchaseId = order.id,
                    supplierId = order.supplierId,
                    supplierName = order.supplierName,
                    receivingLocationId = order.receivingLocationId,
                    receivingLocationName = order.receivingLocationName,
                    orderDate = order.orderDate,
                    expectedDeliveryDate = order.expectedDeliveryDate,
                    memo = order.memo,
                    items = order.purchaseItems
                ),
                closeAfterSave = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun setSupplier(supplier: Supplier) {
        // Confirms which supplier the user selected on the purchase form.
        AppLogger.debug(
            area = "PurchaseEditor",
            event = "supplier_selected",
            message = "Supplier selected for purchase.",
            "supplierId" to supplier.id,
            "supplierName" to supplier.name
        )
        _uiState.update {
            it.copy(form = it.form.copy(supplierId = supplier.id, supplierName = supplier.name))
        }
    }

    fun setBranch(branch: Branch) {
        // Confirms which receiving location was chosen for the purchase.
        AppLogger.debug(
            area = "PurchaseEditor",
            event = "location_selected",
            message = "Receiving location selected for purchase.",
            "branchId" to branch.id,
            "branchName" to branch.name
        )
        _uiState.update {
            val locationChanged = it.form.receivingLocationId != branch.id
            it.copy(
                form = it.form.copy(
                    receivingLocationId = branch.id,
                    receivingLocationName = branch.name,
                    items = if (locationChanged) emptyList() else it.form.items
                ),
                successMessage = if (locationChanged && it.form.items.isNotEmpty()) {
                    "Purchase items were cleared because the receiving location changed."
                } else {
                    it.successMessage
                }
            )
        }
    }

    fun setOrderDate(date: Long) {
        _uiState.update {
            val expected = it.form.expectedDeliveryDate.takeIf { value -> value >= date } ?: 0L
            it.copy(form = it.form.copy(orderDate = date, expectedDeliveryDate = expected))
        }
    }

    fun setExpectedDate(date: Long?) {
        _uiState.update {
            it.copy(form = it.form.copy(expectedDeliveryDate = date ?: 0L))
        }
    }

    fun setMemo(memo: String) {
        _uiState.update { it.copy(form = it.form.copy(memo = memo)) }
    }

    fun setPickerQuery(query: String) {
        _uiState.update { it.copy(pickerQuery = query) }
    }

    fun setPickerInStockOnly(inStockOnly: Boolean) {
        _uiState.update { it.copy(pickerInStockOnly = inStockOnly) }
    }

    fun setPickerSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(pickerSortOption = sortOption) }
    }

    fun addOrUpdateItem(inventoryItem: InventoryItem, quantity: Double) {
        val state = _uiState.value ?: return
        val receivingItem = state.findItemForReceivingLocation(inventoryItem) ?: inventoryItem
        // Tracks item quantity changes so item picker issues can be debugged from Logcat.
        AppLogger.debug(
            area = "PurchaseEditor",
            event = "item_added_or_updated",
            message = "Purchase item quantity saved.",
            "inventoryItemId" to receivingItem.id,
            "itemName" to receivingItem.name,
            "receivingLocation" to state.form.receivingLocationName,
            "quantity" to quantity
        )
        _uiState.update { current ->
            val updatedItems = state.form.items.toMutableList()
            val existingIndex = updatedItems.indexOfFirst { it.sameProductAs(receivingItem) }
            val nextLine = PurchaseOrderItem(
                inventoryItemId = receivingItem.id,
                itemName = receivingItem.name,
                sku = receivingItem.sku,
                barcode = receivingItem.barcode,
                brand = receivingItem.brand,
                category = receivingItem.category,
                orderedQuantity = quantity,
                unit = receivingItem.unit,
                unitCost = receivingItem.costPrice,
                imageUrl = receivingItem.imageUrl,
                supplierName = receivingItem.supplierName
            )
            if (existingIndex >= 0) updatedItems[existingIndex] = nextLine else updatedItems += nextLine
            current.copy(form = current.form.copy(items = updatedItems))
        }
    }

    fun removeItem(inventoryItemId: String) {
        // Helps verify that removing a purchase line updates the draft as expected.
        AppLogger.debug(
            area = "PurchaseEditor",
            event = "item_removed",
            message = "Purchase item removed.",
            "inventoryItemId" to inventoryItemId
        )
        _uiState.update { state ->
            state.copy(form = state.form.copy(items = state.form.items.filterNot { it.inventoryItemId == inventoryItemId }))
        }
    }

    fun addScannedBarcode(barcode: String): Boolean {
        val normalized = barcode.trim()
        if (normalized.isBlank()) return false
        // Purchase scanning reuses the same active, distinct product pool as the purchase item picker.
        val item = _uiState.value
            ?.inventoryItems
            .orEmpty()
            .groupDistinctProducts(_uiState.value?.form?.receivingLocationId.orEmpty())
            .firstOrNull { it.barcode.equals(normalized, ignoreCase = true) }
            ?: return false
        addOrUpdateItem(item, 1.0)
        return true
    }

    fun saveDraft(placeOrder: Boolean) {
        val current = _uiState.value ?: return
        if (current.isSubmitting) return

        // Marks the start of saving so form resets or save delays can be traced.
        AppLogger.info(
            area = "PurchaseEditor",
            event = "save_started",
            message = "Saving purchase form.",
            "purchaseId" to current.form.purchaseId,
            "isEditing" to current.form.isEditing,
            "placeOrder" to placeOrder,
            "itemCount" to current.form.items.size
        )
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            val saveResult = current.form.purchaseId
                .takeIf { it.isNotBlank() }
                ?.let { orderId ->
                    restockOrderRepository.updatePurchaseDraft(orderId, current.form.toInput())
                        .map { orderId }
                }
                ?: restockOrderRepository.createPurchaseDraft(current.form.toInput())

            saveResult
                .onSuccess { orderId ->
                    // Confirms the purchase draft itself was stored successfully.
                    AppLogger.info(
                        area = "PurchaseEditor",
                        event = "save_success",
                        message = "Purchase draft saved successfully.",
                        "purchaseId" to orderId,
                        "placeOrder" to placeOrder
                    )
                    if (placeOrder) {
                        restockOrderRepository.placePurchaseOrder(orderId)
                            .onSuccess {
                                // Confirms the draft also advanced into a placed purchase order.
                                AppLogger.info(
                                    area = "PurchaseEditor",
                                    event = "place_success",
                                    message = "Purchase order placed successfully.",
                                    "purchaseId" to orderId
                                )
                                _uiState.update {
                                    it.copy(
                                        isSubmitting = false,
                                        form = freshPurchaseForm(),
                                        closeAfterSave = true,
                                        successMessage = if (current.form.isEditing) {
                                            "Purchase order updated and placed."
                                        } else {
                                            "Purchase order placed."
                                        }
                                    )
                                }
                            }
                            .onFailure { error ->
                                // Captures order-placement failures after the draft save already succeeded.
                                AppLogger.error(
                                    area = "PurchaseEditor",
                                    event = "place_failed",
                                    message = "Could not place purchase order.",
                                    throwable = error,
                                    "purchaseId" to orderId
                                )
                                _uiState.update {
                                    it.copy(isSubmitting = false, errorMessage = error.message ?: "Could not place order.")
                                }
                            }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                form = freshPurchaseForm(),
                                closeAfterSave = true,
                                successMessage = if (current.form.isEditing) {
                                    "Purchase draft updated."
                                } else {
                                    "Purchase draft saved."
                                }
                            )
                        }
                    }
                }
                .onFailure { error ->
                    // Captures save failures before the purchase draft could be persisted.
                    AppLogger.error(
                        area = "PurchaseEditor",
                        event = "save_failed",
                        message = "Could not save purchase draft.",
                        throwable = error,
                        "purchaseId" to current.form.purchaseId,
                        "placeOrder" to placeOrder
                    )
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = error.message ?: "Could not save purchase.")
                    }
                }
        }
    }

    fun consumeCloseSignal() {
        _uiState.update { it.copy(closeAfterSave = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun pickerItems(): List<InventoryItem> {
        val state = _uiState.value ?: return emptyList()
        val query = state.pickerQuery.trim()
        return state.inventoryItems
            .groupDistinctProducts(state.form.receivingLocationId)
            .filter { !state.pickerInStockOnly || it.quantity > 0.0 }
            .filter { item ->
                query.isBlank() ||
                    item.name.contains(query, true) ||
                    item.sku.contains(query, true) ||
                    item.barcode.contains(query, true) ||
                    item.category.contains(query, true) ||
                    item.brand.contains(query, true)
            }
            .sortedWith(state.pickerSortOption.comparator())
    }

    private fun observeSuppliers() {
        viewModelScope.launch {
            supplierRepository.observeSuppliers().collect { result ->
                result.onSuccess { partners ->
                    val suppliers = partners.filter {
                        PartnerType.fromValue(it.partnerType) == PartnerType.SUPPLIER
                    }
                    _uiState.update { it.copy(suppliers = suppliers, isLoading = false) }
                }
            }
        }
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches().collect { result ->
                result.onSuccess { branches ->
                    _uiState.update { it.copy(branches = branches, isLoading = false) }
                }
            }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            inventoryRepository.observeInventoryItems().collect { result ->
                result.onSuccess { inventoryItems ->
                    _uiState.update { it.copy(inventoryItems = inventoryItems.filterNot { item -> item.isArchived }, isLoading = false) }
                }
            }
        }
    }

    private fun PurchaseFormState.toInput(): PurchaseDraftInput = PurchaseDraftInput(
        supplierId = supplierId,
        supplierName = supplierName,
        receivingLocationId = receivingLocationId,
        receivingLocationName = receivingLocationName,
        orderDate = orderDate,
        expectedDeliveryDate = expectedDeliveryDate,
        memo = memo,
        items = items
    )

    private fun freshPurchaseForm(): PurchaseFormState =
        PurchaseFormState(orderDate = System.currentTimeMillis())

    private fun List<InventoryItem>.groupDistinctProducts(preferredBranchId: String): List<InventoryItem> {
        val grouped = linkedMapOf<String, InventoryItem>()
        forEach { item ->
            val key = ProductIdentity.key(item)
            val existing = grouped[key]
            if (existing == null || item.shouldReplace(existing, preferredBranchId)) {
                grouped[key] = item
            }
        }
        return grouped.values.toList()
    }

    private fun PurchaseEditorUiState.findItemForReceivingLocation(selectedItem: InventoryItem): InventoryItem? =
        inventoryItems.firstOrNull { item ->
            item.branchId == form.receivingLocationId && ProductIdentity.sameProduct(item, selectedItem)
        }

    private fun PurchaseOrderItem.sameProductAs(item: InventoryItem): Boolean =
        when {
            sku.isNotBlank() && item.sku.isNotBlank() -> sku.equals(item.sku, ignoreCase = true)
            barcode.isNotBlank() && item.barcode.isNotBlank() -> barcode.equals(item.barcode, ignoreCase = true)
            else -> itemName.trim().equals(item.name.trim(), ignoreCase = true) &&
                brand.trim().equals(item.brand.trim(), ignoreCase = true) &&
                category.trim().equals(item.category.trim(), ignoreCase = true)
        }

    private fun InventoryItem.shouldReplace(existing: InventoryItem, preferredBranchId: String): Boolean {
        val thisIsPreferred = preferredBranchId.isNotBlank() && branchId == preferredBranchId
        val existingIsPreferred = preferredBranchId.isNotBlank() && existing.branchId == preferredBranchId
        return when {
            thisIsPreferred && !existingIsPreferred -> true
            !thisIsPreferred && existingIsPreferred -> false
            else -> updatedAt > existing.updatedAt
        }
    }

    private fun SortOption.comparator(): Comparator<InventoryItem> = when (this) {
        SortOption.EXPIRY_SOONEST -> compareBy { it.expiryDate.takeIf { expiry -> expiry > 0L } ?: Long.MAX_VALUE }
        SortOption.NAME_ASC -> compareBy { it.name.lowercase() }
        SortOption.NAME_DESC -> compareByDescending { it.name.lowercase() }
        SortOption.QUANTITY_LOW -> compareBy { it.quantity }
        SortOption.QUANTITY_HIGH -> compareByDescending { it.quantity }
        SortOption.SAFETY_STOCK_LOW -> compareBy { it.reorderPoint.takeIf { point -> point > 0 } ?: it.reorderThreshold.toInt() }
        SortOption.RESTOCK_URGENCY -> compareBy<InventoryItem> {
            when {
                it.reorderPoint > 0 && it.quantity <= it.reorderPoint -> 0
                it.reorderThreshold > 0 && it.quantity <= it.reorderThreshold -> 0
                else -> 1
            }
        }.thenBy { it.quantity }
        SortOption.RECENTLY_UPDATED -> compareByDescending { it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt }
    }
}
