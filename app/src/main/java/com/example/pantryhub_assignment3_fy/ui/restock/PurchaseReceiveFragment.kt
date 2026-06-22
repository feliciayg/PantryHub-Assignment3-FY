package com.example.pantryhub_assignment3_fy.ui.restock

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionDetailsViewModel
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
        val currentDraft = viewModel.uiState.value?.receiveDraft
        if (purchaseId.isNotBlank() && currentDraft?.purchaseId != purchaseId) {
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
            state.receiveCompletion?.let(::showReceiveSuccessDialog)

            val draft = state.receiveDraft
            val order = viewModel.currentReceiveOrder()
            if (draft == null || order == null) return@observe

            bindFormValue(binding.locationValueTextView, draft.locationName, getString(R.string.select_a_location))
            binding.itemsValueTextView.text = state.receiveSelectionSummary(requireContext())
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

    private fun showReceiveSuccessDialog(completion: PurchaseReceiveCompletion) {
        viewModel.consumeReceiveCompletion()
        findNavController().previousBackStackEntry
            ?.savedStateHandle
            ?.set(RESULT_PURCHASE_RECEIVED, true)
        val itemLabel = resources.getQuantityString(R.plurals.item_count, completion.itemCount, completion.itemCount)
        val quantityLabel = completion.totalQuantity.toPurchaseQuantityText()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.purchase_receive_success_title)
            .setMessage(getString(R.string.purchase_receive_success_message, itemLabel, quantityLabel))
            .setPositiveButton(R.string.view_stock_in) { _, _ ->
                findNavController().popBackStack(R.id.purchaseDetailFragment, false)
                findNavController().navigate(
                    R.id.transactionDetailsFragment,
                    Bundle().apply {
                        putString(TransactionDetailsViewModel.ARG_TRANSACTION_ID, completion.transactionId)
                    }
                )
            }
            .setNegativeButton(R.string.done) { _, _ ->
                findNavController().popBackStack()
            }
            .setOnCancelListener {
                findNavController().popBackStack()
            }
            .show()
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

internal fun RestockOrdersUiState.receiveSelectionSummary(context: Context): String {
    val draft = receiveDraft ?: return context.getString(
        R.string.purchase_receive_selection_summary,
        context.resources.getQuantityString(R.plurals.item_count, 0, 0),
        "0"
    )
    val itemCount = draft.selectedQuantities.count { it.value > 0.0 }
    val totalQuantity = draft.selectedQuantities.values.sum()
    val itemLabel = context.resources.getQuantityString(R.plurals.item_count, itemCount, itemCount)
    val quantityLabel = if (totalQuantity % 1.0 == 0.0) totalQuantity.toLong().toString() else totalQuantity.toString()
    return context.getString(R.string.purchase_receive_selection_summary, itemLabel, quantityLabel)
}

internal fun PurchaseOrderItem.receiveKey(): String =
    inventoryItemId.ifBlank { listOf(itemName.trim(), sku.trim(), barcode.trim()).joinToString("|") }
