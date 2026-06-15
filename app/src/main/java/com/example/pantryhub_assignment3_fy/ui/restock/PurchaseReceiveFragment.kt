package com.example.pantryhub_assignment3_fy.ui.restock

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentPurchaseReceiveBinding
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.ui.common.bindFormValue
import com.example.pantryhub_assignment3_fy.ui.common.observeMemoEditorResult
import com.example.pantryhub_assignment3_fy.ui.common.openMemoEditor
import com.google.android.material.snackbar.Snackbar

class PurchaseReceiveFragment : Fragment() {
    private var _binding: FragmentPurchaseReceiveBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RestockOrdersViewModel by activityViewModels()
    private lateinit var selectedAdapter: PurchaseReceiveSelectedAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchaseReceiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val purchaseId = arguments?.getString(ARG_PURCHASE_ID).orEmpty()
        if (savedInstanceState == null && purchaseId.isNotBlank()) {
            viewModel.startPartialReceive(purchaseId)
        }

        selectedAdapter = PurchaseReceiveSelectedAdapter(::editSelectedRow)
        binding.selectedItemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedItemsRecyclerView.adapter = selectedAdapter

        binding.formHeader.titleTextView.setText(R.string.new_transaction)
        binding.formHeader.closeButton.setOnClickListener {
            viewModel.clearReceiveDraft()
            findNavController().popBackStack()
        }
        binding.itemsRow.setOnClickListener {
            findNavController().navigate(R.id.action_purchaseReceiveFragment_to_purchaseReceiveItemPickerFragment)
        }
        binding.memoRow.setOnClickListener {
            val draft = viewModel.uiState.value?.receiveDraft ?: return@setOnClickListener
            openMemoEditor(draft.memo.ifBlank { viewModel.defaultReceiveMemo(draft.orderLabel) })
        }
        binding.submitButton.setOnClickListener { viewModel.submitPartialReceive() }
        observeMemoEditorResult(viewModel::updateReceiveMemo)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val draft = state.receiveDraft
            val order = viewModel.currentReceiveOrder()
            if (draft == null || order == null) return@observe

            bindFormValue(binding.locationValueTextView, draft.locationName, getString(R.string.select_a_location))
            binding.itemsValueTextView.text = state.receiveSelectionSummary()
            bindFormValue(
                binding.memoValueTextView,
                draft.memo.ifBlank { viewModel.defaultReceiveMemo(draft.orderLabel) },
                getString(R.string.add_a_note)
            )
            val selectedRows = viewModel.selectedReceiveRows()
            selectedAdapter.submitList(selectedRows)
            binding.selectedItemsRecyclerView.isVisible = selectedRows.isNotEmpty()
            binding.submitButton.isEnabled = !draft.isSubmitting && selectedRows.isNotEmpty()

            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.receiveCompletedMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                }.show()
                viewModel.consumeReceiveCompletedMessage()
                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(RESULT_PURCHASE_RECEIVED, true)
                findNavController().popBackStack()
            }
        }
    }

    private fun editSelectedRow(row: PurchaseReceivePickerRow) {
        if (row.selectedQuantity <= 0.0) {
            viewModel.setReceiveQuantity(row.item.receiveKey(), 0.0)
        } else {
            showQuantityDialog(row)
        }
    }

    private fun showQuantityDialog(row: PurchaseReceivePickerRow) {
        showReceiveQuantityDialog(row) { quantity ->
            viewModel.setReceiveQuantity(row.item.receiveKey(), quantity)
        }
    }

    override fun onDestroyView() {
        binding.selectedItemsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_PURCHASE_ID = "purchaseId"
        const val RESULT_PURCHASE_RECEIVED = "purchase_received"
    }
}

internal fun RestockOrdersUiState.receiveSelectionSummary(): String {
    val draft = receiveDraft ?: return "0 items / 0"
    val itemCount = draft.selectedQuantities.count { it.value > 0.0 }
    val totalQuantity = draft.selectedQuantities.values.sum()
    val itemLabel = if (itemCount == 1) "1 item" else "$itemCount items"
    val quantityLabel = if (totalQuantity % 1.0 == 0.0) totalQuantity.toLong().toString() else totalQuantity.toString()
    return "$itemLabel / $quantityLabel"
}

internal fun PurchaseOrderItem.receiveKey(): String =
    inventoryItemId.ifBlank { listOf(itemName.trim(), sku.trim(), barcode.trim()).joinToString("|") }
