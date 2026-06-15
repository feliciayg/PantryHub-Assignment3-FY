package com.example.pantryhub_assignment3_fy.ui.home

import com.example.pantryhub_assignment3_fy.model.InventoryItem

data class HomeUiState(
    val isLoading: Boolean = true,
    val searchResults: List<InventoryItem> = emptyList(),
    val totalQuantity: Double = 0.0,
    val todayStockInQuantity: Double = 0.0,
    val todayStockOutQuantity: Double = 0.0,
    val outOfStockCount: Int = 0,
    val expiredItemCount: Int = 0,
    val totalStockCostValue: Double = 0.0,
    val totalPotentialSalesValue: Double = 0.0,
    val activePurchaseCount: Int = 0,
    val activeLowStockCount: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
