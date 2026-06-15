package com.example.pantryhub_assignment3_fy.ui.store

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.StoreRepository
import com.example.pantryhub_assignment3_fy.model.StoreDetails
import kotlinx.coroutines.launch

data class StoreManagementUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val details: StoreDetails? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class StoreManagementViewModel(
    private val storeRepository: StoreRepository = StoreRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(StoreManagementUiState())
    val uiState: LiveData<StoreManagementUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true, errorMessage = null)
            storeRepository.loadCurrentStoreDetails()
                .onSuccess { details ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        details = details,
                        errorMessage = null
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = throwable.toFriendlyMessage("Could not load store details.")
                    )
                }
        }
    }

    fun saveStoreDetails(
        name: String,
        description: String,
        registrationNumber: String,
        address: String,
        contactName: String,
        phone: String,
        imageUrl: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isSaving = true, errorMessage = null, successMessage = null)
            storeRepository.updateCurrentStoreDetails(
                name = name,
                description = description,
                registrationNumber = registrationNumber,
                address = address,
                contactName = contactName,
                phone = phone,
                imageUrl = imageUrl
            )
                .onSuccess {
                    storeRepository.loadCurrentStoreDetails()
                        .onSuccess { details ->
                            _uiState.value = _uiState.value?.copy(
                                isSaving = false,
                                details = details,
                                successMessage = "Store details saved."
                            )
                        }
                        .onFailure { throwable ->
                            _uiState.value = _uiState.value?.copy(
                                isSaving = false,
                                errorMessage = throwable.toFriendlyMessage("Could not refresh store details.")
                            )
                        }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value?.copy(
                        isSaving = false,
                        errorMessage = throwable.toFriendlyMessage("Could not save store details.")
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value?.copy(errorMessage = null, successMessage = null)
    }

    private fun Throwable.toFriendlyMessage(fallback: String): String {
        val rawMessage = message.orEmpty()
        return if (rawMessage.contains("Default FirebaseApp is not initialized")) {
            "Firebase is not configured yet. Add google-services.json before testing store actions."
        } else {
            rawMessage.ifBlank { fallback }
        }
    }
}
