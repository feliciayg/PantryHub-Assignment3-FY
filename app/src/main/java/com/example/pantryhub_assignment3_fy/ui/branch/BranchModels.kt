package com.example.pantryhub_assignment3_fy.ui.branch

import androidx.annotation.StringRes
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.Branch

enum class BranchArchiveFilter(@StringRes val labelRes: Int) {
    ACTIVE(R.string.active),
    ARCHIVED(R.string.archived),
    ALL(R.string.all)
}

data class BranchUiState(
    val isLoading: Boolean = true,
    val branches: List<Branch> = emptyList(),
    val visibleBranches: List<Branch> = emptyList(),
    val searchQuery: String = "",
    val archiveFilter: BranchArchiveFilter = BranchArchiveFilter.ACTIVE,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
