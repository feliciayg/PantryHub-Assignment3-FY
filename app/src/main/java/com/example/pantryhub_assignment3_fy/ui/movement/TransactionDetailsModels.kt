package com.example.pantryhub_assignment3_fy.ui.movement

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class TransactionDetailsUiState(
    val isLoading: Boolean = true,
    val details: TransactionDetailsUiModel? = null,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class TransactionDetailsUiModel(
    val transactionId: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    @ColorRes val colorRes: Int,
    val performedByName: String,
    val quantitySummary: String,
    val status: String,
    val transactionRows: List<TransactionDetailRow>,
    val itemLines: List<TransactionItemLineUiModel>,
    val additionalRows: List<TransactionDetailRow>,
    val memoText: String
)

data class TransactionItemLineUiModel(
    val stableId: String,
    val imageUrl: String,
    val itemName: String,
    val identifier: String,
    val secondaryText: String,
    val quantityHighlight: String,
    val balanceSummary: String,
    @ColorRes val accentColorRes: Int
)

data class TransactionDetailRow(
    val label: String,
    val value: String,
    @ColorRes val valueColorRes: Int? = null
)
