package com.example.pantryhub_assignment3_fy.ui.branch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentAddEditBranchBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.google.android.material.snackbar.Snackbar

class AddEditBranchFragment : Fragment() {
    private var _binding: FragmentAddEditBranchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BranchViewModel by activityViewModels()

    private var branchId: String = ""
    private var existingBranch: Branch? = null
    private var saveInProgress = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditBranchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        branchId = arguments?.getString(ARG_BRANCH_ID).orEmpty()
        existingBranch = viewModel.uiState.value?.branches?.firstOrNull { it.id == branchId }

        setupHeader()
        setupInputs()
        populateExistingValues()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (existingBranch == null && branchId.isNotBlank()) {
                existingBranch = state.branches.firstOrNull { it.id == branchId }?.also(::populateValues)
            }

            state.errorMessage?.let {
                saveInProgress = false
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
                if (saveInProgress && message.contains("Branch", ignoreCase = true)) {
                    saveInProgress = false
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun setupHeader() {
        binding.toolbar.title = getString(
            if (branchId.isBlank()) R.string.new_location else R.string.edit_location_title
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.saveButton.setOnClickListener { saveBranch() }
    }

    private fun setupInputs() {
        binding.nameEditText.doAfterTextChanged {
            if (!it.isNullOrBlank()) binding.nameLayout.error = null
        }
    }

    private fun populateExistingValues() {
        existingBranch?.let(::populateValues)
    }

    private fun populateValues(branch: Branch) {
        binding.nameEditText.setText(branch.name)
        binding.memoEditText.setText(branch.notes)
    }

    private fun saveBranch() {
        val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.nameLayout.error = getString(R.string.branch_name_required)
            return
        }

        val current = existingBranch
        // Address stays preserved for old records even though the new location UI is simplified to name + memo.
        val branch = Branch(
            id = current?.id.orEmpty(),
            name = name,
            address = current?.address.orEmpty(),
            notes = binding.memoEditText.text?.toString()?.trim().orEmpty(),
            createdBy = current?.createdBy.orEmpty(),
            createdAt = current?.createdAt ?: 0L,
            updatedBy = current?.updatedBy.orEmpty(),
            updatedAt = current?.updatedAt ?: 0L
        )

        saveInProgress = true
        if (branchId.isBlank()) {
            viewModel.addBranch(branch)
        } else {
            viewModel.updateBranch(branch)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BRANCH_ID = "branchId"
    }
}
