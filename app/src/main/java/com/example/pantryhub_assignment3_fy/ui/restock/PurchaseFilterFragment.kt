package com.example.pantryhub_assignment3_fy.ui.restock

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetFilterSelectorBinding
import com.example.pantryhub_assignment3_fy.databinding.DialogEnterSkuBinding
import com.example.pantryhub_assignment3_fy.databinding.FragmentTransactionFilterBinding
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.ui.common.FilterSelectorOptionUi
import com.example.pantryhub_assignment3_fy.ui.common.addInteractiveFilterRow
import com.example.pantryhub_assignment3_fy.ui.common.formatOptionalDateRange
import com.example.pantryhub_assignment3_fy.ui.common.showFilterSelectorBottomSheet
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class PurchaseFilterFragment : Fragment() {
    private var _binding: FragmentTransactionFilterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RestockOrdersViewModel by activityViewModels()

    private var draftFilters = PurchaseHistoryFilter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        draftFilters = viewModel.uiState.value?.appliedFilters ?: PurchaseHistoryFilter()
        binding.closeButton.setOnClickListener { findNavController().navigateUp() }
        binding.clearButton.setOnClickListener {
            draftFilters = PurchaseHistoryFilter()
            renderRows()
        }
        binding.applyButton.setOnClickListener {
            viewModel.applyPurchaseFilters(draftFilters)
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
            label = getString(R.string.purchase_filter_order_number),
            value = draftFilters.orderNumberQuery,
            valueColorRes = if (draftFilters.orderNumberQuery.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showOrderNumberDialog
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_item),
            value = draftFilters.itemLabel,
            valueColorRes = if (draftFilters.itemKey.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showItemSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_member),
            value = draftFilters.memberName,
            valueColorRes = if (draftFilters.memberName.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showMemberSelector
        )
        addInteractiveFilterRow(
            container = binding.filterRowsContainer,
            label = getString(R.string.transaction_filter_partner),
            value = draftFilters.partnerName,
            valueColorRes = if (draftFilters.partnerName.isNotBlank()) R.color.inventory_primary else R.color.inventory_text_primary,
            onClick = ::showPartnerSelector
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
            draftFilters = draftFilters.copy(dateSelection = PurchaseDateSelection(start, end))
            renderRows()
        }
        picker.show(parentFragmentManager, "purchaseDateRange")
    }

    private fun showOrderNumberDialog() {
        val dialogBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        dialogBinding.skuInputHelperTextView.text = getString(R.string.purchase_filter_order_number_helper)
        dialogBinding.skuInputLayout.hint = getString(R.string.purchase_filter_order_number)
        dialogBinding.skuInputEditText.inputType = InputType.TYPE_CLASS_TEXT
        dialogBinding.skuInputEditText.setText(draftFilters.orderNumberQuery)
        dialogBinding.skuInputEditText.setSelection(dialogBinding.skuInputEditText.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.purchase_filter_order_number)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                draftFilters = draftFilters.copy(
                    orderNumberQuery = dialogBinding.skuInputEditText.text?.toString().orEmpty().trim()
                )
                renderRows()
                dialog.dismiss()
            }
            dialogBinding.skuInputEditText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun showItemSelector() {
        // Purchase filters use the same product identity fallback as older purchase rows: ID first,
        // then exact item/SKU/barcode text when an ID was not stored.
        val options = viewModel.uiState.value
            ?.restockOrders
            .orEmpty()
            .flatMap { order -> order.purchaseItems.map { item -> order to item } }
            .groupBy { (_, item) -> item.filterKey() }
            .values
            .mapNotNull { entries ->
                val item = entries.firstOrNull()?.second ?: return@mapNotNull null
                FilterSelectorOptionUi(
                    id = item.filterKey(),
                    title = item.itemName.ifBlank { getString(R.string.not_added) },
                    subtitle = listOf(
                        item.category.takeIf { it.isNotBlank() },
                        item.brand.takeIf { it.isNotBlank() },
                        item.sku.takeIf { it.isNotBlank() }
                    ).joinToString(" | "),
                    trailingText = entries.size.toString(),
                    searchTokens = entries.flatMap { (_, purchaseItem) ->
                        listOf(
                            purchaseItem.itemName,
                            purchaseItem.sku,
                            purchaseItem.barcode,
                            purchaseItem.brand,
                            purchaseItem.category
                        )
                    },
                    imageUrl = item.imageUrl,
                    iconRes = R.drawable.ic_storage
                )
            }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }

        showFilterSelectorBottomSheet(
            title = getString(R.string.select_item),
            searchHint = getString(R.string.search_item_filter_hint),
            options = options,
            selectedId = draftFilters.itemKey
        ) { option ->
            draftFilters = draftFilters.copy(
                itemKey = option?.id.orEmpty(),
                itemLabel = option?.title.orEmpty()
            )
            renderRows()
        }
    }

    private fun showMemberSelector() {
        val options = groupedOptions(
            idProvider = { order -> order.createdByName.ifBlank { order.createdBy }.trim() },
            titleProvider = { order -> order.createdByName.ifBlank { order.createdBy.ifBlank { getString(R.string.unknown_member) } } },
            subtitleProvider = { order -> order.fallbackOrderLabel },
            trailingProvider = { group -> group.size.toString() },
            searchTokensProvider = { order -> listOf(order.createdByName, order.createdBy, order.fallbackOrderLabel, order.supplierName) },
            iconRes = R.drawable.ic_person
        )

        showFilterSelectorBottomSheet(
            title = getString(R.string.transaction_filter_member),
            searchHint = getString(R.string.search_member_name),
            options = options,
            selectedId = draftFilters.memberName
        ) { option ->
            draftFilters = draftFilters.copy(memberName = option?.id.orEmpty())
            renderRows()
        }
    }

    private fun showPartnerSelector() {
        val options = groupedOptions(
            idProvider = { order -> order.supplierName.trim() },
            titleProvider = { order -> order.supplierName.ifBlank { getString(R.string.none) } },
            subtitleProvider = { order -> order.receivingLocationName.ifBlank { order.fallbackOrderLabel } },
            trailingProvider = { group -> group.size.toString() },
            searchTokensProvider = { order -> listOf(order.supplierName, order.receivingLocationName, order.fallbackOrderLabel) },
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

    private fun groupedOptions(
        idProvider: (RestockOrder) -> String,
        titleProvider: (RestockOrder) -> String,
        subtitleProvider: (RestockOrder) -> String,
        trailingProvider: (List<RestockOrder>) -> String,
        searchTokensProvider: (RestockOrder) -> List<String>,
        iconRes: Int,
        excludeBlankIds: Boolean = false
    ): List<FilterSelectorOptionUi> {
        // Grouping keeps repeated suppliers/members from appearing multiple times in the selector.
        return viewModel.uiState.value
            ?.restockOrders
            .orEmpty()
            .groupBy { idProvider(it) }
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

    private fun PurchaseDateSelection?.toDisplayText(): String {
        return formatOptionalDateRange(this?.normalizedStartMillis, this?.normalizedEndMillis)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun PurchaseOrderItem.filterKey(): String =
    inventoryItemId.ifBlank { listOf(itemName.trim(), sku.trim(), barcode.trim()).joinToString("|") }
