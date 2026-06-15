package com.example.pantryhub_assignment3_fy.ui.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentJoinStoreBinding
import com.example.pantryhub_assignment3_fy.util.UiState
import com.google.android.material.snackbar.Snackbar

class JoinStoreFragment : Fragment() {
    private var _binding: FragmentJoinStoreBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StoreViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJoinStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.joinStoreButton.setOnClickListener {
            binding.inviteCodeLayout.error = null
            val inviteCode = binding.inviteCodeEditText.text.toString()
            if (inviteCode.isBlank()) {
                binding.inviteCodeLayout.error = getString(R.string.invite_code_required)
                return@setOnClickListener
            }
            viewModel.joinStore(inviteCode)
        }

        viewModel.storeState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.isVisible = state is UiState.Loading
            binding.joinStoreButton.isEnabled = state !is UiState.Loading
            when (state) {
                is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                is UiState.Success -> findNavController().navigate(R.id.action_global_homeFragment)
                else -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
