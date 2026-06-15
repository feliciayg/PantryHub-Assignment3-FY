package com.example.pantryhub_assignment3_fy.ui.movement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentStockInItemPickerBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog
import com.example.pantryhub_assignment3_fy.ui.storage.AddEditInventoryItemFragment
import com.google.android.material.snackbar.Snackbar

class StockInItemPickerFragment : Fragment() {
    private var _binding: FragmentStockInItemPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StockInTransactionViewModel by activityViewModels()
    private lateinit var adapter: StockInPickerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockInItemPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = StockInPickerAdapter { item -> showQuantityDialog(item) }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.doneButton.setOnClickListener { findNavController().popBackStack() }
        binding.addItemButton.setOnClickListener { openAddItemForSelectedLocation() }
        binding.searchEditText.doAfterTextChanged {
            viewModel.setPickerQuery(it?.toString().orEmpty())
            renderRows()
        }
        binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPickerInStockOnly(isChecked)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.locationTitleTextView.text = state.selectedBranch?.name.orEmpty()
            binding.addItemButton.isVisible = state.mode == TransactionMode.STOCK_IN
            binding.inStockChip.isVisible = state.mode != TransactionMode.ADJUST_STOCK
            val shouldShowInStockOnly = state.mode == TransactionMode.STOCK_OUT ||
                state.mode == TransactionMode.MOVE_STOCK ||
                (state.mode != TransactionMode.ADJUST_STOCK && state.pickerInStockOnly)
            if (binding.inStockChip.isChecked != shouldShowInStockOnly) {
                binding.inStockChip.isChecked = shouldShowInStockOnly
            }
            binding.inStockChip.isEnabled = state.mode == TransactionMode.STOCK_IN
            adapter.mode = state.mode
            renderRows()
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun renderRows() {
        val rows = viewModel.pickerItems()
        adapter.submitList(rows)
        binding.emptyTextView.isVisible = rows.isEmpty()
        binding.itemsRecyclerView.isVisible = rows.isNotEmpty()
        val selected = viewModel.uiState.value?.selectedLines?.values.orEmpty()
        binding.summaryTextView.text = "${selected.size} ${if (selected.size == 1) "item" else "items"} / ${selected.sumOf { it.quantity }.toMovementQuantityText()}"
    }

    private fun showQuantityDialog(item: InventoryItem) {
        val state = viewModel.uiState.value
        val pendingLine = state?.selectedLines?.get(item.id)
        val pending = pendingLine?.quantity
        val mode = viewModel.uiState.value?.mode ?: TransactionMode.STOCK_IN
        val destinationQuantity = state?.destinationQuantityFor(item)
        showQuantityStepperDialog(
            config = QuantityStepperConfig(
                title = when (mode) {
                    TransactionMode.STOCK_IN -> getString(R.string.enter_stock_in_details_title)
                    TransactionMode.STOCK_OUT -> getString(R.string.enter_stock_out_quantity)
                    TransactionMode.MOVE_STOCK -> getString(R.string.enter_move_quantity)
                    TransactionMode.ADJUST_STOCK -> getString(R.string.adjust_stock_count)
                },
                initialQuantity = pending ?: if (mode == TransactionMode.ADJUST_STOCK) item.quantity else 1.0,
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

    private fun openAddItemForSelectedLocation() {
        val branch = viewModel.uiState.value?.selectedBranch
        if (branch == null) {
            Snackbar.make(binding.root, R.string.stock_in_requires_location, Snackbar.LENGTH_SHORT).show()
            return
        }
        findNavController().navigate(
            R.id.action_stockInItemPickerFragment_to_addEditInventoryItemFragment,
            Bundle().apply {
                putString(AddEditInventoryItemFragment.ARG_MODE, AddEditInventoryItemFragment.MODE_ADD)
                putString(AddEditInventoryItemFragment.ARG_PREFILL_BRANCH_ID, branch.id)
                putString(AddEditInventoryItemFragment.ARG_PREFILL_BRANCH_NAME, branch.name)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
