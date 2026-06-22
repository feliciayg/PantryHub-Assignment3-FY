package com.example.pantryhub_assignment3_fy.ui.storage

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.example.pantryhub_assignment3_fy.databinding.ItemInventoryOptionSwipeBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.model.InventoryOptionType
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.ui.restock.PurchaseEditorViewModel
import com.example.pantryhub_assignment3_fy.ui.movement.StockInTransactionViewModel
import com.example.pantryhub_assignment3_fy.ui.supplier.AddEditSupplierFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CategoryPickerFragment : Fragment() {
    private var _binding: FragmentCategoryPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private val optionViewModel: InventoryOptionViewModel by activityViewModels()
    private val stockInViewModel: StockInTransactionViewModel by activityViewModels()
    private val purchaseViewModel: PurchaseEditorViewModel by activityViewModels()
    private val branchRepository = BranchRepository()
    private lateinit var adapter: CategoryPickerAdapter
    private var fieldType: String = FIELD_CATEGORY
    private var currentValue: String = ""
    private var pendingValues: List<String> = emptyList()
    private var parentCategory: String = ""
    private var pendingCreatedValue: String = ""
    private var managedOptions: List<InventoryOption> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fieldType = arguments?.getString(ARG_FIELD_TYPE).orEmpty().ifBlank { FIELD_CATEGORY }
        currentValue = arguments?.getString(ARG_CURRENT_CATEGORY).orEmpty().trim()
        pendingValues = arguments?.getStringArray(ARG_PENDING_CATEGORIES)?.toList().orEmpty()
        parentCategory = arguments?.getString(ARG_PARENT_CATEGORY).orEmpty()
        adapter = CategoryPickerAdapter(
            onCategorySelected = { option ->
                when (option.kind) {
                    CategoryOptionKind.EXISTING -> returnSelection(option.label, option.id)
                    CategoryOptionKind.ADD_NEW -> showAddValueDialog(option.label)
                }
            },
            onEdit = ::showEditOptionDialog,
            onDelete = ::requestDeleteOption
        )

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
            if (fieldType == FIELD_STOCK_IN_PARTNER || fieldType == FIELD_STOCK_OUT_CUSTOMER) {
                val partnerType = if (fieldType == FIELD_STOCK_OUT_CUSTOMER) {
                    PartnerType.CUSTOMER
                } else {
                    PartnerType.SUPPLIER
                }
                // Reuse the complete partner form so customer contact details are not lost.
                findNavController().navigate(
                    R.id.action_categoryPickerFragment_to_addEditSupplierFragment,
                    Bundle().apply {
                        putString(AddEditSupplierFragment.ARG_PARTNER_TYPE, partnerType.value)
                    }
                )
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
        val initialSearchText = if (usesEmptyInitialSearch()) "" else currentValue
        binding.searchEditText.setText(initialSearchText)
        binding.searchEditText.setSelection(binding.searchEditText.text?.length ?: 0)
        binding.searchEditText.doAfterTextChanged { refreshOptions() }

        viewModel.uiState.observe(viewLifecycleOwner) {
            refreshOptions()
        }
        optionViewModel.uiState.observe(viewLifecycleOwner) { state ->
            managedOptions = when (masterOptionType()) {
                InventoryOptionType.CATEGORY -> state.categories
                InventoryOptionType.BRAND -> state.brands
                null -> emptyList()
            }
            refreshOptions()
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                optionViewModel.clearError()
            }
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
        masterOptionType()?.let(optionViewModel::loadOptions)
        refreshOptions()

        binding.searchEditText.requestFocus()
        binding.searchEditText.post {
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun refreshOptions() {
        val query = binding.searchEditText.text?.toString().orEmpty().trim()
        val allOptions = pickerOptions()
        val filtered = allOptions
            .filter { option ->
                query.isBlank() ||
                    option.label.contains(query, ignoreCase = true) ||
                    option.secondary.contains(query, ignoreCase = true)
            }
            .map { option -> option.copy(kind = CategoryOptionKind.EXISTING) }
            .toMutableList()

        val canAddInline = supportsInlineValueSelection() &&
            query.isNotBlank() &&
            allOptions.none { it.label.equals(query, ignoreCase = true) }
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
        masterOptionType()?.let { type ->
            val preferredBrandOrder = if (type == InventoryOptionType.BRAND) {
                viewModel.brandSuggestionsForCategory(parentCategory)
            } else {
                emptyList()
            }
            val ordered = managedOptions.sortedWith(
                compareBy<InventoryOption> {
                    val index = preferredBrandOrder.indexOfFirst { preferred ->
                        preferred.equals(it.name, ignoreCase = true)
                    }
                    if (index == -1) Int.MAX_VALUE else index
                }.thenBy { it.name.lowercase() }
            )
            val persistedNames = ordered.map { it.name }
            val unsavedCompatibilityValues = (pendingValues + currentValue)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { candidate -> persistedNames.none { it.equals(candidate, ignoreCase = true) } }
                .distinctBy { it.lowercase() }
            return ordered.map { option ->
                CategoryPickerOption(
                    label = option.name,
                    id = option.id,
                    masterOption = option
                )
            } + unsavedCompatibilityValues.map { label ->
                CategoryPickerOption(label = label)
            }
        }
        if (isTransactionLocationField()) {
            val state = stockInViewModel.uiState.value
            val itemCountsByBranch = state?.inventoryItems.orEmpty()
                .groupingBy { it.branchId }
                .eachCount()
            return state?.branches.orEmpty().map { branch ->
                val count = itemCountsByBranch[branch.id] ?: 0
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
            return suppliers
                .filter { PartnerType.fromValue(it.partnerType) == PartnerType.SUPPLIER }
                .map { supplier ->
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
                        val type = masterOptionType()
                        if (type == null) {
                            returnSelection(candidate, addedValue = candidate)
                            dialog.dismiss()
                        } else {
                            positiveButton.isEnabled = false
                            optionViewModel.addOption(type, candidate) { result ->
                                positiveButton.isEnabled = true
                                result
                                    .onSuccess { option ->
                                        returnSelection(option.name, option.id, option.name)
                                        dialog.dismiss()
                                    }
                                    .onFailure {
                                        dialogBinding.skuInputLayout.error =
                                            it.message ?: getString(R.string.inventory_option_save_failed)
                                    }
                            }
                        }
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

    private fun supportsInlineValueSelection(): Boolean = masterOptionType() != null

    private fun usesEmptyInitialSearch(): Boolean =
        fieldType == FIELD_LOCATION ||
            isTransactionLocationField() ||
            fieldType == FIELD_STOCK_IN_PARTNER ||
            fieldType == FIELD_STOCK_OUT_CUSTOMER

    private fun isTransactionLocationField(): Boolean =
        fieldType == FIELD_STOCK_IN_LOCATION ||
            fieldType == FIELD_MOVE_STOCK_FROM ||
            fieldType == FIELD_MOVE_STOCK_TO

    private fun masterOptionType(): InventoryOptionType? = when (fieldType) {
        FIELD_CATEGORY -> InventoryOptionType.CATEGORY
        FIELD_BRAND -> InventoryOptionType.BRAND
        else -> null
    }

    private fun showEditOptionDialog(option: CategoryPickerOption) {
        val masterOption = option.masterOption ?: return
        val type = masterOptionType() ?: return
        optionViewModel.usageCount(type, masterOption.name) { countResult ->
            countResult
                .onSuccess { usageCount -> showRenameDialog(type, masterOption, usageCount) }
                .onFailure { Snackbar.make(binding.root, it.message.orEmpty(), Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun showRenameDialog(
        type: InventoryOptionType,
        option: InventoryOption,
        usageCount: Int
    ) {
        val dialogBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        dialogBinding.skuInputLayout.hint = getString(fieldLabelRes())
        dialogBinding.skuInputEditText.setText(option.name)
        dialogBinding.skuInputEditText.setSelection(option.name.length)
        dialogBinding.skuInputHelperTextView.text = if (usageCount > 0) {
            resources.getQuantityString(
                R.plurals.rename_inventory_option_usage,
                usageCount,
                usageCount
            )
        } else {
            getString(R.string.rename_inventory_option_unused)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.rename_inventory_option, option.name))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = dialogBinding.skuInputEditText.text?.toString().orEmpty().trim()
                dialogBinding.skuInputLayout.error = null
                if (newName.isBlank()) {
                    dialogBinding.skuInputLayout.error = getString(fieldRequiredErrorRes())
                    return@setOnClickListener
                }
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                optionViewModel.renameOption(type, option, newName) { result ->
                    button.isEnabled = true
                    result
                        .onSuccess {
                            if (currentValue.equals(option.name, ignoreCase = true)) {
                                returnSelection(newName, option.id)
                            }
                            dialog.dismiss()
                            Snackbar.make(binding.root, R.string.inventory_option_renamed, Snackbar.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            dialogBinding.skuInputLayout.error =
                                it.message ?: getString(R.string.inventory_option_save_failed)
                        }
                }
            }
        }
        dialog.show()
    }

    private fun requestDeleteOption(option: CategoryPickerOption) {
        val masterOption = option.masterOption ?: return
        val type = masterOptionType() ?: return
        optionViewModel.usageCount(type, masterOption.name) { countResult ->
            countResult
                .onSuccess { usageCount ->
                    if (usageCount == 0) showUnusedDeleteConfirmation(type, masterOption)
                    else showReplacementDeleteDialog(type, masterOption, usageCount)
                }
                .onFailure { Snackbar.make(binding.root, it.message.orEmpty(), Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun showUnusedDeleteConfirmation(
        type: InventoryOptionType,
        option: InventoryOption
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_inventory_option, option.name))
            .setMessage(R.string.delete_unused_inventory_option_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteOption(type, option, replacementName = null)
            }
            .show()
    }

    private fun showReplacementDeleteDialog(
        type: InventoryOptionType,
        option: InventoryOption,
        usageCount: Int
    ) {
        val replacements = managedOptions
            .filterNot { it.id == option.id }
            .map { it.name }
            .toMutableList()
        if (type == InventoryOptionType.CATEGORY &&
            !option.name.equals(OTHER_CATEGORY_VALUE, ignoreCase = true) &&
            replacements.none { it.equals(OTHER_CATEGORY_VALUE, ignoreCase = true) }
        ) {
            replacements += OTHER_CATEGORY_VALUE
        }
        if (type == InventoryOptionType.BRAND) {
            replacements.add(0, NO_BRAND_VALUE)
        }
        if (replacements.isEmpty()) {
            Snackbar.make(binding.root, R.string.create_replacement_before_delete, Snackbar.LENGTH_LONG).show()
            return
        }

        var selectedIndex = 0
        val labels = replacements.map {
            if (type == InventoryOptionType.BRAND && it == NO_BRAND_VALUE) {
                getString(R.string.no_brand)
            } else if (type == InventoryOptionType.CATEGORY &&
                it.equals(OTHER_CATEGORY_VALUE, ignoreCase = true)
            ) {
                getString(R.string.other)
            } else {
                it
            }
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_inventory_option, option.name))
            .setMessage(
                resources.getQuantityString(
                    R.plurals.replace_inventory_option_usage,
                    usageCount,
                    usageCount
                )
            )
            .setSingleChoiceItems(labels, selectedIndex) { _, which -> selectedIndex = which }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.replace_and_delete) { _, _ ->
                deleteOption(type, option, replacements[selectedIndex])
            }
            .show()
    }

    private fun deleteOption(
        type: InventoryOptionType,
        option: InventoryOption,
        replacementName: String?
    ) {
        optionViewModel.deleteOption(type, option, replacementName) { result ->
            result
                .onSuccess {
                    if (currentValue.equals(option.name, ignoreCase = true)) {
                        returnSelection(
                            selectedValue = replacementName.orEmpty(),
                            clearSelection = replacementName.isNullOrBlank()
                        )
                    } else {
                        Snackbar.make(binding.root, R.string.inventory_option_deleted, Snackbar.LENGTH_SHORT).show()
                    }
                }
                .onFailure {
                    Snackbar.make(
                        binding.root,
                        it.message ?: getString(R.string.inventory_option_delete_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

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

    private fun returnSelection(
        selectedValue: String,
        selectedId: String = "",
        addedValue: String = "",
        clearSelection: Boolean = false
    ) {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            RESULT_KEY,
            Bundle().apply {
                putString(RESULT_FIELD_TYPE, fieldType)
                putString(RESULT_SELECTED_VALUE, selectedValue)
                putString(RESULT_SELECTED_ID, selectedId)
                putString(RESULT_ADDED_VALUE, addedValue)
                putBoolean(RESULT_CLEAR_SELECTION, clearSelection)
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
        const val ARG_PARENT_CATEGORY = "parentCategory"
        const val RESULT_KEY = "categoryPickerResult"
        const val RESULT_FIELD_TYPE = "fieldType"
        const val RESULT_SELECTED_VALUE = "selectedValue"
        const val RESULT_SELECTED_ID = "selectedId"
        const val RESULT_ADDED_VALUE = "addedValue"
        const val RESULT_CLEAR_SELECTION = "clearSelection"
        private const val NO_BRAND_VALUE = ""
        private const val OTHER_CATEGORY_VALUE = "Other"
    }
}

private data class CategoryPickerOption(
    val label: String,
    val kind: CategoryOptionKind = CategoryOptionKind.EXISTING,
    val id: String = "",
    val secondary: String = "",
    val masterOption: InventoryOption? = null
)

private enum class CategoryOptionKind {
    EXISTING,
    ADD_NEW
}

private class CategoryPickerAdapter(
    private val onCategorySelected: (CategoryPickerOption) -> Unit,
    private val onEdit: (CategoryPickerOption) -> Unit,
    private val onDelete: (CategoryPickerOption) -> Unit
) : RecyclerView.Adapter<CategoryPickerAdapter.CategoryViewHolder>() {

    private val items = mutableListOf<CategoryPickerOption>()
    private var openOptionKey: String? = null

    fun submitList(newItems: List<CategoryPickerOption>) {
        items.clear()
        items.addAll(newItems)
        if (items.none { it.key == openOptionKey }) openOptionKey = null
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemInventoryOptionSwipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val option = items[position]
        holder.bind(
            option = option,
            isOpen = option.key == openOptionKey,
            onCategorySelected = {
                closeOpenOption()
                onCategorySelected(it)
            },
            onEdit = {
                closeOpenOption()
                onEdit(it)
            },
            onDelete = {
                closeOpenOption()
                onDelete(it)
            },
            onOpened = {
                val previous = openOptionKey
                openOptionKey = it.key
                previous?.let(::notifyOptionChanged)
            },
            onClosed = {
                if (openOptionKey == it.key) openOptionKey = null
            }
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: CategoryViewHolder) {
        holder.closeActions(animate = false)
        super.onViewRecycled(holder)
    }

    private fun closeOpenOption() {
        val previous = openOptionKey ?: return
        openOptionKey = null
        notifyOptionChanged(previous)
    }

    private fun notifyOptionChanged(key: String) {
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) notifyItemChanged(index)
    }

    class CategoryViewHolder(
        private val binding: ItemInventoryOptionSwipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var isRevealed = false
        private var downX = 0f
        private var downY = 0f
        private var startTranslationX = 0f
        private var isDraggingSwipe = false

        fun bind(
            option: CategoryPickerOption,
            isOpen: Boolean,
            onCategorySelected: (CategoryPickerOption) -> Unit,
            onEdit: (CategoryPickerOption) -> Unit,
            onDelete: (CategoryPickerOption) -> Unit,
            onOpened: (CategoryPickerOption) -> Unit,
            onClosed: (CategoryPickerOption) -> Unit
        ) {
            binding.categoryTextView.text = option.label
            binding.secondaryTextView.text = option.secondary
            binding.secondaryTextView.isVisible = option.secondary.isNotBlank()
            val isAddRow = option.kind == CategoryOptionKind.ADD_NEW
            val isManageable = option.masterOption != null
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
            if (isOpen && isManageable) revealActions(animate = false) else closeActions(animate = false)
            binding.optionForeground.setOnClickListener {
                if (isRevealed) {
                    closeActions()
                    onClosed(option)
                } else {
                    onCategorySelected(option)
                }
            }
            binding.editAction.setOnClickListener { if (isManageable) onEdit(option) }
            binding.deleteAction.setOnClickListener { if (isManageable) onDelete(option) }
            setupSwipeTouch(option, isManageable, onOpened, onClosed)
        }

        private fun setupSwipeTouch(
            option: CategoryPickerOption,
            isManageable: Boolean,
            onOpened: (CategoryPickerOption) -> Unit,
            onClosed: (CategoryPickerOption) -> Unit
        ) {
            if (!isManageable) {
                binding.optionForeground.setOnTouchListener(null)
                return
            }
            val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
            binding.optionForeground.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startTranslationX = binding.optionForeground.translationX
                        isDraggingSwipe = false
                        binding.optionForeground.animate().cancel()
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!isDraggingSwipe &&
                            kotlin.math.abs(dx) > touchSlop &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy)
                        ) {
                            isDraggingSwipe = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (isDraggingSwipe) {
                            setSwipeOffset(startTranslationX + dx)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isDraggingSwipe) return@setOnTouchListener false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (kotlin.math.abs(binding.optionForeground.translationX) > revealWidth() * 0.25f) {
                            revealActions()
                            onOpened(option)
                        } else {
                            closeActions()
                            onClosed(option)
                        }
                        isDraggingSwipe = false
                        true
                    }
                    else -> false
                }
            }
        }

        private fun setSwipeOffset(offset: Float) {
            val clamped = offset.coerceIn(-revealWidth(), 0f)
            binding.swipeActionContainer.isVisible = clamped < 0f
            binding.optionForeground.translationX = clamped
            isRevealed = clamped < 0f
        }

        private fun revealActions(animate: Boolean = true) {
            isRevealed = true
            binding.swipeActionContainer.isVisible = true
            moveForegroundTo(-revealWidth(), animate)
        }

        fun closeActions(animate: Boolean = true) {
            isRevealed = false
            moveForegroundTo(0f, animate)
        }

        private fun moveForegroundTo(target: Float, animate: Boolean) {
            binding.optionForeground.animate().cancel()
            if (animate) {
                binding.optionForeground.animate()
                    .translationX(target)
                    .setDuration(160L)
                    .withEndAction { binding.swipeActionContainer.isVisible = target < 0f }
                    .start()
            } else {
                binding.optionForeground.translationX = target
                binding.swipeActionContainer.isVisible = target < 0f
            }
        }

        private fun revealWidth(): Float =
            binding.root.resources.getDimensionPixelSize(R.dimen.inventory_swipe_action_width) * 2f
    }

    private val CategoryPickerOption.key: String
        get() = masterOption?.id?.takeIf { it.isNotBlank() } ?: "${kind.name}:$label"
}
