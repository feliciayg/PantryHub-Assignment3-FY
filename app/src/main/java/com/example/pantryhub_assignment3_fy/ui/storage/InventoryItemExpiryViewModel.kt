package com.example.pantryhub_assignment3_fy.ui.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.ExpiryLotRepository
import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class InventoryItemExpiryUiState(
    val lotsByItemId: Map<String, List<ExpiryLot>> = emptyMap(),
    val errorMessage: String? = null
)

/**
 * Loads exact expiry subcollections only for the product shown on Item Info.
 *
 * Inventory lists keep using lightweight expiry summaries, while this screen needs the real
 * per-batch quantities so separate Stock In expiry dates are never presented as one combined lot.
 */
class InventoryItemExpiryViewModel(
    private val expiryLotRepository: ExpiryLotRepository = ExpiryLotRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(InventoryItemExpiryUiState())
    val uiState: LiveData<InventoryItemExpiryUiState> = _uiState
    private var loadedSignature: String = ""

    fun loadLots(items: List<InventoryItem>) {
        val validItems = items.filter { it.id.isNotBlank() }
        val signature = validItems
            .sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.quantity}:${it.updatedAt}:${it.expiryDate}" }
        if (signature == loadedSignature) return
        loadedSignature = signature

        viewModelScope.launch {
            runCatching {
                validItems.map { item ->
                    async { item.id to expiryLotRepository.loadLots(item) }
                }.awaitAll().toMap()
            }.onSuccess { lots ->
                _uiState.value = InventoryItemExpiryUiState(lotsByItemId = lots)
            }.onFailure { error ->
                loadedSignature = ""
                _uiState.value = _uiState.value.orEmpty().copy(
                    errorMessage = error.message ?: "Could not load expiry batches."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.orEmpty().copy(errorMessage = null)
    }

    private fun InventoryItemExpiryUiState?.orEmpty(): InventoryItemExpiryUiState =
        this ?: InventoryItemExpiryUiState()
}
