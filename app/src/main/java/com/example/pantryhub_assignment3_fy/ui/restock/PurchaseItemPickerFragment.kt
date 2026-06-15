package com.example.pantryhub_assignment3_fy.ui.restock

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
import com.example.pantryhub_assignment3_fy.databinding.FragmentPurchaseItemPickerBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.RadioSheetOption
import com.example.pantryhub_assignment3_fy.ui.common.showRadioSheet
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog
import com.example.pantryhub_assignment3_fy.ui.storage.SortOption

class PurchaseItemPickerFragment : Fragment() {
    private var _binding: FragmentPurchaseItemPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PurchaseEditorViewModel by activityViewModels()
    private lateinit var adapter: PurchaseItemPickerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchaseItemPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PurchaseItemPickerAdapter { item -> showQuantityDialog(item) }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPickerInStockOnly(isChecked)
            renderRows()
        }
        binding.sortButton.setOnClickListener { showSortDialog() }
        binding.searchEditText.doAfterTextChanged {
            viewModel.setPickerQuery(it?.toString().orEmpty())
            renderRows()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (binding.inStockChip.isChecked != state.pickerInStockOnly) {
                binding.inStockChip.setOnCheckedChangeListener(null)
                binding.inStockChip.isChecked = state.pickerInStockOnly
                binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setPickerInStockOnly(isChecked)
                    renderRows()
                }
            }
            renderRows()
        }
    }

    private fun renderRows() {
        val items = viewModel.pickerItems()
        adapter.submitList(items)
        binding.emptyTextView.isVisible = items.isEmpty()
        binding.itemsRecyclerView.isVisible = items.isNotEmpty()
    }

    private fun showQuantityDialog(item: InventoryItem) {
        val currentQuantity = viewModel.uiState.value?.form?.items
            ?.firstOrNull { it.inventoryItemId == item.id }
            ?.orderedQuantity
            ?: 1.0
        showQuantityStepperDialog(
            config = QuantityStepperConfig(
                title = getString(R.string.enter_quantity),
                initialQuantity = currentQuantity,
                minimumQuantity = 1.0,
                unit = item.unit,
                validationMessage = getString(R.string.enter_valid_quantity)
            )
        ) { result ->
            viewModel.addOrUpdateItem(item, result.quantity)
            findNavController().popBackStack()
        }
    }

    private fun showSortDialog() {
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
        val current = viewModel.uiState.value?.pickerSortOption ?: SortOption.NAME_ASC
        // Reuses the same sort sheet as the main item list so sorting stays visually consistent.
        showRadioSheet(getString(R.string.sort_items), options, current.name) { selected ->
            viewModel.setPickerSortOption(SortOption.valueOf(selected))
            renderRows()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
