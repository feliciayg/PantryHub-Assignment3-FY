package com.example.pantryhub_assignment3_fy.ui.movement

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentStockInTransactionBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.bindFormValue
import com.example.pantryhub_assignment3_fy.ui.common.observeMemoEditorResult
import com.example.pantryhub_assignment3_fy.ui.common.openMemoEditor
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.showWarningDialog
import com.example.pantryhub_assignment3_fy.ui.common.TransactionDateTimePicker
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog
import com.example.pantryhub_assignment3_fy.ui.storage.BarcodeScannerPrototypeFragment
import com.example.pantryhub_assignment3_fy.ui.storage.CategoryPickerFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.Date

class StockInTransactionFragment : Fragment() {
    private var _binding: FragmentStockInTransactionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StockInTransactionViewModel by activityViewModels()
    private val selectedAdapter = StockInSelectedAdapter(
        onEdit = ::showStockInQuantityDialog,
        onRemove = { viewModel.removeItem(it.id) }
    )
    private var startedFresh = false
    private var prefillApplied = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockInTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mode = arguments?.getString(ARG_TRANSACTION_MODE)
            ?.let { runCatching { TransactionMode.valueOf(it) }.getOrNull() }
            ?: TransactionMode.STOCK_IN
        val editTransactionId = arguments?.getString(ARG_EDIT_TRANSACTION_ID).orEmpty()
        if (savedInstanceState == null && !startedFresh) {
            if (editTransactionId.isBlank()) {
                viewModel.startNewTransaction(mode)
            } else {
                viewModel.startEditingTransaction(editTransactionId, mode)
            }
            startedFresh = true
        }
        binding.selectedItemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedItemsRecyclerView.adapter = selectedAdapter

        binding.formHeader.closeButton.setOnClickListener {
            viewModel.discardTransaction()
            findNavController().popBackStack()
        }
        binding.locationRow.setOnClickListener {
            openSelector(
                if (viewModel.uiState.value?.mode == TransactionMode.MOVE_STOCK) {
                    CategoryPickerFragment.FIELD_MOVE_STOCK_FROM
                } else {
                    CategoryPickerFragment.FIELD_STOCK_IN_LOCATION
                }
            )
        }
        binding.partnerRow.setOnClickListener {
            openSelector(
                when (viewModel.uiState.value?.mode) {
                    TransactionMode.STOCK_OUT -> CategoryPickerFragment.FIELD_STOCK_OUT_CUSTOMER
                    TransactionMode.MOVE_STOCK -> CategoryPickerFragment.FIELD_MOVE_STOCK_TO
                    TransactionMode.ADJUST_STOCK -> CategoryPickerFragment.FIELD_STOCK_IN_PARTNER
                    else -> CategoryPickerFragment.FIELD_STOCK_IN_PARTNER
                }
            )
        }
        binding.dateRow.setOnClickListener { showTransactionDateTimePicker() }
        binding.itemActionsRow.addItemRow.setOnClickListener {
            val state = viewModel.uiState.value
            if (state?.selectedBranch == null) {
                Snackbar.make(binding.root, R.string.stock_in_requires_location, Snackbar.LENGTH_SHORT).show()
            } else {
                findNavController().navigate(R.id.action_stockInTransactionFragment_to_stockInItemPickerFragment)
            }
        }
        binding.itemActionsRow.scanBarcodeRow.setOnClickListener {
            findNavController().navigate(R.id.barcodeScannerPrototypeFragment)
        }
        binding.memoRow.setOnClickListener { openMemoEditor(viewModel.uiState.value?.memo.orEmpty()) }
        binding.submitButton.setOnClickListener { viewModel.submitTransaction() }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle>(CategoryPickerFragment.RESULT_KEY)
            ?.observe(viewLifecycleOwner) { result ->
                val fieldType = result.getString(CategoryPickerFragment.RESULT_FIELD_TYPE).orEmpty()
                val selectedId = result.getString(CategoryPickerFragment.RESULT_SELECTED_ID).orEmpty()
                val selectedValue = result.getString(CategoryPickerFragment.RESULT_SELECTED_VALUE).orEmpty()
                when (fieldType) {
                    CategoryPickerFragment.FIELD_STOCK_IN_LOCATION -> viewModel.selectBranchById(selectedId, selectedValue)
                    CategoryPickerFragment.FIELD_MOVE_STOCK_FROM -> viewModel.selectBranchById(selectedId, selectedValue)
                    CategoryPickerFragment.FIELD_MOVE_STOCK_TO -> viewModel.selectDestinationBranchById(selectedId, selectedValue)
                    CategoryPickerFragment.FIELD_STOCK_IN_PARTNER -> viewModel.selectSupplierById(selectedId, selectedValue)
                    CategoryPickerFragment.FIELD_STOCK_OUT_CUSTOMER -> viewModel.selectCustomerById(selectedId, selectedValue)
                }
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<Bundle>(CategoryPickerFragment.RESULT_KEY)
            }
        observeBarcodeScannerResult()
        observeMemoEditorResult(viewModel::setMemo)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            bindState(state)
            if (!prefillApplied) {
                val itemId = arguments?.getString(ARG_PREFILL_ITEM_ID).orEmpty()
                val branchId = arguments?.getString(ARG_PREFILL_BRANCH_ID).orEmpty()
                val branchName = arguments?.getString(ARG_PREFILL_BRANCH_NAME).orEmpty()
                if (itemId.isBlank()) {
                    prefillApplied = true
                } else if (state.inventoryItems.any { it.id == itemId }) {
                    prefillApplied = true
                    viewModel.prefillItem(itemId, branchId, branchName)
                }
            }
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.infoMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
            state.warningDialog?.let { warning ->
                showWarningDialog(warning) { viewModel.clearMessages() }
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
                if (state.isEditing) {
                    findNavController().previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(RESULT_TRANSACTION_UPDATED, true)
                }
                findNavController().popBackStack()
            }
        }
    }

    private fun observeBarcodeScannerResult() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
            ?.observe(viewLifecycleOwner) { barcode ->
                findNavController().currentBackStackEntry?.savedStateHandle
                    ?.remove<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
                val value = barcode.trim()
                if (value.isNotBlank() && !viewModel.addScannedBarcode(value)) {
                    Snackbar.make(binding.root, getString(R.string.barcode_item_not_found, value), Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    private fun bindState(state: StockInTransactionUiState) {
        binding.formHeader.titleTextView.text = if (state.isEditing) {
            getString(R.string.edit_transaction)
        } else {
            getString(R.string.new_transaction)
        }
        binding.locationLabelTextView.text = if (state.mode == TransactionMode.MOVE_STOCK) getString(R.string.from) else getString(R.string.location)
        bindFormValue(binding.locationValueTextView, state.selectedBranch?.name, getString(R.string.select_a_location))
        binding.partnerRow.isVisible = state.mode != TransactionMode.ADJUST_STOCK
        binding.partnerLabelTextView.setText(state.mode.partnerLabelRes)
        bindFormValue(
            binding.partnerValueTextView,
            when (state.mode) {
                TransactionMode.STOCK_IN -> state.selectedSupplier?.name
                TransactionMode.STOCK_OUT -> state.selectedCustomer?.name
                TransactionMode.MOVE_STOCK -> state.selectedDestinationBranch?.name
                TransactionMode.ADJUST_STOCK -> null
            },
            getString(state.mode.partnerPlaceholderRes)
        )
        selectedAdapter.mode = state.mode
        bindFormValue(binding.memoValueTextView, state.memo, getString(R.string.add_a_note))
        binding.dateValueTextView.text = formatTransactionDateTime(state.transactionAt)
        binding.submitButton.isEnabled = !state.isSubmitting
        binding.submitButton.text = if (state.isEditing) {
            getString(R.string.save_changes)
        } else {
            getString(R.string.submit)
        }
        val lines = state.selectedLines.values.sortedBy { it.item.name.lowercase() }
        selectedAdapter.submitList(lines)
        binding.itemsCountTextView.text = if (lines.isEmpty()) {
            getString(R.string.zero)
        } else {
            "${lines.size} ${if (lines.size == 1) "item" else "items"} / ${lines.sumOf { it.quantity }.toMovementQuantityText()}"
        }
    }

    private fun openSelector(fieldType: String) {
        val state = viewModel.uiState.value
        findNavController().navigate(
            R.id.action_stockInTransactionFragment_to_categoryPickerFragment,
            Bundle().apply {
                putString(CategoryPickerFragment.ARG_FIELD_TYPE, fieldType)
                putString(
                    CategoryPickerFragment.ARG_CURRENT_CATEGORY,
                    when (fieldType) {
                        CategoryPickerFragment.FIELD_STOCK_IN_LOCATION -> state?.selectedBranch?.name.orEmpty()
                        CategoryPickerFragment.FIELD_MOVE_STOCK_FROM -> state?.selectedBranch?.name.orEmpty()
                        CategoryPickerFragment.FIELD_MOVE_STOCK_TO -> state?.selectedDestinationBranch?.name.orEmpty()
                        CategoryPickerFragment.FIELD_STOCK_IN_PARTNER -> state?.selectedSupplier?.name.orEmpty()
                        else -> state?.selectedCustomer?.name.orEmpty()
                    }
                )
            }
        )
    }

    private fun showStockInQuantityDialog(item: InventoryItem) {
        val state = viewModel.uiState.value
        val pendingLine = state?.selectedLines?.get(item.id)
        val pending = pendingLine?.quantity ?: 1.0
        val mode = state?.mode ?: TransactionMode.STOCK_IN
        val destinationQuantity = state?.destinationQuantityFor(item)
        showQuantityStepperDialog(
            config = QuantityStepperConfig(
                title = when (mode) {
                    TransactionMode.STOCK_IN -> getString(R.string.enter_stock_in_details_title)
                    TransactionMode.STOCK_OUT -> getString(R.string.enter_stock_out_quantity)
                    TransactionMode.MOVE_STOCK -> getString(R.string.enter_move_quantity)
                    TransactionMode.ADJUST_STOCK -> getString(R.string.adjust_stock_count)
                },
                initialQuantity = pending,
                minimumQuantity = if (mode == TransactionMode.ADJUST_STOCK) 0.0 else 1.0,
                currentStock = item.quantity,
                currentStockLabel = if (mode == TransactionMode.MOVE_STOCK) getString(R.string.from_stock_now) else getString(R.string.current_stock),
                unit = item.unit,
                validationMessage = when (mode) {
                    TransactionMode.STOCK_IN -> getString(R.string.enter_valid_quantity)
                    TransactionMode.STOCK_OUT -> getString(R.string.stock_out_exceeds_available)
                    TransactionMode.MOVE_STOCK -> getString(R.string.move_quantity_exceeds_available)
                    TransactionMode.ADJUST_STOCK -> getString(R.string.enter_valid_quantity)
                },
                maximumQuantity = item.quantity.takeIf { mode == TransactionMode.STOCK_OUT || mode == TransactionMode.MOVE_STOCK },
                stockChangeDirection = if (mode == TransactionMode.STOCK_IN) 1 else -1,
                afterStockLabel = when (mode) {
                    TransactionMode.STOCK_IN -> getString(R.string.after_stock_in)
                    TransactionMode.STOCK_OUT -> getString(R.string.after_stock_out)
                    TransactionMode.MOVE_STOCK -> getString(R.string.from_after_move)
                    TransactionMode.ADJUST_STOCK -> getString(R.string.new_stock)
                },
                extraCurrentStock = destinationQuantity.takeIf { mode == TransactionMode.MOVE_STOCK },
                extraCurrentStockLabel = getString(R.string.to_stock_now),
                extraAfterStockLabel = getString(R.string.to_after_move),
                extraStockChangeDirection = 1,
                showExpiryDate = mode == TransactionMode.STOCK_IN,
                initialExpiryDate = pendingLine?.expiryDate,
                minimumExpiryDate = state?.transactionAt,
                expiryDateValidationMessage = getString(R.string.expiry_before_transaction_error),
                quantityIsFinalStock = mode == TransactionMode.ADJUST_STOCK,
                showDifference = mode == TransactionMode.ADJUST_STOCK,
                differenceLabel = getString(R.string.difference)
            )
        ) { result ->
            viewModel.setItemQuantity(
                item = item,
                quantity = result.quantity,
                expiryDate = result.expiryDate,
                preserveExistingExpiryDate = mode != TransactionMode.STOCK_IN
            )
        }
    }

    private fun showTransactionDateTimePicker() {
        val current = viewModel.uiState.value?.transactionAt ?: System.currentTimeMillis()
        TransactionDateTimePicker.show(
            fragmentManager = childFragmentManager,
            initialMillis = current,
            use24HourTime = TransactionDateTimePicker.is24HourTime(requireContext()),
            onSelected = viewModel::setTransactionAt,
            onInvalidFuture = {
                Snackbar.make(binding.root, StockInTransactionViewModel.FUTURE_TRANSACTION_ERROR, Snackbar.LENGTH_LONG).show()
            }
        )
    }

    private fun formatTransactionDateTime(value: Long): String {
        val date = Date(value)
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
        val timeText = android.text.format.DateFormat.getTimeFormat(requireContext()).format(date)
        return "$dateText $timeText"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {
        const val ARG_TRANSACTION_MODE = "transactionMode"
        const val ARG_EDIT_TRANSACTION_ID = "editTransactionId"
        const val ARG_PREFILL_ITEM_ID = "prefillTransactionItemId"
        const val ARG_PREFILL_BRANCH_ID = "prefillTransactionBranchId"
        const val ARG_PREFILL_BRANCH_NAME = "prefillTransactionBranchName"
        const val RESULT_TRANSACTION_UPDATED = "transaction_updated"
    }
}
