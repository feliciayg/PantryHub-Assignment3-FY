package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.InventoryRepository
import kotlinx.coroutines.launch

data class SkuValidationState(
    val isValidating: Boolean = false,
    val acceptedSku: String? = null,
    val acceptedBarcode: String? = null,
    val errorMessage: String? = null
)

class SkuFormViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val inventoryRepository = InventoryRepository()
    val sku: LiveData<String> = savedStateHandle.getLiveData(KEY_SKU, "")
    val barcode: LiveData<String> = savedStateHandle.getLiveData(KEY_BARCODE, "")

    private val _validationState = MutableLiveData(SkuValidationState())
    val validationState: LiveData<SkuValidationState> = _validationState

    fun initialise(isEditing: Boolean, existingOrPrefilledSku: String) {
        if (savedStateHandle.get<Boolean>(KEY_INITIALISED) == true) return
        savedStateHandle[KEY_INITIALISED] = true

        val suppliedSku = existingOrPrefilledSku.trim().uppercase()
        if (isEditing || suppliedSku.isNotBlank()) {
            savedStateHandle[KEY_SKU] = suppliedSku
            return
        }

        viewModelScope.launch {
            inventoryRepository.generateUniqueDisplaySku()
                .onSuccess { savedStateHandle[KEY_SKU] = it }
                .onFailure {
                    savedStateHandle[KEY_INITIALISED] = false
                    _validationState.value = SkuValidationState(errorMessage = it.message ?: "Could not generate SKU.")
                }
        }
    }

    fun useExistingProductSku(value: String) {
        savedStateHandle[KEY_SKU] = value.trim().uppercase()
    }

    fun initialiseBarcode(existingOrPrefilledBarcode: String) {
        if (savedStateHandle.get<Boolean>(KEY_BARCODE_INITIALISED) == true) return
        savedStateHandle[KEY_BARCODE_INITIALISED] = true
        savedStateHandle[KEY_BARCODE] = existingOrPrefilledBarcode.trim()
    }

    fun useExistingProductBarcode(value: String) {
        savedStateHandle[KEY_BARCODE] = value.trim()
    }

    fun validateManualSku(
        value: String,
        currentItemId: String?,
        branchId: String,
        name: String,
        brand: String,
        category: String,
        reusedProductSku: String?
    ) {
        val normalized = value.trim().uppercase()
        _validationState.value = SkuValidationState(isValidating = true)
        viewModelScope.launch {
            inventoryRepository.validateSkuForProduct(
                sku = normalized,
                currentItemId = currentItemId,
                branchId = branchId,
                name = name,
                brand = brand,
                category = category,
                reusedProductSku = reusedProductSku
            ).onSuccess {
                savedStateHandle[KEY_SKU] = normalized
                _validationState.value = SkuValidationState(acceptedSku = normalized)
            }.onFailure {
                _validationState.value = SkuValidationState(
                    errorMessage = it.message ?: "This SKU is already used by another product."
                )
            }
        }
    }

    fun clearValidationResult() {
        _validationState.value = SkuValidationState()
    }

    fun validateManualBarcode(
        value: String,
        currentItemId: String?,
        branchId: String,
        name: String,
        brand: String,
        category: String
    ) {
        val normalized = value.trim()
        _validationState.value = SkuValidationState(isValidating = true)
        if (normalized.isBlank()) {
            savedStateHandle[KEY_BARCODE] = ""
            _validationState.value = SkuValidationState(acceptedBarcode = "")
            return
        }
        viewModelScope.launch {
            inventoryRepository.validateBarcodeForProduct(
                barcode = normalized,
                currentItemId = currentItemId,
                branchId = branchId,
                name = name,
                brand = brand,
                category = category
            ).onSuccess {
                savedStateHandle[KEY_BARCODE] = normalized
                _validationState.value = SkuValidationState(acceptedBarcode = normalized)
            }.onFailure {
                _validationState.value = SkuValidationState(
                    errorMessage = it.message ?: "This barcode is already used by another product."
                )
            }
        }
    }

    companion object {
        private const val KEY_SKU = "sku_form_value"
        private const val KEY_INITIALISED = "sku_form_initialised"
        private const val KEY_BARCODE = "barcode_form_value"
        private const val KEY_BARCODE_INITIALISED = "barcode_form_initialised"
    }
}
