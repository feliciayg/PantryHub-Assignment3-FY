package com.example.pantryhub_assignment3_fy.ui.store

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.StoreRepository
import com.example.pantryhub_assignment3_fy.util.UiState
import kotlinx.coroutines.launch

/**
 * ViewModel for creating or joining a store/team after login/sign-up.
 */
class StoreViewModel(
    private val storeRepository: StoreRepository = StoreRepository()
) : ViewModel() {
    private val _storeState = MutableLiveData<UiState<String>>(UiState.Idle)
    val storeState: LiveData<UiState<String>> = _storeState

    /**
     * Creates a store/team and links it to the current user profile.
     */
    fun createStore(name: String) {
        viewModelScope.launch {
            _storeState.value = UiState.Loading
            // Creating a store/team links the signed-in user as owner before the app opens the main tabs.
            storeRepository.createStore(name)
                .onSuccess { _storeState.value = UiState.Success(it) }
                .onFailure { _storeState.value = UiState.Error(it.toFriendlyMessage("Could not create store.")) }
        }
    }

    /**
     * Joins the store/team that matches the entered invite code.
     */
    fun joinStore(inviteCode: String) {
        viewModelScope.launch {
            _storeState.value = UiState.Loading
            // Joining uses the invite code to find a shared store/team and add the user as a staff staffMember.
            storeRepository.joinStore(inviteCode)
                .onSuccess { _storeState.value = UiState.Success(it) }
                .onFailure { _storeState.value = UiState.Error(it.toFriendlyMessage("Could not join store.")) }
        }
    }

    /**
     * Converts repository exceptions into short form-friendly messages.
     */
    private fun Throwable.toFriendlyMessage(fallback: String): String {
        val rawMessage = message.orEmpty()
        return if (rawMessage.contains("Default FirebaseApp is not initialized")) {
            "Firebase is not configured yet. Add google-services.json before testing store/team actions."
        } else {
            rawMessage.ifBlank { fallback }
        }
    }
}
