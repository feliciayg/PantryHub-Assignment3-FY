package com.example.pantryhub_assignment3_fy.ui.movement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.StockMovementRepository
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

class StockMovementViewModel(
    private val stockMovementRepository: StockMovementRepository = StockMovementRepository(),
    private val inventoryRepository: InventoryRepository = InventoryRepository(),
    private val branchRepository: BranchRepository = BranchRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(StockMovementUiState())
    val uiState: LiveData<StockMovementUiState> = _uiState

    init {
        observeMovements()
        observeInventoryItems()
        observeBranches()
    }

    private fun observeMovements() {
        viewModelScope.launch {
            stockMovementRepository.observeStockMovements().collect { result ->
                result
                    .onSuccess { movements ->
                        AppLogger.info(
                            area = "Transactions",
                            event = "transaction_list_loaded",
                            message = "Transaction list refreshed.",
                            "count" to movements.size
                        )
                        _uiState.update {
                            val activeFilters = it.appliedFilters
                            it.copy(
                                isLoading = false,
                                movements = movements,
                                displayedMovements = movements.toDisplayedMovements(activeFilters),
                                errorMessage = null
                            )
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Could not load stock movements.") }
                    }
            }
        }
    }

    private fun observeInventoryItems() {
        viewModelScope.launch {
            inventoryRepository.observeInventoryItems().collect { result ->
                result
                    .onSuccess { items -> _uiState.update { it.copy(inventoryItems = items.filterNot { item -> item.isArchived }, errorMessage = null) } }
                    .onFailure { throwable -> _uiState.update { it.copy(errorMessage = throwable.message ?: "Could not load inventory items.") } }
            }
        }
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches().collect { result ->
                result
                    .onSuccess { branches -> _uiState.update { it.copy(branches = branches, errorMessage = null) } }
                    .onFailure { throwable -> _uiState.update { it.copy(errorMessage = throwable.message ?: "Could not load branches.") } }
            }
        }
    }

    fun createManualMovement(
        inventoryItem: InventoryItem,
        movementType: StockMovementType,
        quantity: Double,
        reason: String,
        note: String,
        expiryDate: Long? = null
    ) {
        viewModelScope.launch {
            stockMovementRepository.applyManualMovement(inventoryItem, movementType, quantity, reason, note, expiryDate)
                .onSuccess { showSuccess("Stock movement recorded.") }
                .onFailure { showError(it.message ?: "Could not record stock movement.") }
        }
    }

    fun transferBetweenBranches(
        sourceInventoryItem: InventoryItem,
        destinationBranch: Branch,
        quantity: Double,
        note: String
    ) {
        viewModelScope.launch {
            stockMovementRepository.transferBetweenBranches(sourceInventoryItem, destinationBranch, quantity, note)
                .onSuccess { showSuccess("Branch transfer completed.") }
                .onFailure { showError(it.message ?: "Could not complete branch transfer.") }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun setTransactionFilter(filter: TransactionFilterType) {
        applyTransactionFilters(
            _uiState.value?.appliedFilters?.copy(transactionType = filter) ?: TransactionHistoryFilter(transactionType = filter)
        )
    }

    fun applyTransactionFilters(filters: TransactionHistoryFilter) {
        _uiState.update {
            val displayedMovements = it.movements.toDisplayedMovements(filters)
            AppLogger.info(
                area = "Transactions",
                event = "transaction_filters_applied",
                message = "Transaction history filters applied.",
                "dateStart" to filters.dateSelection?.localStartMillis,
                "dateEndExclusive" to filters.dateSelection?.localEndExclusiveMillis,
                "member" to filters.memberName,
                "resultCount" to displayedMovements.size
            )
            it.copy(
                appliedFilters = filters,
                transactionFilter = filters.transactionType,
                hasActiveFilters = filters.hasActiveFilters(),
                displayedMovements = displayedMovements
            )
        }
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun List<StockMovement>.toDisplayedMovements(
        filters: TransactionHistoryFilter
    ): List<StockMovementListItem> {
        val filtered = filter { movement ->
            val movementAt = movement.transactionAt.takeIf { it > 0L } ?: movement.createdAt
            val dateMatches = filters.dateSelection?.let { selection ->
                movementAt in selection.localStartMillis until selection.localEndExclusiveMillis
            } ?: true
            val itemMatches = if (filters.itemId.isBlank()) {
                true
            } else {
                movement.inventoryItemId == filters.itemId
            }
            val memberMatches = when {
                filters.memberId.isNotBlank() -> {
                    movement.performedBy == filters.memberId ||
                        (movement.performedBy.isBlank() &&
                            movement.performedByName.equals(filters.memberName, ignoreCase = true))
                }
                filters.memberName.isBlank() -> true
                else -> movement.performedByName.equals(filters.memberName, ignoreCase = true)
            }
            val partnerMatches = filters.partnerName.isBlank() ||
                movement.counterpartyName.equals(filters.partnerName, ignoreCase = true)
            val locationMatches = if (filters.locationId.isBlank()) {
                true
            } else {
                movement.branchId == filters.locationId
            }
            val transactionMatches = when (filters.transactionType) {
                TransactionFilterType.ALL -> true
                TransactionFilterType.STOCK_IN -> movement.movementType in setOf(
                    StockMovementType.STOCK_IN.name,
                    StockMovementType.RETURN.name,
                    StockMovementType.RESTOCK_RECEIVED.name
                )
                TransactionFilterType.STOCK_OUT -> movement.movementType in setOf(
                    StockMovementType.STOCK_OUT.name,
                    StockMovementType.DAMAGE.name,
                    StockMovementType.EXPIRED.name,
                    StockMovementType.WASTE.name,
                    StockMovementType.SALES_DEDUCTION.name
                )
                TransactionFilterType.MOVE_STOCK -> movement.movementType in setOf(
                    StockMovementType.BRANCH_TRANSFER_IN.name,
                    StockMovementType.BRANCH_TRANSFER_OUT.name
                )
                TransactionFilterType.ADJUST_STOCK -> movement.movementType == StockMovementType.ADJUST_STOCK.name
            }
            dateMatches && itemMatches && memberMatches && partnerMatches && locationMatches && transactionMatches
        }
        val transferTypes = setOf(
            StockMovementType.BRANCH_TRANSFER_IN.name,
            StockMovementType.BRANCH_TRANSFER_OUT.name
        )
        val transferIds = filtered
            .filter { it.movementType in transferTypes }
            .map { it.transactionId.ifBlank { it.id } }
            .toSet()

        val nonTransfers = filtered
            .filterNot { it.movementType in transferTypes && it.transactionId.ifBlank { it.id } in transferIds }
            .map { movement ->
                StockMovementListItem(
                    stableId = movement.id,
                    transactionId = movement.transactionId.ifBlank { movement.id },
                    representativeMovement = movement
                )
            }

        val groupedTransfers = filtered
            .filter { it.movementType in transferTypes }
            .groupBy { it.transactionId.ifBlank { it.id } }
            .values
            .map { movements ->
                val incoming = movements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name }
                val outgoing = movements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name }
                val representative = incoming ?: outgoing ?: movements.first()
                StockMovementListItem(
                    stableId = "transfer:${representative.transactionId.ifBlank { representative.id }}",
                    transactionId = representative.transactionId.ifBlank { representative.id },
                    representativeMovement = representative,
                    pairedTransferMovement = if (representative === incoming) outgoing else incoming
                )
            }

        return (nonTransfers + groupedTransfers).sortedByDescending {
            val movement = it.representativeMovement
            movement.transactionAt.takeIf { value -> value > 0L } ?: movement.createdAt
        }
    }

}
