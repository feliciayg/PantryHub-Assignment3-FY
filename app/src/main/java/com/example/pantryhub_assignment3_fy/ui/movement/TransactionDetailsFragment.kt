package com.example.pantryhub_assignment3_fy.ui.movement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentTransactionDetailsBinding
import com.google.android.material.snackbar.Snackbar

class TransactionDetailsFragment : Fragment() {
    private var _binding: FragmentTransactionDetailsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransactionDetailsViewModel by viewModels()
    private val itemsAdapter = TransactionDetailsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = itemsAdapter
        binding.retryButton.setOnClickListener { viewModel.load() }
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Boolean>(StockInTransactionFragment.RESULT_TRANSACTION_UPDATED)
            ?.observe(viewLifecycleOwner) { updated ->
                if (updated == true) {
                    viewModel.load()
                    findNavController().currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<Boolean>(StockInTransactionFragment.RESULT_TRANSACTION_UPDATED)
                }
            }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.loadingIndicator.isVisible = state.isLoading
            binding.contentScrollView.isVisible = state.details != null
            binding.errorContainer.isVisible = !state.isLoading && state.details == null
            binding.retryButton.isVisible = state.errorMessage != null
            binding.errorTextView.text = when {
                state.notFound -> getString(R.string.transaction_not_found)
                state.errorMessage != null -> state.errorMessage
                else -> getString(R.string.transaction_not_found)
            }
            state.details?.let(::renderDetails)
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.infoMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun renderDetails(details: TransactionDetailsUiModel) {
        val color = ContextCompat.getColor(requireContext(), details.colorRes)
        val performedBy = details.performedByName.ifBlank { getString(R.string.unknown_staff) }
        binding.typeTitleTextView.text = getString(details.titleRes)
        binding.typeTitleTextView.setTextColor(color)
        binding.statusTextView.text = getString(details.statusRes)
        binding.performedByTextView.text = performedBy
        binding.performedByAvatarTextView.text = performedBy.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        binding.performedByAvatarTextView.setTextColor(color)
        binding.headerAccentDivider.setBackgroundColor(color)
        binding.itemsSectionTitleTextView.text = getString(R.string.transaction_items_count, details.itemLines.size)
        renderRows(binding.transactionRowsContainer, details.transactionRows)
        itemsAdapter.submitList(details.itemLines)
        binding.emptyItemsTextView.isVisible = details.itemLines.isEmpty()
        binding.memoValueTextView.text = details.memoText
        binding.memoValueTextView.isVisible = details.memoText.isNotBlank()
        binding.additionalSection.isVisible = details.additionalRows.isNotEmpty()
        binding.memoDivider.isVisible = details.memoText.isNotBlank() || details.additionalRows.isNotEmpty()
        binding.memoLabelTextView.isVisible = details.memoText.isNotBlank()
        renderRows(binding.additionalRowsContainer, details.additionalRows)
    }

    private fun renderRows(container: LinearLayout, rows: List<TransactionDetailRow>) {
        container.removeAllViews()
        rows.forEach { row ->
            container.addView(createRow(row))
        }
    }

    private fun createRow(row: TransactionDetailRow): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height) - resources.getDimensionPixelSize(R.dimen.space_sm)
            setPadding(
                0,
                0,
                0,
                0
            )
            addView(TextView(requireContext()).apply {
                text = getString(row.labelRes)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(requireContext()).apply {
                text = row.value
                gravity = android.view.Gravity.END
                maxWidth = resources.displayMetrics.widthPixels / 2
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        row.valueColorRes ?: R.color.inventory_text_primary
                    )
                )
                textSize = 14f
            })
        }

    override fun onDestroyView() {
        binding.itemsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
