package com.example.pantryhub_assignment3_fy.ui.store

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentMembersBinding
import com.example.pantryhub_assignment3_fy.model.StoreDetails
import com.example.pantryhub_assignment3_fy.ui.shell.ShellViewModel
import com.google.android.material.snackbar.Snackbar

class MembersFragment : Fragment() {
    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShellViewModel by activityViewModels()
    private val membersAdapter = MembersAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.membersRecyclerView.adapter = membersAdapter
        binding.copyCodeButton.setOnClickListener { copyInviteCode() }
        binding.shareCodeButton.setOnClickListener { shareInviteCode() }
        viewModel.load()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val members = state.details?.staff.orEmpty()
            membersAdapter.submitList(members)
            binding.memberCountTextView.text = members.size.toString()
            binding.emptyTextView.isVisible = members.isEmpty()
            binding.membersRecyclerView.isVisible = members.isNotEmpty()
            bindInviteCode(state.details)

            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun bindInviteCode(details: StoreDetails?) {
        val store = details?.store
        val inviteCode = store?.inviteCode.orEmpty()
        binding.inviteCodeValueTextView.text = inviteCode.ifBlank { "-" }
        binding.inviteCodeStoreTextView.text = getString(
            R.string.invite_code_store_format,
            store?.name?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name)
        )
        binding.copyCodeButton.isEnabled = inviteCode.isNotBlank()
        binding.shareCodeButton.isEnabled = inviteCode.isNotBlank()
    }

    private fun copyInviteCode() {
        val inviteCode = binding.inviteCodeValueTextView.text?.toString().orEmpty().trim()
        if (inviteCode.isBlank() || inviteCode == "-") return
        val clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText(getString(R.string.invite_code), inviteCode))
        Snackbar.make(binding.root, R.string.invite_code_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareInviteCode() {
        val state = viewModel.uiState.value ?: return
        val store = state.details?.store ?: return
        if (store.inviteCode.isBlank()) return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.share_store_code_message, store.name.ifBlank { getString(R.string.app_name) }, store.inviteCode)
            )
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_store_code)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.membersRecyclerView.adapter = null
        _binding = null
    }
}
