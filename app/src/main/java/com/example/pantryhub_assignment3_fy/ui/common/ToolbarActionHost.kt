package com.example.pantryhub_assignment3_fy.ui.common

import android.view.View

/**
 * Allows a fragment to handle the shared toolbar action button while keeping page-specific logic local.
 */
interface ToolbarActionHost {
    fun onToolbarActionClick(anchor: View)
    fun onSecondaryToolbarActionClick(anchor: View) = Unit
}
