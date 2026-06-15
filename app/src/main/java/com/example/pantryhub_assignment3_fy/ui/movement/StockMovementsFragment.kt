package com.example.pantryhub_assignment3_fy.ui.movement

import android.app.AlertDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetSelectLocationBinding
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetTransactionTypeBinding
import com.example.pantryhub_assignment3_fy.databinding.DialogBranchTransferBinding
import com.example.pantryhub_assignment3_fy.databinding.DialogStockMovementBinding
import com.example.pantryhub_assignment3_fy.databinding.FragmentStockMovementsBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class StockMovementsFragment : Fragment() {
    private var _binding: FragmentStockMovementsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StockMovementViewModel by activityViewModels()
    private lateinit var adapter: StockMovementAdapter
    private var requestedActionConsumed = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockMovementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = StockMovementAdapter(::openTransactionDetails, showDateHeaders = true)
        binding.movementsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.movementsRecyclerView.adapter = adapter
        binding.addMovementFab.setOnClickListener { showTransactionTypeSheet() }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            adapter.submitMovementList(state.displayedMovements)
            binding.loadingIndicator.isVisible = state.isLoading
            binding.emptyTextView.isVisible = !state.isLoading && state.displayedMovements.isEmpty()
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
            openRequestedActionWhenReady(state)
        }
    }

    private fun openRequestedActionWhenReady(state: StockMovementUiState) {
        if (requestedActionConsumed) return
        val requestedAction = arguments?.getString(ARG_OPEN_ACTION).orEmpty()
        if (requestedAction.isBlank()) return
        if (state.isLoading) return

        requestedActionConsumed = true
        arguments?.remove(ARG_OPEN_ACTION)
        when (requestedAction) {
            ACTION_STOCK_IN -> openTransactionScreen(TransactionMode.STOCK_IN)
            ACTION_STOCK_OUT -> openTransactionScreen(TransactionMode.STOCK_OUT)
            ACTION_MOVE_STOCK -> openTransactionScreen(TransactionMode.MOVE_STOCK)
            ACTION_ADJUST_STOCK -> openTransactionScreen(TransactionMode.ADJUST_STOCK)
        }
    }

    private fun showTransactionTypeSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetTransactionTypeBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.applyTransactionTypeColors(requireContext())

        sheetBinding.stockInRow.setOnClickListener {
            dialog.dismiss()
            openTransactionScreen(TransactionMode.STOCK_IN)
        }
        sheetBinding.stockOutRow.setOnClickListener {
            dialog.dismiss()
            openTransactionScreen(TransactionMode.STOCK_OUT)
        }
        sheetBinding.moveStockRow.setOnClickListener {
            dialog.dismiss()
            openTransactionScreen(TransactionMode.MOVE_STOCK)
        }
        sheetBinding.adjustStockRow.setOnClickListener {
            dialog.dismiss()
            openTransactionScreen(TransactionMode.ADJUST_STOCK)
        }
        dialog.show()
    }

    private fun openTransactionScreen(mode: TransactionMode) {
        AppLogger.info(
            area = "Transactions",
            event = "transaction_mode_selected",
            message = "Transaction mode selected.",
            "mode" to mode.name
        )
        findNavController().navigate(
            R.id.action_stockMovementsFragment_to_stockInTransactionFragment,
            Bundle().apply { putString(StockInTransactionFragment.ARG_TRANSACTION_MODE, mode.name) }
        )
    }

    private fun openTransactionDetails(item: StockMovementListItem) {
        AppLogger.info(
            area = "Transactions",
            event = "transaction_details_opened",
            message = "Transaction details opened.",
            "type" to item.representativeMovement.movementType,
            "item" to item.representativeMovement.itemName
        )
        findNavController().navigate(
            R.id.action_stockMovementsFragment_to_transactionDetailsFragment,
            Bundle().apply {
                putString(
                    TransactionDetailsViewModel.ARG_TRANSACTION_ID,
                    item.transactionId.ifBlank { item.representativeMovement.transactionId.ifBlank { item.representativeMovement.id } }
                )
            }
        )
    }

    private fun openTransactionForLocation(action: TransactionAction, branch: Branch) {
        when (action) {
            TransactionAction.STOCK_IN -> showManualMovementDialog(StockMovementType.STOCK_IN, branch)
            TransactionAction.STOCK_OUT -> showManualMovementDialog(StockMovementType.STOCK_OUT, branch)
            TransactionAction.MOVE_STOCK -> showBranchTransferDialog(branch)
            // No dedicated ADJUST type exists yet; the manual movement form is the closest existing flow.
            TransactionAction.ADJUST_STOCK -> showManualMovementDialog(StockMovementType.STOCK_OUT, branch)
        }
    }

    private fun showSelectLocationSheet(action: TransactionAction) {
        val state = viewModel.uiState.value ?: StockMovementUiState()
        val branches = state.branches.filter { it.id.isNotBlank() }
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetSelectLocationBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.addOptionButton.isVisible = false

        fun renderRows(query: String = "") {
            sheetBinding.locationRowsContainer.removeAllViews()
            val visibleBranches = branches.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true)
            }
            sheetBinding.emptyLocationTextView.isVisible = visibleBranches.isEmpty()
            visibleBranches.forEachIndexed { index, branch ->
                sheetBinding.locationRowsContainer.addView(
                    createLocationRow(branch, state.inventoryItems.count { it.branchId == branch.id }) {
                        dialog.dismiss()
                        openTransactionForLocation(action, branch)
                    }
                )
                if (index != visibleBranches.lastIndex) {
                    sheetBinding.locationRowsContainer.addView(createDivider())
                }
            }
        }

        sheetBinding.searchLocationEditText.doAfterTextChanged {
            renderRows(it?.toString().orEmpty().trim())
        }
        renderRows()
        dialog.show()
    }

    private fun createLocationRow(branch: Branch, itemCount: Int, onClick: () -> Unit): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0
            )
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_store)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.home_action_icon_size),
                resources.getDimensionPixelSize(R.dimen.home_action_icon_size)
            ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.space_md) }
        })
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(requireContext()).apply {
                text = branch.name
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
                textSize = 16f
            })
            addView(TextView(requireContext()).apply {
                text = resources.getQuantityString(R.plurals.location_item_count, itemCount, itemCount)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
                textSize = 12f
            })
        })
        return row
    }

    private fun createDivider(): View =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.inventory_outline))
        }

    private fun selectableItemBackground() =
        TypedValue().let { value ->
            requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            ContextCompat.getDrawable(requireContext(), value.resourceId)
        }

    private fun showManualMovementDialog(defaultType: StockMovementType = StockMovementType.STOCK_OUT, selectedBranch: Branch? = null) {
        val state = viewModel.uiState.value ?: StockMovementUiState()
        val items = state.inventoryItems
            .filter { it.id.isNotBlank() }
            .filter { selectedBranch == null || it.branchId == selectedBranch.id }
            .filter {
                when (defaultType) {
                    StockMovementType.STOCK_OUT -> it.quantity > 0.0
                    else -> true
                }
            }
        if (items.isEmpty()) {
            Snackbar.make(binding.root, R.string.empty_storage, Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogStockMovementBinding.inflate(layoutInflater)
        val itemLabels = items.map { it.itemLabel() }
        val movementTypes = StockMovementType.values()
            .filterNot { it == StockMovementType.BRANCH_TRANSFER_OUT || it == StockMovementType.BRANCH_TRANSFER_IN }
            .filterNot { it == StockMovementType.ADJUST_STOCK }
        val movementLabels = movementTypes.map { it.toDisplay() }
        var selectedItem = items.first()
        var selectedType = defaultType.takeIf { it in movementTypes } ?: StockMovementType.STOCK_OUT

        fun updateExpiryVisibility() {
            dialogBinding.expiryDateLayout.isVisible =
                selectedType == StockMovementType.STOCK_IN || selectedType == StockMovementType.RETURN
            if (!dialogBinding.expiryDateLayout.isVisible) {
                dialogBinding.expiryDateEditText.text?.clear()
            }
        }

        dialogBinding.itemEditText.setAdapter(dropdownAdapter(itemLabels))
        dialogBinding.itemEditText.setText(itemLabels.first(), false)
        dialogBinding.itemEditText.setOnItemClickListener { _, _, position, _ ->
            selectedItem = items[position]
        }
        dialogBinding.typeEditText.setAdapter(dropdownAdapter(movementLabels))
        dialogBinding.typeEditText.setText(selectedType.toDisplay(), false)
        dialogBinding.typeEditText.setOnItemClickListener { _, _, position, _ ->
            selectedType = movementTypes[position]
            updateExpiryVisibility()
        }
        dialogBinding.expiryDateEditText.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.expiry_date)
                .build()
            picker.addOnPositiveButtonClickListener { selected ->
                dialogBinding.expiryDateEditText.setText(DateUtils.formatInputDate(selected))
            }
            picker.show(parentFragmentManager, "movementExpiryDate")
        }
        updateExpiryVisibility()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_stock_movement)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val quantity = dialogBinding.quantityEditText.text.toString().toDoubleOrNull()
                if (quantity == null || quantity <= 0.0) {
                    dialogBinding.quantityLayout.error = "Quantity must be greater than 0."
                    return@setOnClickListener
                }
                val expiryDate = dialogBinding.expiryDateEditText.text.toString()
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { DateUtils.parseInputDate(it) }.getOrNull() }
                viewModel.createManualMovement(
                    inventoryItem = selectedItem,
                    movementType = selectedType,
                    quantity = quantity,
                    reason = dialogBinding.reasonEditText.text.toString(),
                    note = dialogBinding.noteEditText.text.toString(),
                    expiryDate = expiryDate
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showBranchTransferDialog(sourceBranch: Branch? = null) {
        val state = viewModel.uiState.value ?: StockMovementUiState()
        val sourceItems = state.inventoryItems.filter {
            it.id.isNotBlank() &&
                it.quantity > 0.0 &&
                (sourceBranch == null || it.branchId == sourceBranch.id)
        }
        if (sourceItems.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_transfer_sources, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (state.branches.size < 2) {
            Snackbar.make(binding.root, R.string.no_transfer_destinations, Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogBranchTransferBinding.inflate(layoutInflater)
        val itemLabels = sourceItems.map { it.itemLabel() }
        var selectedSource = sourceItems.first()
        var destinationOptions = transferDestinations(state.branches, selectedSource)
        var selectedDestination = destinationOptions.firstOrNull()

        fun updateSourceDetails() {
            dialogBinding.sourceQuantityTextView.text = getString(
                R.string.source_stock_format,
                selectedSource.quantity.toMovementQuantityText(),
                selectedSource.unit,
                selectedSource.branchName.ifBlank { getString(R.string.unassigned_branch) }
            )
            destinationOptions = transferDestinations(state.branches, selectedSource)
            selectedDestination = destinationOptions.firstOrNull()
            dialogBinding.destinationBranchEditText.setAdapter(dropdownAdapter(destinationOptions.map { it.name }))
            dialogBinding.destinationBranchEditText.setText(selectedDestination?.name.orEmpty(), false)
        }

        dialogBinding.sourceItemEditText.setAdapter(dropdownAdapter(itemLabels))
        dialogBinding.sourceItemEditText.setText(itemLabels.first(), false)
        dialogBinding.sourceItemEditText.setOnItemClickListener { _, _, position, _ ->
            selectedSource = sourceItems[position]
            dialogBinding.sourceItemLayout.error = null
            dialogBinding.destinationBranchLayout.error = null
            updateSourceDetails()
        }
        dialogBinding.destinationBranchEditText.setOnItemClickListener { _, _, position, _ ->
            selectedDestination = destinationOptions.getOrNull(position)
            dialogBinding.destinationBranchLayout.error = null
        }
        updateSourceDetails()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.branch_transfer)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.quantityLayout.error = null
                dialogBinding.destinationBranchLayout.error = null

                val destination = selectedDestination
                val quantity = dialogBinding.quantityEditText.text.toString().toDoubleOrNull()
                when {
                    destination == null -> {
                        dialogBinding.destinationBranchLayout.error = getString(R.string.no_transfer_destinations)
                        return@setOnClickListener
                    }
                    destination.id == selectedSource.branchId -> {
                        dialogBinding.destinationBranchLayout.error = getString(R.string.same_branch_transfer_error)
                        return@setOnClickListener
                    }
                    quantity == null || quantity <= 0.0 -> {
                        dialogBinding.quantityLayout.error = "Quantity must be greater than 0."
                        return@setOnClickListener
                    }
                    quantity > selectedSource.quantity -> {
                        dialogBinding.quantityLayout.error = getString(R.string.not_enough_stock)
                        return@setOnClickListener
                    }
                }

                viewModel.transferBetweenBranches(
                    sourceInventoryItem = selectedSource,
                    destinationBranch = destination,
                    quantity = quantity,
                    note = dialogBinding.noteEditText.text.toString()
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun dropdownAdapter(options: List<String>): ArrayAdapter<String> =
        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)

    private fun InventoryItem.itemLabel(): String =
        listOf(name, sku.takeIf { it.isNotBlank() }?.let { "SKU $it" }, branchName.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(" - ")

    private fun transferDestinations(branches: List<Branch>, sourceItem: InventoryItem): List<Branch> =
        branches.filter { it.id.isNotBlank() && it.id != sourceItem.branchId }

    private fun StockMovementType.toDisplay(): String =
        name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }

    private enum class TransactionAction {
        STOCK_IN,
        STOCK_OUT,
        MOVE_STOCK,
        ADJUST_STOCK
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_OPEN_ACTION = "openAction"
        const val ACTION_STOCK_IN = "stockIn"
        const val ACTION_STOCK_OUT = "stockOut"
        const val ACTION_MOVE_STOCK = "moveStock"
        const val ACTION_ADJUST_STOCK = "adjustStock"
    }
}
