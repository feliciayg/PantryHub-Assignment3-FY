package com.example.pantryhub_assignment3_fy.ui.restock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentRestockOrdersBinding
import com.example.pantryhub_assignment3_fy.ui.common.ToolbarActionHost
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

class RestockOrdersFragment : Fragment(), ToolbarActionHost {
    private var _binding: FragmentRestockOrdersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RestockOrdersViewModel by activityViewModels()

    private lateinit var orderAdapter: PurchaseOrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRestockOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbarActions()
        setupFilters()
        setupLists()

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

    private fun setupToolbarActions() {
        binding.addPurchaseFab.setOnClickListener {
            findNavController().navigate(R.id.action_restockOrdersFragment_to_newPurchaseFragment)
        }
    }

    private fun setupFilters() {
        val tabs = listOf(
            PurchaseStatusFilter.ALL,
            PurchaseStatusFilter.DRAFT,
            PurchaseStatusFilter.ORDERED,
            PurchaseStatusFilter.PARTIALLY_RECEIVED,
            PurchaseStatusFilter.RECEIVED,
            PurchaseStatusFilter.CANCELLED
        )
        tabs.forEach { filter ->
            binding.statusTabLayout.addTab(
                binding.statusTabLayout.newTab().setText(filter.label)
            )
        }
        binding.statusTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val filter = tabs.getOrNull(tab.position) ?: PurchaseStatusFilter.ALL
                viewModel.filter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupLists() {
        orderAdapter = PurchaseOrderAdapter(
            onOpen = { order ->
                findNavController().navigate(
                    R.id.action_restockOrdersFragment_to_purchaseDetailFragment,
                    Bundle().apply {
                        putString(PurchaseDetailFragment.ARG_PURCHASE_ID, order.id)
                    }
                )
            }
        )
        binding.purchaseRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.purchaseRecyclerView.adapter = orderAdapter
    }

    private fun render(state: RestockOrdersUiState) {
        binding.loadingIndicator.isVisible = state.isLoading
        val selectedIndex = when (state.selectedFilter) {
            PurchaseStatusFilter.ALL -> 0
            PurchaseStatusFilter.DRAFT -> 1
            PurchaseStatusFilter.ORDERED -> 2
            PurchaseStatusFilter.PARTIALLY_RECEIVED -> 3
            PurchaseStatusFilter.RECEIVED -> 4
            PurchaseStatusFilter.CANCELLED -> 5
        }
        if (binding.statusTabLayout.selectedTabPosition != selectedIndex) {
            binding.statusTabLayout.getTabAt(selectedIndex)?.select()
        }
        orderAdapter.submitList(state.visibleOrders)
        binding.purchaseRecyclerView.isVisible = state.visibleOrders.isNotEmpty()
        binding.contentScrollView.isVisible = state.visibleOrders.isNotEmpty()
        binding.emptyTextView.isVisible = !state.isLoading && state.visibleOrders.isEmpty()
    }

    override fun onToolbarActionClick(anchor: View) {
        findNavController().navigate(R.id.action_restockOrdersFragment_to_purchaseFilterFragment)
    }

    override fun onSecondaryToolbarActionClick(anchor: View) {
        findNavController().navigate(R.id.action_restockOrdersFragment_to_archivedPurchasesFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
