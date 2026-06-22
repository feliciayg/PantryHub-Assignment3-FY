package com.example.pantryhub_assignment3_fy.ui.restock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.databinding.FragmentPurchaseReceiveItemPickerBinding

class PurchaseReceiveItemPickerFragment : Fragment() {
    private var _binding: FragmentPurchaseReceiveItemPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RestockOrdersViewModel by activityViewModels()
    private lateinit var adapter: PurchaseReceivePickerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchaseReceiveItemPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PurchaseReceivePickerAdapter(::showQuantityDialog)
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.doneButton.setOnClickListener { findNavController().popBackStack() }
        binding.searchEditText.doAfterTextChanged {
            viewModel.setReceivePickerQuery(it?.toString().orEmpty())
            render()
        }

        viewModel.uiState.observe(viewLifecycleOwner) {
            binding.toolbar.title = it.receiveDraft?.locationName.orEmpty()
            binding.summaryTextView.text = it.receiveSelectionSummary(requireContext())
            render()
        }
    }

    private fun render() {
        val rows = viewModel.receivePickerItems()
        adapter.submitList(rows)
        binding.emptyTextView.isVisible = rows.isEmpty()
        binding.itemsRecyclerView.isVisible = rows.isNotEmpty()
    }

    private fun showQuantityDialog(row: PurchaseReceivePickerRow) {
        showReceiveQuantityDialog(row) { quantity ->
            viewModel.setReceiveQuantity(row.item.receiveKey(), quantity)
        }
    }

    override fun onDestroyView() {
        binding.itemsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
