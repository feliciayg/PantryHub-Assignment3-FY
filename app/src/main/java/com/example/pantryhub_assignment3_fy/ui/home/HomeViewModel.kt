package com.example.pantryhub_assignment3_fy.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.RestockOrderRepository
import com.example.pantryhub_assignment3_fy.data.repository.StockMovementRepository
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryStatus
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.RestockStatus
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.update
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import kotlinx.coroutines.launch

/**
 * Builds the Home dashboard from existing inventory and restock repository data.
 *
 * Home does not own a Firestore collection; it derives inventory health and
 * transaction summaries from the current store/team inventory.
 */
class HomeViewModel(
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val restockOrderRepository: RestockOrderRepository = RestockOrderRepository(),
    private val stockMovementRepository: StockMovementRepository = StockMovementRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(HomeUiState())
    val uiState: LiveData<HomeUiState> = _uiState

    private var hasResolvedInitialInventorySnapshot: Boolean = false
    private var latestInventoryItems: List<InventoryItem> = emptyList()
    private var latestRestockOrders: List<RestockOrder> = emptyList()
    private var latestStockMovements: List<StockMovement> = emptyList()
    private var searchQuery: String = ""

    init {
        observeInventoryItems()
        observeRestockOrders()
        observeStockMovements()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun searchItems(query: String) {
        searchQuery = query.trim()
        publishState(errorMessage = null)
    }

    private fun observeInventoryItems() {
        viewModelScope.launch {
            inventoryRepository.observeInventoryItems().collect { result ->
                result
                    .onSuccess { inventoryItems ->
                        hasResolvedInitialInventorySnapshot = true
                        latestInventoryItems = inventoryItems.filterNot { it.isArchived }
                        publishState(errorMessage = null)
                    }
                    .onFailure {
                        hasResolvedInitialInventorySnapshot = true
                        showError(it.message ?: "Could not load inventory dashboard.")
                    }
            }
        }
    }

    private fun observeRestockOrders() {
        viewModelScope.launch {
            restockOrderRepository.observeRestockOrders().collect { result ->
                result
                    .onSuccess { items ->
                        latestRestockOrders = items
                        publishState(errorMessage = null)
                    }
                    .onFailure { showError(it.message ?: "Could not load restock overview.") }
            }
        }
    }

    private fun observeStockMovements() {
        viewModelScope.launch {
            stockMovementRepository.observeStockMovements().collect { result ->
                result
                    .onSuccess { movements ->
                        latestStockMovements = movements
                        publishState(errorMessage = null)
                    }
                    .onFailure { showError(it.message ?: "Could not load transaction summary.") }
            }
        }
    }

    private fun publishState(errorMessage: String?) {
        _uiState.update {
            val next = buildDashboardState()
            next.copy(
                isLoading = !hasResolvedInitialInventorySnapshot,
                errorMessage = errorMessage,
                successMessage = it.successMessage
            )
        }
    }

    /**
     * Calculates every Home section from the latest inventory and restock snapshots.
     */
    private fun buildDashboardState(): HomeUiState {
        val trackedItems = latestInventoryItems.filterNot { it.status == InventoryStatus.USED.name }
        val actionableItems = trackedItems.filterNot { it.status == InventoryStatus.WASTED.name }
        val totalQuantity = actionableItems.sumOf { it.quantity.coerceAtLeast(0.0) }
        val activeLowStockCount = actionableItems.count(StockLevelRules::isLowStock)
        val outOfStockCount = actionableItems.count { it.quantity <= 0.0 }
        val expiredItemCount = actionableItems.count { it.status == InventoryStatus.EXPIRED.name }
        val todayStart = DateUtils.todayMillis()
        val tomorrowStart = todayStart + MILLIS_PER_DAY
        val todayMovements = latestStockMovements.filter {
            (it.transactionAt.takeIf { timestamp -> timestamp > 0L } ?: it.createdAt) in todayStart until tomorrowStart
        }
        // Company-wide totals intentionally exclude both sides of branch transfers.
        val todayStockInQuantity = todayMovements
            .filter { it.movementType in STOCK_IN_TYPES }
            .sumOf { it.quantity }
        val todayStockOutQuantity = todayMovements
            .filter { it.movementType in STOCK_OUT_TYPES }
            .sumOf { it.quantity }
        val activePurchaseCount = latestRestockOrders.count {
            it.status == RestockStatus.TO_ORDER.name ||
                it.status == RestockStatus.ORDERED.name ||
                it.status == RestockStatus.IN_TRANSIT.name
        }
        val onHandItems = actionableItems.filter { it.quantity > 0.0 }
        val normalizedQuery = searchQuery.lowercase()
        val searchResults = if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            actionableItems.filter { item ->
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.sku.contains(normalizedQuery, ignoreCase = true) ||
                    item.barcode.contains(normalizedQuery, ignoreCase = true) ||
                    item.brand.contains(normalizedQuery, ignoreCase = true)
            }.take(SEARCH_RESULT_LIMIT)
        }
        return HomeUiState(
            isLoading = false,
            searchResults = searchResults,
            totalQuantity = totalQuantity,
            todayStockInQuantity = todayStockInQuantity,
            todayStockOutQuantity = todayStockOutQuantity,
            outOfStockCount = outOfStockCount,
            expiredItemCount = expiredItemCount,
            totalStockCostValue = onHandItems.sumOf { it.quantity * it.costPrice },
            totalPotentialSalesValue = onHandItems.sumOf { it.quantity * it.sellingPrice },
            activePurchaseCount = activePurchaseCount,
            activeLowStockCount = activeLowStockCount
        )
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val SEARCH_RESULT_LIMIT = 8
        private val STOCK_IN_TYPES = setOf(
            StockMovementType.STOCK_IN.name,
            StockMovementType.RETURN.name,
            StockMovementType.RESTOCK_RECEIVED.name
        )
        private val STOCK_OUT_TYPES = setOf(
            StockMovementType.STOCK_OUT.name,
            StockMovementType.DAMAGE.name,
            StockMovementType.EXPIRED.name,
            StockMovementType.WASTE.name,
            StockMovementType.SALES_DEDUCTION.name
        )
    }
}
