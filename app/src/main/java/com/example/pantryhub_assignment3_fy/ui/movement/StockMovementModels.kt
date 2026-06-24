package com.example.pantryhub_assignment3_fy.ui.movement

import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovement
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class StockMovementUiState(
    val isLoading: Boolean = true,
    val movements: List<StockMovement> = emptyList(),
    val displayedMovements: List<StockMovementListItem> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val appliedFilters: TransactionHistoryFilter = TransactionHistoryFilter(),
    val transactionFilter: TransactionFilterType = TransactionFilterType.ALL,
    val hasActiveFilters: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class StockMovementListItem(
    val stableId: String,
    val transactionId: String,
    val representativeMovement: StockMovement,
    val pairedTransferMovement: StockMovement? = null
)

enum class TransactionFilterType {
    ALL,
    STOCK_IN,
    STOCK_OUT,
    MOVE_STOCK,
    ADJUST_STOCK
}

data class TransactionHistoryFilter(
    val dateSelection: TransactionDateSelection? = null,
    val itemId: String = "",
    val itemLabel: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val partnerName: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val transactionType: TransactionFilterType = TransactionFilterType.ALL
) {
    fun hasActiveFilters(): Boolean {
        return dateSelection != null ||
            itemId.isNotBlank() ||
            memberId.isNotBlank() ||
            memberName.isNotBlank() ||
            partnerName.isNotBlank() ||
            locationId.isNotBlank() ||
            transactionType != TransactionFilterType.ALL
    }
}

data class TransactionDateSelection(
    val startMillis: Long,
    val endMillis: Long
) {
    val normalizedStartMillis: Long
        get() = minOf(startMillis, endMillis)

    val normalizedEndMillis: Long
        get() = maxOf(startMillis, endMillis)

    // MaterialDatePicker returns UTC-midnight values. Convert the represented calendar dates to
    // local day boundaries before comparing them with locally displayed transaction timestamps.
    val localStartMillis: Long
        get() = normalizedStartMillis.toPickerLocalDateStart()

    val localEndExclusiveMillis: Long
        get() = Instant.ofEpochMilli(normalizedEndMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun Long.toPickerLocalDateStart(): Long =
        Instant.ofEpochMilli(this)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
