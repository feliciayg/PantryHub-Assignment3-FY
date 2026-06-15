package com.example.pantryhub_assignment3_fy.ui.common

import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.DialogEnterQuantityBinding
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class QuantityStepperConfig(
    val title: String,
    val initialQuantity: Double,
    val minimumQuantity: Double,
    val currentStock: Double? = null,
    val currentStockLabel: String = "",
    val unit: String = "",
    val validationMessage: String,
    val maximumQuantity: Double? = null,
    val stockChangeDirection: Int = 1,
    val afterStockLabel: String = "",
    val extraCurrentStock: Double? = null,
    val extraCurrentStockLabel: String = "",
    val extraStockChangeDirection: Int = 1,
    val extraAfterStockLabel: String = "",
    val showExpiryDate: Boolean = false,
    val initialExpiryDate: Long? = null,
    val minimumExpiryDate: Long? = null,
    val expiryDateValidationMessage: String = "",
    val quantityIsFinalStock: Boolean = false,
    val showDifference: Boolean = false,
    val differenceLabel: String = ""
)

data class QuantityStepperResult(
    val quantity: Double,
    val expiryDate: Long?
)

fun Fragment.showQuantityStepperDialog(
    config: QuantityStepperConfig,
    onApply: (QuantityStepperResult) -> Unit
) {
    val dialogBinding = DialogEnterQuantityBinding.inflate(layoutInflater)
    var selectedExpiryDate = config.initialExpiryDate?.takeIf { it > 0L }
    dialogBinding.quantityInputEditText.inputType =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    dialogBinding.quantityInputEditText.setText(config.initialQuantity.toCompactQuantityText())
    dialogBinding.quantityInputEditText.setSelection(dialogBinding.quantityInputEditText.text?.length ?: 0)
    dialogBinding.stockPreviewGroup.isVisible = config.currentStock != null
    dialogBinding.currentStockLabelTextView.text = config.currentStockLabel.ifBlank { getString(R.string.current_stock) }
    dialogBinding.afterStockLabelTextView.text = config.afterStockLabel.ifBlank { getString(R.string.after_stock_in) }
    dialogBinding.extraStockPreviewGroup.isVisible = config.extraCurrentStock != null
    dialogBinding.extraCurrentStockLabelTextView.text = config.extraCurrentStockLabel
    dialogBinding.extraAfterStockLabelTextView.text = config.extraAfterStockLabel
    dialogBinding.differenceStockGroup.isVisible = config.showDifference
    dialogBinding.differenceStockLabelTextView.text = config.differenceLabel.ifBlank { getString(R.string.difference) }
    dialogBinding.expiryDateGroup.isVisible = config.showExpiryDate

    fun currentQuantity(): Double? =
        dialogBinding.quantityInputEditText.text?.toString().orEmpty().trim().toDoubleOrNull()

    fun expiryDateLabel(): String =
        selectedExpiryDate?.let { DateUtils.formatDisplayDate(it) } ?: getString(R.string.select_expiry_date)

    fun updateExpiryDateLabel() {
        dialogBinding.expiryDateValueTextView.text = expiryDateLabel()
        dialogBinding.expiryDateValueTextView.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selectedExpiryDate == null) R.color.inventory_primary else R.color.inventory_text_primary
            )
        )
        dialogBinding.clearExpiryDateButton.isVisible = selectedExpiryDate != null
        dialogBinding.expiryChevronImageView.isVisible = selectedExpiryDate == null
    }

    fun setQuantity(value: Double) {
        val safeValue = value.coerceAtLeast(config.minimumQuantity)
            .let { minimumApplied -> config.maximumQuantity?.let { minimumApplied.coerceAtMost(it) } ?: minimumApplied }
            .toCompactQuantityText()
        dialogBinding.quantityInputEditText.setText(safeValue)
        dialogBinding.quantityInputEditText.setSelection(safeValue.length)
    }

    fun updatePreview() {
        val currentStock = config.currentStock ?: return
        val pending = currentQuantity()?.takeIf { it >= config.minimumQuantity } ?: 0.0
        val afterStock = if (config.quantityIsFinalStock) {
            pending
        } else {
            currentStock + (pending * config.stockChangeDirection)
        }.coerceAtLeast(0.0)
        dialogBinding.currentStockValueTextView.text = currentStock.withUnit(config.unit)
        dialogBinding.afterStockValueTextView.text = afterStock.withUnit(config.unit)
        if (config.showDifference) {
            val difference = afterStock - currentStock
            dialogBinding.differenceStockValueTextView.text = difference.withSignedUnit(config.unit)
            dialogBinding.differenceStockValueTextView.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    when {
                        difference > 0.0 -> R.color.inventory_success
                        difference < 0.0 -> R.color.inventory_danger
                        else -> R.color.inventory_text_secondary
                    }
                )
            )
        }
        config.extraCurrentStock?.let { extraCurrentStock ->
            dialogBinding.extraCurrentStockValueTextView.text = extraCurrentStock.withUnit(config.unit)
            dialogBinding.extraAfterStockValueTextView.text =
                (extraCurrentStock + (pending * config.extraStockChangeDirection)).coerceAtLeast(0.0).withUnit(config.unit)
        }
    }

    fun expiryDateIsBeforeMinimum(expiryDate: Long): Boolean {
        val minimum = config.minimumExpiryDate ?: return false
        val expiryLocalDate = Instant.ofEpochMilli(expiryDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val minimumLocalDate = Instant.ofEpochMilli(minimum).atZone(ZoneId.systemDefault()).toLocalDate()
        return expiryLocalDate.isBefore(minimumLocalDate)
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(config.title)
        .setView(dialogBinding.root)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.apply, null)
        .create()

    dialogBinding.quantityInputEditText.doAfterTextChanged {
        dialogBinding.quantityInputEditText.error = null
        updatePreview()
    }
    updatePreview()
    updateExpiryDateLabel()

    dialogBinding.expiryDateGroup.setOnClickListener {
        val selection = selectedExpiryDate
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            ?: Instant.ofEpochMilli(config.minimumExpiryDate ?: System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(selection.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val selectedDate = Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            val selectedMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (expiryDateIsBeforeMinimum(selectedMillis)) {
                dialogBinding.quantityInputEditText.error =
                    config.expiryDateValidationMessage.ifBlank { getString(R.string.expiry_before_transaction_error) }
            } else {
                selectedExpiryDate = selectedMillis
                dialogBinding.quantityInputEditText.error = null
                updateExpiryDateLabel()
            }
        }
        picker.show(parentFragmentManager, "quantity_expiry_date_picker")
    }

    dialogBinding.clearExpiryDateButton.setOnClickListener {
        selectedExpiryDate = null
        updateExpiryDateLabel()
    }

    dialog.setOnShowListener {
        dialogBinding.minusButton.setOnClickListener {
            setQuantity((currentQuantity() ?: config.minimumQuantity) - 1.0)
        }
        dialogBinding.plusButton.setOnClickListener {
            setQuantity((currentQuantity() ?: config.minimumQuantity) + 1.0)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val quantity = currentQuantity()
            if (quantity == null || quantity < config.minimumQuantity || config.maximumQuantity?.let { quantity > it } == true) {
                dialogBinding.quantityInputEditText.error = config.validationMessage
                return@setOnClickListener
            }
            if (selectedExpiryDate?.let { expiryDateIsBeforeMinimum(it) } == true) {
                dialogBinding.quantityInputEditText.error =
                    config.expiryDateValidationMessage.ifBlank { getString(R.string.expiry_before_transaction_error) }
                return@setOnClickListener
            }
            onApply(QuantityStepperResult(quantity, selectedExpiryDate))
            dialog.dismiss()
        }
        dialogBinding.quantityInputEditText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialogBinding.quantityInputEditText.post {
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(dialogBinding.quantityInputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    dialog.show()
}

private fun Double.withUnit(unit: String): String =
    "${toCompactQuantityText()}${unit.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"

private fun Double.withSignedUnit(unit: String): String =
    "${if (this > 0.0) "+" else ""}${withUnit(unit)}"
