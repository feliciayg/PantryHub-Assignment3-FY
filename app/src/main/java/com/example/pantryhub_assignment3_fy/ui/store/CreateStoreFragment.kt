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
import com.example.pantryhub_assignment3_fy.databinding.FragmentCreateStoreBinding
import com.example.pantryhub_assignment3_fy.util.UiState
import com.google.android.material.snackbar.Snackbar

class CreateStoreFragment : Fragment() {
    private var _binding: FragmentCreateStoreBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StoreViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.createStoreButton.setOnClickListener {
            binding.storeNameLayout.error = null
            val storeName = binding.storeNameEditText.text.toString()
            if (storeName.isBlank()) {
                binding.storeNameLayout.error = getString(R.string.store_name_required)
                return@setOnClickListener
            }
            viewModel.createStore(storeName)
        }

        viewModel.storeState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.isVisible = state is UiState.Loading
            binding.createStoreButton.isEnabled = state !is UiState.Loading
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
