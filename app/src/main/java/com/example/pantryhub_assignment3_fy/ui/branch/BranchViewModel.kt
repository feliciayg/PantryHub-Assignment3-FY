package com.example.pantryhub_assignment3_fy.ui.branch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.update
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch

class BranchViewModel(
    private val branchRepository: BranchRepository = BranchRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(BranchUiState())
    val uiState: LiveData<BranchUiState> = _uiState

    init {
        observeBranches()
    }

    private fun observeBranches() {
        viewModelScope.launch {
            branchRepository.observeBranches(includeArchived = true).collect { result ->
                result
                    .onSuccess { branches ->
                        _uiState.update {
                            val next = it.copy(isLoading = false, branches = branches, errorMessage = null)
                            next.copy(visibleBranches = applySearch(next))
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = throwable.toBranchError("Could not load branches."))
                        }
                    }
            }
        }
    }

    fun search(query: String) {
        _uiState.update {
            val next = it.copy(searchQuery = query)
            next.copy(visibleBranches = applySearch(next))
        }
    }

    fun addBranch(branch: Branch) {
        AppLogger.info(
            area = "Locations",
            event = "location_create_start",
            message = "Saving location.",
            "location" to branch.name
        )
        viewModelScope.launch {
            branchRepository.addBranch(branch)
                .onSuccess { showSuccess("Branch saved.") }
                .onFailure {
                    AppLogger.error(
                        area = "Locations",
                        event = "location_create_failed",
                        message = "Could not save location.",
                        throwable = it,
                        "location" to branch.name
                    )
                    showError(it.toBranchError("Could not save branch."))
                }
        }
    }

    fun setArchiveFilter(archiveFilter: BranchArchiveFilter) {
        _uiState.update {
            val next = it.copy(archiveFilter = archiveFilter)
            next.copy(visibleBranches = applySearch(next))
        }
    }

    fun updateBranch(branch: Branch) {
        viewModelScope.launch {
            branchRepository.updateBranch(branch)
                .onSuccess { showSuccess("Branch updated.") }
                .onFailure {
                    AppLogger.error(
                        area = "Locations",
                        event = "location_update_failed",
                        message = "Could not update location.",
                        throwable = it,
                        "location" to branch.name
                    )
                    showError(it.toBranchError("Could not update branch."))
                }
        }
    }

    fun archiveBranch(branchId: String) {
        viewModelScope.launch {
            branchRepository.archiveBranch(branchId)
                .onSuccess { showSuccess("Location archived.") }
                .onFailure {
                    AppLogger.error(
                        area = "Locations",
                        event = "location_archive_failed",
                        message = "Could not archive location.",
                        throwable = it,
                        "locationId" to branchId
                    )
                    showError(it.toBranchError("Could not archive location."))
                }
        }
    }

    fun restoreBranch(branchId: String) {
        viewModelScope.launch {
            branchRepository.restoreBranch(branchId)
                .onSuccess { showSuccess("Location restored.") }
                .onFailure { showError(it.toBranchError("Could not restore location.")) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun applySearch(state: BranchUiState): List<Branch> {
        val query = state.searchQuery.trim()
        val filtered = state.branches.filter {
            when (state.archiveFilter) {
                BranchArchiveFilter.ACTIVE -> !it.isArchived
                BranchArchiveFilter.ARCHIVED -> it.isArchived
                BranchArchiveFilter.ALL -> true
            }
        }
        if (query.isBlank()) return filtered
        return filtered.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true)
        }
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun Throwable.toBranchError(fallback: String): String {
        return if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Branch access denied. Publish the current Firestore rules and confirm your account exists in this store's staff collection."
        } else {
            message ?: fallback
        }
    }
}
