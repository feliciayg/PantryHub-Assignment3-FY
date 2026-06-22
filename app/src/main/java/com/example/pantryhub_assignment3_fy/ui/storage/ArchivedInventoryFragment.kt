package com.example.pantryhub_assignment3_fy.ui.storage

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentArchivedInventoryBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.RadioSheetOption
import com.example.pantryhub_assignment3_fy.ui.common.showRadioSheet
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import kotlin.math.roundToInt

/**
 * Dedicated page for archived items so the main Items screen can stay focused on active stock.
 */
class ArchivedInventoryFragment : Fragment() {
    private var _binding: FragmentArchivedInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private lateinit var adapter: InventoryAdapter
    private lateinit var groupSummaryAdapter: InventoryGroupSummaryAdapter
    private var selectedBranch: String = FilterOptions.ALL_BRANCH
    private var groupOption: GroupOption = GroupOption.NONE
    private var sortOption: SortOption = SortOption.NAME_ASC
    private var groupSortOption: GroupSortOption = GroupSortOption.VALUE_ASC

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchivedInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = InventoryAdapter(
            onClick = ::openItem,
            onEdit = {},
            onDelete = {}
        )
        groupSummaryAdapter = InventoryGroupSummaryAdapter {
            // Archived grouping is a read-only summary on this page; tapping cards intentionally does not
            // navigate into the normal active item group flow.
        }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
        binding.toolbar.title = getString(R.string.archived_items)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.locationSelector.setOnClickListener { showLocationSheet() }
        binding.groupButton.setOnClickListener { showGroupSheet() }
        binding.sortButton.setOnClickListener { showSortSheet() }
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewModel.uiState.observe(viewLifecycleOwner) { render() }
    }

    private fun render() {
        if (_binding == null) return
        val state = viewModel.uiState.value ?: return
        val query = binding.searchEditText.text?.toString().orEmpty().trim()
        binding.locationSelector.text = selectedBranch
        binding.groupButton.text = getString(groupOption.labelRes)

        val rows = buildArchivedRows(state, query)
        val isGrouped = groupOption != GroupOption.NONE
        val displayedAdapter = if (isGrouped) groupSummaryAdapter else adapter
        if (binding.itemsRecyclerView.adapter !== displayedAdapter) {
            binding.itemsRecyclerView.adapter = displayedAdapter
        }

        if (isGrouped) {
            val groups = buildArchivedGroups(rows)
            groupSummaryAdapter.submitList(groups)
            binding.emptyTextView.isVisible = groups.isEmpty()
        } else {
            adapter.submitList(rows)
            binding.emptyTextView.isVisible = rows.isEmpty()
        }
    }

    private fun buildArchivedRows(state: InventoryUiState, query: String): List<InventoryDisplayRow> {
        val selectedBranchModel = state.branches.firstOrNull { it.name == selectedBranch }
        val archivedItems = state.inventoryItems
            .filter { it.isArchived }
            .filter {
                selectedBranch == FilterOptions.ALL_BRANCH ||
                    it.branchId == selectedBranchModel?.id ||
                    it.branchName == selectedBranch
            }
            .filter { query.isBlank() || it.matchesArchivedSearch(query) }
            .sortedWith(sortComparator(sortOption))

        val productRecords = if (selectedBranch == FilterOptions.ALL_BRANCH) {
            archivedItems.groupArchivedProductRecords()
        } else {
            archivedItems.map { listOf(it) }
        }
        return productRecords
            .filter { it.isNotEmpty() }
            .map { records ->
                val representative = records.archivedRepresentativeProduct()
                InventoryDisplayRow(
                    id = "archived:${representative.id}",
                    representativeItem = representative.copy(quantity = records.sumOf { it.quantity }),
                    realInventoryItemId = records.firstOrNull()?.id,
                    matchingRecords = records,
                    quantity = records.sumOf { it.quantity },
                    branchId = representative.branchId,
                    branchName = representative.branchName,
                    isAggregate = records.size > 1
                )
            }
    }

    private fun showLocationSheet() {
        val state = viewModel.uiState.value ?: InventoryUiState()
        val rows = listOf(
            RadioSheetOption(
                key = FilterOptions.ALL_BRANCH,
                title = getString(R.string.all_locations)
            )
        ) + state.branches.map { branch ->
            RadioSheetOption(
                key = branch.name,
                title = branch.name
            )
        }
        showRadioSheet(getString(R.string.select_location), rows, selectedBranch) { selected ->
            selectedBranch = selected
            render()
        }
    }

    private fun showGroupSheet() {
        val options = GroupOption.entries.map { RadioSheetOption(it.name, getString(it.labelRes)) }
        showRadioSheet(getString(R.string.group_by), options, groupOption.name) { selected ->
            groupOption = GroupOption.valueOf(selected)
            render()
        }
    }

    private fun showSortSheet() {
        if (groupOption != GroupOption.NONE) {
            val options = GroupSortOption.entries.map { RadioSheetOption(it.name, getString(it.labelRes)) }
            showRadioSheet(getString(R.string.sort_items), options, groupSortOption.name) { selected ->
                groupSortOption = GroupSortOption.valueOf(selected)
                render()
            }
            return
        }
        val options = listOf(
            SortOption.NAME_ASC,
            SortOption.NAME_DESC,
            SortOption.QUANTITY_HIGH,
            SortOption.QUANTITY_LOW,
            SortOption.SAFETY_STOCK_LOW,
            SortOption.EXPIRY_SOONEST,
            SortOption.RECENTLY_UPDATED
        ).map { RadioSheetOption(it.name, getString(it.labelRes)) }
        showRadioSheet(getString(R.string.sort_items), options, sortOption.name) { selected ->
            sortOption = SortOption.valueOf(selected)
            render()
        }
    }

    private fun sortComparator(sort: SortOption): Comparator<InventoryItem> = when (sort) {
        SortOption.NAME_ASC -> compareBy { it.name.lowercase() }
        SortOption.NAME_DESC -> compareByDescending { it.name.lowercase() }
        SortOption.QUANTITY_HIGH -> compareByDescending { it.quantity }
        SortOption.QUANTITY_LOW -> compareBy { it.quantity }
        SortOption.SAFETY_STOCK_LOW -> compareBy { it.reorderThreshold }
        SortOption.EXPIRY_SOONEST -> compareBy { it.expiryDate.takeIf { value -> value > 0L } ?: Long.MAX_VALUE }
        SortOption.RECENTLY_UPDATED -> compareByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.createdAt }
        else -> compareBy { it.name.lowercase() }
    }

    private fun buildArchivedGroups(rows: List<InventoryDisplayRow>): List<GroupSummaryUiModel> {
        val contributions = rows.flatMap { it.groupContributions(groupOption) }
        val grouped = contributions
            .groupBy { it.key }
            .map { (key, values) ->
                GroupSummaryUiModel(
                    groupKey = key,
                    displayName = values.first().displayName,
                    itemCount = values.distinctBy { it.row.id }.size,
                    totalQuantity = values.sumOf { it.row.quantity },
                    percentage = 0,
                    previewImageUrls = values.map { it.row.imageUrl }.distinct().take(3),
                    groupedItems = values.map { it.row }
                )
            }
        val totalQuantity = grouped.sumOf { it.totalQuantity }
        val withPercentages = grouped.map { group ->
            group.copy(
                percentage = if (totalQuantity <= 0.0) 0 else ((group.totalQuantity / totalQuantity) * 100.0).roundToInt()
            )
        }
        return when (groupSortOption) {
            GroupSortOption.VALUE_ASC -> withPercentages.sortedBy { it.displayName.lowercase() }
            GroupSortOption.VALUE_DESC -> withPercentages.sortedByDescending { it.displayName.lowercase() }
            GroupSortOption.QUANTITY_HIGH -> withPercentages.sortedByDescending { it.totalQuantity }
            GroupSortOption.QUANTITY_LOW -> withPercentages.sortedBy { it.totalQuantity }
            GroupSortOption.ITEM_COUNT_HIGH -> withPercentages.sortedByDescending { it.itemCount }
            GroupSortOption.ITEM_COUNT_LOW -> withPercentages.sortedBy { it.itemCount }
        }
    }

    private fun InventoryDisplayRow.groupContributions(option: GroupOption): List<ArchivedGroupContribution> {
        val displayName = when (option) {
            GroupOption.NONE -> ""
            GroupOption.NAME -> name.trim().ifBlank { "Unnamed Item" }
            GroupOption.COST -> representativeItem.costPrice.takeIf { it > 0.0 }?.toStorageMoneyText() ?: "Not Set"
            GroupOption.PRICE -> representativeItem.sellingPrice.takeIf { it > 0.0 }?.toStorageMoneyText() ?: "Not Set"
            GroupOption.CATEGORY -> category.trim().ifBlank { "Uncategorized" }
            GroupOption.BRAND -> brand.trim().ifBlank { "No Brand" }
            GroupOption.SAFETY_STOCK -> StockLevelRules.effectiveReorderPoint(representativeItem)
                .takeIf { it > 0.0 }
                ?.toStorageQuantityText()
                ?: "Not Set"
            GroupOption.EXPIRY_DATE -> expiryDate.takeIf { it > 0L }?.let(DateUtils::formatDisplayDate) ?: "No Expiry Date"
        }
        val key = if (option == GroupOption.NAME) displayName.lowercase() else displayName
        return listOf(ArchivedGroupContribution(key, displayName, this))
    }

    private fun openItem(row: InventoryDisplayRow) {
        val id = row.realInventoryItemId ?: row.matchingRecords.firstOrNull()?.id.orEmpty()
        if (id.isBlank()) return
        findNavController().navigate(
            R.id.inventoryItemDetailFragment,
            Bundle().apply {
                putString(InventoryItemDetailFragment.ARG_INVENTORY_ITEM_ID, id)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun InventoryItem.matchesArchivedSearch(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            sku.contains(query, ignoreCase = true) ||
            barcode.contains(query, ignoreCase = true) ||
            brand.contains(query, ignoreCase = true)

    private fun List<InventoryItem>.archivedRepresentativeProduct(): InventoryItem =
        maxByOrNull { it.updatedAt.takeIf { timestamp -> timestamp > 0L } ?: it.createdAt } ?: first()

    private fun List<InventoryItem>.groupArchivedProductRecords(): List<List<InventoryItem>> {
        val groups = mutableListOf<MutableList<InventoryItem>>()
        forEach { item ->
            val existing = groups.firstOrNull { records ->
                records.firstOrNull()?.let { ProductIdentity.sameProduct(it, item) } == true
            }
            if (existing == null) groups += mutableListOf(item) else existing += item
        }
        return groups
    }

    private data class ArchivedGroupContribution(
        val key: String,
        val displayName: String,
        val row: InventoryDisplayRow
    )
}
