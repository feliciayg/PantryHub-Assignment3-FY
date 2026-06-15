package com.example.pantryhub_assignment3_fy.ui.storage

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.data.repository.BranchRepository
import com.example.pantryhub_assignment3_fy.databinding.DialogEnterSkuBinding
import com.example.pantryhub_assignment3_fy.databinding.FragmentCategoryPickerBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemCategoryOptionBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.ui.restock.PurchaseEditorViewModel
import com.example.pantryhub_assignment3_fy.ui.movement.StockInTransactionViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CategoryPickerFragment : Fragment() {
    private var _binding: FragmentCategoryPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private val stockInViewModel: StockInTransactionViewModel by activityViewModels()
    private val purchaseViewModel: PurchaseEditorViewModel by activityViewModels()
    private val branchRepository = BranchRepository()
    private lateinit var adapter: CategoryPickerAdapter
    private var fieldType: String = FIELD_CATEGORY
    private var currentValue: String = ""
    private var currentBranchId: String = ""
    private var pendingValues: List<String> = emptyList()
    private var parentCategory: String = ""
    private var pendingCreatedValue: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fieldType = arguments?.getString(ARG_FIELD_TYPE).orEmpty().ifBlank { FIELD_CATEGORY }
        currentValue = arguments?.getString(ARG_CURRENT_CATEGORY).orEmpty().trim()
        currentBranchId = arguments?.getString(ARG_CURRENT_BRANCH_ID).orEmpty()
        pendingValues = arguments?.getStringArray(ARG_PENDING_CATEGORIES)?.toList().orEmpty()
        parentCategory = arguments?.getString(ARG_PARENT_CATEGORY).orEmpty()
        adapter = CategoryPickerAdapter { option ->
            when (option.kind) {
                CategoryOptionKind.EXISTING -> returnSelection(option.label, option.id)
                CategoryOptionKind.ADD_NEW -> returnSelection(option.label)
            }
        }

        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.addButton.contentDescription = getString(
            when (fieldType) {
                FIELD_BRAND -> R.string.add_brand
                FIELD_LOCATION, FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO -> R.string.add_location
                FIELD_STOCK_IN_PARTNER -> R.string.add_partner
                FIELD_STOCK_OUT_CUSTOMER -> R.string.add_customer
                else -> R.string.add_category
            }
        )
        binding.addButton.setOnClickListener {
            if (fieldType == FIELD_STOCK_IN_PARTNER) {
                findNavController().navigate(R.id.action_categoryPickerFragment_to_addEditSupplierFragment)
            } else {
                showAddValueDialog(binding.searchEditText.text?.toString().orEmpty().trim())
            }
        }
        binding.searchEditText.hint = getString(
            when (fieldType) {
                FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO -> R.string.search_or_enter_location
                FIELD_STOCK_IN_PARTNER -> R.string.search_or_enter_partner
                FIELD_STOCK_OUT_CUSTOMER -> R.string.search_or_enter_customer
                else -> R.string.search_or_enter_value
            }
        )
        val initialSearchText = if (
            fieldType == FIELD_LOCATION ||
            fieldType == FIELD_STOCK_IN_LOCATION ||
            fieldType == FIELD_MOVE_STOCK_FROM ||
            fieldType == FIELD_MOVE_STOCK_TO ||
            fieldType == FIELD_STOCK_IN_PARTNER ||
            fieldType == FIELD_STOCK_OUT_CUSTOMER
        ) "" else currentValue
        binding.searchEditText.setText(initialSearchText)
        binding.searchEditText.setSelection(binding.searchEditText.text?.length ?: 0)
        binding.searchEditText.doAfterTextChanged { refreshOptions() }

        viewModel.uiState.observe(viewLifecycleOwner) {
            refreshOptions()
        }
        stockInViewModel.uiState.observe(viewLifecycleOwner) { state ->
            refreshOptions()
            if (pendingCreatedValue.isNotBlank()) {
                val created = when (fieldType) {
                    FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO ->
                        state.branches.firstOrNull { it.name.equals(pendingCreatedValue, ignoreCase = true) }?.let { it.id to it.name }
                    FIELD_STOCK_IN_PARTNER -> state.suppliers.firstOrNull { it.name.equals(pendingCreatedValue, ignoreCase = true) }?.let { it.id to it.name }
                    FIELD_STOCK_OUT_CUSTOMER -> state.customers.firstOrNull { it.name.equals(pendingCreatedValue, ignoreCase = true) }?.let { it.id to it.name }
                    else -> null
                }
                if (created != null) {
                    pendingCreatedValue = ""
                    returnSelection(created.second, created.first, created.second)
                }
            }
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                stockInViewModel.clearMessages()
            }
        }
        purchaseViewModel.uiState.observe(viewLifecycleOwner) {
            refreshOptions()
        }
        refreshOptions()

        binding.searchEditText.requestFocus()
        binding.searchEditText.post {
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun refreshOptions() {
        val query = binding.searchEditText.text?.toString().orEmpty().trim()
        val filtered = pickerOptions()
            .filter { option ->
                query.isBlank() ||
                    option.label.contains(query, ignoreCase = true) ||
                    option.secondary.contains(query, ignoreCase = true)
            }
            .map { option ->
                CategoryPickerOption(
                    label = option.label,
                    kind = CategoryOptionKind.EXISTING,
                    id = option.id,
                    secondary = option.secondary
                )
            }
            .toMutableList()

        val canAddInline = supportsInlineValueSelection() &&
            query.isNotBlank() &&
            pickerOptions().none { it.label.equals(query, ignoreCase = true) }
        if (canAddInline) {
            filtered.add(0, CategoryPickerOption(label = query, kind = CategoryOptionKind.ADD_NEW))
        }

        adapter.submitList(filtered)
        binding.addButton.isVisible = true
        binding.emptyTextView.isVisible = filtered.isEmpty()
        binding.emptyTextView.text = when (fieldType) {
            FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO -> getString(R.string.no_locations_available)
            FIELD_STOCK_IN_PARTNER -> getString(R.string.no_partners_available)
            FIELD_STOCK_OUT_CUSTOMER -> getString(R.string.no_customers_available)
            else -> getString(R.string.no_matching_categories)
        }
    }

    private fun pickerOptions(): List<CategoryPickerOption> {
        if (fieldType == FIELD_STOCK_IN_LOCATION || fieldType == FIELD_MOVE_STOCK_FROM || fieldType == FIELD_MOVE_STOCK_TO) {
            val state = stockInViewModel.uiState.value
            return state?.branches.orEmpty().map { branch ->
                val count = state?.inventoryItems.orEmpty().count { it.branchId == branch.id }
                CategoryPickerOption(
                    label = branch.name,
                    id = branch.id,
                    secondary = resources.getQuantityString(R.plurals.location_item_count, count, count)
                )
            }
        }
        if (fieldType == FIELD_STOCK_IN_PARTNER) {
            val suppliers = purchaseViewModel.uiState.value?.suppliers
                ?.takeIf { it.isNotEmpty() }
                ?: stockInViewModel.uiState.value?.suppliers.orEmpty()
            return suppliers.map { supplier ->
                CategoryPickerOption(
                    label = supplier.name,
                    id = supplier.id,
                    secondary = supplier.phone.ifBlank { supplier.contactPerson }
                )
            }
        }
        if (fieldType == FIELD_STOCK_OUT_CUSTOMER) {
            return stockInViewModel.uiState.value?.customers.orEmpty().map { customer ->
                CategoryPickerOption(customer.name, id = customer.id, secondary = customer.phone.ifBlank { customer.email })
            }
        }
        val baseOptions = when (fieldType) {
            FIELD_BRAND -> viewModel.brandSuggestionsForCategory(parentCategory)
            FIELD_LOCATION -> viewModel.uiState.value?.branches.orEmpty().map { it.name.trim() }.filter { it.isNotBlank() }
            else -> FilterOptions.categories.filterNot { it == FilterOptions.ALL_CATEGORY }
        }
        val derivedOptions = when (fieldType) {
            FIELD_BRAND -> emptyList()
            FIELD_LOCATION -> emptyList()
            else -> viewModel.uiState.value?.inventoryItems.orEmpty()
                .map { it.category.trim() }
                .filter { it.isNotBlank() }
        }
        val mergedOptions = linkedMapOf<String, String>()
        (baseOptions + derivedOptions + pendingValues + listOf(currentValue))
            .filter { it.isNotBlank() }
            .forEach { option ->
                mergedOptions.putIfAbsent(option.lowercase(), option)
            }

        val orderedBaseOptions = baseOptions.distinctBy { it.lowercase() }.mapNotNull { mergedOptions[it.lowercase()] }
        val customOptions = mergedOptions
            .filterKeys { key -> baseOptions.none { it.equals(key, ignoreCase = true) } }
            .values
            .sortedBy { it.lowercase() }
        return (orderedBaseOptions + customOptions).map { label ->
            CategoryPickerOption(
                label = label,
                id = if (fieldType == FIELD_LOCATION) {
                    viewModel.uiState.value?.branches?.firstOrNull { it.name.equals(label, ignoreCase = true) }?.id.orEmpty()
                } else ""
            )
        }
    }

    private fun showAddValueDialog(initialValue: String) {
        val dialogBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        dialogBinding.skuInputLayout.hint = getString(fieldLabelRes())
        dialogBinding.skuInputEditText.filters = emptyArray()
        dialogBinding.skuInputEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        dialogBinding.skuInputEditText.setText(initialValue)
        dialogBinding.skuInputEditText.setSelection(dialogBinding.skuInputEditText.text?.length ?: 0)
        dialogBinding.root.findViewById<android.widget.TextView>(R.id.skuInputHelperTextView)?.text =
            getString(R.string.category_add_prompt)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(fieldLabelRes())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                dialogBinding.skuInputLayout.error = null
                val candidate = dialogBinding.skuInputEditText.text?.toString().orEmpty().trim()
                when {
                    candidate.isBlank() -> dialogBinding.skuInputLayout.error = getString(fieldRequiredErrorRes())
                    pickerOptions().any { it.label.equals(candidate, ignoreCase = true) } ->
                        dialogBinding.skuInputLayout.error = getString(fieldAlreadyExistsErrorRes())
                    fieldType == FIELD_STOCK_IN_LOCATION || fieldType == FIELD_MOVE_STOCK_FROM -> {
                        pendingCreatedValue = candidate
                        stockInViewModel.addBranch(candidate)
                        positiveButton.isEnabled = false
                    }
                    fieldType == FIELD_MOVE_STOCK_TO -> {
                        pendingCreatedValue = candidate
                        stockInViewModel.addDestinationBranch(candidate)
                        positiveButton.isEnabled = false
                    }
                    fieldType == FIELD_STOCK_IN_PARTNER -> {
                        pendingCreatedValue = candidate
                        stockInViewModel.addSupplier(candidate)
                        positiveButton.isEnabled = false
                    }
                    fieldType == FIELD_STOCK_OUT_CUSTOMER -> {
                        pendingCreatedValue = candidate
                        stockInViewModel.addCustomer(candidate)
                        positiveButton.isEnabled = false
                    }
                    fieldType == FIELD_LOCATION -> {
                        positiveButton.isEnabled = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            branchRepository.addBranch(Branch(name = candidate))
                                .onSuccess { branchId ->
                                    positiveButton.isEnabled = true
                                    returnSelection(candidate, selectedId = branchId, addedValue = candidate)
                                    dialog.dismiss()
                                }
                                .onFailure {
                                    positiveButton.isEnabled = true
                                    dialogBinding.skuInputLayout.error = it.message ?: getString(R.string.location_save_failed)
                                }
                        }
                    }
                    else -> {
                        returnSelection(candidate, addedValue = candidate)
                        dialog.dismiss()
                    }
                }
            }
            dialogBinding.skuInputEditText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialogBinding.skuInputEditText.post {
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(dialogBinding.skuInputEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun supportsInlineValueSelection(): Boolean =
        fieldType != FIELD_LOCATION && fieldType != FIELD_STOCK_IN_LOCATION &&
            fieldType != FIELD_MOVE_STOCK_FROM && fieldType != FIELD_MOVE_STOCK_TO &&
            fieldType != FIELD_STOCK_IN_PARTNER && fieldType != FIELD_STOCK_OUT_CUSTOMER

    private fun fieldLabelRes(): Int = when (fieldType) {
        FIELD_BRAND -> R.string.brand
        FIELD_LOCATION, FIELD_STOCK_IN_LOCATION -> R.string.location
        FIELD_MOVE_STOCK_FROM -> R.string.from
        FIELD_MOVE_STOCK_TO -> R.string.to
        FIELD_STOCK_IN_PARTNER -> R.string.partner
        FIELD_STOCK_OUT_CUSTOMER -> R.string.customer
        else -> R.string.category
    }

    private fun fieldRequiredErrorRes(): Int = when (fieldType) {
        FIELD_BRAND -> R.string.brand_required
        FIELD_LOCATION, FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO -> R.string.location_required
        FIELD_STOCK_IN_PARTNER -> R.string.supplier_name_required
        FIELD_STOCK_OUT_CUSTOMER -> R.string.customer_name_required
        else -> R.string.category_required
    }

    private fun fieldAlreadyExistsErrorRes(): Int = when (fieldType) {
        FIELD_BRAND -> R.string.brand_already_exists
        FIELD_LOCATION, FIELD_STOCK_IN_LOCATION, FIELD_MOVE_STOCK_FROM, FIELD_MOVE_STOCK_TO -> R.string.location_already_exists
        FIELD_STOCK_IN_PARTNER -> R.string.supplier_name_already_exists
        FIELD_STOCK_OUT_CUSTOMER -> R.string.customer_name_already_exists
        else -> R.string.category_already_exists
    }

    private fun returnSelection(selectedValue: String, selectedId: String = "", addedValue: String = "") {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            RESULT_KEY,
            Bundle().apply {
                putString(RESULT_FIELD_TYPE, fieldType)
                putString(RESULT_SELECTED_VALUE, selectedValue)
                putString(RESULT_SELECTED_ID, selectedId)
                putString(RESULT_ADDED_VALUE, addedValue)
            }
        )
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FIELD_CATEGORY = "category"
        const val FIELD_BRAND = "brand"
        const val FIELD_LOCATION = "location"
        const val FIELD_STOCK_IN_LOCATION = "stockInLocation"
        const val FIELD_STOCK_IN_PARTNER = "stockInPartner"
        const val FIELD_STOCK_OUT_CUSTOMER = "stockOutCustomer"
        const val FIELD_MOVE_STOCK_FROM = "moveStockFrom"
        const val FIELD_MOVE_STOCK_TO = "moveStockTo"
        const val ARG_FIELD_TYPE = "fieldType"
        const val ARG_CURRENT_CATEGORY = "currentCategory"
        const val ARG_PENDING_CATEGORIES = "pendingCategories"
        const val ARG_CURRENT_BRANCH_ID = "currentBranchId"
        const val ARG_PARENT_CATEGORY = "parentCategory"
        const val RESULT_KEY = "categoryPickerResult"
        const val RESULT_FIELD_TYPE = "fieldType"
        const val RESULT_SELECTED_VALUE = "selectedValue"
        const val RESULT_SELECTED_ID = "selectedId"
        const val RESULT_ADDED_VALUE = "addedValue"
    }
}

private data class CategoryPickerOption(
    val label: String,
    val kind: CategoryOptionKind = CategoryOptionKind.EXISTING,
    val id: String = "",
    val secondary: String = ""
)

private enum class CategoryOptionKind {
    EXISTING,
    ADD_NEW
}

private class CategoryPickerAdapter(
    private val onCategorySelected: (CategoryPickerOption) -> Unit
) : RecyclerView.Adapter<CategoryPickerAdapter.CategoryViewHolder>() {

    private val items = mutableListOf<CategoryPickerOption>()

    fun submitList(newItems: List<CategoryPickerOption>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position], onCategorySelected)
    }

    override fun getItemCount(): Int = items.size

    class CategoryViewHolder(
        private val binding: ItemCategoryOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(option: CategoryPickerOption, onCategorySelected: (CategoryPickerOption) -> Unit) {
            binding.categoryTextView.text = option.label
            binding.secondaryTextView.text = option.secondary
            binding.secondaryTextView.isVisible = option.secondary.isNotBlank()
            val isAddRow = option.kind == CategoryOptionKind.ADD_NEW
            binding.categoryTextView.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isAddRow) R.color.inventory_primary else R.color.inventory_text_primary
                )
            )
            binding.trailingIconView.isVisible = isAddRow
            if (isAddRow) {
                binding.trailingIconView.setImageResource(R.drawable.ic_add)
            }
            binding.root.setOnClickListener { onCategorySelected(option) }
        }
    }
}
