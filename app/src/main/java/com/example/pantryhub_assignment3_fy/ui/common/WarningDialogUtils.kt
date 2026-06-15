package com.example.pantryhub_assignment3_fy.ui.common

import androidx.fragment.app.Fragment
import com.example.pantryhub_assignment3_fy.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Reusable explanatory dialog for important blocked actions where a Snackbar is too easy to miss.
 */
data class WarningDialogContent(
    val title: String,
    val message: String
)

fun Fragment.showWarningDialog(
    content: WarningDialogContent,
    onDismiss: (() -> Unit)? = null
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(content.title)
        .setMessage(content.message)
        .setPositiveButton(R.string.ok, null)
        .setOnDismissListener { onDismiss?.invoke() }
        .show()
}
