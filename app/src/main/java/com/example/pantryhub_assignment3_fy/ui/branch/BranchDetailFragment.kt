package com.example.pantryhub_assignment3_fy.ui.branch

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentBranchDetailBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.ui.common.tintMenuIcons
import com.google.android.material.snackbar.Snackbar

class BranchDetailFragment : Fragment() {
    private var _binding: FragmentBranchDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BranchViewModel by activityViewModels()

    private var branchId: String = ""
    private var currentBranch: Branch? = null
    private var deleteInProgress = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBranchDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        branchId = arguments?.getString(ARG_BRANCH_ID).orEmpty()
        setupToolbar()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val branch = state.branches.firstOrNull { it.id == branchId }
            if (branch != null) {
                currentBranch = branch
                render(branch)
            }

            state.errorMessage?.let {
                deleteInProgress = false
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
                if (deleteInProgress && it.contains("archived", ignoreCase = true)) {
                    deleteInProgress = false
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.branchDetailToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.branchDetailToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.branchDetailToolbar.inflateMenu(R.menu.menu_branch_detail)
        binding.branchDetailToolbar.tintMenuIcons(requireContext())
        binding.branchDetailToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.editBranch -> {
                    findNavController().navigate(
                        R.id.action_branchDetailFragment_to_addEditBranchFragment,
                        Bundle().apply {
                            putString(AddEditBranchFragment.ARG_BRANCH_ID, branchId)
                        }
                    )
                    true
                }
                R.id.deleteBranch -> {
                    currentBranch?.let(::confirmDelete)
                    true
                }
                R.id.restoreBranch -> {
                    currentBranch?.let { viewModel.restoreBranch(it.id) }
                    true
                }
                else -> false
            }
        }
    }

    private fun render(branch: Branch) {
        binding.branchDetailToolbar.title = branch.name
        binding.branchDetailToolbar.menu.findItem(R.id.restoreBranch)?.isVisible = branch.isArchived
        binding.branchDetailToolbar.menu.findItem(R.id.editBranch)?.isVisible = !branch.isArchived
        binding.branchDetailToolbar.menu.findItem(R.id.deleteBranch)?.isVisible = !branch.isArchived
        binding.branchDetailToolbar.tintMenuIcons(requireContext())
        binding.nameValueTextView.text = branch.name.ifBlank { getString(R.string.not_added) }
        binding.memoValueTextView.text = branch.notes.ifBlank { getString(R.string.no_notes_added) }
    }

    private fun confirmDelete(branch: Branch) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.archive_branch))
            .setMessage(getString(R.string.archive_branch_message, branch.name))
            .setPositiveButton(getString(R.string.archive)) { _, _ ->
                deleteInProgress = true
                viewModel.archiveBranch(branch.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BRANCH_ID = "branchId"
    }
}
