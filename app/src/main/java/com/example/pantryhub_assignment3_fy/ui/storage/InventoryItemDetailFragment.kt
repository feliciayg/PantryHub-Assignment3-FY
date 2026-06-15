package com.example.pantryhub_assignment3_fy.ui.storage

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.BottomSheetTransactionTypeBinding
import com.example.pantryhub_assignment3_fy.databinding.FragmentInventoryItemDetailBinding
import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.ui.common.QuantityStepperConfig
import com.example.pantryhub_assignment3_fy.ui.common.showQuantityStepperDialog
import com.example.pantryhub_assignment3_fy.ui.common.tintMenuIcons
import com.example.pantryhub_assignment3_fy.ui.movement.StockInTransactionFragment
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionMode
import com.example.pantryhub_assignment3_fy.ui.movement.applyTransactionTypeColors
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.ExpiryLotRules
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

class InventoryItemDetailFragment : Fragment() {
    private var _binding: FragmentInventoryItemDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by activityViewModels()
    private lateinit var inventoryItemId: String
    private var summaryBranchId: String = ""
    private var representativeItem: InventoryItem? = null
    private var matchingItems: List<InventoryItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        inventoryItemId = arguments?.getString(ARG_INVENTORY_ITEM_ID).orEmpty()
        summaryBranchId = arguments?.getString(ARG_SUMMARY_BRANCH_ID).orEmpty()
        setupToolbar()
        setupTabs()
        binding.locationSearchEditText.doAfterTextChanged { renderInventoryTab() }
        binding.inStockChip.setOnCheckedChangeListener { _, _ -> renderInventoryTab() }
        binding.allLocationsSummaryRow.setOnClickListener { openTransactionBreakdown(summaryBranchId) }
        binding.transactButton.setOnClickListener { showTransactionTypeSheet() }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val selected = state.inventoryItems.firstOrNull { it.id == inventoryItemId }
            if (selected != null) {
                representativeItem = selected
                matchingItems = state.inventoryItems
                    .filter { ProductIdentity.sameProduct(selected, it) }
                    .sortedBy { it.branchName.lowercase() }
                render(selected)
            }
            state.errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun setupToolbar() {
        binding.detailToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.detailToolbar.inflateMenu(R.menu.menu_inventory_item_detail)
        binding.detailToolbar.tintMenuIcons(requireContext())
        binding.detailToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.editInventoryItem -> {
                    findNavController().navigate(
                        R.id.action_inventoryItemDetailFragment_to_addEditInventoryItemFragment,
                        Bundle().apply {
                            putString(AddEditInventoryItemFragment.ARG_MODE, AddEditInventoryItemFragment.MODE_EDIT)
                            putString(AddEditInventoryItemFragment.ARG_INVENTORY_ITEM_ID, inventoryItemId)
                        }
                    )
                    true
                }
                R.id.deleteInventoryItem -> {
                    representativeItem?.let(::confirmDelete)
                    true
                }
                R.id.restoreInventoryItem -> {
                    representativeItem?.let { viewModel.restoreInventoryItem(it.id) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        binding.detailTabLayout.addTab(binding.detailTabLayout.newTab().setText(R.string.details))
        binding.detailTabLayout.addTab(binding.detailTabLayout.newTab().setText(R.string.inventory))
        binding.detailTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val inventory = tab.position == 1
                binding.detailsScrollView.isVisible = !inventory
                binding.inventoryContentLayout.isVisible = inventory
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun render(item: InventoryItem) {
        binding.nameTextView.text = item.name
        binding.detailToolbar.menu.findItem(R.id.restoreInventoryItem)?.isVisible = item.isArchived
        binding.detailToolbar.menu.findItem(R.id.editInventoryItem)?.isVisible = !item.isArchived
        // Archived items are already removed from active stock lists, so the detail page only offers Restore.
        binding.detailToolbar.menu.findItem(R.id.deleteInventoryItem)?.isVisible = !item.isArchived
        binding.detailToolbar.tintMenuIcons(requireContext())
        binding.inventoryImageView.loadInventoryImage(item.imageUrl)
        val summaryItems = summaryItems()
        binding.summaryLocationLabelTextView.text = summaryLocationLabel(summaryItems)
        binding.allLocationsQuantityTextView.text = summaryItems.sumOf { it.quantity }.toStorageQuantityText()
        renderDetailsTab(item)
        renderInventoryTab()
    }

    private fun summaryItems(): List<InventoryItem> {
        if (summaryBranchId.isBlank()) return matchingItems
        return matchingItems.filter { it.branchId == summaryBranchId }.ifEmpty { matchingItems }
    }

    private fun summaryLocationLabel(items: List<InventoryItem>): String {
        if (summaryBranchId.isBlank()) return getString(R.string.all_locations)
        return items.firstOrNull { it.branchId == summaryBranchId }
            ?.branchName
            ?.ifBlank { getString(R.string.unassigned_branch) }
            ?: representativeItem?.branchName?.ifBlank { getString(R.string.unassigned_branch) }
            ?: getString(R.string.all_locations)
    }

    private fun renderDetailsTab(item: InventoryItem) {
        binding.detailsRowsContainer.removeAllViews()
        addDetailRow("SKU", item.sku.ifBlank { getString(R.string.not_added) })
        addDetailRow("Barcode", item.barcode.ifBlank { getString(R.string.not_added) })
        addSectionDivider(binding.detailsRowsContainer)
        addDetailRow("Category", item.category.ifBlank { getString(R.string.not_added) })
        addDetailRow("Brand", item.brand.ifBlank { getString(R.string.not_added) })
        addSectionDivider(binding.detailsRowsContainer)
        addDetailRow("Cost", item.costPrice.toStorageMoneyText())
        addDetailRow("Price", item.sellingPrice.toStorageMoneyText())
        renderExpirySummary()
    }

    private fun renderInventoryTab() {
        val query = binding.locationSearchEditText.text?.toString().orEmpty().trim()
        val rows = matchingItems
            .filter { query.isBlank() || it.branchName.contains(query, ignoreCase = true) }
            .filter { !binding.inStockChip.isChecked || it.quantity > 0.0 }
        binding.locationRowsContainer.removeAllViews()
        binding.noLocationStockTextView.isVisible = rows.isEmpty()
        rows.forEach { item ->
            addLocationRow(item)
        }
    }

    private fun addDetailRow(label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0
            )
        }
        row.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 15f
            maxLines = 1
        })
        binding.detailsRowsContainer.addView(row)
        addThinDivider(binding.detailsRowsContainer)
    }

    private fun addLocationRow(item: InventoryItem) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener { openTransactionBreakdown(item.branchId) }
        }
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(requireContext()).apply {
                text = item.branchName.ifBlank { getString(R.string.unassigned_branch) }
                setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
                textSize = 15f
            })
            addView(TextView(requireContext()).apply {
                val expiryHint = item.inventoryExpiryHint()
                text = expiryHint
                isVisible = expiryHint != null
                setTextColor(ContextCompat.getColor(requireContext(), item.inventoryExpiryHintColor()))
                textSize = 13f
                setPadding(0, resources.getDimensionPixelSize(R.dimen.space_xs), 0, 0)
            })
        })
        row.addView(TextView(requireContext()).apply {
            text = "${item.quantity.toStorageQuantityText()} ${item.unit}".trim()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
        })
        binding.locationRowsContainer.addView(row)
        addThinDivider(binding.locationRowsContainer)
    }

    private fun renderExpirySummary() {
        val lots = expiryBreakdownLots()
        val today = LocalDate.now()
        val expiringSoonCutoff = today.plusDays(ExpiryLotRules.EXPIRING_SOON_DAYS)
        val datedLots = lots.filter { it.expiryDate != null }
        val nextExpiryLot = datedLots.minByOrNull { it.expiryDate!! }
        val expiredQuantity = datedLots
            .filter { it.toLocalDate()?.isBefore(today) == true }
            .sumOf { it.quantity }
        val expiringSoonQuantity = datedLots
            .filter {
                val date = it.toLocalDate()
                date != null && !date.isBefore(today) && !date.isAfter(expiringSoonCutoff)
            }
            .sumOf { it.quantity }
        val affectedLocations = lots
            .map { it.branchName.ifBlank { getString(R.string.unassigned_branch) } }
            .toSet()
            .size

        binding.expirySummaryRowsContainer.removeAllViews()
        binding.expirySummaryEmptyTextView.isVisible = lots.isEmpty()
        AppLogger.info(
            area = "Expiry",
            event = "expiry_summary_calculated",
            message = "Expiry summary calculated for item detail.",
            "lots" to lots.size,
            "expiredQuantity" to expiredQuantity,
            "expiringSoonQuantity" to expiringSoonQuantity
        )
        if (lots.isEmpty()) return

        addSummaryRow(
            getString(R.string.next_expiry),
            nextExpiryLot?.summaryDateText() ?: getString(R.string.no_expiry_tracked),
            nextExpiryLot?.expiryColor() ?: R.color.inventory_text_secondary
        )
        addSummaryRow(
            getString(R.string.expiring_soon_quantity),
            formatQuantity(expiringSoonQuantity),
            if (expiringSoonQuantity > 0.0) R.color.inventory_warning else R.color.inventory_text_primary
        )
        addSummaryRow(
            getString(R.string.expired_quantity),
            formatQuantity(expiredQuantity),
            if (expiredQuantity > 0.0) R.color.inventory_danger else R.color.inventory_text_primary
        )
        if (expiredQuantity > 0.0) {
            addMarkExpiredRow()
        }
        addSummaryRow(
            getString(R.string.affected_locations),
            affectedLocations.toString(),
            R.color.inventory_text_primary
        )
    }

    private fun addSummaryRow(label: String, value: String, valueColorRes: Int) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0
            )
        }
        row.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(ContextCompat.getColor(requireContext(), valueColorRes))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        binding.expirySummaryRowsContainer.addView(row)
        addThinDivider(binding.expirySummaryRowsContainer)
    }

    private fun addMarkExpiredRow() {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.item_form_row_height)
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            setPadding(
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding),
                0
            )
            setOnClickListener { showMarkExpiredDialog() }
        }
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.expired_stock_available)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.mark_expired)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_danger))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary))
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
        })
        binding.expirySummaryRowsContainer.addView(row)
        addThinDivider(binding.expirySummaryRowsContainer)
    }

    private fun expiryBreakdownLots(): List<ExpiryLot> {
        val compatibleLots = matchingItems.flatMap { item ->
            val realLots = item.expiryLots.filter { it.quantity > 0.0 }
            val lotQuantity = realLots.sumOf { it.quantity }.coerceAtMost(item.quantity)
            val remainingLegacyQuantity = (item.quantity - lotQuantity).coerceAtLeast(0.0)
            buildList {
                addAll(realLots)
                if (remainingLegacyQuantity > 0.0) {
                    add(
                        ExpiryLot(
                            inventoryItemId = item.id,
                            branchId = item.branchId,
                            branchName = item.branchName,
                            expiryDate = item.expiryDate.takeIf { it > 0L },
                            quantity = remainingLegacyQuantity,
                            createdAt = item.createdAt
                        )
                    )
                }
            }
        }
        return ExpiryLotRules.combineByExpiry(compatibleLots)
    }

    private fun showTransactionTypeSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetTransactionTypeBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.applyTransactionTypeColors(requireContext())
        sheetBinding.stockInRow.setOnClickListener {
            dialog.dismiss()
            openTransaction(TransactionMode.STOCK_IN)
        }
        sheetBinding.stockOutRow.setOnClickListener {
            dialog.dismiss()
            openTransaction(TransactionMode.STOCK_OUT)
        }
        sheetBinding.moveStockRow.setOnClickListener {
            dialog.dismiss()
            openTransaction(TransactionMode.MOVE_STOCK)
        }
        sheetBinding.adjustStockRow.setOnClickListener {
            dialog.dismiss()
            openTransaction(TransactionMode.ADJUST_STOCK)
        }
        dialog.show()
    }

    private fun showMarkExpiredDialog() {
        val totalExpiredQuantity = totalExpiredQuantity()
        val totalStock = matchingItems.sumOf { it.quantity }
        if (totalExpiredQuantity <= 0.0) {
            Snackbar.make(binding.root, R.string.no_expired_stock_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        showQuantityStepperDialog(
            QuantityStepperConfig(
                title = getString(R.string.mark_expired),
                initialQuantity = 1.0,
                minimumQuantity = 1.0,
                maximumQuantity = totalExpiredQuantity,
                currentStock = totalStock,
                currentStockLabel = getString(R.string.current_stock),
                afterStockLabel = getString(R.string.after_mark_expired),
                unit = representativeItem?.unit.orEmpty(),
                validationMessage = getString(R.string.mark_expired_quantity_error),
                stockChangeDirection = -1,
                showDifference = true,
                differenceLabel = getString(R.string.expired_difference_label)
            )
        ) { result ->
            val allocations = expiredAllocations(result.quantity)
            viewModel.markExpiredStock(requireContext().applicationContext, allocations)
        }
    }

    private fun openTransaction(mode: TransactionMode) {
        AppLogger.info(
            area = "Transactions",
            event = "transaction_mode_selected",
            message = "Transaction mode selected from item detail.",
            "mode" to mode.name,
            "item" to representativeItem?.name.orEmpty()
        )
        findNavController().navigate(
            R.id.stockInTransactionFragment,
            Bundle().apply {
                putString(StockInTransactionFragment.ARG_TRANSACTION_MODE, mode.name)
            }
        )
    }

    private fun openTransactionBreakdown(branchId: String) {
        representativeItem?.let { item ->
            AppLogger.info(
                area = "Expiry",
                event = "item_calendar_opened",
                message = "Opening item transaction calendar from detail.",
                "item" to item.name,
                "locationFilter" to branchId.ifBlank { "All Locations" }
            )
            findNavController().navigate(
                R.id.action_inventoryItemDetailFragment_to_itemTransactionBreakdownFragment,
                Bundle().apply {
                    putString(ItemTransactionBreakdownFragment.ARG_PRODUCT_KEY, ProductIdentity.key(item))
                    putString(ItemTransactionBreakdownFragment.ARG_BRANCH_ID, branchId)
                }
            )
        }
    }

    private fun confirmDelete(inventoryItem: InventoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.archive))
            .setMessage(getString(R.string.archive_item_message, inventoryItem.name))
            .setPositiveButton(getString(R.string.archive)) { _, _ ->
                viewModel.archiveInventoryItem(requireContext().applicationContext, inventoryItem.id)
                findNavController().popBackStack()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSectionDivider(parent: LinearLayout) {
        parent.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.space_sm))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.inventory_background))
        })
    }

    private fun addThinDivider(parent: LinearLayout) {
        parent.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.item_form_horizontal_padding)
            }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.inventory_outline))
        })
    }

    private fun selectableItemBackground() =
        android.util.TypedValue().let { value ->
            requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            ContextCompat.getDrawable(requireContext(), value.resourceId)
        }

    private fun ExpiryLot.expiryColor(): Int {
        val expiry = expiryDate ?: return R.color.inventory_text_secondary
        val date = Instant.ofEpochMilli(expiry).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when {
            date.isBefore(today) -> R.color.inventory_danger
            !date.isAfter(today.plusDays(ExpiryLotRules.EXPIRING_SOON_DAYS)) -> R.color.inventory_warning
            else -> R.color.inventory_text_secondary
        }
    }

    private fun ExpiryLot.toLocalDate(): LocalDate? =
        expiryDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    private fun ExpiryLot.summaryDateText(): String {
        val date = toLocalDate() ?: return getString(R.string.no_expiry_tracked)
        val today = LocalDate.now()
        return when {
            date.isBefore(today) -> getString(R.string.expired_on_format, DateUtils.formatDisplayDate(expiryDate ?: 0L))
            date.isEqual(today) -> getString(R.string.expires_today)
            else -> getString(R.string.expires_on_format, DateUtils.formatDisplayDate(expiryDate ?: 0L))
        }
    }

    private fun InventoryItem.inventoryExpiryHint(): String? {
        val nextLot = itemLots(this)
            .filter { it.expiryDate != null && it.quantity > 0.0 }
            .minByOrNull { it.expiryDate!! }
            ?: return null
        val date = nextLot.toLocalDate() ?: return null
        val today = LocalDate.now()
        return when {
            date.isBefore(today) -> getString(R.string.expired_quantity_short, nextLot.quantity.toStorageQuantityText(), unit)
            date.isEqual(today) -> getString(R.string.expires_today_quantity, nextLot.quantity.toStorageQuantityText(), unit)
            else -> {
                val daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, date).absoluteValue
                if (daysUntilExpiry <= ExpiryLotRules.EXPIRING_SOON_DAYS) {
                    getString(
                        R.string.expires_in_days_quantity,
                        daysUntilExpiry.toInt(),
                        nextLot.quantity.toStorageQuantityText(),
                        unit
                    )
                } else {
                    getString(
                        R.string.expires_on_quantity,
                        DateUtils.formatDisplayDate(nextLot.expiryDate ?: 0L),
                        nextLot.quantity.toStorageQuantityText(),
                        unit
                    )
                }
            }
        }
    }

    private fun InventoryItem.inventoryExpiryHintColor(): Int {
        val nextLot = itemLots(this)
            .filter { it.expiryDate != null && it.quantity > 0.0 }
            .minByOrNull { it.expiryDate!! }
            ?: return R.color.inventory_text_secondary
        return nextLot.expiryColor()
    }

    private fun itemLots(item: InventoryItem): List<ExpiryLot> {
        val realLots = item.expiryLots.filter { it.quantity > 0.0 }
        val lotQuantity = realLots.sumOf { it.quantity }.coerceAtMost(item.quantity)
        val remainingLegacyQuantity = (item.quantity - lotQuantity).coerceAtLeast(0.0)
        return buildList {
            addAll(realLots)
            if (remainingLegacyQuantity > 0.0 && item.expiryDate > 0L) {
                add(
                    ExpiryLot(
                        inventoryItemId = item.id,
                        branchId = item.branchId,
                        branchName = item.branchName,
                        expiryDate = item.expiryDate,
                        quantity = remainingLegacyQuantity,
                        createdAt = item.createdAt
                    )
                )
            }
        }
    }

    private fun totalExpiredQuantity(): Double {
        val today = LocalDate.now()
        return matchingItems.sumOf { item ->
            itemLots(item)
                .filter { lot -> lot.expiryDate != null && lot.quantity > 0.0 && (lot.toLocalDate()?.isBefore(today) == true) }
                .sumOf { it.quantity }
        }
    }

    private fun expiredAllocations(requestedQuantity: Double): Map<String, Double> {
        var remaining = requestedQuantity
        val today = LocalDate.now()
        val orderedItems = matchingItems
            .mapNotNull { item ->
                val expiredQuantity = itemLots(item)
                    .filter { lot -> lot.expiryDate != null && lot.quantity > 0.0 && (lot.toLocalDate()?.isBefore(today) == true) }
                    .sumOf { it.quantity }
                expiredQuantity.takeIf { it > 0.0 }?.let { quantity ->
                    val oldestExpiry = itemLots(item)
                        .filter { lot -> lot.expiryDate != null && lot.quantity > 0.0 && (lot.toLocalDate()?.isBefore(today) == true) }
                        .minOfOrNull { it.expiryDate ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
                    Triple(item.id, quantity, oldestExpiry)
                }
            }
            .sortedBy { it.third }

        val allocations = linkedMapOf<String, Double>()
        orderedItems.forEach { (itemId, availableExpired, _) ->
            if (remaining <= 0.0) return@forEach
            val deducted = minOf(availableExpired, remaining)
            if (deducted > 0.0) {
                allocations[itemId] = deducted
                remaining -= deducted
            }
        }
        return allocations
    }

    private fun formatQuantity(quantity: Double): String =
        "${quantity.toStorageQuantityText()} ${representativeItem?.unit.orEmpty()}".trim()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_INVENTORY_ITEM_ID = "inventoryItemId"
        const val ARG_SUMMARY_BRANCH_ID = "summaryBranchId"
    }
}
