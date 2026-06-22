package com.example.pantryhub_assignment3_fy.ui.supplier

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentSuppliersBinding
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

class SuppliersFragment : Fragment() {
    private var _binding: FragmentSuppliersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SupplierViewModel by activityViewModels()
    private lateinit var adapter: SupplierAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSuppliersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.suppliersToolbar.title = getString(R.string.partners)
        binding.suppliersToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.suppliersToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.suppliersToolbar.menu.add(Menu.NONE, MENU_FILTER, Menu.NONE, R.string.filters)
            .setIcon(R.drawable.ic_filter)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        binding.suppliersToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FILTER -> {
                    showFilterMenu()
                    true
                }
                else -> false
            }
        }
        adapter = SupplierAdapter(
            onClick = { supplier ->
                findNavController().navigate(
                    R.id.action_suppliersFragment_to_supplierDetailFragment,
                    Bundle().apply {
                        putString(SupplierDetailFragment.ARG_SUPPLIER_ID, supplier.id)
                    }
                )
            },
            onFavoriteClick = viewModel::toggleFavorite
        )
        binding.suppliersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.suppliersRecyclerView.adapter = adapter
        binding.addSupplierFab.setOnClickListener {
            val partnerType = when (viewModel.uiState.value?.selectedTab) {
                PartnerTab.CUSTOMERS -> PartnerType.CUSTOMER.value
                else -> PartnerType.SUPPLIER.value
            }
            findNavController().navigate(
                R.id.action_suppliersFragment_to_addEditSupplierFragment,
                Bundle().apply {
                    putString(AddEditSupplierFragment.ARG_PARTNER_TYPE, partnerType)
                }
            )
        }
        setupTabs()
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            adapter.submitList(state.visibleSuppliers)
            binding.loadingIndicator.isVisible = state.isLoading
            binding.emptyTextView.isVisible = !state.isLoading && state.visibleSuppliers.isEmpty()
            updateSelectedTab(state.selectedTab)
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

    private fun setupTabs() {
        if (binding.partnerTabLayout.tabCount > 0) return
        PartnerTab.entries.forEach { tab ->
            binding.partnerTabLayout.addTab(binding.partnerTabLayout.newTab().setText(tab.labelRes))
        }
        binding.partnerTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(PartnerTab.entries.getOrElse(tab.position) { PartnerTab.ALL })
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun updateSelectedTab(tab: PartnerTab) {
        val index = PartnerTab.entries.indexOf(tab)
        if (index >= 0 && binding.partnerTabLayout.selectedTabPosition != index) {
            binding.partnerTabLayout.getTabAt(index)?.select()
        }
    }

    private fun showFilterMenu() {
        val anchor = binding.suppliersToolbar.findViewById<View>(MENU_FILTER) ?: binding.suppliersToolbar
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, FILTER_ALL, 0, R.string.partners_filter_all)
            menu.add(0, FILTER_FAVORITES_FIRST, 1, R.string.partners_filter_favorites_first)
            menu.add(0, FILTER_FAVORITES_ONLY, 2, R.string.partners_filter_favorites_only)
            menu.add(0, FILTER_ARCHIVE_ACTIVE, 3, R.string.active)
            menu.add(0, FILTER_ARCHIVE_ARCHIVED, 4, R.string.archived)
            menu.add(0, FILTER_ARCHIVE_ALL, 5, R.string.all)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    FILTER_ALL -> viewModel.setFilterMode(PartnerFilterMode.ALL)
                    FILTER_FAVORITES_FIRST -> viewModel.setFilterMode(PartnerFilterMode.FAVORITES_FIRST)
                    FILTER_FAVORITES_ONLY -> viewModel.setFilterMode(PartnerFilterMode.FAVORITES_ONLY)
                    FILTER_ARCHIVE_ACTIVE -> viewModel.setArchiveFilter(PartnerArchiveFilter.ACTIVE)
                    FILTER_ARCHIVE_ARCHIVED -> viewModel.setArchiveFilter(PartnerArchiveFilter.ARCHIVED)
                    FILTER_ARCHIVE_ALL -> viewModel.setArchiveFilter(PartnerArchiveFilter.ALL)
                }
                true
            }
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MENU_FILTER = 100
        private const val FILTER_ALL = 1
        private const val FILTER_FAVORITES_FIRST = 2
        private const val FILTER_FAVORITES_ONLY = 3
        private const val FILTER_ARCHIVE_ACTIVE = 4
        private const val FILTER_ARCHIVE_ARCHIVED = 5
        private const val FILTER_ARCHIVE_ALL = 6
    }
}
