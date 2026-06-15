package com.example.pantryhub_assignment3_fy.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentProfileBinding
import com.example.pantryhub_assignment3_fy.ui.shell.ShellViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * Shows the current user's profile and store role.
 */
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShellViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.editDisplayNameButton.setOnClickListener { showEditDisplayNameDialog() }
        binding.signOutButton.setOnClickListener {
            viewModel.signOut()
            findNavController().navigate(R.id.action_global_loginFragment)
        }
        viewModel.load()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            state.details?.let {
                binding.displayNameTextView.text = it.currentUser.displayName
                binding.emailTextView.text = "@${it.currentUser.email}"
                binding.storeNameTextView.text = it.store.name
                binding.roleTextView.text = it.currentStaff.role.replaceFirstChar { char -> char.uppercase() }
            }
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    /**
     * Opens a small dialog for editing the display name stored in Firestore.
     */
    private fun showEditDisplayNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(binding.displayNameTextView.text)
            setSelectAllOnFocus(true)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_display_name)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ -> viewModel.updateDisplayName(input.text.toString()) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
