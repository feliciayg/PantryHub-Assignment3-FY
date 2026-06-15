package com.example.pantryhub_assignment3_fy.ui.supplier

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentAddEditSupplierBinding
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier
import com.google.android.material.snackbar.Snackbar

class AddEditSupplierFragment : Fragment() {
    private var _binding: FragmentAddEditSupplierBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SupplierViewModel by activityViewModels()
    private var supplierId: String = ""
    private var partnerType: PartnerType = PartnerType.SUPPLIER
    private var saveInProgress = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditSupplierBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        supplierId = arguments?.getString(ARG_SUPPLIER_ID).orEmpty()
        partnerType = PartnerType.fromValue(arguments?.getString(ARG_PARTNER_TYPE))
        binding.supplierEditToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.supplierEditToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        viewModel.findSupplier(supplierId)?.let {
            partnerType = PartnerType.fromValue(it.partnerType)
            fillForm(it)
        }
        updateTitleAndFields()
        binding.saveSupplierButton.setOnClickListener { saveSupplier() }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                saveInProgress = false
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                if (saveInProgress) {
                    saveInProgress = false
                    viewModel.clearMessages()
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun fillForm(supplier: Supplier) {
        binding.nameEditText.setText(supplier.name)
        binding.phoneEditText.setText(supplier.phone)
        binding.emailEditText.setText(supplier.email)
        binding.addressEditText.setText(supplier.address)
        binding.memoEditText.setText(supplier.notes)
    }

    private fun updateTitleAndFields() {
        val isCustomer = partnerType == PartnerType.CUSTOMER
        binding.supplierEditToolbar.title = getString(
            when {
                supplierId.isBlank() && isCustomer -> R.string.new_customer
                supplierId.isBlank() -> R.string.add_supplier
                isCustomer -> R.string.edit_customer
                else -> R.string.edit_supplier
            }
        )
        binding.nameLabelTextView.text = getString(R.string.name_required_label)
        binding.nameLayout.hint = getString(R.string.partner_name_placeholder)
        binding.phoneLabelTextView.text = getString(R.string.phone)
        binding.phoneLayout.hint = getString(R.string.partner_phone_placeholder)
        binding.emailLabelTextView.text = getString(R.string.email)
        binding.emailLayout.hint = getString(R.string.partner_email_placeholder)
        binding.addressLabelTextView.text = getString(R.string.address)
        binding.addressLayout.hint = getString(R.string.partner_address_placeholder)
        binding.memoLabelTextView.text = getString(R.string.memo)
        binding.memoLayout.hint = getString(R.string.partner_memo_placeholder)
        binding.saveSupplierButton.text = getString(R.string.save)
    }

    private fun saveSupplier() {
        clearErrors()
        val name = binding.nameEditText.text.toString().trim()
        if (name.isBlank()) {
            binding.nameLayout.error = getString(R.string.supplier_name_required)
            return
        }

        val existing = viewModel.findSupplier(supplierId)
        val supplier = Supplier(
            id = supplierId,
            name = name,
            partnerType = partnerType.value,
            isFavorite = existing?.isFavorite ?: false,
            contactPerson = existing?.contactPerson.orEmpty(),
            phone = binding.phoneEditText.text.toString(),
            email = binding.emailEditText.text.toString(),
            address = binding.addressEditText.text.toString(),
            paymentTerms = existing?.paymentTerms.orEmpty(),
            leadTimeDays = existing?.leadTimeDays ?: 0,
            notes = binding.memoEditText.text.toString()
        )
        saveInProgress = true
        if (supplierId.isBlank()) viewModel.addSupplier(supplier) else viewModel.updateSupplier(supplier)
    }

    private fun clearErrors() {
        binding.nameLayout.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SUPPLIER_ID = "supplierId"
        const val ARG_PARTNER_TYPE = "partnerType"
    }
}
