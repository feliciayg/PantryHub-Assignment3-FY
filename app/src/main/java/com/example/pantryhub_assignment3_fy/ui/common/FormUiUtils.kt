package com.example.pantryhub_assignment3_fy.ui.common

import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R

fun Fragment.bindFormValue(textView: TextView, value: String?, placeholder: String) {
    val shown = value.orEmpty()
    textView.text = shown.ifBlank { placeholder }
    textView.setTextColor(
        ContextCompat.getColor(
            requireContext(),
            if (shown.isBlank()) R.color.inventory_text_secondary else R.color.inventory_text_primary
        )
    )
}

fun Fragment.openMemoEditor(
    initialValue: String,
    title: String = getString(R.string.memo)
) {
    findNavController().navigate(
        R.id.memoEditorFragment,
        Bundle().apply {
            putString(MemoEditorFragment.ARG_INITIAL_VALUE, initialValue)
            putString(MemoEditorFragment.ARG_TITLE, title)
        }
    )
}

fun Fragment.observeMemoEditorResult(onSave: (String) -> Unit) {
    findNavController().currentBackStackEntry?.savedStateHandle
        ?.getLiveData<String>(MemoEditorFragment.RESULT_KEY)
        ?.observe(viewLifecycleOwner) { memo ->
            onSave(memo)
            findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>(MemoEditorFragment.RESULT_KEY)
        }
}
