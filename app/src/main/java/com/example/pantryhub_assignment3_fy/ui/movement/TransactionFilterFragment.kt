package com.example.pantryhub_assignment3_fy.ui.movement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentTransactionFilterBinding
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.ui.common.FilterSelectorOptionUi
import com.example.pantryhub_assignment3_fy.ui.common.addInteractiveFilterRow
import com.example.pantryhub_assignment3_fy.ui.common.formatOptionalDateRange
import com.example.pantryhub_assignment3_fy.ui.common.showFilterSelectorBottomSheet
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Locale

class TransactionFilterFragment : Fragment() {
    private var _binding: FragmentTransactionFilterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StockMovementViewModel by activityViewModels()

    private var draftFilters = TransactionHistoryFilter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        draftFilters = viewModel.uiState.value?.appliedFilters ?: TransactionHistoryFilter()
        binding.closeButton.setOnClickListener { findNavController().navigateUp() }
        binding.clearButton.setOnClickListener {
            draftFilters = TransactionHistoryFilter()
            renderRows()
        }
        binding.applyButton.setOnClickListener {
            viewModel.applyTransactionFilters(draftFilters)
            findNavController().navigateUp()
        }
        renderRows()
    }

    private fun renderRows() {
        binding.filterRowsContainer.removeAllViews()
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_date),
            value = draftFilters.dateSelection.toDisplayText(),
            valueColorRes = if (draftFilters.dateSelection != null) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showDatePicker
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_item),
            value = draftFilters.itemLabel,
            valueColorRes = if (draftFilters.itemId.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showItemSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_member),
            value = draftFilters.memberName,
            valueColorRes = if (draftFilters.memberId.isNotBlank() || draftFilters.memberName.isNotBlank()) {
                R.color.inventory_primary
            } else {
                R.color.inventory_text_primary
            },
            onClick = ::showMemberSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_transaction),
            value = draftFilters.transactionType.displayLabel(),
            valueColorRes = TransactionTypeVisuals.colorFor(draftFilters.transactionType),
            onClick = ::showTransactionTypeSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_partner),
            value = draftFilters.partnerName,
            valueColorRes = if (draftFilters.partnerName.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showPartnerSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_location),
            value = draftFilters.locationName,
            valueColorRes = if (draftFilters.locationId.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showLocationSelector
        )
    }

    private fun showDatePicker() {
        val current = draftFilters.dateSelection
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.transaction_filter_date)
            .apply {
                if (current != null) {
                    setSelection(androidx.core.util.Pair(current.normalizedStartMillis, current.normalizedEndMillis))
                }
            }
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: start
            draftFilters = draftFilters.copy(
                dateSelection = TransactionDateSelection(startMillis = start, endMillis = end)
            )
            renderRows()
        }
        picker.addOnNegativeButtonClickListener {
            // Keep current selection when user dismisses without applying.
        }
        picker.show(parentFragmentManager, "transactionDateRange")
    }

    private fun showTransactionTypeSelector() {
        // "All" is a real filter value here, not a separate clear row, so the list has one clear
        // meaning for every selectable transaction type.
        val options = TransactionFilterType.values().map { type ->
            FilterSelectorOptionUi(
                id = type.name,
                title = type.displayLabel(),
                iconRes = TransactionTypeVisuals.iconForFilter(type)
            )
        }
        showFilterSelectorBottomSheet(
            title = getString(R.string.transaction_filter_transaction),
            searchHint = getString(R.string.search_transaction_type),
            options = options,
            selectedId = draftFilters.transactionType.name
        ) { option ->
            draftFilters = draftFilters.copy(
                transactionType = option?.id?.let(TransactionFilterType::valueOf) ?: TransactionFilterType.ALL
            )
            renderRows()
        }
    }

    private fun showItemSelector() {
        // Group movements by item ID first, then by exact item name for older records that may not
        // have an inventoryItemId saved yet.
        val itemSummaries = viewModel.uiState.value
            ?.movements
            .orEmpty()
            .groupBy { it.inventoryItemId.ifBlank { it.itemName.lowercase(Locale.getDefault()) } }
            .values
            .mapNotNull { movements ->
                val first = movements.firstOrNull() ?: return@mapNotNull null
                val count = movements.size
                FilterSelectorOptionUi(
                    id = first.inventoryItemId.ifBlank { first.itemName.trim() },
                    title = first.itemName.ifBlank { getString(R.string.not_added) },
                    subtitle = listOf(
                        first.sku.takeIf { it.isNotBlank() },
                        first.barcode.takeIf { it.isNotBlank() },
                        first.branchName.takeIf { it.isNotBlank() }
                    ).joinToString(" | "),
                    trailingText = count.toString(),
                    searchTokens = listOf(first.itemName, first.sku, first.barcode, first.branchName, first.note),
                    imageUrl = first.imageUrl,
                    iconRes = R.drawable.ic_storage
                )
            }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }

        showFilterSelectorBottomSheet(
            title = getString(R.string.select_item),
            searchHint = getString(R.string.search_item_filter_hint),
            options = itemSummaries,
            selectedId = draftFilters.itemId
        ) { option ->
            draftFilters = draftFilters.copy(
                itemId = option?.id.orEmpty(),
                itemLabel = option?.title.orEmpty()
            )
            renderRows()
        }
    }

    private fun showMemberSelector() {
        val options = uniqueMovementValues(
            titleProvider = { it.performedByName.ifBlank { getString(R.string.unknown_member) } },
            // Use the Firebase UID when available so a display-name change does not break history filtering.
            idProvider = { it.performedBy.ifBlank { it.performedByName.trim() } },
            subtitleProvider = { movement ->
                movement.movementType.toReadableType()
            },
            trailingProvider = { group -> group.size.toString() },
            searchTokensProvider = { movement -> listOf(movement.performedByName, movement.itemName, movement.branchName) },
            iconRes = R.drawable.ic_person
        )

        showFilterSelectorBottomSheet(
            title = getString(R.string.transaction_filter_member),
            searchHint = getString(R.string.search_member_name),
            options = options,
            selectedId = draftFilters.memberId.ifBlank { draftFilters.memberName }
        ) { option ->
            draftFilters = draftFilters.copy(
                memberId = option?.id.orEmpty(),
                memberName = option?.title.orEmpty()
            )
            renderRows()
        }
    }

    private fun showPartnerSelector() {
        val options = uniqueMovementValues(
            titleProvider = { it.counterpartyName.ifBlank { getString(R.string.none) } },
            idProvider = { it.counterpartyName.trim() },
            subtitleProvider = { movement ->
                movement.counterpartyType.ifBlank { movement.movementType.toReadableType() }
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            },
            trailingProvider = { group -> group.size.toString() },
            searchTokensProvider = { movement -> listOf(movement.counterpartyName, movement.itemName, movement.note) },
            iconRes = R.drawable.ic_restock,
            excludeBlankIds = true
        )

        showFilterSelectorBottomSheet(
            title = getString(R.string.partner),
            searchHint = getString(R.string.search_partner),
            options = options,
            selectedId = draftFilters.partnerName
        ) { option ->
            draftFilters = draftFilters.copy(partnerName = option?.id.orEmpty())
            renderRows()
        }
    }

    private fun showLocationSelector() {
        // Location filter only shows real locations that appear in transaction history.
        val options = viewModel.uiState.value
            ?.movements
            ?.groupBy { it.branchId.ifBlank { it.branchName.trim() } }
            ?.values
            ?.mapNotNull { movements ->
                val first = movements.firstOrNull() ?: return@mapNotNull null
                val id = first.branchId.ifBlank { first.branchName.trim() }
                if (id.isBlank()) return@mapNotNull null
                FilterSelectorOptionUi(
                    id = id,
                    title = first.branchName.ifBlank { getString(R.string.unassigned_branch) },
                    subtitle = resources.getQuantityString(R.plurals.location_item_count, movements.size, movements.size),
                    searchTokens = listOf(first.branchName, first.itemName, first.note),
                    iconRes = R.drawable.ic_store
                )
            }
            ?.sortedBy { it.title.lowercase(Locale.getDefault()) }
            .orEmpty()

        showFilterSelectorBottomSheet(
            title = getString(R.string.location),
            searchHint = getString(R.string.search_location_name),
            options = options,
            selectedId = draftFilters.locationId
        ) { option ->
            draftFilters = draftFilters.copy(
                locationId = option?.id.orEmpty(),
                locationName = option?.title.orEmpty()
            )
            renderRows()
        }
    }

    private fun uniqueMovementValues(
        titleProvider: (StockMovement) -> String,
        idProvider: (StockMovement) -> String,
        subtitleProvider: (StockMovement) -> String,
        trailingProvider: (List<StockMovement>) -> String,
        searchTokensProvider: (StockMovement) -> List<String>,
        iconRes: Int,
        excludeBlankIds: Boolean = false
    ): List<FilterSelectorOptionUi> {
        val movements = viewModel.uiState.value?.movements.orEmpty()
        // One option can represent many movement records, so trailing text shows how many records
        // contributed to that member/partner choice.
        return movements
            .groupBy { idProvider(it).trim() }
            .entries
            .mapNotNull { (id, group) ->
                if (excludeBlankIds && id.isBlank()) return@mapNotNull null
                val first = group.firstOrNull() ?: return@mapNotNull null
                FilterSelectorOptionUi(
                    id = id,
                    title = titleProvider(first),
                    subtitle = subtitleProvider(first),
                    trailingText = trailingProvider(group),
                    searchTokens = group.flatMap(searchTokensProvider),
                    iconRes = iconRes
                )
            }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    private fun TransactionDateSelection?.toDisplayText(): String {
        return formatOptionalDateRange(this?.normalizedStartMillis, this?.normalizedEndMillis)
    }

    private fun TransactionFilterType.displayLabel(): String = when (this) {
        TransactionFilterType.ALL -> getString(R.string.all_transactions)
        TransactionFilterType.STOCK_IN -> getString(R.string.stock_in)
        TransactionFilterType.STOCK_OUT -> getString(R.string.stock_out)
        TransactionFilterType.MOVE_STOCK -> getString(R.string.move_stock)
        TransactionFilterType.ADJUST_STOCK -> getString(R.string.adjust_stock)
    }

    private fun String.toReadableType(): String = when (this) {
        "STOCK_IN" -> getString(R.string.stock_in)
        "STOCK_OUT" -> getString(R.string.stock_out)
        "RESTOCK_RECEIVED" -> getString(R.string.restock_received)
        "SALES_DEDUCTION" -> getString(R.string.sales_deduction)
        "BRANCH_TRANSFER_IN", "BRANCH_TRANSFER_OUT" -> getString(R.string.move_stock)
        "ADJUST_STOCK" -> getString(R.string.adjust_stock)
        else -> lowercase(Locale.getDefault()).replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
