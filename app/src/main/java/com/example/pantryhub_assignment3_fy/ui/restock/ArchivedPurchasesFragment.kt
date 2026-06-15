package com.example.pantryhub_assignment3_fy.ui.restock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentArchivedPurchasesBinding

/**
 * Dedicated purchase archive page so normal purchase filters stay focused on active orders.
 */
class ArchivedPurchasesFragment : Fragment() {
    private var _binding: FragmentArchivedPurchasesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RestockOrdersViewModel by activityViewModels()
    private lateinit var adapter: PurchaseOrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchivedPurchasesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PurchaseOrderAdapter { order ->
            findNavController().navigate(
                R.id.purchaseDetailFragment,
                Bundle().apply { putString(PurchaseDetailFragment.ARG_PURCHASE_ID, order.id) }
            )
        }
        binding.toolbar.title = getString(R.string.archived_purchases)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.purchaseRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.purchaseRecyclerView.adapter = adapter

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val archived = state.restockOrders
                .filter { it.isArchived }
                .sortedByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.createdAt }
            adapter.submitList(archived)
            binding.purchaseRecyclerView.isVisible = archived.isNotEmpty()
            binding.emptyTextView.isVisible = archived.isEmpty()
        }
    }

    override fun onDestroyView() {
        binding.purchaseRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
