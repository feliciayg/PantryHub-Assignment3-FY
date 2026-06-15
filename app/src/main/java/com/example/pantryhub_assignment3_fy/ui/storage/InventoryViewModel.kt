package com.example.pantryhub_assignment3_fy.ui.storage

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import com.example.pantryhub_assignment3_fy.util.update
import com.example.pantryhub_assignment3_fy.notification.InventoryReminderScheduler
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * ViewModel for the Inventory page and stock detail/edit flows.
 *
 * It observes inventory items, applies local search/filter/sort, forwards CRUD
 * actions to InventoryRepository, and keeps expiry reminders in sync after changes.
 */
class InventoryViewModel(
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val branchRepository: BranchRepository = BranchRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(InventoryUiState())
    val uiState: LiveData<InventoryUiState> = _uiState
    private var backfillAttempted = false

    init {
        observeInventoryItems()
        observeBranches()
    }

    private fun observeInventoryItems() {
        viewModelScope.launch {
            // The repository emits live Firestore updates from the current store/team's inventoryItems collection.
            inventoryRepository.observeInventoryItems(includeArchived = true).collect { result ->
                result
                    .onSuccess { inventoryItems ->
                        AppLogger.info(
                            area = "Items",
                            event = "item_list_loaded",
                            message = "Inventory list refreshed.",
                            "count" to inventoryItems.size
                        )
                        _uiState.update {
                            val next = it.copy(isLoading = false, inventoryItems = inventoryItems, errorMessage = null)
                            next.withDerivedInventory()
                        }
                        backfillMissingCombinationsOnce()
                    }
                    .onFailure { throwable ->
                        AppLogger.error(
                            area = "Items",
                            event = "item_list_failed",
                            message = "Could not refresh inventory list.",
                            throwable = throwable
                        )
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = throwable.message ?: "Could not load stock items.")
                        }
                    }
            }
        }
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches().collect { result ->
                result
                    .onSuccess { branches ->
                        _uiState.update {
                            val next = it.copy(branches = branches, errorMessage = null)
                            next.withDerivedInventory()
                        }
                        backfillMissingCombinationsOnce()
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(errorMessage = throwable.message ?: "Could not load branches.")
                        }
                    }
            }
        }
    }

    private fun backfillMissingCombinationsOnce() {
        val state = currentState()
        if (backfillAttempted || state.inventoryItems.isEmpty() || state.branches.isEmpty()) return
        backfillAttempted = true
        viewModelScope.launch {
            inventoryRepository.backfillMissingProductLocationRecords()
                .onSuccess { created ->
                    if (created > 0) showSuccess("Added $created missing location inventory records.")
                }
                .onFailure { showError(it.message ?: "Could not repair missing location inventory records.") }
        }
    }

    /**
     * Applies a text search to the current Storage list.
     */
    fun search(query: String) {
        _uiState.update {
            val next = it.copy(searchQuery = query)
            next.withDerivedInventory()
        }
    }

    /**
     * Applies location, category, and status filters together.
     */
    fun filter(location: String, category: String, status: String, branch: String = currentState().selectedBranch) {
        filter(location, category, status, branch, currentState().selectedBrand, currentState().selectedSupplier)
    }

    fun filter(location: String, category: String, status: String, branch: String, brand: String, supplier: String) {
        _uiState.update {
            val next = it.copy(
                selectedLocation = location,
                selectedCategory = category,
                selectedStatus = status,
                selectedBranch = branch,
                selectedBrand = brand,
                selectedSupplier = supplier
            )
            next.withDerivedInventory()
        }
    }

    fun selectBranch(branch: String) {
        AppLogger.info(
            area = "Locations",
            event = "location_selected",
            message = "Items location filter selected.",
            "location" to branch
        )
        filter(
            currentState().selectedLocation,
            currentState().selectedCategory,
            currentState().selectedStatus,
            branch,
            currentState().selectedBrand,
            currentState().selectedSupplier
        )
    }

    fun group(groupOption: GroupOption) {
        _uiState.update {
            val next = it.copy(groupOption = groupOption)
            next.withDerivedInventory()
        }
    }

    fun toggleInStockOnly(enabled: Boolean) {
        val status = if (enabled) FilterOptions.IN_STOCK_STATUS else FilterOptions.ALL_STATUS
        _uiState.update {
            val next = it.copy(selectedStatus = status)
            next.withDerivedInventory()
        }
    }

    fun setArchiveFilter(archiveFilter: ArchiveFilter) {
        _uiState.update {
            val next = it.copy(archiveFilter = archiveFilter)
            next.withDerivedInventory()
        }
    }

    fun sortGroups(groupSortOption: GroupSortOption) {
        _uiState.update {
            val next = it.copy(groupSortOption = groupSortOption)
            next.withDerivedInventory()
        }
    }

    fun rowsForGroup(
        groupOption: GroupOption,
        groupKey: String,
        query: String,
        inStockOnly: Boolean = false,
        sortOption: SortOption = currentState().sortOption
    ): List<InventoryDisplayRow> {
        val state = currentState().copy(
            groupOption = groupOption,
            searchQuery = query,
            selectedStatus = if (inStockOnly) FilterOptions.IN_STOCK_STATUS else FilterOptions.ALL_STATUS,
            sortOption = sortOption
        )
        return buildGroupSummaries(state).firstOrNull { it.groupKey == groupKey }?.groupedItems.orEmpty()
    }

    /**
     * Applies a local sort option to visible inventoryItems.
     */
    fun sort(sortOption: SortOption) {
        _uiState.update {
            val next = it.copy(sortOption = sortOption)
            next.withDerivedInventory()
        }
    }

    /**
     * Creates a new inventoryItem item and schedules its expiry reminder if enabled.
     */
    fun addInventoryItem(context: Context, form: InventoryItemFormData, receivedFromRestockOrder: Boolean = false) {
        AppLogger.info(
            area = "Items",
            event = "item_create_start",
            message = "Adding inventory item.",
            "item" to form.name,
            "location" to form.branchName,
            "quantity" to form.quantity
        )
        viewModelScope.launch {
            inventoryRepository.addInventoryItem(form.toInventoryItem(), receivedFromRestockOrder)
                .onSuccess { inventoryItemId ->
                    val savedItem = inventoryRepository.getInventoryItem(inventoryItemId)
                        .getOrNull()
                        ?: form.toInventoryItem().copy(id = inventoryItemId)
                    InventoryReminderScheduler.schedule(context, savedItem)
                    showSuccess(
                        if (form.sku.isBlank() && savedItem.sku.isNotBlank()) {
                            "Stock item saved. Generated SKU: ${savedItem.sku}"
                        } else {
                            "Stock item saved."
                        }
                    )
                    AppLogger.info(
                        area = "Items",
                        event = "item_create_success",
                        message = "Inventory item saved.",
                        "item" to savedItem.name,
                        "location" to savedItem.branchName,
                        "quantity" to savedItem.quantity
                    )
                }
                .onFailure {
                    AppLogger.error(
                        area = "Items",
                        event = "item_create_failed",
                        message = "Could not save inventory item.",
                        throwable = it,
                        "item" to form.name,
                        "location" to form.branchName
                    )
                    showError(it.message ?: "Could not save stock item.")
                }
        }
    }

    /**
     * Adds quantity into an existing matching inventoryItem instead of saving a duplicate item.
     */
    fun mergeDuplicateInventoryItem(context: Context, existingItem: InventoryItem, form: InventoryItemFormData, receivedFromRestockOrder: Boolean = false) {
        viewModelScope.launch {
            inventoryRepository.mergeInventoryQuantity(
                existingItem.id,
                form.quantity,
                form.expiryDate.takeIf { it > 0L },
                receivedFromRestockOrder
            )
                .onSuccess {
                    InventoryReminderScheduler.schedule(context, existingItem.copy(quantity = existingItem.quantity + form.quantity))
                    showSuccess("Added quantity to existing ${existingItem.name}.")
                }
                .onFailure { showError(it.message ?: "Could not update existing stock item.") }
        }
    }

    /**
     * Updates an existing inventoryItem and reschedules its expiry reminder.
     */
    fun updateInventoryItem(context: Context, form: InventoryItemFormData) {
        AppLogger.info(
            area = "Items",
            event = "item_update_start",
            message = "Updating inventory item.",
            "item" to form.name,
            "location" to form.branchName,
            "quantity" to form.quantity
        )
        viewModelScope.launch {
            inventoryRepository.updateInventoryItem(form.toInventoryItem())
                .onSuccess {
                    InventoryReminderScheduler.schedule(context, form.toInventoryItem())
                    showSuccess("Stock item updated.")
                    AppLogger.info(
                        area = "Items",
                        event = "item_update_success",
                        message = "Inventory item updated.",
                        "item" to form.name,
                        "location" to form.branchName,
                        "quantity" to form.quantity
                    )
                }
                .onFailure {
                    AppLogger.error(
                        area = "Items",
                        event = "item_update_failed",
                        message = "Could not update inventory item.",
                        throwable = it,
                        "item" to form.name,
                        "location" to form.branchName
                    )
                    showError(it.message ?: "Could not update stock item.")
                }
        }
    }

    /**
     * Archives inventory item and cancels any local reminder alarm for it.
     */
    fun archiveInventoryItem(context: Context, inventoryItemId: String) {
        AppLogger.info(
            area = "Items",
            event = "item_archive_start",
            message = "Archiving inventory item.",
            "itemId" to inventoryItemId
        )
        viewModelScope.launch {
            inventoryRepository.archiveInventoryItem(inventoryItemId)
                .onSuccess {
                    InventoryReminderScheduler.cancel(context, inventoryItemId)
                    showSuccess("Stock item archived.")
                    AppLogger.info(
                        area = "Items",
                        event = "item_archive_success",
                        message = "Inventory item archived.",
                        "itemId" to inventoryItemId
                    )
                }
                .onFailure {
                    AppLogger.error(
                        area = "Items",
                        event = "item_archive_failed",
                        message = "Could not archive inventory item.",
                        throwable = it,
                        "itemId" to inventoryItemId
                    )
                    showError(it.message ?: "Could not archive stock item.")
                }
        }
    }

    fun restoreInventoryItem(inventoryItemId: String) {
        viewModelScope.launch {
            inventoryRepository.restoreInventoryItem(inventoryItemId)
                .onSuccess { showSuccess("Stock item restored.") }
                .onFailure { showError(it.message ?: "Could not restore stock item.") }
        }
    }

    /**
     * Records waste quantity/reason and updates reminders if stock remains.
     */
    fun logWaste(context: Context, inventoryItemId: String, quantityWasted: Double, reason: String) {
        val existingItem = findInventoryItem(inventoryItemId)
        viewModelScope.launch {
            // Quantity wasted decreases the item and records the selected waste reason in notes for Step 2.
            inventoryRepository.logWaste(inventoryItemId, quantityWasted, reason)
                .onSuccess {
                    updateReminderAfterQuantityChange(context, existingItem, quantityWasted)
                    showSuccess("Waste logged.")
                }
                .onFailure { showError(it.message ?: "Could not log waste.") }
        }
    }

    fun markExpiredStock(context: Context, deductions: Map<String, Double>) {
        val work = deductions.filterValues { it > 0.0 }
        if (work.isEmpty()) {
            showError("No expired stock was selected.")
            return
        }
        viewModelScope.launch {
            runCatching {
                work.forEach { (inventoryItemId, quantity) ->
                    val existingItem = findInventoryItem(inventoryItemId)
                    inventoryRepository.markExpiredStock(inventoryItemId, quantity).getOrThrow()
                    updateReminderAfterQuantityChange(context, existingItem, quantity)
                }
            }
                .onSuccess { showSuccess("Expired stock removed.") }
                .onFailure { showError(it.message ?: "Could not remove expired stock.") }
        }
    }

    fun prepareInventoryCsvExport() {
        _uiState.update {
            it.copy(
                csvExportContent = InventoryCsv.exportInventoryItems(it.inventoryItems),
                successMessage = "Inventory CSV is ready to save."
            )
        }
    }

    fun clearCsvExportContent() {
        _uiState.update { it.copy(csvExportContent = null) }
    }

    fun clearCsvSummary() {
        _uiState.update { it.copy(csvSummaryTitle = null, csvSummaryMessage = null) }
    }

    fun importInventoryCsv(context: Context, csv: String) {
        viewModelScope.launch {
            val parsed = InventoryCsv.parseRows(csv)
            if (parsed.errors.isNotEmpty()) {
                showError(parsed.errors.joinToString(separator = "\n"))
                return@launch
            }

            val existingItems = currentState().inventoryItems.associateBy { it.id }
            var importedCount = 0
            var updatedCount = 0
            val rowErrors = mutableListOf<String>()

            parsed.rows.forEach { row ->
                val form = row.toInventoryItemForm(existingItems[row.value("id")])
                if (form == null) {
                    rowErrors += row.validationError()
                    return@forEach
                }

                // CSV import uses id-based matching only: matching ids update existing documents,
                // missing or unknown ids create new documents with generated Firestore ids.
                val existing = existingItems[form.id]
                val result = if (existing != null) {
                    inventoryRepository.updateInventoryItem(form.toInventoryItem())
                } else {
                    inventoryRepository.addInventoryItem(form.toInventoryItem(), receivedFromRestockOrder = false)
                }

                result
                    .onSuccess { value ->
                        val savedItem = if (existing != null) form.toInventoryItem() else form.toInventoryItem().copy(id = value as? String ?: "")
                        InventoryReminderScheduler.schedule(context, savedItem)
                        if (existing != null) updatedCount++ else importedCount++
                    }
                    .onFailure { rowErrors += "Row ${row.rowNumber}: ${it.message ?: "Could not save row."}" }
            }

            showCsvSummary(
                title = "Inventory CSV import summary",
                message = buildCsvSummaryMessage(
                    counts = listOf(
                        "Created" to importedCount,
                        "Updated" to updatedCount,
                        "Skipped" to rowErrors.size,
                        "Errors" to rowErrors.size
                    ),
                    rowErrors = rowErrors
                )
            )
        }
    }

    fun importSalesCsv(csv: String) {
        viewModelScope.launch {
            val parsed = InventoryCsv.parseSalesRows(csv)
            if (parsed.errors.isNotEmpty()) {
                showError(parsed.errors.joinToString(separator = "\n"))
                return@launch
            }

            val inventoryItems = currentState().inventoryItems
            val itemsById = inventoryItems.associateBy { it.id }
            val itemsBySku = inventoryItems.filter { it.sku.isNotBlank() }.groupBy { it.sku.trim().lowercase() }
            val itemsByBarcode = inventoryItems.filter { it.barcode.isNotBlank() }.groupBy { it.barcode.trim().lowercase() }
            val itemsByName = inventoryItems.groupBy { it.name.trim().lowercase() }
            var deductedCount = 0
            val rowErrors = mutableListOf<String>()

            parsed.rows.forEach { row ->
                val quantitySold = row.value("quantitysold").toDoubleOrNull()
                val itemId = row.value("itemid")
                val sku = row.value("sku")
                val barcode = row.value("barcode")
                val name = row.value("name")
                val branchId = row.value("branchid")
                val branchName = row.value("branchname")

                // Sales CSV matching is strict and deterministic: itemId, then SKU, then barcode,
                // then exact name. Branch information resolves products stocked at multiple branches.
                val directIdMatch = itemId.takeIf { it.isNotBlank() }?.let { itemsById[it] }
                val candidates = when {
                    directIdMatch != null -> listOf(directIdMatch)
                    sku.isNotBlank() -> itemsBySku[sku.lowercase()].orEmpty()
                    barcode.isNotBlank() -> itemsByBarcode[barcode.lowercase()].orEmpty()
                    name.isNotBlank() -> itemsByName[name.lowercase()].orEmpty()
                    else -> emptyList()
                }
                val branchCandidates = candidates.filter {
                    when {
                        branchId.isNotBlank() -> it.branchId == branchId
                        branchName.isNotBlank() -> it.branchName.equals(branchName, ignoreCase = true)
                        else -> true
                    }
                }
                // Never deduct from an arbitrary branch when one product identifier matches several records.
                val matchedItem = branchCandidates.singleOrNull()
                val isAmbiguous = branchCandidates.size > 1

                // Invalid or unmatched rows are skipped so one bad POS row cannot stop the full import.
                when {
                    itemId.isBlank() && sku.isBlank() && barcode.isBlank() && name.isBlank() -> rowErrors += "Row ${row.rowNumber}: itemId, sku, barcode, or name is required."
                    quantitySold == null || quantitySold <= 0.0 -> rowErrors += "Row ${row.rowNumber}: quantitySold must be a positive number."
                    isAmbiguous -> rowErrors += "Row ${row.rowNumber}: product matches multiple branches; provide branchId or branchName."
                    matchedItem == null -> rowErrors += "Row ${row.rowNumber}: no matching inventory item."
                    quantitySold > matchedItem.quantity -> rowErrors += "Row ${row.rowNumber}: quantitySold cannot reduce ${matchedItem.name} below 0."
                    else -> {
                        inventoryRepository.deductSalesImport(
                            matchedItem.id,
                            quantitySold,
                            row.value("note").ifBlank { row.value("solddate").takeIf { it.isNotBlank() }?.let { "soldDate: $it" }.orEmpty() }
                        )
                            .onSuccess { deductedCount++ }
                            .onFailure { rowErrors += "Row ${row.rowNumber}: ${it.message ?: "Could not deduct stock."}" }
                    }
                }
            }

            showCsvSummary(
                title = "Sales CSV import summary",
                message = buildCsvSummaryMessage(
                    counts = listOf(
                        "Deducted" to deductedCount,
                        "Skipped" to rowErrors.size,
                        "Errors" to rowErrors.size
                    ),
                    rowErrors = rowErrors
                )
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun findInventoryItem(inventoryItemId: String): InventoryItem? {
        return currentState().inventoryItems.firstOrNull { it.id == inventoryItemId }
    }

    fun brandSuggestionsForCategory(category: String): List<String> {
        val selectedCategory = category.trim()
        val useAllBrands = selectedCategory.isBlank() ||
            selectedCategory == FilterOptions.ALL_CATEGORY ||
            selectedCategory.equals("Other", ignoreCase = true)

        // Brand suggestions are derived from existing inventory only; no brand/category records are stored.
        return currentState().inventoryItems
            .asSequence()
            .filter { useAllBrands || it.category.equals(selectedCategory, ignoreCase = true) }
            .map { it.brand.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
            .toList()
    }

    /**
     * Checks whether the Add Inventory Item form exactly matches an existing inventory item.
     */
    fun findExactInventoryDuplicateForAdd(form: InventoryItemFormData): InventoryItem? {
        val newItem = form.toInventoryItem()

        // A location has one inventory document per product. Different expiry dates are merged
        // into that document as separate expiry lots instead of creating duplicate product rows.
        return currentState().inventoryItems.firstOrNull { existing ->
                existing.sku.equals(newItem.sku, ignoreCase = true) &&
                existing.barcode.equals(newItem.barcode, ignoreCase = true) &&
                existing.name.normalizedName() == newItem.name.normalizedName() &&
                existing.brand.equals(newItem.brand, ignoreCase = true) &&
                existing.category.equals(newItem.category, ignoreCase = true) &&
                existing.branchId == newItem.branchId &&
                existing.unit.equals(newItem.unit, ignoreCase = true)
        }
    }

    /**
     * Recreates local reminder alarms after the app has loaded the latest inventory data.
     */
    fun scheduleLoadedInventoryReminders(context: Context) {
        InventoryReminderScheduler.scheduleAll(context, currentState().inventoryItems)
    }

    private fun updateReminderAfterQuantityChange(context: Context, inventoryItem: InventoryItem?, quantityChanged: Double) {
        if (inventoryItem == null) return
        val remainingQuantity = (inventoryItem.quantity - quantityChanged).coerceAtLeast(0.0)

        // A inventoryItem item with no remaining stock should not notify later. Partial usage/waste keeps
        // the same reminder date because the item still exists and can still expire.
        if (remainingQuantity <= 0.0) {
            InventoryReminderScheduler.cancel(context, inventoryItem.id)
        } else {
            InventoryReminderScheduler.schedule(context, inventoryItem.copy(quantity = remainingQuantity))
        }
    }

    private fun InventoryUiState.withDerivedInventory(): InventoryUiState {
        val visibleItems = applySearchFilterSort(this)
        return copy(
            visibleInventoryItems = visibleItems,
            visibleInventoryRows = applyInventoryDisplayRows(this),
            groupSummaries = if (groupOption == GroupOption.NONE) emptyList() else buildGroupSummaries(this)
        )
    }

    private fun applySearchFilterSort(state: InventoryUiState): List<InventoryItem> {
        val filtered = state.inventoryItems
            .filter {
                when (state.archiveFilter) {
                    ArchiveFilter.ACTIVE -> !it.isArchived
                    ArchiveFilter.ARCHIVED -> it.isArchived
                    ArchiveFilter.ALL -> true
                }
            }
            .filter { it.shouldShowInStorage(state.selectedStatus) }
            .filter { state.selectedLocation == FilterOptions.ALL_LOCATION || it.storageLocation == state.selectedLocation }
            .filter { state.selectedCategory == FilterOptions.ALL_CATEGORY || it.category == state.selectedCategory }
            .filter { state.selectedBranch == FilterOptions.ALL_BRANCH || it.branchName == state.selectedBranch }
            .filter { state.selectedBrand == FilterOptions.ALL_BRAND || it.brand == state.selectedBrand }
            .filter { state.selectedSupplier == FilterOptions.ALL_SUPPLIER || it.supplierName == state.selectedSupplier }
            .filter { inventoryItem -> inventoryItem.matchesItemSearch(state.searchQuery) }
            .filter { inventoryItem -> inventoryItem.matchesStatusFilter(state.selectedStatus) }
        return filtered.sortedBy { it.name.lowercase() }
    }

    private fun applyInventoryDisplayRows(state: InventoryUiState): List<InventoryDisplayRow> {
        val products = state.inventoryItems
            .filter {
                when (state.archiveFilter) {
                    ArchiveFilter.ACTIVE -> !it.isArchived
                    ArchiveFilter.ARCHIVED -> it.isArchived
                    ArchiveFilter.ALL -> true
                }
            }
            .filter { it.shouldShowInStorage(FilterOptions.ALL_STATUS) }
            .groupProductRecords()
            .filter { it.isNotEmpty() }

        val rows = if (state.selectedBranch == FilterOptions.ALL_BRANCH) {
            products.map { records ->
                val representative = records.representativeProduct()
                InventoryDisplayRow(
                    id = "product:${ProductIdentity.key(representative)}",
                    representativeItem = representative.copy(quantity = records.sumOf { it.quantity }),
                    realInventoryItemId = records.firstOrNull()?.id,
                    matchingRecords = records,
                    quantity = records.sumOf { it.quantity },
                    isAggregate = true
                )
            }
        } else {
            val selectedBranch = state.branches.firstOrNull { it.name == state.selectedBranch }
            state.inventoryItems
                .filter {
                    when (state.archiveFilter) {
                        ArchiveFilter.ACTIVE -> !it.isArchived
                        ArchiveFilter.ARCHIVED -> it.isArchived
                        ArchiveFilter.ALL -> true
                    }
                }
                .filter {
                    if (selectedBranch != null) it.branchId == selectedBranch.id else it.branchName == state.selectedBranch
                }
                .filter { it.shouldShowInStorage(FilterOptions.ALL_STATUS) }
                .map { realRecord ->
                    InventoryDisplayRow(
                        id = realRecord.id,
                        representativeItem = realRecord,
                        realInventoryItemId = realRecord.id,
                        matchingRecords = listOf(realRecord),
                        quantity = realRecord.quantity,
                        branchId = realRecord.branchId,
                        branchName = realRecord.branchName
                    )
                }
        }

        val filtered = rows
            .filter { state.selectedLocation == FilterOptions.ALL_LOCATION || it.representativeItem.storageLocation == state.selectedLocation }
            .filter { state.selectedCategory == FilterOptions.ALL_CATEGORY || it.category == state.selectedCategory }
            .filter { state.selectedBrand == FilterOptions.ALL_BRAND || it.brand == state.selectedBrand }
            .filter { state.selectedSupplier == FilterOptions.ALL_SUPPLIER || it.supplierName == state.selectedSupplier }
            .filter { it.matchesStatusFilter(state.selectedStatus) }
            .filter { it.matchesItemSearch(state.searchQuery) }

        val sorted = when (state.sortOption) {
            SortOption.EXPIRY_SOONEST -> filtered.sortedBy { it.expiryDate }
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.QUANTITY_LOW -> filtered.sortedBy { it.quantity }
            SortOption.QUANTITY_HIGH -> filtered.sortedByDescending { it.quantity }
            SortOption.SAFETY_STOCK_LOW -> filtered.sortedBy { StockLevelRules.effectiveReorderPoint(it.representativeItem) }
            SortOption.RESTOCK_URGENCY -> filtered.sortedWith(
                compareByDescending<InventoryDisplayRow> { StockLevelRules.isOutOfStock(it.representativeItem) }
                    .thenByDescending { StockLevelRules.restockUrgency(it.representativeItem) }
                    .thenBy { it.quantity }
                    .thenBy { it.name.lowercase() }
            )
            SortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt }
        }

        return sorted
    }

    private fun buildGroupSummaries(state: InventoryUiState): List<GroupSummaryUiModel> {
        val rowsBeforeSearch = applyInventoryDisplayRows(state.copy(searchQuery = "", groupOption = GroupOption.NONE))
        val contributions = rowsBeforeSearch.flatMap { it.groupContributions(state.groupOption) }
        val query = state.searchQuery.trim()
        val grouped = contributions
            .groupBy { it.key }
            .mapNotNull { (key, values) ->
                val displayName = values.first().displayName
                val searchedValues = when {
                    query.isBlank() || displayName.contains(query, ignoreCase = true) -> values
                    else -> values.filter { it.row.matchesItemSearch(query) }
                }
                if (searchedValues.isEmpty()) return@mapNotNull null
                GroupSummaryUiModel(
                    groupKey = key,
                    displayName = displayName,
                    itemCount = searchedValues.distinctBy { it.row.id }.size,
                    totalQuantity = searchedValues.sumOf { it.row.quantity },
                    percentage = 0,
                    previewImageUrls = searchedValues
                        .map { it.row.imageUrl }
                        .distinct()
                        .take(3),
                    groupedItems = searchedValues.map { it.row }
                )
            }
        val totalVisibleQuantity = grouped.sumOf { it.totalQuantity }
        val withPercentages = grouped.map {
            it.copy(
                percentage = if (totalVisibleQuantity <= 0.0) {
                    0
                } else {
                    ((it.totalQuantity / totalVisibleQuantity) * 100.0).roundToInt()
                }
            )
        }
        return when (state.groupSortOption) {
            GroupSortOption.VALUE_ASC -> withPercentages.sortedBy { it.displayName.lowercase() }
            GroupSortOption.VALUE_DESC -> withPercentages.sortedByDescending { it.displayName.lowercase() }
            GroupSortOption.QUANTITY_HIGH -> withPercentages.sortedByDescending { it.totalQuantity }
            GroupSortOption.QUANTITY_LOW -> withPercentages.sortedBy { it.totalQuantity }
            GroupSortOption.ITEM_COUNT_HIGH -> withPercentages.sortedByDescending { it.itemCount }
            GroupSortOption.ITEM_COUNT_LOW -> withPercentages.sortedBy { it.itemCount }
        }
    }

    private fun InventoryDisplayRow.groupContributions(option: GroupOption): List<GroupContribution> {
        if (option == GroupOption.EXPIRY_DATE) return expiryGroupContributions()
        val displayName = when (option) {
            GroupOption.NONE -> ""
            GroupOption.NAME -> name.trim().ifBlank { "Unnamed Item" }
            GroupOption.COST -> representativeItem.costPrice.takeIf { it > 0.0 }?.toStorageMoneyText() ?: "Not Set"
            GroupOption.PRICE -> representativeItem.sellingPrice.takeIf { it > 0.0 }?.toStorageMoneyText() ?: "Not Set"
            GroupOption.CATEGORY -> category.trim().ifBlank { "Uncategorized" }
            GroupOption.BRAND -> brand.trim().ifBlank { "No Brand" }
            GroupOption.SAFETY_STOCK -> StockLevelRules.effectiveReorderPoint(representativeItem)
                .takeIf { it > 0.0 }
                ?.toStorageQuantityText()
                ?: "Not Set"
            GroupOption.EXPIRY_DATE -> error("Expiry groups are calculated from lots.")
        }
        val key = if (option == GroupOption.NAME) displayName.lowercase() else displayName
        return listOf(GroupContribution(key, displayName, this))
    }

    private fun InventoryDisplayRow.expiryGroupContributions(): List<GroupContribution> {
        val lots = expiryLots.filter { it.quantity > 0.0 }
        if (lots.isEmpty()) {
            val date = expiryDate.takeIf { it > 0L }
            val display = date?.let(DateUtils::formatDisplayDate) ?: "No Expiry Date"
            return listOf(GroupContribution(date?.toString() ?: NO_EXPIRY_GROUP_KEY, display, this))
        }
        val lotQuantity = lots.sumOf { it.quantity }
        val contributions = lots.map { lot ->
            val display = lot.expiryDate?.let(DateUtils::formatDisplayDate) ?: "No Expiry Date"
            GroupContribution(
                key = lot.expiryDate?.toString() ?: NO_EXPIRY_GROUP_KEY,
                displayName = display,
                row = copy(quantity = lot.quantity)
            )
        }.toMutableList()
        val untrackedQuantity = (quantity - lotQuantity).coerceAtLeast(0.0)
        if (untrackedQuantity > 0.0) {
            contributions += GroupContribution(
                key = NO_EXPIRY_GROUP_KEY,
                displayName = "No Expiry Date",
                row = copy(quantity = untrackedQuantity)
            )
        }
        return contributions
    }

    private fun InventoryItem.matchesItemSearch(query: String): Boolean =
        query.isBlank() ||
            name.contains(query, ignoreCase = true) ||
            sku.contains(query, ignoreCase = true) ||
            barcode.contains(query, ignoreCase = true) ||
            brand.contains(query, ignoreCase = true)

    private fun InventoryDisplayRow.matchesItemSearch(query: String): Boolean =
        representativeItem.matchesItemSearch(query)

    private fun InventoryDisplayRow.matchesStatusFilter(selectedStatus: String): Boolean {
        if (selectedStatus == FilterOptions.ALL_STATUS) return true
        return when (selectedStatus) {
            FilterOptions.IN_STOCK_STATUS, "Fresh" -> quantity > StockLevelRules.effectiveReorderPoint(representativeItem)
            FilterOptions.LOW_STOCK_STATUS -> quantity > 0.0 && quantity <= StockLevelRules.effectiveReorderPoint(representativeItem)
            "Out of Stock" -> quantity <= 0.0
            "Expired" -> matchingRecords.any { it.status == InventoryStatus.EXPIRED.name }
            "Expiring Soon" -> matchingRecords.any { it.status == InventoryStatus.EXPIRING_SOON.name || it.status == InventoryStatus.USE_TODAY.name }
            else -> matchingRecords.any { it.matchesStatusFilter(selectedStatus) }
        }
    }

    private fun List<InventoryItem>.representativeProduct(): InventoryItem =
        maxByOrNull { it.updatedAt.takeIf { timestamp -> timestamp > 0 } ?: it.createdAt } ?: first()

    private fun List<InventoryItem>.groupProductRecords(): List<List<InventoryItem>> {
        val groups = mutableListOf<MutableList<InventoryItem>>()
        forEach { item ->
            val group = groups.firstOrNull { records ->
                records.firstOrNull()?.let { ProductIdentity.sameProduct(it, item) } == true
            }
            if (group == null) groups += mutableListOf(item) else group += item
        }
        return groups
    }

    private fun InventoryItem.shouldShowInStorage(selectedStatus: String): Boolean {
        // Legacy USED records stay compatible; new zero-quantity items use OUT_OF_STOCK.
        val isArchivedZeroStock = quantity <= 0.0 &&
            (status == InventoryStatus.USED.name || status == InventoryStatus.WASTED.name)
        if (!isArchivedZeroStock) return true

        // Legacy used-up items stay hidden normally, but Low Stock should reveal them as restock candidates.
        return selectedStatus.toStatusName() == status ||
            (status == InventoryStatus.USED.name && selectedStatus == FilterOptions.LOW_STOCK_STATUS)
    }

    private fun InventoryItem.matchesStatusFilter(selectedStatus: String): Boolean {
        if (selectedStatus == FilterOptions.ALL_STATUS) return true

        // Low Stock is a quantity condition, not only a saved status. This keeps Storage aligned
        // with Home even when expiry urgency has priority over LOW_STOCK in InventoryStatusCalculator.
        if (selectedStatus == FilterOptions.LOW_STOCK_STATUS) {
            return isRestockCandidate()
        }

        return status == selectedStatus.toStatusName()
    }

    private fun String.toStatusName(): String {
        return if (this == "Priority Today") InventoryStatus.USE_TODAY.name else uppercase().replace(" ", "_")
    }

    private fun InventoryItem.isRestockCandidate(): Boolean {
        val isActiveLowStock = status != InventoryStatus.WASTED.name && StockLevelRules.isLowStock(this)
        return isActiveLowStock
    }

    private fun String.normalizedName(): String {
        val compactName = trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return compactName.split(" ").joinToString(" ") { word ->
            when {
                word.endsWith("ies") && word.length > 3 -> word.dropLast(3) + "y"
                word.endsWith("oes") && word.length > 3 -> word.dropLast(2)
                word.endsWith("ses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes") -> word.dropLast(2)
                word.endsWith("s") && word.length > 1 -> word.dropLast(1)
                else -> word
            }
        }
    }

    private fun InventoryItemFormData.toInventoryItem(): InventoryItem {
        return InventoryItem(
            id = id,
            sku = sku.trim(),
            barcode = barcode.trim(),
            name = name.trim(),
            brand = brand.trim(),
            category = category,
            branchId = branchId,
            branchName = branchName,
            storageLocation = storageLocation,
            quantity = quantity,
            unit = unit,
            costPrice = costPrice,
            sellingPrice = sellingPrice,
            minimumStockLevel = 0,
            reorderPoint = reorderPoint,
            maximumStockLevel = maximumStockLevel,
            reorderThreshold = reorderPoint.toDouble(),
            aisle = aisle.trim(),
            shelf = shelf.trim(),
            addedDate = addedDate,
            expiryDate = expiryDate,
            batchNumber = batchNumber.trim(),
            shelfLifeDays = shelfLifeDays.takeIf { it > 0 } ?: DateUtils.daysBetween(addedDate, expiryDate),
            reminderDaysBefore = reminderDaysBefore,
            notes = notes.trim(),
            supplierId = supplierId,
            supplierName = supplierName.trim(),
            supplierPhone = supplierPhone.trim(),
            supplierEmail = supplierEmail.trim(),
            imageUrl = imageUrl,
            tags = tags.map { it.trim() }.filter { it.isNotBlank() },
            status = InventoryStatus.FRESH.name
        )
    }

    private fun CsvRow.toInventoryItemForm(existing: InventoryItem?): InventoryItemFormData? {
        val name = value("name")
        val quantity = value("quantity").toDoubleOrNull()
        val unit = value("unit")
        val reorderPoint = value("reorderpoint").takeIf { it.isNotBlank() }?.toIntOrNull()
        val reorderThreshold = value("reorderthreshold").ifBlank { value("lowstockthreshold") }.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val costPrice = value("costprice").ifBlank { "0" }.toDoubleOrNull()
        val sellingPrice = value("sellingprice").ifBlank { "0" }.toDoubleOrNull()
        val maximumStockLevel = value("maximumstocklevel").ifBlank { "0" }.toIntOrNull()
        val expiryDate = value("expirydate").takeIf { it.isNotBlank() }?.let {
            runCatching { DateUtils.parseInputDate(it) }.getOrNull()
        }

        // Row validation is intentionally strict for required numeric/date fields so bad CSV rows
        // are skipped instead of creating misleading stock records.
        if (name.isBlank() || quantity == null || quantity < 0.0 || unit.isBlank()) return null
        if (value("reorderpoint").isNotBlank() && reorderPoint == null) return null
        if ((value("reorderthreshold").isNotBlank() || value("lowstockthreshold").isNotBlank()) && reorderThreshold == null) return null
        if (costPrice == null || costPrice < 0.0) return null
        if (sellingPrice == null || sellingPrice < 0.0) return null
        if (maximumStockLevel == null || maximumStockLevel < 0) return null
        val finalReorderPoint = reorderPoint ?: reorderThreshold?.toInt() ?: existing?.reorderPoint ?: existing?.reorderThreshold?.toInt() ?: 0
        if (maximumStockLevel > 0 && finalReorderPoint > maximumStockLevel) return null
        if (value("expirydate").isNotBlank() && expiryDate == null) return null

        val addedDate = existing?.addedDate?.takeIf { it > 0L } ?: DateUtils.todayMillis()
        val finalExpiryDate = expiryDate ?: existing?.expiryDate?.takeIf { it > 0L } ?: DateUtils.todayMillis()
        val syncedReorderThreshold = finalReorderPoint.toDouble()
        val csvBranchName = value("branchname")
        val csvBranchId = value("branchid").ifBlank {
            currentState().branches.firstOrNull { it.name.equals(csvBranchName, ignoreCase = true) }?.id.orEmpty()
        }
        if (existing == null && csvBranchName.isNotBlank() && value("branchid").isBlank() && csvBranchId.isBlank()) return null
        return InventoryItemFormData(
            id = existing?.id.orEmpty(),
            sku = value("sku").ifBlank { existing?.sku.orEmpty() },
            barcode = value("barcode").ifBlank { existing?.barcode.orEmpty() },
            name = name,
            brand = value("brand").ifBlank { existing?.brand.orEmpty() },
            category = value("category").ifBlank { existing?.category ?: "Other" },
            branchId = csvBranchId.ifBlank { existing?.branchId.orEmpty() },
            branchName = csvBranchName.ifBlank { existing?.branchName.orEmpty() },
            storageLocation = value("storagelocation").ifBlank { existing?.storageLocation ?: "Inventory" },
            quantity = quantity,
            unit = unit,
            costPrice = costPrice,
            sellingPrice = sellingPrice,
            minimumStockLevel = 0,
            reorderPoint = finalReorderPoint,
            maximumStockLevel = maximumStockLevel,
            // Keep the legacy field synchronized so older Firestore documents remain compatible.
            reorderThreshold = syncedReorderThreshold,
            aisle = value("aisle").ifBlank { existing?.aisle.orEmpty() },
            shelf = value("shelf").ifBlank { existing?.shelf.orEmpty() },
            addedDate = addedDate,
            expiryDate = finalExpiryDate,
            batchNumber = value("batchnumber").ifBlank { existing?.batchNumber.orEmpty() },
            shelfLifeDays = DateUtils.daysBetween(addedDate, finalExpiryDate),
            reminderDaysBefore = existing?.reminderDaysBefore ?: 0,
            notes = value("notes").ifBlank { existing?.notes.orEmpty() },
            supplierId = existing?.supplierId.orEmpty(),
            supplierName = value("suppliername").ifBlank { existing?.supplierName.orEmpty() },
            supplierPhone = value("supplierphone").ifBlank { existing?.supplierPhone.orEmpty() },
            supplierEmail = value("supplieremail").ifBlank { existing?.supplierEmail.orEmpty() },
            imageUrl = existing?.imageUrl.orEmpty(),
            tags = value("tags").split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { existing?.tags.orEmpty() }
        )
    }

    private fun CsvRow.validationError(): String {
        return when {
            value("name").isBlank() -> "Row $rowNumber: name is required."
            value("quantity").toDoubleOrNull() == null -> "Row $rowNumber: quantity must be a number."
            value("quantity").toDoubleOrNull()?.let { it < 0.0 } == true -> "Row $rowNumber: quantity must be 0 or more."
            value("unit").isBlank() -> "Row $rowNumber: unit is required."
            value("reorderpoint").isNotBlank() && value("reorderpoint").toIntOrNull() == null -> "Row $rowNumber: reorderPoint must be a whole number."
            (value("reorderthreshold").isNotBlank() || value("lowstockthreshold").isNotBlank()) &&
                value("reorderthreshold").ifBlank { value("lowstockthreshold") }.toDoubleOrNull() == null -> "Row $rowNumber: reorderThreshold must be a number."
            value("costprice").ifBlank { "0" }.toDoubleOrNull()?.let { it < 0.0 } != false -> "Row $rowNumber: costPrice must be 0 or more."
            value("sellingprice").ifBlank { "0" }.toDoubleOrNull()?.let { it < 0.0 } != false -> "Row $rowNumber: sellingPrice must be 0 or more."
            value("maximumstocklevel").ifBlank { "0" }.toIntOrNull()?.let { it < 0 } != false -> "Row $rowNumber: maximumStockLevel must be 0 or more."
            value("maximumstocklevel").ifBlank { "0" }.toIntOrNull()?.let { maximum ->
                val point = value("reorderpoint").toIntOrNull()
                    ?: value("reorderthreshold").ifBlank { value("lowstockthreshold") }.toDoubleOrNull()?.toInt()
                    ?: 0
                maximum > 0 && point > maximum
            } == true -> "Row $rowNumber: reorderPoint cannot be greater than maximumStockLevel."
            value("expirydate").isNotBlank() && runCatching { DateUtils.parseInputDate(value("expirydate")) }.isFailure -> "Row $rowNumber: expiryDate must be yyyy-MM-dd."
            value("branchname").isNotBlank() && value("branchid").isBlank() &&
                currentState().branches.none { it.name.equals(value("branchname"), ignoreCase = true) } -> "Row $rowNumber: branchName does not match an existing branch."
            else -> "Row $rowNumber: could not import row."
        }
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun showCsvSummary(title: String, message: String) {
        _uiState.update { it.copy(csvSummaryTitle = title, csvSummaryMessage = message) }
    }

    private fun buildCsvSummaryMessage(counts: List<Pair<String, Int>>, rowErrors: List<String>): String {
        val summary = counts.joinToString(separator = "\n") { "${it.first}: ${it.second}" }
        val sampleErrors = rowErrors.take(5)
        return if (sampleErrors.isEmpty()) {
            summary
        } else {
            "$summary\n\nFirst issues:\n${sampleErrors.joinToString(separator = "\n")}"
        }
    }

    private fun currentState(): InventoryUiState = _uiState.value ?: InventoryUiState()
}

private data class GroupContribution(
    val key: String,
    val displayName: String,
    val row: InventoryDisplayRow
)

private const val NO_EXPIRY_GROUP_KEY = "no-expiry"
