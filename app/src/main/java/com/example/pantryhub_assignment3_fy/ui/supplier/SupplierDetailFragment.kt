package com.example.pantryhub_assignment3_fy.ui.supplier

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentSupplierDetailBinding
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.example.pantryhub_assignment3_fy.ui.common.tintMenuIcons
import com.google.android.material.snackbar.Snackbar

class SupplierDetailFragment : Fragment() {
    private var _binding: FragmentSupplierDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SupplierViewModel by activityViewModels()
    private var supplierId: String = ""
    private var currentSupplier: Supplier? = null
    private var deleteInProgress = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSupplierDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        supplierId = arguments?.getString(ARG_SUPPLIER_ID).orEmpty()
        binding.supplierDetailToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.supplierDetailToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.supplierDetailToolbar.inflateMenu(R.menu.menu_supplier_detail)
        binding.supplierDetailToolbar.tintMenuIcons(requireContext())
        binding.supplierDetailToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.editSupplier -> {
                    findNavController().navigate(
                        R.id.action_supplierDetailFragment_to_addEditSupplierFragment,
                        Bundle().apply {
                            putString(AddEditSupplierFragment.ARG_SUPPLIER_ID, supplierId)
                        }
                    )
                    true
                }
                R.id.deleteSupplier -> {
                    currentSupplier?.let { confirmDelete(it) }
                    true
                }
                R.id.restoreSupplier -> {
                    currentSupplier?.let { viewModel.restoreSupplier(it.id) }
                    true
                }
                else -> false
            }
        }
        binding.phoneRow.setOnClickListener { currentSupplier?.phone?.takeIf { it.isNotBlank() }?.let(::dial) }
        binding.callButton.setOnClickListener { currentSupplier?.phone?.takeIf { it.isNotBlank() }?.let(::dial) }
        binding.emailRow.setOnClickListener { currentSupplier?.email?.takeIf { it.isNotBlank() }?.let(::email) }
        binding.emailButton.setOnClickListener { currentSupplier?.email?.takeIf { it.isNotBlank() }?.let(::email) }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val supplier = state.suppliers.firstOrNull { it.id == supplierId }
            if (supplier != null) {
                currentSupplier = supplier
                render(supplier)
            }
            state.errorMessage?.let {
                deleteInProgress = false
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
                if (deleteInProgress && it.contains("archived", ignoreCase = true)) {
                    deleteInProgress = false
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun render(supplier: Supplier) {
        val partnerType = PartnerType.fromValue(supplier.partnerType)
        val isCustomer = partnerType == PartnerType.CUSTOMER
        binding.supplierDetailToolbar.title = getString(
            if (isCustomer) R.string.customer_details else R.string.supplier_details
        )
        binding.supplierDetailToolbar.menu.findItem(R.id.restoreSupplier)?.isVisible = supplier.isArchived
        binding.supplierDetailToolbar.menu.findItem(R.id.editSupplier)?.isVisible = !supplier.isArchived
        binding.supplierDetailToolbar.menu.findItem(R.id.deleteSupplier)?.isVisible = !supplier.isArchived
        binding.supplierDetailToolbar.tintMenuIcons(requireContext())
        binding.nameValueTextView.text = supplier.name.ifBlank { getString(R.string.not_added) }
        binding.phoneValueTextView.text = supplier.phone.ifBlank { getString(R.string.not_added) }
        binding.emailValueTextView.text = supplier.email.ifBlank { getString(R.string.none) }
        binding.addressValueTextView.text = supplier.address.ifBlank { getString(R.string.not_added) }
        binding.memoValueTextView.text = supplier.notes.ifBlank { getString(R.string.no_notes_added) }
        bindContactAction(
            hasValue = supplier.phone.isNotBlank(),
            row = binding.phoneRow,
            actionButton = binding.callButton
        )
        bindContactAction(
            hasValue = supplier.email.isNotBlank(),
            row = binding.emailRow,
            actionButton = binding.emailButton
        )
    }

    private fun confirmDelete(supplier: Supplier) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.archive))
            .setMessage("Archive ${supplier.name}? It will disappear from normal partner lists, but linked history remains.")
            .setPositiveButton(getString(R.string.archive)) { _, _ ->
                deleteInProgress = true
                viewModel.archiveSupplier(supplier.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun bindContactAction(hasValue: Boolean, row: View, actionButton: View) {
        row.isEnabled = hasValue
        row.alpha = if (hasValue) 1f else 0.7f
        actionButton.isVisible = hasValue
    }

    private fun dial(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_app_for_call, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun email(email: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email)}")))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_app_for_email, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SUPPLIER_ID = "supplierId"
    }
}
