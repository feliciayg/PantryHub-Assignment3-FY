package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.InventoryOptionRepository
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.model.InventoryOptionType
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

data class InventoryOptionUiState(
    val categories: List<InventoryOption> = emptyList(),
    val brands: List<InventoryOption> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class InventoryOptionViewModel(
    private val repository: InventoryOptionRepository = InventoryOptionRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(InventoryOptionUiState())
    val uiState: LiveData<InventoryOptionUiState> = _uiState

    fun loadOptions(type: InventoryOptionType) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.loadOptions(type)
                .onSuccess { options ->
                    _uiState.update {
                        when (type) {
                            InventoryOptionType.CATEGORY -> it.copy(categories = options, isLoading = false)
                            InventoryOptionType.BRAND -> it.copy(brands = options, isLoading = false)
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun usageCount(type: InventoryOptionType, name: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch { onResult(repository.usageCount(type, name)) }
    }

    fun addOption(
        type: InventoryOptionType,
        name: String,
        onResult: (Result<InventoryOption>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addOption(type, name)
            if (result.isSuccess) loadOptions(type)
            onResult(result)
        }
    }

    fun renameOption(
        type: InventoryOptionType,
        option: InventoryOption,
        newName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.renameOption(type, option, newName)
            if (result.isSuccess) loadOptions(type)
            onResult(result)
        }
    }

    fun deleteOption(
        type: InventoryOptionType,
        option: InventoryOption,
        replacementName: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteOption(type, option, replacementName)
            if (result.isSuccess) loadOptions(type)
            onResult(result)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
