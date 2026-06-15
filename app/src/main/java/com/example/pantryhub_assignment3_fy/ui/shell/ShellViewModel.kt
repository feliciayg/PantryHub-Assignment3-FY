package com.example.pantryhub_assignment3_fy.ui.shell

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.AuthRepository
import com.example.pantryhub_assignment3_fy.data.repository.StoreRepository
import com.example.pantryhub_assignment3_fy.model.StoreDetails
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import kotlinx.coroutines.launch

data class ShellUiState(
    val isLoading: Boolean = false,
    val details: StoreDetails? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * Supplies data for the shared activity shell: drawer header, profile details, and sign out.
 */
class ShellViewModel(
    private val storeRepository: StoreRepository = StoreRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(ShellUiState())
    val uiState: LiveData<ShellUiState> = _uiState

    /**
     * Loads store/team and user details for the shared drawer/profile shell.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            storeRepository.loadCurrentStoreDetails()
                .onSuccess { details ->
                    _uiState.update { it.copy(isLoading = false, details = details, errorMessage = null) }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(isLoading = false, errorMessage = throwable.message ?: "Could not load store.")
                    }
                }
        }
    }

    /**
     * Updates the user's display name and refreshes drawer/profile details.
     */
    fun updateDisplayName(displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            storeRepository.updateCurrentUserDisplayName(displayName)
                .onSuccess {
                    storeRepository.loadCurrentStoreDetails()
                        .onSuccess { details ->
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    details = details,
                                    successMessage = "Display name updated."
                                )
                            }
                        }
                        .onFailure { throwable ->
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    errorMessage = throwable.message ?: "Could not refresh profile."
                                )
                            }
                        }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Could not update display name."
                        )
                    }
                }
        }
    }

    /**
     * Signs out and clears shell UI state.
     */
    fun signOut() {
        AppLogger.info(
            area = "Settings",
            event = "sign_out_tapped",
            message = "User requested sign out."
        )
        authRepository.signOut()
        AppLogger.info(
            area = "Settings",
            event = "sign_out_success",
            message = "User signed out."
        )
        _uiState.value = ShellUiState()
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
