package com.example.pantryhub_assignment3_fy.ui.branch

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
import com.example.pantryhub_assignment3_fy.databinding.FragmentBranchesBinding
import com.google.android.material.snackbar.Snackbar

class BranchesFragment : Fragment() {
    private var _binding: FragmentBranchesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BranchViewModel by activityViewModels()
    private lateinit var adapter: BranchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBranchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.branchesToolbar.title = getString(R.string.branch_management)
        binding.branchesToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.branchesToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.branchesToolbar.menu.add(Menu.NONE, MENU_FILTER, Menu.NONE, R.string.filters)
            .setIcon(R.drawable.ic_filter)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        binding.branchesToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FILTER -> {
                    showFilterMenu()
                    true
                }
                else -> false
            }
        }
        adapter = BranchAdapter(::openBranchDetail)
        binding.branchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.branchesRecyclerView.adapter = adapter
        binding.addBranchFab.setOnClickListener {
            findNavController().navigate(R.id.action_branchesFragment_to_addEditBranchFragment)
        }
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            adapter.submitList(state.visibleBranches)
            binding.loadingIndicator.isVisible = state.isLoading
            binding.emptyTextView.isVisible = !state.isLoading && state.visibleBranches.isEmpty()
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

    private fun openBranchDetail(branch: com.example.pantryhub_assignment3_fy.model.Branch) {
        findNavController().navigate(
            R.id.action_branchesFragment_to_branchDetailFragment,
            Bundle().apply {
                putString(BranchDetailFragment.ARG_BRANCH_ID, branch.id)
            }
        )
    }

    private fun showFilterMenu() {
        val anchor = binding.branchesToolbar.findViewById<View>(MENU_FILTER) ?: binding.branchesToolbar
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, FILTER_ACTIVE, 0, R.string.active)
            menu.add(0, FILTER_ARCHIVED, 1, R.string.archived)
            menu.add(0, FILTER_ALL, 2, R.string.all)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    FILTER_ACTIVE -> viewModel.setArchiveFilter(BranchArchiveFilter.ACTIVE)
                    FILTER_ARCHIVED -> viewModel.setArchiveFilter(BranchArchiveFilter.ARCHIVED)
                    FILTER_ALL -> viewModel.setArchiveFilter(BranchArchiveFilter.ALL)
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
        private const val FILTER_ACTIVE = 1
        private const val FILTER_ARCHIVED = 2
        private const val FILTER_ALL = 3
    }
}
