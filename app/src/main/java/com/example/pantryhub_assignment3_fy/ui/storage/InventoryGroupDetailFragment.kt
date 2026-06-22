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
import com.example.pantryhub_assignment3_fy.databinding.FragmentInventoryGroupDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.radiobutton.MaterialRadioButton

class InventoryGroupDetailFragment : Fragment() {
    private var _binding: FragmentInventoryGroupDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private lateinit var adapter: InventoryAdapter
    private val groupOption: GroupOption
        get() = runCatching {
            GroupOption.valueOf(requireArguments().getString(ARG_GROUP_OPTION).orEmpty())
        }.getOrDefault(GroupOption.NONE)
    private val groupKey: String get() = requireArguments().getString(ARG_GROUP_KEY).orEmpty()
    private var inStockOnly: Boolean = false
    private var sortOption: SortOption = SortOption.NAME_ASC

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = InventoryAdapter(
            onClick = ::openItem,
            onEdit = {},
            onDelete = {}
        )
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = getString(
            R.string.group_detail_title_format,
            getString(groupOption.labelRes),
            requireArguments().getString(ARG_GROUP_TITLE).orEmpty()
        )
        binding.inStockChip.setOnCheckedChangeListener { _, isChecked ->
            inStockOnly = isChecked
            render()
        }
        binding.sortButton.setOnClickListener { showSortSheet() }

        val initialSearch = requireArguments().getString(ARG_INITIAL_SEARCH).orEmpty()
        binding.searchEditText.setText(initialSearch)
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        viewModel.uiState.observe(viewLifecycleOwner) { render() }
    }

    private fun render() {
        if (_binding == null) return
        val rows = viewModel.rowsForGroup(
            groupOption = groupOption,
            groupKey = groupKey,
            query = binding.searchEditText.text?.toString().orEmpty(),
            inStockOnly = inStockOnly,
            sortOption = sortOption
        )
        adapter.submitList(rows)
        binding.emptyTextView.isVisible = rows.isEmpty()
    }

    private fun showSortSheet() {
        val options = listOf(
            SortOption.NAME_ASC,
            SortOption.NAME_DESC,
            SortOption.QUANTITY_HIGH,
            SortOption.QUANTITY_LOW,
            SortOption.SAFETY_STOCK_LOW,
            SortOption.RESTOCK_URGENCY,
            SortOption.EXPIRY_SOONEST,
            SortOption.RECENTLY_UPDATED
        )
        val dialog = BottomSheetDialog(requireContext())
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_lg),
                resources.getDimensionPixelSize(R.dimen.space_lg),
                resources.getDimensionPixelSize(R.dimen.space_lg),
                resources.getDimensionPixelSize(R.dimen.space_lg)
            )
            setBackgroundResource(R.drawable.bg_bottom_sheet)
        }
        container.addView(android.widget.TextView(requireContext()).apply {
            text = getString(R.string.sort_items)
            setTextColor(resources.getColor(R.color.inventory_text_primary, requireContext().theme))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        options.forEach { option ->
            container.addView(MaterialRadioButton(requireContext()).apply {
                text = getString(option.labelRes)
                isChecked = option == sortOption
                minHeight = resources.getDimensionPixelSize(R.dimen.form_field_height)
                setTextColor(resources.getColor(R.color.inventory_text_primary, requireContext().theme))
                setOnClickListener {
                    sortOption = option
                    dialog.dismiss()
                    render()
                }
            })
        }
        dialog.setContentView(container)
        dialog.show()
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

    companion object {
        const val ARG_GROUP_OPTION = "groupOption"
        const val ARG_GROUP_KEY = "groupKey"
        const val ARG_GROUP_TITLE = "groupTitle"
        const val ARG_INITIAL_SEARCH = "initialSearch"
    }
}
