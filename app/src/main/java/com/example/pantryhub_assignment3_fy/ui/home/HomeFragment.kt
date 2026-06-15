package com.example.pantryhub_assignment3_fy.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentHomeBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemHomeActionRowBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.toSummaryQuantityText
import com.example.pantryhub_assignment3_fy.ui.movement.StockMovementsFragment
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionMode
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionTypeVisuals
import com.example.pantryhub_assignment3_fy.ui.storage.AddEditInventoryItemFragment
import com.example.pantryhub_assignment3_fy.ui.storage.BarcodeScannerPrototypeFragment
import com.example.pantryhub_assignment3_fy.ui.storage.FilterOptions
import com.example.pantryhub_assignment3_fy.ui.storage.InventoryFragment
import com.example.pantryhub_assignment3_fy.ui.storage.InventoryItemDetailFragment
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact Home overview. Business calculations stay in HomeViewModel and action rows route to
 * existing inventory, transaction, purchase, sales, calendar, and history flows.
 */
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val insightsAdapter = HomeInsightsAdapter { metric ->
        metric.statusFilter?.let(::navigateToItems)
    }
    private var searchResults: List<InventoryItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupCarousel()
        setupSearch()
        setupActionSections()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun setupCarousel() {
        binding.insightsViewPager.adapter = insightsAdapter
        binding.insightsViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = renderIndicators(position)
        })
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchItems(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.searchEditText.setOnItemClickListener { _, _, position, _ ->
            searchResults.getOrNull(position)?.let(::navigateToInventoryItemDetail)
        }
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
                viewModel.searchItems(value)
            }
    }

    private fun setupActionSections() {
        addAction(binding.itemsActionsContainer, R.drawable.ic_add_item, R.string.add_item) {
            navigateToAddInventoryItem()
        }

        addAction(
            binding.transactionsActionsContainer,
            R.drawable.ic_stock_in,
            R.string.stock_in,
            TransactionTypeVisuals.colorFor(TransactionMode.STOCK_IN)
        ) {
            navigateToTransactions(StockMovementsFragment.ACTION_STOCK_IN)
        }
        addAction(
            binding.transactionsActionsContainer,
            R.drawable.ic_stock_out,
            R.string.stock_out,
            TransactionTypeVisuals.colorFor(TransactionMode.STOCK_OUT)
        ) {
            navigateToTransactions(StockMovementsFragment.ACTION_STOCK_OUT)
        }
        addAction(
            binding.transactionsActionsContainer,
            R.drawable.ic_move_stock,
            R.string.move_stock,
            TransactionTypeVisuals.colorFor(TransactionMode.MOVE_STOCK)
        ) {
            navigateToTransactions(StockMovementsFragment.ACTION_MOVE_STOCK)
        }
        addAction(
            binding.transactionsActionsContainer,
            R.drawable.ic_adjust_stock,
            R.string.adjust_stock,
            TransactionTypeVisuals.colorFor(TransactionMode.ADJUST_STOCK)
        ) {
            navigateToTransactions(StockMovementsFragment.ACTION_ADJUST_STOCK)
        }

        addAction(binding.alertsActionsContainer, R.drawable.ic_status_low_stock, R.string.view_shortages) {
            navigateToItems(FilterOptions.LOW_STOCK_STATUS)
        }

        addAction(binding.purchasesSalesActionsContainer, R.drawable.ic_restock, R.string.purchases) {
            findNavController().navigate(R.id.restockOrdersFragment)
        }
        addAction(binding.purchasesSalesActionsContainer, R.drawable.ic_upload, R.string.sales) {
            findNavController().navigate(
                R.id.inventoryFragment,
                Bundle().apply {
                    putBoolean(InventoryFragment.ARG_OPEN_SALES_IMPORT, true)
                }
            )
        }

    }

    private fun addAction(
        container: LinearLayout,
        iconRes: Int,
        labelRes: Int,
        @ColorRes iconTintRes: Int? = null,
        onClick: () -> Unit
    ) {
        val row = ItemHomeActionRowBinding.inflate(layoutInflater, container, false)
        val label = getString(labelRes)
        row.actionIcon.setImageResource(iconRes)
        iconTintRes?.let {
            row.actionIcon.setColorFilter(ContextCompat.getColor(requireContext(), it))
        }
        row.actionIcon.contentDescription = label
        row.actionLabel.setText(labelRes)
        row.root.contentDescription = label
        row.root.setOnClickListener { onClick() }
        container.addView(row.root)
    }

    private fun render(state: HomeUiState) {
        binding.loadingIndicator.isVisible = state.isLoading
        renderInsights(state)
        renderSearchResults(state.searchResults)
    }

    private fun renderInsights(state: HomeUiState) {
        val currency = NumberFormat.getCurrencyInstance()
        currency.maximumFractionDigits = 0
        insightsAdapter.submitPages(
            listOf(
                HomeInsightPage(
                    title = getString(R.string.today_summary),
                    subtitle = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                    metrics = listOf(
                        HomeInsightMetric(state.totalQuantity.toSummaryQuantityText(), getString(R.string.total_quantity)),
                        HomeInsightMetric(state.todayStockInQuantity.toSummaryQuantityText(), getString(R.string.stock_in)),
                        HomeInsightMetric(state.todayStockOutQuantity.toSummaryQuantityText(), getString(R.string.stock_out))
                    )
                ),
                HomeInsightPage(
                    title = getString(R.string.attention_needed),
                    subtitle = getString(R.string.inventory_health),
                    metrics = listOf(
                        HomeInsightMetric(
                            value = state.activeLowStockCount.toString(),
                            label = getString(R.string.shortages),
                            statusFilter = FilterOptions.LOW_STOCK_STATUS
                        ),
                        HomeInsightMetric(
                            value = state.outOfStockCount.toString(),
                            label = getString(R.string.out_of_stock),
                            statusFilter = "Out of Stock"
                        ),
                        HomeInsightMetric(
                            value = state.expiredItemCount.toString(),
                            label = getString(R.string.expired),
                            statusFilter = "Expired"
                        )
                    )
                ),
                HomeInsightPage(
                    title = getString(R.string.inventory_value),
                    subtitle = getString(R.string.inventory_insights_title),
                    metrics = listOf(
                        HomeInsightMetric(currency.format(state.totalStockCostValue), getString(R.string.cost_value)),
                        HomeInsightMetric(currency.format(state.totalPotentialSalesValue), getString(R.string.sales_value)),
                        HomeInsightMetric(state.activePurchaseCount.toString(), getString(R.string.active_purchases))
                    )
                )
            )
        )
    }

    private fun renderSearchResults(items: List<InventoryItem>) {
        searchResults = items
        val labels = items.map { item ->
            listOf(item.name, item.sku.takeIf(String::isNotBlank)?.let { "SKU $it" }, item.brand.takeIf(String::isNotBlank))
                .filterNotNull()
                .joinToString(" · ")
        }
        binding.searchEditText.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown_menu_white, labels))
        if (labels.isNotEmpty() && binding.searchEditText.hasFocus()) binding.searchEditText.showDropDown()
    }

    private fun renderIndicators(selected: Int) {
        listOf(binding.indicatorOne, binding.indicatorTwo, binding.indicatorThree).forEachIndexed { index, view ->
            view.setBackgroundResource(if (index == selected) R.drawable.bg_home_indicator_selected else R.drawable.bg_home_indicator_unselected)
        }
    }

    private fun navigateToInventoryItemDetail(item: InventoryItem) {
        findNavController().navigate(
            R.id.action_homeFragment_to_inventoryItemDetailFragment,
            Bundle().apply {
                putString(InventoryItemDetailFragment.ARG_INVENTORY_ITEM_ID, item.id)
            }
        )
    }

    private fun navigateToAddInventoryItem() {
        findNavController().navigate(
            R.id.action_homeFragment_to_addEditInventoryItemFragment,
            Bundle().apply {
                putString(AddEditInventoryItemFragment.ARG_MODE, AddEditInventoryItemFragment.MODE_ADD)
            }
        )
    }

    private fun navigateToTransactions(action: String) {
        findNavController().navigate(
            R.id.stockMovementsFragment,
            Bundle().apply {
                putString(StockMovementsFragment.ARG_OPEN_ACTION, action)
            }
        )
    }

    private fun navigateToItems(status: String) {
        findNavController().navigate(
            R.id.inventoryFragment,
            Bundle().apply {
                putString(InventoryFragment.ARG_STATUS_FILTER, status)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
