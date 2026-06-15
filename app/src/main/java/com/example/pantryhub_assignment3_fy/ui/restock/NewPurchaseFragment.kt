package com.example.pantryhub_assignment3_fy.ui.restock

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentNewPurchaseBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.ui.common.bindFormValue
import com.example.pantryhub_assignment3_fy.ui.common.observeMemoEditorResult
import com.example.pantryhub_assignment3_fy.ui.common.openMemoEditor
import com.example.pantryhub_assignment3_fy.ui.storage.BarcodeScannerPrototypeFragment
import com.example.pantryhub_assignment3_fy.ui.storage.CategoryPickerFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class NewPurchaseFragment : Fragment() {
    private var _binding: FragmentNewPurchaseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PurchaseEditorViewModel by activityViewModels()
    private val listViewModel: RestockOrdersViewModel by activityViewModels()
    private lateinit var selectedAdapter: PurchaseSelectedItemAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewPurchaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val editingPurchaseId = arguments?.getString(ARG_PURCHASE_ID).orEmpty()
        if (editingPurchaseId.isNotBlank()) {
            val order = listViewModel.uiState.value?.restockOrders.orEmpty().firstOrNull { it.id == editingPurchaseId }
            if (order != null && viewModel.uiState.value?.form?.purchaseId != editingPurchaseId) {
                viewModel.loadDraftForEdit(order)
            }
        } else {
            viewModel.ensureNewPurchaseStarted()
        }
        selectedAdapter = PurchaseSelectedItemAdapter(
            onEdit = ::editItemQuantity,
            onRemove = { item -> viewModel.removeItem(item.inventoryItemId) }
        )
        binding.selectedItemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedItemsRecyclerView.adapter = selectedAdapter

        binding.formHeader.titleTextView.setText(
            if (editingPurchaseId.isNotBlank()) R.string.edit_restock_item else R.string.new_purchase_order
        )
        binding.formHeader.closeButton.setOnClickListener { findNavController().popBackStack() }
        binding.supplierRow.setOnClickListener { openSelector(CategoryPickerFragment.FIELD_STOCK_IN_PARTNER) }
        binding.locationRow.setOnClickListener { openSelector(CategoryPickerFragment.FIELD_LOCATION) }
        binding.orderDateRow.setOnClickListener { showDatePicker(isExpectedDate = false) }
        binding.expectedDeliveryRow.setOnClickListener { showDatePicker(isExpectedDate = true) }
        binding.itemActionsRow.addItemRow.setOnClickListener {
            findNavController().navigate(R.id.action_newPurchaseFragment_to_purchaseItemPickerFragment)
        }
        binding.itemActionsRow.scanBarcodeRow.setOnClickListener {
            findNavController().navigate(R.id.barcodeScannerPrototypeFragment)
        }
        binding.memoRow.setOnClickListener { openMemoEditor(viewModel.uiState.value?.form?.memo.orEmpty()) }
        binding.formActionButtons.secondaryButton.setText(R.string.save_draft)
        binding.formActionButtons.primaryButton.setText(R.string.save)
        binding.formActionButtons.secondaryButton.setOnClickListener { viewModel.saveDraft(placeOrder = false) }
        binding.formActionButtons.primaryButton.setOnClickListener { viewModel.saveDraft(placeOrder = true) }
        observeMemoEditorResult(viewModel::setMemo)
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle>(CategoryPickerFragment.RESULT_KEY)
            ?.observe(viewLifecycleOwner) { result ->
                val fieldType = result.getString(CategoryPickerFragment.RESULT_FIELD_TYPE).orEmpty()
                val selectedId = result.getString(CategoryPickerFragment.RESULT_SELECTED_ID).orEmpty()
                val selectedValue = result.getString(CategoryPickerFragment.RESULT_SELECTED_VALUE).orEmpty()
                when (fieldType) {
                    CategoryPickerFragment.FIELD_LOCATION -> viewModel.setBranch(Branch(id = selectedId, name = selectedValue))
                    CategoryPickerFragment.FIELD_STOCK_IN_PARTNER -> viewModel.setSupplier(Supplier(id = selectedId, name = selectedValue))
                }
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<Bundle>(CategoryPickerFragment.RESULT_KEY)
            }
        observeBarcodeScannerResult()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
            if (state.closeAfterSave) {
                viewModel.consumeCloseSignal()
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

    private fun render(state: PurchaseEditorUiState) {
        val form = state.form
        binding.formHeader.titleTextView.setText(
            if (form.isEditing) R.string.edit_restock_item else R.string.new_purchase_order
        )
        bindFormValue(binding.supplierValueTextView, form.supplierName, getString(R.string.select))
        bindFormValue(binding.locationValueTextView, form.receivingLocationName, getString(R.string.select))
        binding.orderDateValueTextView.text = form.orderDate.toPurchaseDateText()
        bindFormValue(
            binding.expectedDeliveryValueTextView,
            form.expectedDeliveryDate.takeIf { it > 0L }?.toDateText(),
            getString(R.string.select)
        )
        bindFormValue(binding.memoValueTextView, form.memo, getString(R.string.add_a_note))
        selectedAdapter.submitList(form.items)
        binding.selectedItemsRecyclerView.isVisible = form.items.isNotEmpty()
        val totalQuantity = form.items.sumOf { it.orderedQuantity }
        val totalUnit = form.items.firstOrNull()?.unit.orEmpty()
        binding.itemsCountTextView.text = if (form.items.isEmpty()) {
            getString(R.string.zero)
        } else {
            getString(
                R.string.purchase_items_summary,
                form.items.size,
                totalQuantity.toPurchaseQuantityText(),
                totalUnit.ifBlank { getString(R.string.items) }
            )
        }
        binding.formActionButtons.secondaryButton.isEnabled = !state.isSubmitting
        binding.formActionButtons.primaryButton.isEnabled = !state.isSubmitting
    }

    private fun openSelector(fieldType: String) {
        val state = viewModel.uiState.value
        findNavController().navigate(
            R.id.action_newPurchaseFragment_to_categoryPickerFragment,
            Bundle().apply {
                putString(CategoryPickerFragment.ARG_FIELD_TYPE, fieldType)
                putString(
                    CategoryPickerFragment.ARG_CURRENT_CATEGORY,
                    when (fieldType) {
                        CategoryPickerFragment.FIELD_LOCATION -> state?.form?.receivingLocationName.orEmpty()
                        else -> state?.form?.supplierName.orEmpty()
                    }
                )
                putString(CategoryPickerFragment.ARG_CURRENT_BRANCH_ID, state?.form?.receivingLocationId.orEmpty())
            }
        )
    }

    private fun showDatePicker(isExpectedDate: Boolean) {
        val current = viewModel.uiState.value?.form ?: return
        val selection = if (isExpectedDate) {
            current.expectedDeliveryDate.takeIf { it > 0L } ?: current.orderDate
        } else {
            current.orderDate
        }
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(
                Instant.ofEpochMilli(selection)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            )
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val millis = Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (isExpectedDate) viewModel.setExpectedDate(millis) else viewModel.setOrderDate(millis)
        }
        picker.show(parentFragmentManager, if (isExpectedDate) "purchase_expected_date" else "purchase_order_date")
    }

    private fun editItemQuantity(item: PurchaseOrderItem) {
        val inventoryItem = viewModel.uiState.value?.inventoryItems.orEmpty()
            .firstOrNull { it.id == item.inventoryItemId }
            ?: return
        val input = EditText(requireContext()).apply {
            setText(item.orderedQuantity.toPurchaseQuantityText())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.enter_quantity)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val quantity = input.text?.toString().orEmpty().trim().toDoubleOrNull()
                        if (quantity == null || quantity <= 0.0) {
                            input.error = getString(R.string.enter_valid_quantity)
                        } else {
                            viewModel.addOrUpdateItem(inventoryItem, quantity)
                            dialog.dismiss()
                        }
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_PURCHASE_ID = "purchaseId"
    }
}

private fun Long.toDateText(): String =
    com.example.pantryhub_assignment3_fy.util.DateUtils.formatDisplayDate(this)

private fun Long.toPurchaseDateText(): String {
    val now = System.currentTimeMillis()
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val valueDate = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return if (today == valueDate) "Today" else toDateText()
}
