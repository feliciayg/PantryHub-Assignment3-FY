package com.example.pantryhub_assignment3_fy.ui.store

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentStoreManagementBinding
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.google.android.material.snackbar.Snackbar

class StoreManagementFragment : Fragment() {
    private var _binding: FragmentStoreManagementBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StoreManagementViewModel by viewModels()
    private var selectedImageUri: String = ""
    private var isBindingStoreData = false
    private var hasLoadedStore = false
    private var saveInProgress = false

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        persistImagePermission(uri)
        selectedImageUri = uri.toString()
        binding.storeImageView.loadInventoryImage(selectedImageUri, R.drawable.ic_store)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.imagePickerContainer.setOnClickListener { imagePickerLauncher.launch(arrayOf("image/*")) }
        binding.saveButton.setOnClickListener { saveStoreDetails() }
        viewModel.load()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressBar.isVisible = state.isLoading || state.isSaving
            binding.saveButton.isEnabled = !state.isSaving
            state.details?.let { details ->
                if (!hasLoadedStore || !isFormDirty(details)) {
                    bindStoreDetails(details)
                    hasLoadedStore = true
                }
            }
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                saveInProgress = false
                viewModel.clearMessage()
            }
            state.successMessage?.let {
                if (saveInProgress) {
                    saveInProgress = false
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }
            }
        }
    }

    private fun bindStoreDetails(details: com.example.pantryhub_assignment3_fy.model.StoreDetails) {
        isBindingStoreData = true
        binding.storeNameEditText.setText(details.store.name)
        binding.storeDescriptionEditText.setText(details.store.description)
        binding.registrationNumberEditText.setText(details.store.registrationNumber)
        binding.addressEditText.setText(details.store.address)
        binding.contactEditText.setText(details.store.contactName.ifBlank { details.currentUser.displayName })
        binding.phoneEditText.setText(details.store.phone)
        selectedImageUri = details.store.imageUrl
        binding.storeImageView.loadInventoryImage(selectedImageUri, R.drawable.ic_store)
        isBindingStoreData = false
    }

    private fun saveStoreDetails() {
        binding.storeNameLayout.error = null
        val name = binding.storeNameEditText.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            binding.storeNameLayout.error = getString(R.string.store_name_required)
            return
        }
        saveInProgress = true
        viewModel.saveStoreDetails(
            name = name,
            description = binding.storeDescriptionEditText.text?.toString().orEmpty(),
            registrationNumber = binding.registrationNumberEditText.text?.toString().orEmpty(),
            address = binding.addressEditText.text?.toString().orEmpty(),
            contactName = binding.contactEditText.text?.toString().orEmpty(),
            phone = binding.phoneEditText.text?.toString().orEmpty(),
            imageUrl = selectedImageUri
        )
    }

    private fun isFormDirty(details: com.example.pantryhub_assignment3_fy.model.StoreDetails): Boolean {
        if (isBindingStoreData) return false
        return binding.storeNameEditText.text?.toString().orEmpty() != details.store.name ||
            binding.storeDescriptionEditText.text?.toString().orEmpty() != details.store.description ||
            binding.registrationNumberEditText.text?.toString().orEmpty() != details.store.registrationNumber ||
            binding.addressEditText.text?.toString().orEmpty() != details.store.address ||
            binding.contactEditText.text?.toString().orEmpty() != details.store.contactName.ifBlank { details.currentUser.displayName } ||
            binding.phoneEditText.text?.toString().orEmpty() != details.store.phone ||
            selectedImageUri != details.store.imageUrl
    }

    private fun persistImagePermission(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
