package com.example.pantryhub_assignment3_fy.ui.movement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.data.repository.CustomerRepository
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.StockMovementRepository
import com.example.pantryhub_assignment3_fy.data.repository.SupplierRepository
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.Customer
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.ui.common.WarningDialogContent
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

class StockInTransactionViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val branchRepository: BranchRepository = BranchRepository(),
    private val customerRepository: CustomerRepository = CustomerRepository(),
    private val supplierRepository: SupplierRepository = SupplierRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val stockMovementRepository: StockMovementRepository = StockMovementRepository()
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle,
        BranchRepository(),
        CustomerRepository(),
        SupplierRepository(),
        InventoryRepository(),
        StockMovementRepository()
    )

    private val _uiState = MutableLiveData(
        StockInTransactionUiState(
            mode = savedStateHandle.get<String>(KEY_MODE)?.let { runCatching { TransactionMode.valueOf(it) }.getOrNull() } ?: TransactionMode.STOCK_IN,
            transactionAt = savedStateHandle[KEY_TRANSACTION_AT] ?: System.currentTimeMillis(),
            editTransactionId = savedStateHandle.get<String>(KEY_EDIT_TRANSACTION_ID).orEmpty()
        )
    )
    val uiState: LiveData<StockInTransactionUiState> = _uiState

    init {
        observeBranches()
        observeCustomers()
        observeSuppliers()
        observeInventory()
    }

    fun startNewTransaction(mode: TransactionMode) {
        clearEditState()
        if (savedStateHandle.get<Boolean>(KEY_FORM_STARTED) == true && _uiState.value?.mode == mode) return
        AppLogger.info(
            area = "Transactions",
            event = "transaction_form_opened",
            message = "Transaction form opened.",
            "mode" to mode.name
        )
        savedStateHandle[KEY_FORM_STARTED] = true
        savedStateHandle[KEY_MODE] = mode.name
        val transactionAt = System.currentTimeMillis()
        savedStateHandle[KEY_TRANSACTION_AT] = transactionAt
        _uiState.update {
            StockInTransactionUiState(
                mode = mode,
                branches = it.branches,
                customers = it.customers,
                suppliers = it.suppliers,
                inventoryItems = it.inventoryItems,
                isLoading = it.isLoading,
                transactionAt = transactionAt
            )
        }
    }

    fun startEditingTransaction(transactionId: String, mode: TransactionMode) {
        if (transactionId.isBlank()) {
            showError("Transaction could not be found.")
            return
        }
        clearEditState()
        showWarning(
            title = "Transaction history is read-only",
            message = "Completed stock transactions cannot be edited. Create an Adjust Stock transaction to correct stock instead."
        )
    }

    fun setTransactionAt(transactionAt: Long) {
        if (transactionAt > System.currentTimeMillis()) {
            showError(FUTURE_TRANSACTION_ERROR)
            return
        }
        savedStateHandle[KEY_TRANSACTION_AT] = transactionAt
        _uiState.update { it.copy(transactionAt = transactionAt) }
    }

    fun discardTransaction() {
        savedStateHandle[KEY_FORM_STARTED] = false
        savedStateHandle[KEY_TRANSACTION_AT] = System.currentTimeMillis()
        clearEditState()
    }

    fun selectBranch(branch: Branch) {
        AppLogger.info(
            area = "Transactions",
            event = "transaction_location_selected",
            message = "Transaction location selected.",
            "location" to branch.name
        )
        _uiState.update {
            val changed = it.selectedBranch?.id?.let { currentId -> currentId != branch.id } == true
            val clearsItems = changed && it.selectedLines.isNotEmpty()
            it.copy(
                selectedBranch = branch,
                selectedLines = if (changed) emptyMap() else it.selectedLines,
                infoMessage = if (clearsItems) "Selected items were cleared because the location changed." else it.infoMessage
            )
        }
    }

    fun selectDestinationBranch(branch: Branch) {
        AppLogger.info(
            area = "Transactions",
            event = "transaction_destination_selected",
            message = "Move Stock destination selected.",
            "location" to branch.name
        )
        _uiState.update { state ->
            state.copy(
                selectedDestinationBranch = branch,
                errorMessage = if (branch.id == state.selectedBranch?.id) "Choose a different To location." else state.errorMessage
            )
        }
    }

    fun selectSupplier(supplier: Supplier?) {
        supplier?.let {
            AppLogger.info(
                area = "Suppliers",
                event = "supplier_selected",
                message = "Supplier selected for transaction.",
                "name" to it.name
            )
        }
        _uiState.update { it.copy(selectedSupplier = supplier) }
    }

    fun selectCustomer(customer: Customer?) {
        customer?.let {
            AppLogger.info(
                area = "Customers",
                event = "customer_selected",
                message = "Customer selected for transaction.",
                "name" to it.name
            )
        }
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun selectBranchById(branchId: String, branchName: String) {
        val branch = _uiState.value?.branches.orEmpty().firstOrNull { it.id == branchId }
            ?: Branch(id = branchId, name = branchName)
        selectBranch(branch)
    }

    fun selectDestinationBranchById(branchId: String, branchName: String) {
        val branch = _uiState.value?.branches.orEmpty().firstOrNull { it.id == branchId }
            ?: Branch(id = branchId, name = branchName)
        selectDestinationBranch(branch)
    }

    fun selectSupplierById(supplierId: String, supplierName: String) {
        val supplier = _uiState.value?.suppliers.orEmpty().firstOrNull { it.id == supplierId }
            ?: Supplier(id = supplierId, name = supplierName)
        selectSupplier(supplier)
    }

    fun selectCustomerById(customerId: String, customerName: String) {
        val customer = _uiState.value?.customers.orEmpty().firstOrNull { it.id == customerId }
            ?: Customer(id = customerId, name = customerName)
        selectCustomer(customer)
    }

    fun setMemo(memo: String) {
        _uiState.update { it.copy(memo = memo.trim()) }
    }

    fun setPickerQuery(query: String) {
        _uiState.update { it.copy(pickerQuery = query) }
    }

    fun setPickerInStockOnly(inStockOnly: Boolean) {
        _uiState.update { it.copy(pickerInStockOnly = inStockOnly) }
    }

    fun setItemQuantity(
        item: InventoryItem,
        quantity: Double,
        expiryDate: Long? = null,
        preserveExistingExpiryDate: Boolean = true
    ) {
        _uiState.update { state ->
            val updated = state.selectedLines.toMutableMap()
            if (quantity > 0.0 || state.mode == TransactionMode.ADJUST_STOCK) {
                val previous = updated[item.id]
                updated[item.id] = StockInLine(
                    item = item,
                    quantity = quantity,
                    expiryDate = if (preserveExistingExpiryDate) expiryDate ?: previous?.expiryDate else expiryDate
                )
                AppLogger.debug(
                    area = "Transactions",
                    event = if (previous == null) "transaction_item_added" else "transaction_quantity_changed",
                    message = "Pending transaction item updated.",
                    "mode" to state.mode.name,
                    "item" to item.name,
                    "quantity" to quantity,
                    "location" to item.branchName
                )
            } else {
                updated.remove(item.id)
            }
            state.copy(selectedLines = updated)
        }
    }

    fun addScannedBarcode(barcode: String): Boolean {
        val normalized = barcode.trim()
        val state = _uiState.value ?: return false
        if (normalized.isBlank()) return false
        val branchId = state.selectedBranch?.id.orEmpty()
        // Barcode scan reuses the picker safety rules: selected location only, active stock for out/move.
        val item = state.inventoryItems.firstOrNull {
            it.id.isNotBlank() &&
                it.branchId == branchId &&
                it.barcode.equals(normalized, ignoreCase = true) &&
                (state.mode == TransactionMode.STOCK_IN || it.quantity > 0.0)
        } ?: return false
        setItemQuantity(item = item, quantity = state.selectedLines[item.id]?.quantity ?: 1.0)
        return true
    }

    fun removeItem(itemId: String) {
        _uiState.update { state ->
            state.copy(selectedLines = state.selectedLines - itemId)
        }
    }

    fun addBranch(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val existing = _uiState.value?.branches.orEmpty()
            .firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            selectBranch(existing)
            return
        }
        viewModelScope.launch {
            branchRepository.addBranch(Branch(name = cleanName))
                .onSuccess { id -> selectBranch(Branch(id = id, name = cleanName)) }
                .onFailure { showError(it.message ?: "Could not save location.") }
        }
    }

    fun addDestinationBranch(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val existing = _uiState.value?.branches.orEmpty()
            .firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            selectDestinationBranch(existing)
            return
        }
        viewModelScope.launch {
            branchRepository.addBranch(Branch(name = cleanName))
                .onSuccess { id -> selectDestinationBranch(Branch(id = id, name = cleanName)) }
                .onFailure { showError(it.message ?: "Could not save location.") }
        }
    }

    fun addSupplier(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val existing = _uiState.value?.suppliers.orEmpty()
            .firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            selectSupplier(existing)
            return
        }
        viewModelScope.launch {
            supplierRepository.addSupplier(Supplier(name = cleanName))
                .onSuccess { id -> selectSupplier(Supplier(id = id, name = cleanName)) }
                .onFailure { showError(it.message ?: "Could not save partner.") }
        }
    }

    fun addCustomer(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val existing = _uiState.value?.customers.orEmpty().firstOrNull { it.name.equals(cleanName, true) }
        if (existing != null) {
            selectCustomer(existing)
            return
        }
        viewModelScope.launch {
            customerRepository.addCustomer(cleanName)
                .onSuccess { id -> selectCustomer(Customer(id = id, name = cleanName)) }
                .onFailure { showError(it.message ?: "Could not save customer.") }
        }
    }

    // Breakpoint: start here to inspect selected mode, location, destination, and pending lines before validation.
    fun submitTransaction() {
        val state = _uiState.value ?: return
        if (state.isSubmitting) return
        val branch = state.selectedBranch
        val destinationBranch = state.selectedDestinationBranch
        val lines = state.selectedLines.values.toList()
        when {
            branch == null -> {
                logTransactionValidationFailure(state.mode, "missing_location")
                showWarning(
                    title = "Location required",
                    message = if (state.mode == TransactionMode.MOVE_STOCK) {
                        "Select a From location before continuing."
                    } else {
                        "Select a location before continuing."
                    }
                )
                return
            }
            state.mode == TransactionMode.MOVE_STOCK && destinationBranch == null -> {
                logTransactionValidationFailure(state.mode, "missing_destination")
                showWarning(
                    title = "Destination required",
                    message = "Select a To location before moving stock."
                )
                return
            }
            state.mode == TransactionMode.MOVE_STOCK && destinationBranch?.id == branch.id -> {
                logTransactionValidationFailure(state.mode, "same_location")
                showWarning(
                    title = "Invalid transfer",
                    message = "From and To locations cannot be the same. Choose a different destination location."
                )
                return
            }
            lines.isEmpty() -> {
                logTransactionValidationFailure(state.mode, "missing_items")
                showError("Add at least one item.")
                return
            }
            state.mode != TransactionMode.ADJUST_STOCK && lines.any { it.quantity <= 0.0 } -> {
                logTransactionValidationFailure(state.mode, "invalid_quantity")
                showError("Enter a valid quantity.")
                return
            }
            state.mode == TransactionMode.ADJUST_STOCK && lines.any { it.quantity < 0.0 } -> {
                logTransactionValidationFailure(state.mode, "invalid_quantity")
                showError("Enter a valid quantity.")
                return
            }
            state.mode == TransactionMode.ADJUST_STOCK && lines.any { it.quantity == it.item.quantity } -> {
                logTransactionValidationFailure(state.mode, "unchanged_adjustment")
                showError("Adjusted quantity must be different from current stock.")
                return
            }
            state.transactionAt > System.currentTimeMillis() -> {
                logTransactionValidationFailure(state.mode, "future_date")
                showWarning(
                    title = "Invalid transaction date",
                    message = FUTURE_TRANSACTION_ERROR
                )
                return
            }
            // Breakpoint: inspect current quantity versus requested stock-out quantity and confirm blocked validation.
            state.mode == TransactionMode.STOCK_OUT && lines.any { it.quantity > it.item.quantity } -> {
                logTransactionValidationFailure(state.mode, "insufficient_stock")
                showWarning(
                    title = "Not enough stock",
                    message = "Stock Out quantity cannot exceed the available stock for this item."
                )
                return
            }
            // Breakpoint: inspect move quantity against source stock before allowing transfer.
            state.mode == TransactionMode.MOVE_STOCK && lines.any { it.quantity > it.item.quantity } -> {
                logTransactionValidationFailure(state.mode, "insufficient_stock")
                showWarning(
                    title = "Not enough stock",
                    message = "Move quantity cannot exceed the available stock in the source location."
                )
                return
            }
            state.mode == TransactionMode.STOCK_IN && lines.any { line ->
                line.expiryDate?.let { it.isBeforeTransactionDate(state.transactionAt) } == true
            } -> {
                logTransactionValidationFailure(state.mode, "invalid_expiry_date")
                showWarning(
                    title = "Invalid expiry date",
                    message = "Expiry date cannot be earlier than the transaction date."
                )
                return
            }
        }

        AppLogger.info(
            area = "Transactions",
            event = "transaction_submit_start",
            message = "Submitting stock transaction.",
            "mode" to state.mode.name,
            "location" to branch.name,
            "items" to lines.size,
            "quantity" to lines.sumOf { it.quantity }
        )
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            runCatching {
                require(!state.isEditing) {
                    "Completed stock transactions are read-only. Use Adjust Stock to correct stock."
                }
                val transactionId = UUID.randomUUID().toString()
                lines.forEach { line ->
                    // Breakpoint: inspect each selected line before the repository applies the movement logic.
                    if (state.mode == TransactionMode.MOVE_STOCK) {
                        stockMovementRepository.transferBetweenBranches(
                            sourceInventoryItem = line.effectiveItemForSave(),
                            destinationBranch = destinationBranch ?: error("Select a To location first."),
                            quantity = line.quantity,
                            note = state.memo,
                            transactionAt = state.transactionAt,
                            transactionId = transactionId
                        ).getOrThrow()
                    } else if (state.mode == TransactionMode.ADJUST_STOCK) {
                        stockMovementRepository.adjustStock(
                            inventoryItem = line.effectiveItemForSave(),
                            newQuantity = line.quantity,
                            note = state.memo,
                            transactionAt = state.transactionAt,
                            transactionId = transactionId
                        ).getOrThrow()
                    } else {
                        val counterpartyId = if (state.mode == TransactionMode.STOCK_IN) state.selectedSupplier?.id.orEmpty() else state.selectedCustomer?.id.orEmpty()
                        val counterpartyName = if (state.mode == TransactionMode.STOCK_IN) state.selectedSupplier?.name.orEmpty() else state.selectedCustomer?.name.orEmpty()
                        stockMovementRepository.applyManualMovement(
                            inventoryItem = line.effectiveItemForSave(),
                            movementType = if (state.mode == TransactionMode.STOCK_IN) StockMovementType.STOCK_IN else StockMovementType.STOCK_OUT,
                            quantity = line.quantity,
                            reason = counterpartyName,
                            note = state.memo,
                            expiryDate = line.expiryDate.takeIf { state.mode == TransactionMode.STOCK_IN },
                            transactionAt = state.transactionAt,
                            counterpartyId = counterpartyId,
                            counterpartyName = counterpartyName,
                            counterpartyType = if (state.mode == TransactionMode.STOCK_IN) "SUPPLIER" else "CUSTOMER",
                            transactionId = transactionId
                        ).getOrThrow()
                    }
                }
            }.onSuccess {
                AppLogger.info(
                    area = "Transactions",
                    event = "transaction_submit_success",
                    message = "Stock transaction submitted.",
                    "mode" to state.mode.name,
                    "location" to branch.name,
                    "items" to lines.size,
                    "quantity" to lines.sumOf { it.quantity }
                )
                savedStateHandle[KEY_FORM_STARTED] = false
                savedStateHandle[KEY_TRANSACTION_AT] = System.currentTimeMillis()
                clearEditState()
                _uiState.update {
                    StockInTransactionUiState(
                        mode = state.mode,
                        branches = it.branches,
                        customers = it.customers,
                        suppliers = it.suppliers,
                        inventoryItems = it.inventoryItems,
                        editTransactionId = "",
                        successMessage = when (state.mode) {
                            TransactionMode.STOCK_IN -> if (state.isEditing) "Stock in updated." else "Stock in recorded."
                            TransactionMode.STOCK_OUT -> if (state.isEditing) "Stock out updated." else "Stock out recorded."
                            TransactionMode.MOVE_STOCK -> if (state.isEditing) "Stock move updated." else "Stock moved successfully."
                            TransactionMode.ADJUST_STOCK -> if (state.isEditing) "Stock adjustment updated." else "Stock adjusted."
                        }
                    )
                }
            }.onFailure { throwable ->
                AppLogger.error(
                    area = "Transactions",
                    event = "transaction_submit_failed",
                    message = "Could not submit stock transaction.",
                    throwable = throwable,
                    "mode" to state.mode.name,
                    "location" to branch.name
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = throwable.message ?: if (state.isEditing) {
                            "Could not update this transaction."
                        } else {
                            "Could not record stock in."
                        }
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, infoMessage = null, warningDialog = null) }
    }

    fun pickerItems(): List<StockInPickerRow> {
        val state = _uiState.value ?: return emptyList()
        val branchId = state.selectedBranch?.id.orEmpty()
        val query = state.pickerQuery.trim()
        return state.inventoryItems
            .filter { it.id.isNotBlank() && it.branchId == branchId }
            .filter { state.mode != TransactionMode.STOCK_OUT || it.quantity > 0.0 }
            .filter { state.mode != TransactionMode.MOVE_STOCK || it.quantity > 0.0 }
            .filter {
                state.mode == TransactionMode.STOCK_OUT ||
                    state.mode == TransactionMode.MOVE_STOCK ||
                    state.mode == TransactionMode.ADJUST_STOCK ||
                    !state.pickerInStockOnly ||
                    it.quantity > 0.0
            }
            .filter { item ->
                query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.sku.contains(query, ignoreCase = true) ||
                    item.barcode.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true) ||
                    item.brand.contains(query, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
            .map { item ->
                val selectedLine = state.selectedLines[item.id]
                val displayItem = selectedLine?.item ?: item
                StockInPickerRow(
                    item = displayItem,
                    selectedQuantity = selectedLine?.quantity ?: 0.0,
                    selectedExpiryDate = selectedLine?.expiryDate,
                    isSelected = selectedLine != null
                )
            }
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches().collect { result ->
                result
                    .onSuccess { branches -> _uiState.update { it.copy(branches = branches, isLoading = false) } }
                    .onFailure { showError(it.message ?: "Could not load locations.") }
            }
        }
    }

    private fun observeSuppliers() {
        viewModelScope.launch {
            supplierRepository.observeSuppliers().collect { result ->
                result
                    .onSuccess { suppliers -> _uiState.update { it.copy(suppliers = suppliers) } }
                    .onFailure { showError(it.message ?: "Could not load partners.") }
            }
        }
    }

    private fun observeCustomers() {
        viewModelScope.launch {
            customerRepository.observeCustomers().collect { result ->
                result
                    .onSuccess { customers -> _uiState.update { it.copy(customers = customers) } }
                    .onFailure { showError(it.message ?: "Could not load customers.") }
            }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            inventoryRepository.observeInventoryItems().collect { result ->
                result
                    .onSuccess { items -> _uiState.update { it.copy(inventoryItems = items.filterNot { item -> item.isArchived }) } }
                    .onFailure { showError(it.message ?: "Could not load items.") }
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(isLoading = false, isSubmitting = false, errorMessage = message) }
    }

    private fun showWarning(title: String, message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isSubmitting = false,
                warningDialog = WarningDialogContent(title = title, message = message)
            )
        }
    }

    private fun logTransactionValidationFailure(mode: TransactionMode, reason: String) {
        AppLogger.warn(
            area = "Transactions",
            event = "transaction_validation_failed",
            message = "Transaction validation blocked submission.",
            "mode" to mode.name,
            "reason" to reason
        )
    }

    private fun clearEditState() {
        savedStateHandle[KEY_EDIT_TRANSACTION_ID] = ""
    }

    private fun buildEditState(
        current: StockInTransactionUiState,
        movements: List<StockMovement>,
        mode: TransactionMode
    ): StockInTransactionUiState {
        val transactionAt = movements.minOfOrNull { it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt }
            ?: System.currentTimeMillis()
        val primary = movements.first()
        val selectedBranch = current.branches.firstOrNull { it.id == primary.branchId }
            ?: Branch(id = primary.branchId, name = primary.branchName)
        val selectedDestinationBranch = movements
            .firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name }
            ?.let { incoming ->
                current.branches.firstOrNull { it.id == incoming.branchId }
                    ?: Branch(id = incoming.branchId, name = incoming.branchName)
            }
        val selectedSupplier = if (mode == TransactionMode.STOCK_IN && primary.counterpartyName.isNotBlank()) {
            current.suppliers.firstOrNull { supplier ->
                supplier.id == primary.counterpartyId ||
                    supplier.name.equals(primary.counterpartyName, ignoreCase = true)
            } ?: Supplier(id = primary.counterpartyId, name = primary.counterpartyName)
        } else {
            null
        }
        val selectedCustomer = if (mode == TransactionMode.STOCK_OUT && primary.counterpartyName.isNotBlank()) {
            current.customers.firstOrNull { customer ->
                customer.id == primary.counterpartyId ||
                    customer.name.equals(primary.counterpartyName, ignoreCase = true)
            } ?: Customer(id = primary.counterpartyId, name = primary.counterpartyName)
        } else {
            null
        }
        val selectedLines = buildEditLines(current.inventoryItems, movements, mode)
        return current.copy(
            mode = mode,
            isLoading = false,
            isSubmitting = false,
            selectedBranch = selectedBranch,
            selectedDestinationBranch = selectedDestinationBranch,
            selectedSupplier = selectedSupplier,
            selectedCustomer = selectedCustomer,
            selectedLines = selectedLines,
            transactionAt = transactionAt,
            memo = movements.map { it.note.trim() }.firstOrNull { it.isNotBlank() }.orEmpty(),
            editTransactionId = savedStateHandle.get<String>(KEY_EDIT_TRANSACTION_ID).orEmpty(),
            errorMessage = null,
            successMessage = null,
            infoMessage = "Editing this transaction will safely replace the current record."
        )
    }

    private fun buildEditLines(
        inventoryItems: List<InventoryItem>,
        movements: List<StockMovement>,
        mode: TransactionMode
    ): Map<String, StockInLine> = when (mode) {
        TransactionMode.MOVE_STOCK -> {
            movements
                .filter { it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name }
                .associate { movement ->
                    // The edit form shows the source stock as it looked before the original
                    // transfer so quantity validation matches the corrected replacement flow.
                    val item = inventoryItems.firstOrNull { it.id == movement.inventoryItemId }
                    val baseline = item?.copy(quantity = movement.quantityBefore) ?: movement.toEditableInventoryItem()
                    baseline.id to StockInLine(
                        item = baseline,
                        quantity = movement.quantity,
                        expiryDate = null,
                        sourceInventoryItemId = movement.inventoryItemId
                    )
                }
        }
        TransactionMode.ADJUST_STOCK -> {
            movements.associate { movement ->
                val item = inventoryItems.firstOrNull { it.id == movement.inventoryItemId }
                val baseline = item?.copy(quantity = movement.quantityBefore) ?: movement.toEditableInventoryItem()
                baseline.id to StockInLine(
                    item = baseline,
                    quantity = movement.quantityAfter,
                    expiryDate = null,
                    sourceInventoryItemId = movement.inventoryItemId
                )
            }
        }
        else -> {
            movements.associate { movement ->
                val item = inventoryItems.firstOrNull { it.id == movement.inventoryItemId }
                val baseline = item?.copy(quantity = movement.quantityBefore) ?: movement.toEditableInventoryItem()
                baseline.id to StockInLine(
                    item = baseline,
                    quantity = movement.quantity,
                    expiryDate = movement.expiryDate.takeIf { it > 0L },
                    sourceInventoryItemId = movement.inventoryItemId
                )
            }
        }
    }

    private fun Long.isBeforeTransactionDate(transactionAt: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val expiryDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
        val transactionDate = Instant.ofEpochMilli(transactionAt).atZone(zone).toLocalDate()
        return expiryDate.isBefore(transactionDate)
    }

    companion object {
        const val FUTURE_TRANSACTION_ERROR = "Transaction date and time cannot be in the future."
        private const val KEY_FORM_STARTED = "stock_in_form_started"
        private const val KEY_TRANSACTION_AT = "stock_in_transaction_at"
        private const val KEY_MODE = "stock_transaction_mode"
        private const val KEY_EDIT_TRANSACTION_ID = "stock_edit_transaction_id"
    }
}

data class StockInTransactionUiState(
    val mode: TransactionMode = TransactionMode.STOCK_IN,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val branches: List<Branch> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
    val selectedBranch: Branch? = null,
    val selectedDestinationBranch: Branch? = null,
    val selectedSupplier: Supplier? = null,
    val selectedCustomer: Customer? = null,
    val selectedLines: Map<String, StockInLine> = emptyMap(),
    val transactionAt: Long = System.currentTimeMillis(),
    val editTransactionId: String = "",
    val memo: String = "",
    val pickerQuery: String = "",
    val pickerInStockOnly: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val infoMessage: String? = null,
    val warningDialog: WarningDialogContent? = null
)

val StockInTransactionUiState.isEditing: Boolean
    get() = editTransactionId.isNotBlank()

fun StockInTransactionUiState.destinationQuantityFor(sourceItem: InventoryItem): Double {
    val destinationBranchId = selectedDestinationBranch?.id.orEmpty()
    if (destinationBranchId.isBlank()) return 0.0
    return inventoryItems.firstOrNull { item ->
        item.branchId == destinationBranchId && item.matchesSameProduct(sourceItem)
    }?.quantity ?: 0.0
}

data class StockInLine(
    val item: InventoryItem,
    val quantity: Double,
    val expiryDate: Long? = null,
    val sourceInventoryItemId: String = item.id
)

data class StockInPickerRow(
    val item: InventoryItem,
    val selectedQuantity: Double,
    val selectedExpiryDate: Long? = null,
    val isSelected: Boolean = false
)

private fun InventoryItem.matchesSameProduct(other: InventoryItem): Boolean = when {
    sku.isNotBlank() && other.sku.isNotBlank() -> sku.equals(other.sku, ignoreCase = true)
    barcode.isNotBlank() && other.barcode.isNotBlank() -> barcode.equals(other.barcode, ignoreCase = true)
    else -> name.equals(other.name, ignoreCase = true) &&
        brand.equals(other.brand, ignoreCase = true) &&
        category.equals(other.category, ignoreCase = true)
}

private fun StockMovement.toEditableInventoryItem(): InventoryItem = InventoryItem(
    id = inventoryItemId,
    sku = sku,
    barcode = barcode,
    name = itemName,
    branchId = branchId,
    branchName = branchName,
    quantity = quantityBefore,
    unit = unit,
    costPrice = costPrice,
    imageUrl = imageUrl
)

private fun StockInLine.effectiveItemForSave(): InventoryItem =
    if (item.id == sourceInventoryItemId) item else item.copy(id = sourceInventoryItemId)
