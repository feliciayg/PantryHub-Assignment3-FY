package com.example.pantryhub_assignment3_fy.ui.restock

import androidx.fragment.app.Fragment
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog

internal fun Fragment.showReceiveQuantityDialog(
    row: PurchaseReceivePickerRow,
    onApply: (Double) -> Unit
) {
    showQuantityStepperDialog(
        config = QuantityStepperConfig(
            title = getString(R.string.enter_quantity),
            initialQuantity = row.selectedQuantity.takeIf { it > 0.0 } ?: 1.0,
            minimumQuantity = 1.0,
            currentStock = row.remainingQuantity,
            currentStockLabel = getString(R.string.outstanding_qty),
            unit = row.item.unit,
            validationMessage = getString(R.string.purchase_receive_exceeds_remaining),
            maximumQuantity = row.remainingQuantity,
            stockChangeDirection = -1,
            afterStockLabel = getString(R.string.outstanding_after_receive),
            extraCurrentStock = row.availableQuantity,
            extraCurrentStockLabel = getString(R.string.available_qty),
            extraStockChangeDirection = 1,
            extraAfterStockLabel = getString(R.string.available_after_receive)
        )
    ) { result ->
        // Receiving is stored as a pending draft first so the picker and the form stay in sync
        // until the user explicitly submits the purchase receipt.
        onApply(result.quantity)
    }
}
