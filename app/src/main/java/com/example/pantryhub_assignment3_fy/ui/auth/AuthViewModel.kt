package com.example.pantryhub_assignment3_fy.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.AuthRepository
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.UiState
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.launch

/**
 * ViewModel for login and sign-up screens.
 *
 * It forwards credential work to AuthRepository and exposes simple loading/success/error state.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _authState = MutableLiveData<UiState<UserProfile>>(UiState.Idle)
    val authState: LiveData<UiState<UserProfile>> = _authState
    private val _passwordResetState = MutableLiveData<UiState<Unit>>(UiState.Idle)
    val passwordResetState: LiveData<UiState<Unit>> = _passwordResetState

    /**
     * Attempts to sign in and returns the loaded user profile on success.
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = UiState.Loading
            // The ViewModel asks the repository to authenticate and exposes only UI-friendly state.
            authRepository.login(email, password)
                .onSuccess { _authState.value = UiState.Success(it) }
                .onFailure {
                    _authState.value = UiState.Error(
                        it.toFriendlyMessage(
                            fallback = "Invalid email or password. Please try again.",
                            hideAuthDetails = true
                        )
                    )
                }
        }
    }

    /**
     * Creates an account and matching InventoryHub profile.
     */
    fun signUp(email: String, displayName: String, password: String) {
        viewModelScope.launch {
            _authState.value = UiState.Loading
            authRepository.signUp(email, displayName, password)
                .onSuccess { _authState.value = UiState.Success(it) }
                .onFailure { _authState.value = UiState.Error(it.toFriendlyMessage("Could not create account.")) }
        }
    }

    /**
     * Requests Firebase to send reset instructions to the supplied email address.
     */
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _passwordResetState.value = UiState.Loading
            authRepository.sendPasswordReset(email)
                .onSuccess { _passwordResetState.value = UiState.Success(Unit) }
                .onFailure {
                    _passwordResetState.value = UiState.Error(
                        it.toFriendlyMessage("Could not submit password reset request.")
                    )
                }
        }
    }

    fun clearPasswordResetState() {
        _passwordResetState.value = UiState.Idle
    }

    /**
     * Converts technical Firebase errors into messages that are safer and clearer for users.
     */
    private fun Throwable.toFriendlyMessage(fallback: String, hideAuthDetails: Boolean = false): String {
        val rawMessage = message.orEmpty()
        // Login should not expose Firebase's detailed credential wording, but sign-up still
        // returns helpful validation messages such as email taken or password too short.
        return when {
            rawMessage.contains("Default FirebaseApp is not initialized") ->
                "Firebase is not configured yet. Add google-services.json before testing account actions."
            hideAuthDetails && (this is FirebaseAuthException ||
                rawMessage.contains("auth credential", ignoreCase = true) ||
                rawMessage.contains("password is invalid", ignoreCase = true) ||
                rawMessage.contains("no user record", ignoreCase = true)) -> fallback
            else -> rawMessage.ifBlank { fallback }
        }
    }
}
