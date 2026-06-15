package com.example.pantryhub_assignment3_fy.ui.branch

import com.example.pantryhub_assignment3_fy.model.Branch

enum class BranchArchiveFilter(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ALL("All")
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
