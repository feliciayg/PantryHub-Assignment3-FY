package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class ItemNameFormViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val name: LiveData<String> = savedStateHandle.getLiveData(KEY_NAME, "")

    fun initialise(value: String) {
        if (savedStateHandle.get<Boolean>(KEY_INITIALISED) == true) return
        savedStateHandle[KEY_INITIALISED] = true
        savedStateHandle[KEY_NAME] = value
    }

    fun update(value: String) {
        savedStateHandle[KEY_NAME] = value
    }

    companion object {
        private const val KEY_NAME = "item_name_form_value"
        private const val KEY_INITIALISED = "item_name_form_initialised"
    }
}
