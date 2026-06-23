package com.example.pantryhub_assignment3_fy.ui.storage

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.data.repository.CustomUnitRepository
import com.example.pantryhub_assignment3_fy.data.repository.RestockOrderRepository
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetPhotoOptionsBinding
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetSkuOptionsBinding
import com.example.pantryhub_assignment3_fy.databinding.DialogEnterSkuBinding
import com.example.pantryhub_assignment3_fy.databinding.FragmentAddEditInventoryItemBinding
import com.example.pantryhub_assignment3_fy.model.Branch
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.InventoryOption
import com.example.pantryhub_assignment3_fy.notification.InventoryReminderScheduler
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.io.File

/**
 * Shared form for adding stock, editing stock, and storing received restock items.
 */
class AddEditInventoryItemFragment : Fragment() {
    private var _binding: FragmentAddEditInventoryItemBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private val skuViewModel: SkuFormViewModel by viewModels()
    private val nameViewModel: ItemNameFormViewModel by viewModels()
    private val quantityUnitViewModel: QuantityUnitFormViewModel by viewModels()
    private val customUnitViewModel: CustomUnitViewModel by viewModels()
    private val restockOrderRepository = RestockOrderRepository()
    private var mode: String = MODE_ADD
    private var inventoryItemId: String? = null
    private var sourceRestockOrderId: String? = null
    private var saveInProgress = false
    private var selectedBranchId: String = ""
    private var selectedBranchName: String = ""
    private var branches: List<Branch> = emptyList()
    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""
    private var selectedImageUri: String = ""
    private var selectedUnit: String = DEFAULT_UNIT
    private var skuInputDialog: AlertDialog? = null
    private var skuInputBinding: DialogEnterSkuBinding? = null
    private var pendingScanTarget: ScanTarget = ScanTarget.SKU
    private var pendingCameraPhotoUri: Uri? = null
    private val pendingCategoryOptions = linkedSetOf<String>()
    private val pendingBrandOptions = linkedSetOf<String>()

    private val itemPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val capturedUri = pendingCameraPhotoUri
        pendingCameraPhotoUri = null
        if (result.resultCode == Activity.RESULT_OK && capturedUri != null) {
            renderSelectedImage(capturedUri)
        }
    }

    private val itemPhotoPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            _binding?.root?.let {
                Snackbar.make(it, R.string.item_photo_camera_permission_denied, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::renderSelectedImage)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        _binding?.root?.let {
            Snackbar.make(it, pendingScanTarget.prototypeMessageRes, Snackbar.LENGTH_LONG).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraPrototype()
        } else {
            _binding?.root?.let {
                Snackbar.make(it, pendingScanTarget.permissionDeniedMessageRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditInventoryItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val restoredImageUri = savedInstanceState?.getString(KEY_SELECTED_IMAGE_URI).orEmpty()
        pendingCameraPhotoUri = savedInstanceState?.getString(KEY_PENDING_CAMERA_PHOTO_URI)?.let(Uri::parse)
        mode = arguments?.getString(ARG_MODE) ?: MODE_ADD
        inventoryItemId = arguments?.getString(ARG_INVENTORY_ITEM_ID)
        sourceRestockOrderId = arguments?.getString(ARG_SOURCE_RESTOCK_ORDER_ID)
        setupToolbar()
        setupDropdowns()
        setupDatePickers()
        setupRowActions()
        setupVisibleRowSync()
        setupMode()
        if (restoredImageUri.isNotBlank()) renderSelectedImage(restoredImageUri)
        setupSkuState()
        setupNameState()
        setupQuantityUnitState()
        customUnitViewModel.uiState.observe(viewLifecycleOwner) { state ->
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                customUnitViewModel.clearError()
            }
        }
        customUnitViewModel.loadUnits()
        binding.saveButton.setOnClickListener { saveInventoryItem() }
        binding.cancelButton.setOnClickListener { findNavController().popBackStack() }
        binding.branchEditText.setOnItemClickListener { _, _, position, _ ->
            branches.getOrNull(position)?.let { branch ->
                selectedBranchId = branch.id
                selectedBranchName = branch.name
            }
        }
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle>(CategoryPickerFragment.RESULT_KEY)
            ?.observe(viewLifecycleOwner) { result ->
                val fieldType = result.getString(CategoryPickerFragment.RESULT_FIELD_TYPE).orEmpty()
                val selectedValue = result.getString(CategoryPickerFragment.RESULT_SELECTED_VALUE).orEmpty()
                val addedValue = result.getString(CategoryPickerFragment.RESULT_ADDED_VALUE).orEmpty()
                val selectedId = result.getString(CategoryPickerFragment.RESULT_SELECTED_ID).orEmpty()
                val clearSelection = result.getBoolean(CategoryPickerFragment.RESULT_CLEAR_SELECTION, false)
                when (fieldType) {
                    CategoryPickerFragment.FIELD_CATEGORY -> {
                        if (addedValue.isNotBlank()) registerPendingCategory(addedValue)
                        if (clearSelection) applyCategorySelection("")
                        else if (selectedValue.isNotBlank()) applyCategorySelection(selectedValue)
                    }
                    CategoryPickerFragment.FIELD_BRAND -> {
                        if (addedValue.isNotBlank()) registerPendingBrand(addedValue)
                        if (clearSelection) applyBrandSelection("")
                        else if (selectedValue.isNotBlank()) applyBrandSelection(selectedValue)
                    }
                    CategoryPickerFragment.FIELD_LOCATION -> {
                        if (selectedValue.isNotBlank()) applyLocationSelection(selectedId, selectedValue)
                    }
                }
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<Bundle>(CategoryPickerFragment.RESULT_KEY)
            }
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
            ?.observe(viewLifecycleOwner) { barcode ->
                findNavController().currentBackStackEntry?.savedStateHandle
                    ?.remove<String>(BarcodeScannerPrototypeFragment.RESULT_BARCODE)
                applyScannedBarcode(barcode)
            }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            branches = state.branches
            binding.branchEditText.setAdapter(dropdownAdapter(branches.map { it.name }))
            binding.categoryEditText.setAdapter(dropdownAdapter(categoryDropdownOptions()))
            refreshBrandSuggestions()
            if (selectedBranchId.isNotBlank()) {
                val linkedBranch = branches.firstOrNull { it.id == selectedBranchId }
                if (linkedBranch != null) {
                    selectedBranchName = linkedBranch.name
                    binding.branchEditText.setText(linkedBranch.name, false)
                }
            }
            state.errorMessage?.let {
                when {
                    it.contains("Duplicate SKU", ignoreCase = true) -> binding.skuLayout.error = it
                    it.contains("Duplicate barcode", ignoreCase = true) -> binding.barcodeLayout.error = it
                }
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                saveInProgress = false
                viewModel.clearMessages()
            }
            if (mode == MODE_EDIT && binding.skuEditText.text.isNullOrBlank()) {
                inventoryItemId?.let(viewModel::findInventoryItem)?.let {
                    fillForm(it)
                    skuViewModel.useExistingProductSku(it.sku)
                }
            }
            state.successMessage?.let {
                if (saveInProgress) {
                    saveInProgress = false
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                    viewModel.clearMessages()
                    markSourceRestockOrderStoredIfNeeded()
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.formHeader.closeButton.setOnClickListener { findNavController().popBackStack() }
        binding.attributesSectionHeader.titleTextView.setText(R.string.attributes)
        binding.attributesSectionHeader.infoButton.visibility = View.VISIBLE
        binding.pricingSectionHeader.titleTextView.setText(R.string.pricing)
        binding.startingQuantitySectionHeader.titleTextView.setText(R.string.starting_quantity)
    }

    private fun setupRowActions() {
        binding.imagePickerContainer.setOnClickListener {
            showPhotoOptions()
        }
        binding.skuInfoButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.what_is_a_sku)
                .setMessage(R.string.sku_information_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        binding.attributesSectionHeader.infoButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.attributes_info_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        binding.skuRow.setOnClickListener {
            showSkuOptions()
        }
        binding.nameRow.setOnClickListener {
            binding.nameEditText.requestFocus()
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.nameEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.barcodeRow.setOnClickListener {
            showBarcodeOptions()
        }
        binding.categoryRow.setOnClickListener {
            findNavController().navigate(
                R.id.action_addEditInventoryItemFragment_to_categoryPickerFragment,
                Bundle().apply {
                    putString(CategoryPickerFragment.ARG_FIELD_TYPE, CategoryPickerFragment.FIELD_CATEGORY)
                    putString(CategoryPickerFragment.ARG_CURRENT_CATEGORY, binding.categoryEditText.text.toString().trim())
                    putStringArray(CategoryPickerFragment.ARG_PENDING_CATEGORIES, pendingCategoryOptions.toTypedArray())
                }
            )
        }
        binding.brandRow.setOnClickListener {
            findNavController().navigate(
                R.id.action_addEditInventoryItemFragment_to_categoryPickerFragment,
                Bundle().apply {
                    putString(CategoryPickerFragment.ARG_FIELD_TYPE, CategoryPickerFragment.FIELD_BRAND)
                    putString(CategoryPickerFragment.ARG_CURRENT_CATEGORY, binding.brandEditText.text.toString().trim())
                    putStringArray(CategoryPickerFragment.ARG_PENDING_CATEGORIES, pendingBrandOptions.toTypedArray())
                    putString(CategoryPickerFragment.ARG_PARENT_CATEGORY, binding.categoryEditText.text.toString().trim())
                }
            )
        }
        binding.safetyStockRow.setOnClickListener {
            binding.safetyStockInputEditText.requestFocus()
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.safetyStockInputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.expiryDateRow.setOnClickListener {
            showDatePicker {
                binding.expiryDateEditText.setText(DateUtils.formatInputDate(it))
            }
        }
        binding.costRow.setOnClickListener {
            binding.costInputEditText.requestFocus()
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.costInputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.priceRow.setOnClickListener {
            binding.priceInputEditText.requestFocus()
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(binding.priceInputEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        binding.locationRow.setOnClickListener {
            findNavController().navigate(
                R.id.action_addEditInventoryItemFragment_to_categoryPickerFragment,
                Bundle().apply {
                    putString(CategoryPickerFragment.ARG_FIELD_TYPE, CategoryPickerFragment.FIELD_LOCATION)
                    putString(CategoryPickerFragment.ARG_CURRENT_CATEGORY, binding.branchEditText.text.toString().trim())
                }
            )
        }
        binding.quantityRow.setOnClickListener {
            showQuantityDialog()
        }
        binding.unitRow.setOnClickListener {
            showUnitSheet()
        }
    }

    private fun setupVisibleRowSync() {
        listOf(
            binding.skuEditText,
            binding.barcodeEditText,
            binding.quantityEditText,
            binding.reorderPointEditText,
            binding.expiryDateEditText,
            binding.costPriceEditText,
            binding.sellingPriceEditText
        ).forEach { editText ->
            editText.addTextChangedListener(simpleTextWatcher { updateVisibleRows() })
        }
        binding.categoryEditText.addTextChangedListener(simpleTextWatcher { updateVisibleRows() })
        binding.brandEditText.addTextChangedListener(simpleTextWatcher { updateVisibleRows() })
        binding.branchEditText.addTextChangedListener(simpleTextWatcher { updateVisibleRows() })
        binding.safetyStockInputEditText.addTextChangedListener(simpleTextWatcher {
            val visibleValue = binding.safetyStockInputEditText.text.toString()
            if (binding.reorderPointEditText.text.toString() != visibleValue) {
                binding.reorderPointEditText.setText(visibleValue)
            }
        })
        binding.costInputEditText.addTextChangedListener(simpleTextWatcher {
            val visibleValue = binding.costInputEditText.text.toString()
            if (binding.costPriceEditText.text.toString() != visibleValue) {
                binding.costPriceEditText.setText(visibleValue)
            }
        })
        binding.priceInputEditText.addTextChangedListener(simpleTextWatcher {
            val visibleValue = binding.priceInputEditText.text.toString()
            if (binding.sellingPriceEditText.text.toString() != visibleValue) {
                binding.sellingPriceEditText.setText(visibleValue)
            }
        })
        updateVisibleRows()
    }

    private fun updateVisibleRows() {
        binding.skuValueTextView.text = binding.skuEditText.text.toString()
        binding.barcodeValueTextView.text = binding.barcodeEditText.text.toString()
        binding.categoryValueTextView.text = binding.categoryEditText.text.toString()
        binding.brandValueTextView.text = binding.brandEditText.text.toString()
        val safetyStock = binding.reorderPointEditText.text.toString()
        if (binding.safetyStockInputEditText.text.toString() != safetyStock) {
            binding.safetyStockInputEditText.setText(safetyStock)
            binding.safetyStockInputEditText.setSelection(safetyStock.length)
        }
        binding.expiryDateValueTextView.text = binding.expiryDateEditText.text.toString()
        val cost = binding.costPriceEditText.text.toString()
        if (binding.costInputEditText.text.toString() != cost) {
            binding.costInputEditText.setText(cost)
            binding.costInputEditText.setSelection(cost.length)
        }
        val price = binding.sellingPriceEditText.text.toString()
        if (binding.priceInputEditText.text.toString() != price) {
            binding.priceInputEditText.setText(price)
            binding.priceInputEditText.setSelection(price.length)
        }
        binding.locationValueTextView.text = binding.branchEditText.text.toString()
        binding.quantityValueTextView.text = binding.quantityEditText.text.toString()
        binding.unitValueTextView.text = selectedUnit
    }

    private fun setupSkuState() {
        skuViewModel.sku.observe(viewLifecycleOwner) { sku ->
            if (binding.skuEditText.text.toString() != sku) {
                binding.skuEditText.setText(sku)
            }
        }
        skuViewModel.barcode.observe(viewLifecycleOwner) { barcode ->
            if (binding.barcodeEditText.text.toString() != barcode) {
                binding.barcodeEditText.setText(barcode)
            }
        }
        skuViewModel.validationState.observe(viewLifecycleOwner) { state ->
            val dialog = skuInputDialog
            val inputBinding = skuInputBinding
            if (dialog != null && inputBinding != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.isValidating
                state.errorMessage?.let { inputBinding.skuInputLayout.error = it }
                if (state.acceptedSku != null || state.acceptedBarcode != null) {
                    dialog.dismiss()
                    skuViewModel.clearValidationResult()
                }
            } else if (state.errorMessage != null) {
                Snackbar.make(binding.root, state.errorMessage, Snackbar.LENGTH_LONG).show()
                skuViewModel.clearValidationResult()
            }
        }

        skuViewModel.initialise(mode == MODE_EDIT, binding.skuEditText.text.toString())
        skuViewModel.initialiseBarcode(binding.barcodeEditText.text.toString())
    }

    private fun setupNameState() {
        nameViewModel.name.observe(viewLifecycleOwner) { savedName ->
            if (binding.nameEditText.text.toString() != savedName) {
                binding.nameEditText.setText(savedName)
                binding.nameEditText.setSelection(savedName.length)
            }
        }
        binding.nameEditText.addTextChangedListener(simpleTextWatcher {
            nameViewModel.update(binding.nameEditText.text.toString())
        })
        nameViewModel.initialise(binding.nameEditText.text.toString())
    }

    private fun setupQuantityUnitState() {
        quantityUnitViewModel.quantity.observe(viewLifecycleOwner) { savedQuantity ->
            if (binding.quantityEditText.text.toString() != savedQuantity) {
                binding.quantityEditText.setText(savedQuantity)
            }
        }
        quantityUnitViewModel.unit.observe(viewLifecycleOwner) { savedUnit ->
            selectedUnit = savedUnit.ifBlank { DEFAULT_UNIT }
            updateVisibleRows()
        }
        quantityUnitViewModel.initialise(
            quantityValue = binding.quantityEditText.text.toString(),
            unitValue = selectedUnit
        )
    }

    private fun showQuantityDialog() {
        val startingQuantity = binding.quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
        showQuantityStepperDialog(
            config = QuantityStepperConfig(
                title = getString(R.string.enter_quantity),
                initialQuantity = startingQuantity,
                minimumQuantity = 0.0,
                validationMessage = getString(R.string.enter_valid_quantity)
            )
        ) { result ->
            quantityUnitViewModel.updateQuantity(result.quantity.toStorageQuantityText())
        }
    }

    private fun showUnitSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val container = createUnitSheetContainer()
        container.addView(TextView(requireContext()).apply {
            text = getString(R.string.select_unit)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_sm))
        })
        val customUnits = customUnitViewModel.uiState.value?.units.orEmpty()
        (CustomUnitRepository.STANDARD_UNITS + customUnits.map { it.name }).forEach { unit ->
            container.addView(MaterialRadioButton(requireContext()).apply {
                text = unit
                isChecked = unit.equals(selectedUnit, ignoreCase = true)
                minHeight = resources.getDimensionPixelSize(R.dimen.items_control_height)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
                setOnClickListener {
                    quantityUnitViewModel.updateUnit(unit)
                    dialog.dismiss()
                }
            })
        }
        container.addView(MaterialButton(requireContext()).apply {
            text = getString(R.string.add_custom_unit)
            setIconResource(R.drawable.ic_add)
            styleUnitActionButton()
            setOnClickListener {
                dialog.dismiss()
                showAddCustomUnitDialog()
            }
        })
        container.addView(MaterialButton(requireContext()).apply {
            text = getString(R.string.manage_custom_units)
            setIconResource(R.drawable.ic_settings)
            styleUnitActionButton()
            setOnClickListener {
                dialog.dismiss()
                showManageCustomUnitsSheet()
            }
        })
        // Keep both management actions reachable on smaller screens.
        dialog.setContentView(ScrollView(requireContext()).apply { addView(container) })
        dialog.show()
    }

    private fun MaterialButton.styleUnitActionButton() {
        val actionColor = ContextCompat.getColor(requireContext(), R.color.inventory_primary)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setTextColor(actionColor)
        iconTint = android.content.res.ColorStateList.valueOf(actionColor)
        backgroundTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        elevation = 0f
    }

    private fun createUnitSheetContainer(): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.space_lg)
            )
        }

    private fun showAddCustomUnitDialog() {
        val dialogBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        dialogBinding.skuInputLayout.hint = getString(R.string.custom_unit_symbol)
        dialogBinding.skuInputEditText.filters = arrayOf(InputFilter.LengthFilter(12))
        dialogBinding.skuInputEditText.inputType = InputType.TYPE_CLASS_TEXT
        dialogBinding.skuInputHelperTextView.text = getString(R.string.custom_unit_helper)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_custom_unit)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()
        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.setOnClickListener {
                dialogBinding.skuInputLayout.error = null
                val name = dialogBinding.skuInputEditText.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    dialogBinding.skuInputLayout.error = getString(R.string.unit_required)
                    return@setOnClickListener
                }
                addButton.isEnabled = false
                customUnitViewModel.addUnit(name) { result ->
                    addButton.isEnabled = true
                    result
                        .onSuccess { unit ->
                            quantityUnitViewModel.updateUnit(unit.name)
                            dialog.dismiss()
                            Snackbar.make(binding.root, R.string.custom_unit_added, Snackbar.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            dialogBinding.skuInputLayout.error =
                                it.message ?: getString(R.string.custom_unit_save_failed)
                        }
                }
            }
        }
        dialog.show()
    }

    private fun showManageCustomUnitsSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val container = createUnitSheetContainer()
        container.addView(TextView(requireContext()).apply {
            text = getString(R.string.manage_custom_units)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_sm))
        })
        val units = customUnitViewModel.uiState.value?.units.orEmpty()
        if (units.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_custom_units)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
                setPadding(0, resources.getDimensionPixelSize(R.dimen.space_md), 0, resources.getDimensionPixelSize(R.dimen.space_md))
            })
        } else {
            units.forEach { unit -> container.addView(createCustomUnitManagementRow(dialog, unit)) }
        }
        dialog.setContentView(container)
        dialog.show()
    }

    private fun createCustomUnitManagementRow(
        dialog: BottomSheetDialog,
        unit: InventoryOption
    ): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.form_field_height)
        addView(TextView(requireContext()).apply {
            text = unit.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_delete)
            contentDescription = getString(R.string.delete_custom_unit, unit.name)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.inventory_danger))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.items_control_height),
                resources.getDimensionPixelSize(R.dimen.items_control_height)
            )
            setOnClickListener {
                confirmDeleteCustomUnit(dialog, unit)
            }
        })
    }

    private fun confirmDeleteCustomUnit(dialog: BottomSheetDialog, unit: InventoryOption) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_custom_unit_title, unit.name))
            .setMessage(R.string.delete_custom_unit_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                customUnitViewModel.deleteUnit(unit) { result ->
                    result
                        .onSuccess {
                            if (selectedUnit.equals(unit.name, ignoreCase = true)) {
                                quantityUnitViewModel.updateUnit(DEFAULT_UNIT)
                            }
                            dialog.dismiss()
                            Snackbar.make(binding.root, R.string.custom_unit_deleted, Snackbar.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Snackbar.make(
                                binding.root,
                                it.message ?: getString(R.string.custom_unit_delete_failed),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .show()
    }

    private fun showSkuOptions() {
        showIdentifierOptions(
            titleRes = R.string.sku_options,
            scanLabelRes = R.string.scan_sku_label,
            manualLabelRes = R.string.enter_manually,
            onScan = { requestCamera(ScanTarget.SKU) },
            onManual = { showManualSkuDialog() }
        )
    }

    private fun showBarcodeOptions() {
        showIdentifierOptions(
            titleRes = R.string.barcode_options,
            scanLabelRes = R.string.scan_barcode,
            manualLabelRes = R.string.enter_manually,
            onScan = {
                findNavController().navigate(R.id.action_addEditInventoryItemFragment_to_barcodeScannerPrototypeFragment)
            },
            onManual = { showManualBarcodeDialog() }
        )
    }

    private fun applyScannedBarcode(barcode: String) {
        val candidate = barcode.trim()
        if (candidate.isBlank()) return
        skuViewModel.validateManualBarcode(
            value = candidate,
            currentItemId = inventoryItemId,
            branchId = selectedBranchId,
            name = binding.nameEditText.text.toString().trim(),
            brand = binding.brandEditText.text.toString().trim(),
            category = binding.categoryEditText.text.toString().trim()
        )
    }

    private fun showPhotoOptions() {
        val sheetBinding = BottomSheetPhotoOptionsBinding.inflate(layoutInflater)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetBinding.root)
        sheetBinding.takePhotoRow.setOnClickListener {
            sheet.dismiss()
            requestCameraOrOpenCamera()
        }
        sheetBinding.uploadPhotoRow.setOnClickListener {
            sheet.dismiss()
            openPhotoPicker()
        }
        sheet.show()
    }

    private fun requestCameraOrOpenCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            itemPhotoPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photoUri = createImageUri()
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            clipData = ClipData.newUri(requireContext().contentResolver, getString(R.string.item_photo), photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            if (intent.resolveActivity(requireContext().packageManager) == null) {
                Snackbar.make(binding.root, R.string.item_photo_camera_not_available, Snackbar.LENGTH_LONG).show()
            } else {
                pendingCameraPhotoUri = photoUri
                itemPhotoLauncher.launch(intent)
            }
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.item_photo_camera_not_available, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openPhotoPicker() {
        imagePickerLauncher.launch(arrayOf("image/*"))
    }

    private fun createImageUri(): Uri {
        val imageFile = File.createTempFile("inventory_item_photo_", ".jpg", requireContext().cacheDir)
        // FileProvider gives the camera app temporary write access without exposing raw file paths.
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )
    }

    private fun renderSelectedImage(uri: Uri) {
        val stableUri = copyImageToPersistentStorage(uri) ?: uri
        selectedImageUri = stableUri.toString()
        binding.itemImageView.setImageURI(stableUri)
        AppLogger.info(
            area = "Items",
            event = "item_image_selected",
            message = "Item image selected for preview.",
            "persisted" to (stableUri != uri)
        )
    }

    private fun renderSelectedImage(imageUrl: String) {
        selectedImageUri = imageUrl
        binding.itemImageView.loadInventoryImage(imageUrl)
    }

    private fun copyImageToPersistentStorage(sourceUri: Uri): Uri? {
        return try {
            if (sourceUri.scheme == "file" && sourceUri.path?.contains("/item_images/") == true) {
                return sourceUri
            }
            val imagesDir = File(requireContext().filesDir, "item_images").apply { mkdirs() }
            val extension = when (requireContext().contentResolver.getType(sourceUri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(imagesDir, "inventory_item_${System.currentTimeMillis()}.$extension")
            requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            Uri.fromFile(destination)
        } catch (error: Exception) {
            AppLogger.warn(
                area = "Items",
                event = "item_image_persist_failed",
                message = "Could not copy selected item image to app storage.",
                "reason" to error.message
            )
            null
        }
    }

    private fun showIdentifierOptions(
        titleRes: Int,
        scanLabelRes: Int,
        manualLabelRes: Int,
        onScan: () -> Unit,
        onManual: () -> Unit
    ) {
        val sheetBinding = BottomSheetSkuOptionsBinding.inflate(layoutInflater)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetBinding.root)
        sheetBinding.optionsTitleTextView.setText(titleRes)
        sheetBinding.scanOptionTextView.setText(scanLabelRes)
        sheetBinding.scanSkuRow.contentDescription = getString(scanLabelRes)
        sheetBinding.manualOptionTextView.setText(manualLabelRes)
        sheetBinding.enterSkuManuallyRow.contentDescription = getString(manualLabelRes)
        sheetBinding.scanSkuRow.setOnClickListener {
            sheet.dismiss()
            onScan()
        }
        sheetBinding.enterSkuManuallyRow.setOnClickListener {
            sheet.dismiss()
            onManual()
        }
        sheet.show()
    }

    private fun showManualSkuDialog() {
        val inputBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        inputBinding.skuInputEditText.filters = arrayOf(InputFilter.AllCaps())
        inputBinding.skuInputEditText.setText(skuViewModel.sku.value.orEmpty())
        inputBinding.skuInputEditText.setSelection(inputBinding.skuInputEditText.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.enter_sku_manually)
            .setView(inputBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null)
            .create()
        skuInputDialog = dialog
        skuInputBinding = inputBinding
        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmButton.setOnClickListener {
                inputBinding.skuInputLayout.error = null
                val candidate = inputBinding.skuInputEditText.text.toString().trim().uppercase()
                when {
                    candidate.isBlank() -> inputBinding.skuInputLayout.error = "SKU cannot be blank."
                    !candidate.matches(SKU_INPUT_PATTERN) ->
                        inputBinding.skuInputLayout.error = "Use only A-Z, 0-9, and hyphens."
                    else -> {
                        confirmButton.isEnabled = false
                        skuViewModel.validateManualSku(
                            value = candidate,
                            currentItemId = inventoryItemId,
                            branchId = selectedBranchId,
                            name = binding.nameEditText.text.toString().trim(),
                            brand = binding.brandEditText.text.toString().trim(),
                            category = binding.categoryEditText.text.toString().trim(),
                            reusedProductSku = null
                        )
                    }
                }
            }
            inputBinding.skuInputEditText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            inputBinding.skuInputEditText.post {
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(inputBinding.skuInputEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.setOnDismissListener {
            skuInputDialog = null
            skuInputBinding = null
            skuViewModel.clearValidationResult()
        }
        dialog.show()
    }

    private fun showManualBarcodeDialog() {
        val inputBinding = DialogEnterSkuBinding.inflate(layoutInflater)
        inputBinding.skuInputHelperTextView.setText(R.string.enter_barcode_number_below)
        inputBinding.skuInputLayout.hint = getString(R.string.barcode)
        inputBinding.skuInputEditText.inputType = InputType.TYPE_CLASS_NUMBER
        inputBinding.skuInputEditText.setText(skuViewModel.barcode.value.orEmpty())
        inputBinding.skuInputEditText.setSelection(inputBinding.skuInputEditText.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.enter_barcode_manually)
            .setView(inputBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()
        skuInputDialog = dialog
        skuInputBinding = inputBinding
        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmButton.setOnClickListener {
                inputBinding.skuInputLayout.error = null
                val candidate = inputBinding.skuInputEditText.text.toString().trim()
                when {
                    candidate.isNotBlank() && !candidate.matches(BARCODE_INPUT_PATTERN) ->
                        inputBinding.skuInputLayout.error = "Use digits only."
                    else -> {
                        confirmButton.isEnabled = false
                        skuViewModel.validateManualBarcode(
                            value = candidate,
                            currentItemId = inventoryItemId,
                            branchId = selectedBranchId,
                            name = binding.nameEditText.text.toString().trim(),
                            brand = binding.brandEditText.text.toString().trim(),
                            category = binding.categoryEditText.text.toString().trim()
                        )
                    }
                }
            }
            inputBinding.skuInputEditText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            inputBinding.skuInputEditText.post {
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                    ?.showSoftInput(inputBinding.skuInputEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.setOnDismissListener {
            skuInputDialog = null
            skuInputBinding = null
            skuViewModel.clearValidationResult()
        }
        dialog.show()
    }

    private fun requestCamera(target: ScanTarget) {
        pendingScanTarget = target
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraPrototype()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraPrototype() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            if (intent.resolveActivity(requireContext().packageManager) == null) {
                Snackbar.make(binding.root, pendingScanTarget.cameraNotAvailableMessageRes, Snackbar.LENGTH_LONG).show()
            } else {
                cameraLauncher.launch(intent)
            }
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, pendingScanTarget.cameraNotAvailableMessageRes, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun applyCategorySelection(category: String) {
        binding.categoryEditText.setText(category, false)
        refreshBrandSuggestions()
        updateVisibleRows()
    }

    private fun applyBrandSelection(brand: String) {
        binding.brandEditText.setText(brand, false)
        updateVisibleRows()
    }

    private fun applyLocationSelection(branchId: String, branchName: String) {
        val matchedBranch = branches.firstOrNull {
            (branchId.isNotBlank() && it.id == branchId) || it.name.equals(branchName, ignoreCase = true)
        }
        selectedBranchId = matchedBranch?.id ?: branchId
        selectedBranchName = matchedBranch?.name ?: branchName
        binding.branchEditText.setText(selectedBranchName, false)
        updateVisibleRows()
    }

    private fun registerPendingCategory(category: String) {
        val trimmedCategory = category.trim()
        if (trimmedCategory.isBlank()) return
        if (pendingCategoryOptions.none { it.equals(trimmedCategory, ignoreCase = true) }) {
            pendingCategoryOptions += trimmedCategory
        }
        binding.categoryEditText.setAdapter(dropdownAdapter(categoryDropdownOptions()))
    }

    private fun registerPendingBrand(brand: String) {
        val trimmedBrand = brand.trim()
        if (trimmedBrand.isBlank()) return
        if (pendingBrandOptions.none { it.equals(trimmedBrand, ignoreCase = true) }) {
            pendingBrandOptions += trimmedBrand
        }
        binding.brandEditText.setAdapter(dropdownAdapter(brandDropdownOptions()))
    }

    private fun setupDropdowns() {
        // These controlled dropdowns keep saved values identical to filters used by Items/Home.
        binding.categoryEditText.setAdapter(dropdownAdapter(categoryDropdownOptions()))
        binding.brandEditText.threshold = 0
        binding.brandEditText.setOnClickListener { showBrandSuggestions() }
        binding.brandEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showBrandSuggestions()
        }
        binding.categoryEditText.setOnItemClickListener { _, _, _, _ -> refreshBrandSuggestions() }
        binding.categoryEditText.addTextChangedListener(simpleTextWatcher { refreshBrandSuggestions() })
        refreshBrandSuggestions()
    }

    private fun dropdownAdapter(options: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
    }

    /**
     * Decides whether this screen is Add, Edit, or Add-from-Restock mode.
     */
    private fun setupMode() {
        if (mode == MODE_EDIT) {
            binding.formHeader.titleTextView.setText(R.string.edit_item)
            binding.saveButton.text = getString(R.string.save)
            binding.cancelButton.isVisible = false
            val existing = inventoryItemId?.let { viewModel.findInventoryItem(it) }
            if (existing != null) fillForm(existing) else Snackbar.make(binding.root, "Stock item is still loading.", Snackbar.LENGTH_SHORT).show()
        } else {
            binding.formHeader.titleTextView.setText(R.string.new_item)
            binding.saveButton.text = getString(R.string.save)
            applyRestockPrefill()
        }
    }

    private fun applyRestockPrefill() {
        val name = arguments?.getString(ARG_PREFILL_NAME).orEmpty()
        val quantity = arguments?.getString(ARG_PREFILL_QUANTITY).orEmpty().toDoubleOrNull() ?: 0.0
        val unit = arguments?.getString(ARG_PREFILL_UNIT).orEmpty()
        val branchId = arguments?.getString(ARG_PREFILL_BRANCH_ID).orEmpty()
        val branchName = arguments?.getString(ARG_PREFILL_BRANCH_NAME).orEmpty()
        val sku = arguments?.getString(ARG_PREFILL_SKU).orEmpty()
        val barcode = arguments?.getString(ARG_PREFILL_BARCODE).orEmpty()
        val category = arguments?.getString(ARG_PREFILL_CATEGORY).orEmpty()
        val brand = arguments?.getString(ARG_PREFILL_BRAND).orEmpty()

        if (name.isNotBlank()) binding.nameEditText.setText(name)
        if (quantity > 0.0) binding.quantityEditText.setText(quantity.toStorageQuantityText())
        if (unit.isNotBlank()) selectedUnit = unit
        if (branchId.isNotBlank() || branchName.isNotBlank()) {
            selectedBranchId = branchId
            selectedBranchName = branchName
            if (branchName.isNotBlank()) binding.branchEditText.setText(branchName, false)
        }
        if (sku.isNotBlank()) binding.skuEditText.setText(sku)
        if (barcode.isNotBlank()) binding.barcodeEditText.setText(barcode)
        if (category.isNotBlank()) binding.categoryEditText.setText(category, false)
        if (brand.isNotBlank()) binding.brandEditText.setText(brand)
    }

    /**
     * Connects added/expiry date fields to Material date pickers.
     */
    private fun setupDatePickers() {
        binding.expiryDateEditText.setOnClickListener {
            showDatePicker {
                binding.expiryDateEditText.setText(DateUtils.formatInputDate(it))
            }
        }
    }

    private fun simpleTextWatcher(afterChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = afterChanged()
        }
    }

    private fun showDatePicker(onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            // MaterialDatePicker returns UTC-day millis; convert it back to local start-of-day millis for InventoryItem.
            val selectedDate = Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            onSelected(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "inventory_date_picker")
    }

    private fun fillForm(inventoryItem: InventoryItem) {
        binding.skuEditText.setText(inventoryItem.sku)
        binding.barcodeEditText.setText(inventoryItem.barcode)
        binding.brandEditText.setText(inventoryItem.brand)
        binding.nameEditText.setText(inventoryItem.name)
        binding.categoryEditText.setText(inventoryItem.category, false)
        selectedBranchId = inventoryItem.branchId
        selectedBranchName = inventoryItem.branchName
        binding.branchEditText.setText(inventoryItem.branchName.ifBlank { getString(R.string.unassigned_branch) }, false)
        binding.quantityEditText.setText(inventoryItem.quantity.toStorageQuantityText())
        selectedUnit = inventoryItem.unit.ifBlank { DEFAULT_UNIT }
        binding.reorderPointEditText.setText(inventoryItem.reorderPoint.takeIf { it > 0 }?.toString() ?: inventoryItem.reorderThreshold.toStorageQuantityText())
        binding.costPriceEditText.setText(inventoryItem.costPrice.takeIf { it > 0.0 }?.toStorageQuantityText().orEmpty())
        binding.sellingPriceEditText.setText(inventoryItem.sellingPrice.takeIf { it > 0.0 }?.toStorageQuantityText().orEmpty())
        if (inventoryItem.expiryDate > 0L) binding.expiryDateEditText.setText(DateUtils.formatInputDate(inventoryItem.expiryDate))
        selectedSupplierId = inventoryItem.supplierId
        selectedSupplierName = inventoryItem.supplierName
        renderSelectedImage(inventoryItem.imageUrl)
    }

    /**
     * Builds and validates the form, then routes to add, edit, or duplicate-merge logic.
     */
    private fun saveInventoryItem() {
        clearErrors()
        val form = buildForm() ?: return
        saveValidatedInventoryItem(form)
    }

    private fun saveValidatedInventoryItem(form: InventoryItemFormData) {
        if (mode == MODE_EDIT) {
            saveInProgress = true
            viewModel.updateInventoryItem(requireContext().applicationContext, form)
        } else {
            val duplicateInventoryItem = viewModel.findExactInventoryDuplicateForAdd(form)
            if (duplicateInventoryItem == null) {
                addInventoryItemAsNewItem(form)
            } else {
                showDuplicateInventoryItemDialog(duplicateInventoryItem, form)
            }
        }
    }

    private fun showDuplicateInventoryItemDialog(existingItem: InventoryItem, form: InventoryItemFormData) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.duplicate_inventory_item_title))
            .setMessage(getString(R.string.duplicate_inventory_item_message, existingItem.name))
            .setPositiveButton(R.string.add_to_existing) { _, _ ->
                mergeWithExistingInventoryItem(existingItem, form)
            }
            .setNegativeButton(R.string.save_separate) { _, _ ->
                addInventoryItemAsNewItem(form)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun addInventoryItemAsNewItem(form: InventoryItemFormData) {
        saveInProgress = true
        viewModel.addInventoryItem(
            requireContext().applicationContext,
            form,
            receivedFromRestockOrder = !sourceRestockOrderId.isNullOrBlank()
        )
    }

    private fun mergeWithExistingInventoryItem(existingItem: InventoryItem, form: InventoryItemFormData) {
        saveInProgress = true
        viewModel.mergeDuplicateInventoryItem(
            requireContext().applicationContext,
            existingItem,
            form,
            receivedFromRestockOrder = !sourceRestockOrderId.isNullOrBlank()
        )
    }

    /**
     * Reads input fields into InventoryItemFormData, returning null if validation fails.
     */
    private fun buildForm(): InventoryItemFormData? {
        val name = binding.nameEditText.text.toString().trim()
        val category = binding.categoryEditText.text.toString().trim()
        val quantity = binding.quantityEditText.text.toString().toDoubleOrNull()
        val costPriceText = binding.costPriceEditText.text.toString().trim()
        val sellingPriceText = binding.sellingPriceEditText.text.toString().trim()
        val reorderPointText = binding.reorderPointEditText.text.toString().trim()
        val costPrice = costPriceText.ifBlank { "0" }.toDoubleOrNull()
        val sellingPrice = sellingPriceText.ifBlank { "0" }.toDoubleOrNull()
        val reorderPoint = reorderPointText.ifBlank { "0" }.toIntOrNull()
        val expiryDateText = binding.expiryDateEditText.text.toString()

        if (name.isBlank()) {
            binding.nameEditText.requestFocus()
            Snackbar.make(binding.root, "Enter an item name.", Snackbar.LENGTH_LONG).show()
            return null
        }
        if (category.isBlank()) return errorOn(binding.categoryLayout, "Category is required.")
        if (category !in categoryDropdownOptions()) return errorOn(binding.categoryLayout, "Choose a category from the list.")
        if (quantity == null || quantity < 0) {
            Snackbar.make(binding.root, R.string.enter_valid_quantity, Snackbar.LENGTH_LONG).show()
            return null
        }
        if (selectedUnit.isBlank()) {
            Snackbar.make(binding.root, R.string.select_a_unit, Snackbar.LENGTH_LONG).show()
            return null
        }
        if (costPrice == null || costPrice < 0.0) return errorOn(binding.costPriceLayout, "Cost price must be 0 or more.")
        if (sellingPrice == null || sellingPrice < 0.0) return errorOn(binding.sellingPriceLayout, "Selling price must be 0 or more.")
        if (reorderPoint == null || reorderPoint < 0) return errorOn(binding.reorderPointLayout, "Safety stock must be 0 or more.")
        val existingItem = inventoryItemId?.let { viewModel.findInventoryItem(it) }
        val expiryDate = if (expiryDateText.isBlank()) {
            existingItem?.expiryDate ?: 0L
        } else {
            runCatching { DateUtils.parseInputDate(expiryDateText) }.getOrNull()
                ?: return errorOn(binding.expiryDateLayout, "Use yyyy-MM-dd.")
        }
        val addedDate = existingItem?.addedDate?.takeIf { it > 0L } ?: DateUtils.todayMillis()

        val selectedBranchText = binding.branchEditText.text.toString().trim()
        val selectedBranch = branches.firstOrNull { it.name == selectedBranchText }
        val finalBranchId = selectedBranch?.id ?: selectedBranchId
        val finalBranchName = selectedBranch?.name ?: selectedBranchName
        if (finalBranchId.isBlank()) return errorOn(binding.branchLayout, "Branch is required.")

        return InventoryItemFormData(
            id = inventoryItemId.orEmpty(),
            sku = binding.skuEditText.text.toString().trim(),
            barcode = binding.barcodeEditText.text.toString().trim(),
            name = name,
            brand = binding.brandEditText.text.toString().trim(),
            category = category,
            branchId = finalBranchId,
            branchName = finalBranchName,
            storageLocation = existingItem?.storageLocation.orEmpty(),
            quantity = quantity,
            unit = selectedUnit.ifBlank { DEFAULT_UNIT },
            costPrice = costPrice,
            sellingPrice = sellingPrice,
            minimumStockLevel = existingItem?.minimumStockLevel ?: 0,
            reorderPoint = reorderPoint,
            maximumStockLevel = existingItem?.maximumStockLevel ?: 0,
            // Keep legacy reorderThreshold synced because existing low-stock code still uses it.
            reorderThreshold = reorderPoint.toDouble(),
            aisle = existingItem?.aisle.orEmpty(),
            shelf = existingItem?.shelf.orEmpty(),
            addedDate = addedDate,
            expiryDate = expiryDate,
            batchNumber = existingItem?.batchNumber.orEmpty(),
            shelfLifeDays = existingItem?.shelfLifeDays ?: 0,
            reminderDaysBefore = InventoryReminderScheduler.FIXED_REMINDER_DAYS_BEFORE,
            notes = existingItem?.notes.orEmpty(),
            supplierId = selectedSupplierId,
            // Inventory items keep only the supplier link/name; legacy contact snapshots survive edits.
            supplierName = selectedSupplierName,
            supplierPhone = existingItem?.supplierPhone.orEmpty(),
            supplierEmail = existingItem?.supplierEmail.orEmpty(),
            imageUrl = selectedImageUri,
            tags = existingItem?.tags.orEmpty()
        )
    }

    private fun <T> errorOn(layout: com.google.android.material.textfield.TextInputLayout, message: String): T? {
        layout.error = message
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        return null
    }

    private fun clearErrors() {
        binding.skuLayout.error = null
        binding.barcodeLayout.error = null
        binding.branchLayout.error = null
        binding.categoryLayout.error = null
        binding.quantityLayout.error = null
        binding.costPriceLayout.error = null
        binding.sellingPriceLayout.error = null
        binding.reorderPointLayout.error = null
        binding.expiryDateLayout.error = null
    }

    private fun categoryDropdownOptions(): List<String> {
        val inventoryOptions = viewModel.uiState.value?.inventoryItems.orEmpty()
            .map { it.category.trim() }
            .filter { it.isNotBlank() }
        val currentValue = binding.categoryEditText.text?.toString().orEmpty().trim()
        val mergedOptions = linkedMapOf<String, String>()

        (inventoryOptions + pendingCategoryOptions + listOf(currentValue))
            .filter { it.isNotBlank() }
            .forEach { option ->
                mergedOptions.putIfAbsent(option.lowercase(), option)
            }
        return mergedOptions.values.sortedBy { it.lowercase() }
    }

    private fun refreshBrandSuggestions() {
        // Brand suggestions are filtered by category, but staff can still type a new brand.
        binding.brandEditText.setAdapter(dropdownAdapter(brandDropdownOptions()))
    }

    private fun showBrandSuggestions() {
        val suggestions = brandDropdownOptions()
        binding.brandEditText.setAdapter(dropdownAdapter(suggestions))
        if (suggestions.isNotEmpty()) binding.brandEditText.showDropDown()
    }

    private fun brandDropdownOptions(): List<String> {
        val baseOptions = viewModel.brandSuggestionsForCategory(binding.categoryEditText.text.toString())
        val currentValue = binding.brandEditText.text?.toString().orEmpty().trim()
        val mergedOptions = linkedMapOf<String, String>()
        (baseOptions + pendingBrandOptions + listOf(currentValue))
            .filter { it.isNotBlank() }
            .forEach { option ->
                mergedOptions.putIfAbsent(option.lowercase(), option)
            }
        return mergedOptions.values.toList()
    }

    private fun markSourceRestockOrderStoredIfNeeded() {
        val restockOrderId = sourceRestockOrderId
        if (mode != MODE_ADD || restockOrderId.isNullOrBlank()) {
            findNavController().popBackStack()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            restockOrderRepository.archiveReceivedOrder(restockOrderId)
                .onFailure {
                    Snackbar.make(binding.root, it.message ?: "Stock item saved, but restock item was not updated.", Snackbar.LENGTH_LONG).show()
                }
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        skuInputDialog?.dismiss()
        skuInputDialog = null
        skuInputBinding = null
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_IMAGE_URI, selectedImageUri)
        outState.putString(KEY_PENDING_CAMERA_PHOTO_URI, pendingCameraPhotoUri?.toString())
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_INVENTORY_ITEM_ID = "inventoryItemId"
        const val ARG_PREFILL_NAME = "prefillName"
        const val ARG_PREFILL_QUANTITY = "prefillQuantity"
        const val ARG_PREFILL_UNIT = "prefillUnit"
        const val ARG_PREFILL_BRANCH_ID = "prefillBranchId"
        const val ARG_PREFILL_BRANCH_NAME = "prefillBranchName"
        const val ARG_PREFILL_SKU = "prefillSku"
        const val ARG_PREFILL_BARCODE = "prefillBarcode"
        const val ARG_PREFILL_CATEGORY = "prefillCategory"
        const val ARG_PREFILL_BRAND = "prefillBrand"
        const val ARG_SOURCE_RESTOCK_ORDER_ID = "sourceRestockOrderId"
        const val MODE_ADD = "add"
        const val MODE_EDIT = "edit"
        private const val DEFAULT_UNIT = QuantityUnitFormViewModel.DEFAULT_UNIT
        private val SKU_INPUT_PATTERN = Regex("^[A-Z0-9-]+$")
        private val BARCODE_INPUT_PATTERN = Regex("^\\d+$")
        private const val KEY_SELECTED_IMAGE_URI = "selectedImageUri"
        private const val KEY_PENDING_CAMERA_PHOTO_URI = "pendingCameraPhotoUri"
    }

    private enum class ScanTarget(
        val prototypeMessageRes: Int,
        val permissionDeniedMessageRes: Int,
        val cameraNotAvailableMessageRes: Int
    ) {
        SKU(
            R.string.sku_scanning_prototype,
            R.string.camera_permission_denied,
            R.string.camera_not_available
        )
    }
}
