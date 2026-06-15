package com.example.pantryhub_assignment3_fy.ui.storage

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentInventoryBinding
import com.example.pantryhub_assignment3_fy.ui.common.RadioSheetOption
import com.example.pantryhub_assignment3_fy.ui.common.ToolbarActionHost
import com.example.pantryhub_assignment3_fy.ui.common.showRadioSheet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar

/**
 * Displays inventory items and entry points for stock CRUD actions.
 */
class InventoryFragment : Fragment(), ToolbarActionHost {
    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private lateinit var adapter: InventoryAdapter
    private lateinit var groupSummaryAdapter: InventoryGroupSummaryAdapter
    private var pendingCsvExportContent: String? = null
    private var initialActionConsumed = false
    private var lastSortOption: SortOption? = null
    private var lastGroupSortOption: GroupSortOption? = null
    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val csv = runCatching {
            requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }.getOrNull()
        if (csv.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.csv_import_failed, Snackbar.LENGTH_LONG).show()
        } else {
            viewModel.importInventoryCsv(requireContext().applicationContext, csv)
        }
    }
    private val importSalesCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val csv = runCatching {
            requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }.getOrNull()
        if (csv.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.sales_csv_import_failed, Snackbar.LENGTH_LONG).show()
        } else {
            viewModel.importSalesCsv(csv)
        }
    }
    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) {
            pendingCsvExportContent = null
            return@registerForActivityResult
        }
        val csv = pendingCsvExportContent ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open export file.")
        }
            .onSuccess { Snackbar.make(binding.root, R.string.csv_export_saved, Snackbar.LENGTH_SHORT).show() }
            .onFailure { Snackbar.make(binding.root, R.string.csv_export_failed, Snackbar.LENGTH_LONG).show() }
        pendingCsvExportContent = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupSearch()
        setupHeaderControls()
        applyInitialStatusFilter()
        binding.addInventoryItemFab.setOnClickListener {
            findNavController().navigate(
                R.id.action_inventoryFragment_to_addEditInventoryItemFragment,
                Bundle().apply {
                    putString(AddEditInventoryItemFragment.ARG_MODE, AddEditInventoryItemFragment.MODE_ADD)
                }
            )
        }
        binding.groupButton.setOnClickListener { showGroupSheet() }
        binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleInStockOnly(isChecked)
        }
        binding.sortButton.setOnClickListener { showSortSheet() }
        openInitialActionIfNeeded()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val isLowStockFilter = state.selectedStatus == FilterOptions.LOW_STOCK_STATUS
            val shouldScrollToTopForSort = when (state.groupOption) {
                GroupOption.NONE -> lastSortOption != null && lastSortOption != state.sortOption
                else -> lastGroupSortOption != null && lastGroupSortOption != state.groupSortOption
            }
            binding.loadingIndicator.isVisible = state.isLoading
            binding.needToBuyHeaderTextView.isVisible = isLowStockFilter
            renderLocationOptions(state)
            val isGrouped = state.groupOption != GroupOption.NONE
            val displayedAdapter = if (isGrouped) groupSummaryAdapter else adapter
            if (binding.inventoryRecyclerView.adapter !== displayedAdapter) {
                binding.inventoryRecyclerView.adapter = displayedAdapter
            }
            binding.groupButton.text = state.groupOption.label
            binding.inStockChip.setOnCheckedChangeListener(null)
            binding.inStockChip.isChecked = state.selectedStatus == FilterOptions.IN_STOCK_STATUS
            binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleInStockOnly(isChecked)
            }
            binding.emptyTextView.text = if (isLowStockFilter) {
                getString(R.string.empty_low_stock_need_to_buy)
            } else {
                getString(R.string.empty_items_filtered)
            }
            val isInventoryEmpty = if (isGrouped) state.groupSummaries.isEmpty() else state.visibleInventoryRows.isEmpty()
            binding.emptyTextView.isVisible = !state.isLoading && isInventoryEmpty
            if (isGrouped) {
                groupSummaryAdapter.submitList(state.groupSummaries) {
                    if (shouldScrollToTopForSort) binding.inventoryRecyclerView.scrollToPosition(0)
                }
            } else {
                adapter.submitList(state.visibleInventoryRows) {
                    if (shouldScrollToTopForSort) binding.inventoryRecyclerView.scrollToPosition(0)
                }
            }
            lastSortOption = state.sortOption
            lastGroupSortOption = state.groupSortOption
            viewModel.scheduleLoadedInventoryReminders(requireContext().applicationContext)
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
            state.csvExportContent?.let {
                // Export uses Android's document picker so the user chooses a safe save location.
                pendingCsvExportContent = it
                exportCsvLauncher.launch(getString(R.string.inventory_csv_filename))
                viewModel.clearCsvExportContent()
            }
            if (state.csvSummaryTitle != null && state.csvSummaryMessage != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(state.csvSummaryTitle)
                    .setMessage(state.csvSummaryMessage)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                viewModel.clearCsvSummary()
            }
        }
    }

    private fun openCsvImportPicker() {
        // Import uses OpenDocument so Android grants temporary read access to the selected CSV.
        importCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
    }

    private fun openSalesCsvImportPicker() {
        // Sales CSV import only reads the picked file; all deduction logic stays in the ViewModel/repository.
        importSalesCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
    }

    private fun setupHeaderControls() {
        binding.locationSelector.setOnClickListener { showLocationSheet() }
        binding.searchLayout.setEndIconOnClickListener {
            findNavController().navigate(R.id.barcodeScannerPrototypeFragment)
        }
        observeBarcodeScannerResult()
    }

    private fun observeBarcodeScannerResult() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
            ?.observe(viewLifecycleOwner) { barcode ->
                findNavController().currentBackStackEntry?.savedStateHandle
                    ?.remove<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
                val value = barcode.trim()
                binding.searchEditText.setText(value)
                binding.searchEditText.setSelection(value.length)
                viewModel.search(value)
            }
    }

    private fun renderLocationOptions(state: InventoryUiState) {
        binding.locationSelector.text = state.selectedBranch
    }

    private fun showCsvOverflowMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, MENU_IMPORT_CSV, 0, R.string.import_csv)
            menu.add(0, MENU_EXPORT_CSV, 1, R.string.export_csv)
            menu.add(0, MENU_IMPORT_SALES_CSV, 2, R.string.import_sales_csv)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_IMPORT_CSV -> openCsvImportPicker()
                    MENU_EXPORT_CSV -> viewModel.prepareInventoryCsvExport()
                    MENU_IMPORT_SALES_CSV -> openSalesCsvImportPicker()
                }
                true
            }
        }.show()
    }

    override fun onToolbarActionClick(anchor: View) {
        showCsvOverflowMenu(anchor)
    }

    override fun onSecondaryToolbarActionClick(anchor: View) {
        findNavController().navigate(R.id.action_inventoryFragment_to_archivedInventoryFragment)
    }

    private fun openInitialActionIfNeeded() {
        if (initialActionConsumed || arguments?.getBoolean(ARG_OPEN_SALES_IMPORT) != true) return
        initialActionConsumed = true
        arguments?.remove(ARG_OPEN_SALES_IMPORT)
        openSalesCsvImportPicker()
    }

    /**
     * Configures item cards. Swipe reveal lives inside InventoryAdapter so RecyclerView
     * does not reset the row when the user releases their finger.
     */
    private fun setupRecyclerView() {
        adapter = InventoryAdapter(
            onClick = ::handleInventoryRowClick,
            onEdit = ::navigateToEditInventoryItem,
            onDelete = ::confirmDelete
        )
        groupSummaryAdapter = InventoryGroupSummaryAdapter(::openGroupDetail)
        binding.inventoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.inventoryRecyclerView.adapter = adapter
    }

    private fun handleInventoryRowClick(row: InventoryDisplayRow) {
        val realId = row.realInventoryItemId
        when {
            realId != null -> navigateToInventoryItemDetail(realId)
            else -> navigateToInventoryItemDetail(row.matchingRecords.firstOrNull()?.id.orEmpty())
        }
    }

    private fun openGroupDetail(group: GroupSummaryUiModel) {
        val state = viewModel.uiState.value ?: InventoryUiState()
        findNavController().navigate(
            R.id.inventoryGroupDetailFragment,
            Bundle().apply {
                putString(InventoryGroupDetailFragment.ARG_GROUP_OPTION, state.groupOption.name)
                putString(InventoryGroupDetailFragment.ARG_GROUP_KEY, group.groupKey)
                putString(InventoryGroupDetailFragment.ARG_GROUP_TITLE, group.displayName)
                putString(InventoryGroupDetailFragment.ARG_INITIAL_SEARCH, state.searchQuery)
            }
        )
    }

    private fun navigateToInventoryItemDetail(inventoryItemId: String) {
        if (inventoryItemId.isBlank()) {
            Snackbar.make(binding.root, R.string.could_not_open_item, Snackbar.LENGTH_SHORT).show()
            return
        }
        val state = viewModel.uiState.value ?: InventoryUiState()
        val selectedBranchId = state.branches
            .firstOrNull { state.selectedBranch != FilterOptions.ALL_BRANCH && it.name == state.selectedBranch }
            ?.id
            .orEmpty()
        findNavController().navigate(
            R.id.action_inventoryFragment_to_inventoryItemDetailFragment,
            Bundle().apply {
                putString(InventoryItemDetailFragment.ARG_INVENTORY_ITEM_ID, inventoryItemId)
                putString(InventoryItemDetailFragment.ARG_SUMMARY_BRANCH_ID, selectedBranchId)
            }
        )
    }

    private fun navigateToEditInventoryItem(row: InventoryDisplayRow) {
        val id = row.realInventoryItemId
        if (id == null) {
            Snackbar.make(binding.root, R.string.could_not_open_item, Snackbar.LENGTH_SHORT).show()
            return
        }
        findNavController().navigate(
            R.id.action_inventoryFragment_to_addEditInventoryItemFragment,
            Bundle().apply {
                putString(AddEditInventoryItemFragment.ARG_MODE, AddEditInventoryItemFragment.MODE_EDIT)
                putString(AddEditInventoryItemFragment.ARG_INVENTORY_ITEM_ID, id)
            }
        )
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    /**
     * Applies an optional status filter passed from Home status cards.
     */
    private fun applyInitialStatusFilter() {
        val statusFilter = arguments?.getString(ARG_STATUS_FILTER).orEmpty()
        if (statusFilter.isBlank()) return

        // Home status cards open Storage with the matching filter already applied.
        val current = viewModel.uiState.value ?: InventoryUiState()
        viewModel.filter(current.selectedLocation, current.selectedCategory, statusFilter)
    }

    private fun showLocationSheet() {
        val current = viewModel.uiState.value ?: InventoryUiState()
        val rows = listOf(
            RadioSheetOption(
                key = FilterOptions.ALL_BRANCH,
                title = FilterOptions.ALL_BRANCH,
                subtitle = current.inventoryItems.sumOf { it.quantity }.toStorageQuantityText()
            )
        ) + current.branches.map { branch ->
            RadioSheetOption(
                key = branch.name,
                title = branch.name,
                subtitle = current.inventoryItems.filter { it.branchId == branch.id || it.branchName == branch.name }.sumOf { it.quantity }.toStorageQuantityText()
            )
        }
        showRadioSheet(getString(R.string.select_location), rows, current.selectedBranch) { selected ->
            viewModel.selectBranch(selected)
        }
    }

    private fun showGroupSheet() {
        val options = GroupOption.entries.map { RadioSheetOption(it.name, it.label) }
        showRadioSheet(getString(R.string.group_by), options, (viewModel.uiState.value ?: InventoryUiState()).groupOption.name) { selected ->
            viewModel.group(GroupOption.valueOf(selected))
        }
    }

    /**
     * Opens the Storage sort sheet.
     */
    private fun showSortSheet() {
        val current = viewModel.uiState.value ?: InventoryUiState()
        if (current.groupOption != GroupOption.NONE) {
            val options = GroupSortOption.entries.map { RadioSheetOption(it.name, it.label) }
            showRadioSheet(getString(R.string.sort_items), options, current.groupSortOption.name) { selected ->
                viewModel.sortGroups(GroupSortOption.valueOf(selected))
            }
            return
        }
        val options = listOf(
            SortOption.NAME_ASC,
            SortOption.NAME_DESC,
            SortOption.QUANTITY_HIGH,
            SortOption.QUANTITY_LOW,
            SortOption.SAFETY_STOCK_LOW,
            SortOption.RESTOCK_URGENCY,
            SortOption.EXPIRY_SOONEST,
            SortOption.RECENTLY_UPDATED
        ).map { RadioSheetOption(it.name, it.label) }
        showRadioSheet(getString(R.string.sort_items), options, current.sortOption.name) { selected ->
            viewModel.sort(SortOption.valueOf(selected))
        }
    }

    private fun confirmDelete(row: InventoryDisplayRow) {
        val inventoryItem = row.realInventoryItemId?.let(viewModel::findInventoryItem)
        if (inventoryItem == null) {
            Snackbar.make(binding.root, R.string.could_not_open_item, Snackbar.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.archive))
            .setMessage(
                getString(
                    R.string.archive_inventory_item_message,
                    inventoryItem.name,
                    inventoryItem.branchName.ifBlank { getString(R.string.unassigned_branch) }
                )
            )
            .setPositiveButton(getString(R.string.archive)) { _, _ -> viewModel.archiveInventoryItem(requireContext().applicationContext, inventoryItem.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ChipGroup.checkedChipText(): String {
        return findViewById<Chip>(checkedChipId)?.text?.toString().orEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_STATUS_FILTER = "statusFilter"
        const val ARG_OPEN_SALES_IMPORT = "openSalesImport"
        private const val MENU_IMPORT_CSV = 1
        private const val MENU_EXPORT_CSV = 2
        private const val MENU_IMPORT_SALES_CSV = 3
    }
}
