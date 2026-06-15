package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class QuantityUnitFormViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val quantity: LiveData<String> = savedStateHandle.getLiveData(KEY_QUANTITY, "")
    val unit: LiveData<String> = savedStateHandle.getLiveData(KEY_UNIT, DEFAULT_UNIT)

    fun initialise(quantityValue: String, unitValue: String) {
        if (savedStateHandle.get<Boolean>(KEY_INITIALISED) == true) return
        savedStateHandle[KEY_INITIALISED] = true
        savedStateHandle[KEY_QUANTITY] = quantityValue
        savedStateHandle[KEY_UNIT] = unitValue.ifBlank { DEFAULT_UNIT }
    }

    fun updateQuantity(value: String) {
        savedStateHandle[KEY_QUANTITY] = value
    }

    fun updateUnit(value: String) {
        savedStateHandle[KEY_UNIT] = value.ifBlank { DEFAULT_UNIT }
    }

    companion object {
        const val DEFAULT_UNIT = "pcs"
        private const val KEY_INITIALISED = "quantity_unit_form_initialised"
        private const val KEY_QUANTITY = "quantity_form_value"
        private const val KEY_UNIT = "unit_form_value"
    }
}
