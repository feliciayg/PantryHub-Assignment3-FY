package com.example.pantryhub_assignment3_fy.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentMemoEditorBinding

class MemoEditorFragment : Fragment() {
    private var _binding: FragmentMemoEditorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_done) {
                saveMemo()
                true
            } else {
                false
            }
        }
        binding.toolbar.title = arguments?.getString(ARG_TITLE).orEmpty().ifBlank { getString(R.string.memo) }
        binding.memoEditText.setText(arguments?.getString(ARG_INITIAL_VALUE).orEmpty())
        binding.memoEditText.setSelection(binding.memoEditText.text?.length ?: 0)
        binding.memoEditText.requestFocus()
    }

    private fun saveMemo() {
        findNavController().previousBackStackEntry
            ?.savedStateHandle
            ?.set(RESULT_KEY, binding.memoEditText.text?.toString().orEmpty())
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_INITIAL_VALUE = "initialValue"
        const val ARG_TITLE = "title"
        const val RESULT_KEY = "memo_editor_result"
    }
}
