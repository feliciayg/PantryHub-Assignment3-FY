package com.example.pantryhub_assignment3_fy.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentLoginBinding
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.UiState
import com.google.android.material.snackbar.Snackbar

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.loginButton.setOnClickListener {
            clearErrors()
            if (!validateForm()) return@setOnClickListener
            // Shows that the user submitted the login form from the UI layer.
            AppLogger.debug(
                area = "LoginUi",
                event = "submit_tapped",
                message = "Login button tapped.",
                "email" to binding.emailEditText.text.toString().trim()
            )
            viewModel.login(
                binding.emailEditText.text.toString(),
                binding.passwordEditText.text.toString()
            )
        }
        binding.forgotPasswordTextView.setOnClickListener {
            clearErrors()
            val email = binding.emailEditText.text.toString().trim()
            when {
                email.isBlank() -> binding.emailLayout.error = getString(R.string.email_required)
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    binding.emailLayout.error = getString(R.string.email_invalid)
                else -> viewModel.sendPasswordReset(email)
            }
        }
        binding.signUpTextView.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signUpFragment)
        }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }
        viewModel.passwordResetState.observe(viewLifecycleOwner) { state ->
            renderPasswordResetState(state)
        }
    }

    private fun renderState(state: UiState<com.example.pantryhub_assignment3_fy.model.UserProfile>) {
        val isLoading = state is UiState.Loading
        binding.loginButton.isEnabled = !isLoading
        binding.loginButton.text = getString(if (isLoading) R.string.logging_in else R.string.sign_in)
        binding.signUpTextView.isEnabled = !isLoading
        when (state) {
            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            is UiState.Success -> {
                // Explains which post-login screen the fragment will route to.
                AppLogger.info(
                    area = "LoginUi",
                    event = "navigate_after_login",
                    message = "Routing user after login.",
                    "hasStore" to !state.data.currentStoreId.isNullOrBlank()
                )
                // Login routes users based on whether their profile is already linked to a store.
                if (state.data.currentStoreId.isNullOrBlank()) {
                    findNavController().navigate(R.id.action_loginFragment_to_storeSetupFragment)
                } else {
                    findNavController().navigate(R.id.action_global_homeFragment)
                }
            }
            else -> Unit
        }
    }

    private fun renderPasswordResetState(state: UiState<Unit>) {
        val isLoading = state is UiState.Loading
        binding.forgotPasswordTextView.isEnabled = !isLoading
        when (state) {
            is UiState.Error -> {
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                viewModel.clearPasswordResetState()
            }
            is UiState.Success -> {
                Snackbar.make(binding.root, R.string.password_reset_instructions_sent, Snackbar.LENGTH_LONG).show()
                viewModel.clearPasswordResetState()
            }
            else -> Unit
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        // Local validation marks the exact field in red before Firebase receives the request.
        if (email.isBlank()) {
            binding.emailLayout.error = getString(R.string.email_required)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.email_invalid)
            isValid = false
        }
        if (password.isBlank()) {
            binding.passwordLayout.error = getString(R.string.password_required)
            isValid = false
        }
        return isValid
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
