package com.example.pantryhub_assignment3_fy.ui.movement

import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetTransactionTypeBinding
import com.example.pantryhub_assignment3_fy.model.StockMovementType

object TransactionTypeVisuals {
    @ColorRes
    fun colorFor(mode: TransactionMode): Int = when (mode) {
        TransactionMode.STOCK_IN -> R.color.transaction_stock_in_blue
        TransactionMode.STOCK_OUT -> R.color.transaction_stock_out_red
        TransactionMode.MOVE_STOCK -> R.color.transaction_move_stock_amber
        TransactionMode.ADJUST_STOCK -> R.color.transaction_adjust_stock_green
    }

    @ColorRes
    fun colorFor(filterType: TransactionFilterType): Int = when (filterType) {
        TransactionFilterType.STOCK_IN -> R.color.transaction_stock_in_blue
        TransactionFilterType.STOCK_OUT -> R.color.transaction_stock_out_red
        TransactionFilterType.MOVE_STOCK -> R.color.transaction_move_stock_amber
        TransactionFilterType.ADJUST_STOCK -> R.color.transaction_adjust_stock_green
        TransactionFilterType.ALL -> R.color.inventory_text_primary
    }

    @ColorRes
    fun colorForMovement(movementType: String): Int = when (movementType) {
        StockMovementType.STOCK_IN.name,
        StockMovementType.RETURN.name,
        StockMovementType.RESTOCK_RECEIVED.name -> R.color.transaction_stock_in_blue

        StockMovementType.STOCK_OUT.name,
        StockMovementType.DAMAGE.name,
        StockMovementType.EXPIRED.name,
        StockMovementType.WASTE.name,
        StockMovementType.SALES_DEDUCTION.name -> R.color.transaction_stock_out_red

        StockMovementType.BRANCH_TRANSFER_IN.name,
        StockMovementType.BRANCH_TRANSFER_OUT.name -> R.color.transaction_move_stock_amber

        StockMovementType.ADJUST_STOCK.name -> R.color.transaction_adjust_stock_green
        else -> R.color.inventory_primary
    }

    @DrawableRes
    fun iconForFilter(filterType: TransactionFilterType): Int = when (filterType) {
        TransactionFilterType.STOCK_IN -> R.drawable.ic_stock_in
        TransactionFilterType.STOCK_OUT -> R.drawable.ic_stock_out
        TransactionFilterType.MOVE_STOCK -> R.drawable.ic_move_stock
        TransactionFilterType.ADJUST_STOCK -> R.drawable.ic_adjust_stock
        TransactionFilterType.ALL -> R.drawable.ic_activity
    }

    @DrawableRes
    fun iconForMovement(movementType: String): Int = when (movementType) {
        StockMovementType.STOCK_IN.name,
        StockMovementType.RETURN.name,
        StockMovementType.RESTOCK_RECEIVED.name -> R.drawable.ic_stock_in

        StockMovementType.STOCK_OUT.name,
        StockMovementType.DAMAGE.name,
        StockMovementType.EXPIRED.name,
        StockMovementType.WASTE.name,
        StockMovementType.SALES_DEDUCTION.name -> R.drawable.ic_stock_out

        StockMovementType.BRANCH_TRANSFER_IN.name,
        StockMovementType.BRANCH_TRANSFER_OUT.name -> R.drawable.ic_move_stock

        StockMovementType.ADJUST_STOCK.name -> R.drawable.ic_adjust_stock
        else -> R.drawable.ic_activity
    }
}

fun BottomSheetTransactionTypeBinding.applyTransactionTypeColors(context: Context) {
    stockInIcon.imageTintList = context.tintFor(TransactionMode.STOCK_IN)
    stockOutIcon.imageTintList = context.tintFor(TransactionMode.STOCK_OUT)
    moveStockIcon.imageTintList = context.tintFor(TransactionMode.MOVE_STOCK)
    adjustStockIcon.imageTintList = context.tintFor(TransactionMode.ADJUST_STOCK)
}

private fun Context.tintFor(mode: TransactionMode): ColorStateList =
    ColorStateList.valueOf(ContextCompat.getColor(this, TransactionTypeVisuals.colorFor(mode)))
