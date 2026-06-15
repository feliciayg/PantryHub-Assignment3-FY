package com.example.pantryhub_assignment3_fy.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentSignUpBinding
import com.example.pantryhub_assignment3_fy.model.UserProfile
import com.example.pantryhub_assignment3_fy.util.UiState
import com.google.android.material.snackbar.Snackbar

class SignUpFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.signUpButton.setOnClickListener {
            clearErrors()
            if (!validateForm()) return@setOnClickListener
            viewModel.signUp(
                binding.emailEditText.text.toString(),
                binding.displayNameEditText.text.toString(),
                binding.passwordEditText.text.toString()
            )
        }
        binding.loginTextView.setOnClickListener {
            findNavController().navigate(R.id.action_signUpFragment_to_loginFragment)
        }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }
    }

    private fun renderState(state: UiState<UserProfile>) {
        val isLoading = state is UiState.Loading
        binding.signUpButton.isEnabled = !isLoading
        binding.signUpButton.text = getString(if (isLoading) R.string.creating_account else R.string.create_account)
        binding.loginTextView.isEnabled = !isLoading
        when (state) {
            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            is UiState.Success -> findNavController().navigate(R.id.action_signUpFragment_to_storeSetupFragment)
            else -> Unit
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val email = binding.emailEditText.text.toString().trim()
        val displayName = binding.displayNameEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        // Local validation gives immediate red field borders while repository validation remains
        // as a second safety layer before Firebase creates the account.
        when {
            email.isBlank() -> {
                binding.emailLayout.error = getString(R.string.email_required)
                isValid = false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.emailLayout.error = getString(R.string.email_invalid)
                isValid = false
            }
        }
        if (displayName.isBlank()) {
            binding.displayNameLayout.error = getString(R.string.display_name_required)
            isValid = false
        }
        when {
            password.isBlank() -> {
                binding.passwordLayout.error = getString(R.string.password_required)
                isValid = false
            }
            password.length < 6 -> {
                binding.passwordLayout.error = getString(R.string.password_min_length)
                isValid = false
            }
        }
        return isValid
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.displayNameLayout.error = null
        binding.passwordLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
