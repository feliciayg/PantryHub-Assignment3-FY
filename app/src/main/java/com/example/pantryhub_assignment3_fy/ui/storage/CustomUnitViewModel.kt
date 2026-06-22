package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.CustomUnitRepository
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

data class CustomUnitUiState(
    val units: List<InventoryOption> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CustomUnitViewModel(
    private val repository: CustomUnitRepository = CustomUnitRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(CustomUnitUiState())
    val uiState: LiveData<CustomUnitUiState> = _uiState

    fun loadUnits() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.loadUnits()
                .onSuccess { units -> _uiState.update { it.copy(units = units, isLoading = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun addUnit(name: String, onResult: (Result<InventoryOption>) -> Unit) {
        viewModelScope.launch {
            val result = repository.addUnit(name)
            if (result.isSuccess) loadUnits()
            onResult(result)
        }
    }

    fun deleteUnit(unit: InventoryOption, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteUnusedUnit(unit)
            if (result.isSuccess) loadUnits()
            onResult(result)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
