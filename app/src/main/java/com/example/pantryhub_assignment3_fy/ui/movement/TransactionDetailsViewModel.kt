package com.example.pantryhub_assignment3_fy.ui.movement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import com.example.pantryhub_assignment3_fy.data.repository.StockMovementRepository
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update

class TransactionDetailsViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val movementRepository = StockMovementRepository()
    private val inventoryRepository = InventoryRepository()
    private val transactionId = savedStateHandle.get<String>(ARG_TRANSACTION_ID).orEmpty()
    private val _uiState = MutableLiveData(TransactionDetailsUiState())
    val uiState: LiveData<TransactionDetailsUiState> = _uiState

    init {
        load()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun load() {
        if (transactionId.isBlank()) {
            _uiState.value = TransactionDetailsUiState(isLoading = false, notFound = true)
            return
        }
        _uiState.value = TransactionDetailsUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val movements = resolveTransactionMovements(
                    movementRepository.observeStockMovements().first().getOrThrow(),
                    transactionId
                )
                val inventory = inventoryRepository.observeInventoryItems().first().getOrThrow()
                movements to inventory
            }.onSuccess { (movements, inventory) ->
                if (movements.isNotEmpty()) {
                    AppLogger.info(
                        area = "Transactions",
                        event = "transaction_details_opened",
                        message = "Transaction details loaded.",
                        "type" to movements.first().movementType,
                        "lines" to movements.size
                    )
                }
                _uiState.value = if (movements.isEmpty()) {
                    TransactionDetailsUiState(isLoading = false, notFound = true)
                } else {
                    TransactionDetailsUiState(
                        isLoading = false,
                        details = movements.toDetailsUi(transactionId, inventory)
                    )
                }
            }.onFailure {
                _uiState.value = TransactionDetailsUiState(
                    isLoading = false,
                    errorMessage = it.message ?: "Could not load transaction details."
                )
            }
        }
    }

    private fun resolveTransactionMovements(
        allMovements: List<StockMovement>,
        requestedId: String
    ): List<StockMovement> {
        val groupedMatches = allMovements
            .filter { it.transactionId == requestedId }
            .sortedBy { it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt }
        if (groupedMatches.isNotEmpty()) return groupedMatches

        val primary = allMovements.firstOrNull { it.id == requestedId } ?: return emptyList()
        if (primary.movementType !in TRANSFER_TYPES || primary.transactionId.isNotBlank()) {
            return listOf(primary)
        }

        // Compatibility for older transfer records that were saved without a shared transactionId.
        val counterpartType = when (primary.movementType) {
            StockMovementType.BRANCH_TRANSFER_OUT.name -> StockMovementType.BRANCH_TRANSFER_IN.name
            StockMovementType.BRANCH_TRANSFER_IN.name -> StockMovementType.BRANCH_TRANSFER_OUT.name
            else -> return listOf(primary)
        }
        val primaryOccurredAt = primary.transactionAt.takeIf { it > 0L } ?: primary.createdAt
        val pair = allMovements
            .asSequence()
            .filter { it.id != primary.id }
            .filter { it.transactionId.isBlank() }
            .filter { it.movementType == counterpartType }
            .filter { it.quantity == primary.quantity }
            .filter { it.productKey() == primary.productKey() }
            .filter {
                val candidateOccurredAt = it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt
                abs(candidateOccurredAt - primaryOccurredAt) <= LEGACY_TRANSFER_PAIR_WINDOW_MS
            }
            .minByOrNull {
                val candidateOccurredAt = it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt
                abs(candidateOccurredAt - primaryOccurredAt)
            }

        return listOfNotNull(primary, pair)
            .sortedBy { it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt }
    }

    private fun List<StockMovement>.toDetailsUi(
        requestedId: String,
        inventory: List<InventoryItem>
    ): TransactionDetailsUiModel {
        val mode = transactionMode()
        val color = TransactionTypeVisuals.colorFor(mode)
        val primary = first()
        val occurredAt = map { it.transactionAt.takeIf { value -> value > 0L } ?: it.createdAt }.minOrNull() ?: 0L
        val createdAt = map { it.createdAt }.filter { it > 0L }.maxOrNull() ?: 0L
        val transactionReference = primary.transactionId.ifBlank { requestedId }
        val fromLocation = firstOrNull {
            it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name
        }?.branchName.orEmpty()
        val toLocation = firstOrNull {
            it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name
        }?.branchName.orEmpty()
        val location = firstOrNull { it.branchName.isNotBlank() }?.branchName.orEmpty()
        val counterparty = firstOrNull {
            it.counterpartyName.isNotBlank()
        }?.counterpartyName.orEmpty()
        val performedByName = primary.performedByName.ifBlank { "Unknown staff" }
        val memoText = map { it.note.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        val transactionRows = buildList {
            if (occurredAt > 0L) add(TransactionDetailRow("Date", occurredAt.toDisplayDateTime()))
            if (mode == TransactionMode.MOVE_STOCK) {
                if (fromLocation.isNotBlank()) add(TransactionDetailRow("From", fromLocation))
                if (toLocation.isNotBlank()) add(TransactionDetailRow("To", toLocation))
            } else {
                if (location.isNotBlank()) add(TransactionDetailRow("Location", location))
            }
            if (counterparty.isNotBlank() && mode == TransactionMode.STOCK_IN) {
                add(TransactionDetailRow("Partner", counterparty))
            } else if (counterparty.isNotBlank() && mode == TransactionMode.STOCK_OUT) {
                add(TransactionDetailRow("Customer", counterparty))
            }
        }
        return TransactionDetailsUiModel(
            transactionId = transactionReference,
            title = mode.displayName(),
            iconRes = mode.iconRes(),
            colorRes = color,
            performedByName = performedByName,
            quantitySummary = "",
            status = "Completed",
            transactionRows = transactionRows,
            itemLines = itemLines(mode, inventory, color),
            additionalRows = emptyList(),
            memoText = memoText
        )
    }

    private fun List<StockMovement>.itemLines(
        mode: TransactionMode,
        inventory: List<InventoryItem>,
        @androidx.annotation.ColorRes color: Int
    ): List<TransactionItemLineUiModel> {
        if (mode == TransactionMode.MOVE_STOCK) {
            return groupBy { it.productKey() }.values.map { movements ->
                val source = movements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name }
                    ?: movements.first()
                val destination = movements.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name }
                val item = inventory.firstOrNull { it.id == source.inventoryItemId }
                TransactionItemLineUiModel(
                    stableId = source.inventoryItemId.ifBlank { source.id },
                    imageUrl = item?.imageUrl.orEmpty().ifBlank { source.imageUrl },
                    itemName = source.itemName,
                    identifier = source.identifierText(),
                    secondaryText = destination?.let {
                        "${source.branchName.ifBlank { "From" }} -> ${it.branchName.ifBlank { "To" }}"
                    }.orEmpty(),
                    quantityHighlight = source.quantity.clean(),
                    balanceSummary = "${source.quantityBefore.clean()} ${source.unit} -> ${source.quantityAfter.clean()} ${source.unit}".trim(),
                    accentColorRes = color
                )
            }
        }
        return map { movement ->
            val item = inventory.firstOrNull { it.id == movement.inventoryItemId }
            val expiryText = if (mode == TransactionMode.STOCK_IN && movement.expiryDate > 0L) {
                "Expires: ${movement.expiryDate.toDisplayDate()}"
            } else {
                ""
            }
            TransactionItemLineUiModel(
                stableId = movement.inventoryItemId.ifBlank { movement.id },
                imageUrl = item?.imageUrl.orEmpty().ifBlank { movement.imageUrl },
                itemName = movement.itemName,
                identifier = movement.identifierText(),
                secondaryText = expiryText,
                quantityHighlight = when (mode) {
                    TransactionMode.STOCK_IN -> "+ ${movement.quantity.clean()}"
                    TransactionMode.STOCK_OUT -> "- ${movement.quantity.clean()}"
                    TransactionMode.MOVE_STOCK -> movement.quantity.clean()
                    TransactionMode.ADJUST_STOCK -> (movement.quantityAfter - movement.quantityBefore).let { difference ->
                        when {
                            difference > 0 -> "+ ${difference.clean()}"
                            difference < 0 -> "- ${kotlin.math.abs(difference).clean()}"
                            else -> difference.clean()
                        }
                    }
                },
                balanceSummary = "${movement.quantityBefore.clean()} ${movement.unit} -> ${movement.quantityAfter.clean()} ${movement.unit}".trim(),
                accentColorRes = color
            )
        }
    }

    private fun List<StockMovement>.transactionMode(): TransactionMode = when {
        any { it.movementType in TRANSFER_TYPES } -> TransactionMode.MOVE_STOCK
        any { it.movementType == StockMovementType.ADJUST_STOCK.name } -> TransactionMode.ADJUST_STOCK
        any { it.movementType in OUT_TYPES } -> TransactionMode.STOCK_OUT
        else -> TransactionMode.STOCK_IN
    }

    private fun TransactionMode.displayName(): String = when (this) {
        TransactionMode.STOCK_IN -> "Stock In"
        TransactionMode.STOCK_OUT -> "Stock Out"
        TransactionMode.MOVE_STOCK -> "Move Stock"
        TransactionMode.ADJUST_STOCK -> "Adjust Stock"
    }

    private fun TransactionMode.iconRes(): Int = when (this) {
        TransactionMode.STOCK_IN -> R.drawable.ic_stock_in
        TransactionMode.STOCK_OUT -> R.drawable.ic_stock_out
        TransactionMode.MOVE_STOCK -> R.drawable.ic_move_stock
        TransactionMode.ADJUST_STOCK -> R.drawable.ic_adjust_stock
    }

    private fun StockMovement.productKey(): String = when {
        sku.isNotBlank() -> "sku:${sku.trim().lowercase()}"
        barcode.isNotBlank() -> "barcode:${barcode.trim().lowercase()}"
        else -> "name:${itemName.trim().lowercase()}"
    }

    private fun StockMovement.identifierText(): String = when {
        sku.isNotBlank() -> "SKU: $sku"
        barcode.isNotBlank() -> "Barcode: $barcode"
        else -> ""
    }

    private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
    private fun Long.toDisplayDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
    private fun Long.toDisplayDateTime(): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

    companion object {
        const val ARG_TRANSACTION_ID = "transactionId"
        private const val LEGACY_TRANSFER_PAIR_WINDOW_MS = 2 * 60 * 1000L
        private val TRANSFER_TYPES = setOf(
            StockMovementType.BRANCH_TRANSFER_OUT.name,
            StockMovementType.BRANCH_TRANSFER_IN.name
        )
        private val OUT_TYPES = setOf(
            StockMovementType.STOCK_OUT.name,
            StockMovementType.DAMAGE.name,
            StockMovementType.EXPIRED.name,
            StockMovementType.WASTE.name,
            StockMovementType.SALES_DEDUCTION.name
        )
    }
}
