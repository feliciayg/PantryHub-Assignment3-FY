package com.example.pantryhub_assignment3_fy.ui.restock

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentPurchaseDetailBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemPurchaseReceivingEventBinding
import com.example.pantryhub_assignment3_fy.model.PurchaseReceivingEvent
import com.example.pantryhub_assignment3_fy.model.PurchaseReceivingEventItem
import com.example.pantryhub_assignment3_fy.model.RestockOrder
import com.example.pantryhub_assignment3_fy.model.RestockStatus
import com.example.pantryhub_assignment3_fy.model.remainingQuantity
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.google.android.material.snackbar.Snackbar

class PurchaseDetailFragment : Fragment() {
    private var _binding: FragmentPurchaseDetailBinding? = null
    private val binding get() = _binding!!
    private val listViewModel: RestockOrdersViewModel by activityViewModels()
    private lateinit var itemsAdapter: PurchaseDetailItemsAdapter
    private var purchaseId: String = ""
    private var currentTab: PurchaseDetailTab = PurchaseDetailTab.DETAILS
    private var deleteInProgress = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchaseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        purchaseId = arguments?.getString(ARG_PURCHASE_ID).orEmpty()
        itemsAdapter = PurchaseDetailItemsAdapter()
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsRecyclerView.adapter = itemsAdapter
        setupToolbar()
        binding.detailsTabTextView.setOnClickListener { setTab(PurchaseDetailTab.DETAILS) }
        binding.statusTabTextView.setOnClickListener { setTab(PurchaseDetailTab.STATUS) }

        listViewModel.uiState.observe(viewLifecycleOwner) { state ->
            val order = state.restockOrders.firstOrNull { it.id == purchaseId }
            if (order == null) {
                if (deleteInProgress) {
                    deleteInProgress = false
                    findNavController().popBackStack()
                }
                return@observe
            }
            binding.partiallyReceiveButton.setOnClickListener {
                viewModelStartPartialReceive(order)
            }
            binding.receiveAllButton.setOnClickListener { listViewModel.receiveAll(order) }
            render(order)
            state.errorMessage?.let {
                deleteInProgress = false
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                listViewModel.clearMessages()
            }
            state.successMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                listViewModel.clearMessages()
                if (deleteInProgress && (it.contains("deleted", ignoreCase = true) || it.contains("archived", ignoreCase = true))) {
                    deleteInProgress = false
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            val order = listViewModel.uiState.value?.restockOrders.orEmpty().firstOrNull { it.id == purchaseId }
            when (item.itemId) {
                R.id.cancelPurchase -> {
                    order?.let(::confirmCancel)
                    true
                }
                R.id.deletePurchase -> {
                    order?.let {
                        if (it.isDraft()) {
                            confirmDelete(it)
                        } else {
                            confirmArchive(it)
                        }
                    }
                    true
                }
                R.id.sendPurchaseEmail -> {
                    order?.let(::sendPurchaseEmail)
                    true
                }
                R.id.restorePurchase -> {
                    order?.let(listViewModel::restorePurchase)
                    true
                }
                else -> false
            }
        }
    }

    private fun render(order: RestockOrder) {
        binding.toolbar.title = order.fallbackOrderLabel
        renderToolbarActions(order)
        renderStatusChip(order.normalizedStatus)
        binding.orderDateTextView.text = order.orderDate.toPurchaseDateTimeText()
        binding.expectedDateTextView.text = order.expectedDeliveryDate.takeIf { it > 0L }?.let(DateUtils::formatDisplayDate) ?: "-"
        binding.receivingLocationTextView.text = order.receivingLocationName.ifBlank { "-" }
        binding.supplierTextView.text = order.supplierName.ifBlank { "-" }
        binding.createdByTextView.text = order.createdByName.ifBlank { order.createdBy.ifBlank { "-" } }

        itemsAdapter.tab = currentTab
        itemsAdapter.submitList(order.purchaseItems)

        binding.totalsCard.isVisible = currentTab == PurchaseDetailTab.DETAILS
        binding.statusSummaryContainer.isVisible = currentTab == PurchaseDetailTab.STATUS
        binding.subtotalTextView.text = order.totalCost.toPurchaseMoneyText()
        binding.totalTextView.text = order.totalCost.toPurchaseMoneyText()
        binding.statusSummaryItemsTextView.text = order.itemCount.toPurchaseItemLabel()
        binding.statusSummaryQuantityTextView.text =
            "${order.totalReceivedQuantity.toPurchaseQuantityText()}/${order.quantity.toPurchaseQuantityText()}"

        val events = listViewModel.receivingEventsForPurchase(order)
        renderReceivingEvents(events)
        binding.emptyReceivedItemsTextView.isVisible = events.isEmpty()
        binding.receivedEventsContainer.isVisible = events.isNotEmpty()

        val canReceive = !order.isArchived &&
            (order.normalizedStatus == RestockStatus.ORDERED || order.normalizedStatus == RestockStatus.PARTIALLY_RECEIVED)
        binding.partiallyReceiveButton.isEnabled = canReceive && order.purchaseItems.any { it.remainingQuantity() > 0.0 }
        binding.receiveAllButton.isEnabled = canReceive && !order.isFullyReceived()
        binding.receiveAllButton.alpha = if (binding.receiveAllButton.isEnabled) 1f else 0.45f
        binding.partiallyReceiveButton.alpha = if (binding.partiallyReceiveButton.isEnabled) 1f else 0.45f
        binding.receiveActionsContainer.isVisible = !order.isArchived
    }

    private fun renderReceivingEvents(events: List<PurchaseReceivingEvent>) {
        binding.receivedEventsContainer.removeAllViews()
        events.forEach { event ->
            val rowBinding = ItemPurchaseReceivingEventBinding.inflate(
                layoutInflater,
                binding.receivedEventsContainer,
                false
            )
            bindReceivingEvent(rowBinding, event)
            binding.receivedEventsContainer.addView(rowBinding.root)
        }
    }

    private fun bindReceivingEvent(
        rowBinding: ItemPurchaseReceivingEventBinding,
        event: PurchaseReceivingEvent
    ) {
        rowBinding.dateTextView.text = event.receivedAt.toPurchaseDateTimeText()
        val itemCount = event.receivedItems.size
        val totalQuantity = event.receivedItems.sumOf { it.receivedQuantity }
        rowBinding.summaryTextView.text = "${itemCount.toPurchaseItemLabel()} / ${totalQuantity.toPurchaseQuantityText()}"
        rowBinding.receivedItemsContainer.removeAllViews()
        rowBinding.receivedItemsContainer.isVisible = event.receivedItems.isNotEmpty()
        event.receivedItems.forEach { item ->
            rowBinding.receivedItemsContainer.addView(createReceivedItemRow(item))
        }
        rowBinding.root.setOnClickListener { openReceivingTransaction(event) }
    }

    private fun createReceivedItemRow(item: PurchaseReceivingEventItem): LinearLayout {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        textColumn.addView(TextView(context).apply {
            text = item.itemName.ifBlank { item.sku.ifBlank { item.barcode.ifBlank { "-" } } }
            setTextColor(ContextCompat.getColor(context, R.color.inventory_text_primary))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textColumn.addView(TextView(context).apply {
            val metaText = listOf(item.sku, item.barcode)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            val movementText = "${item.quantityBefore.toPurchaseQuantityText()} -> ${item.quantityAfter.toPurchaseQuantityText()}"
            text = listOf(metaText, movementText)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            setTextColor(ContextCompat.getColor(context, R.color.inventory_text_secondary))
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        row.addView(textColumn)
        row.addView(TextView(context).apply {
            text = "+${item.receivedQuantity.toPurchaseQuantityText()} ${item.unit}".trim()
            setTextColor(ContextCompat.getColor(context, R.color.transaction_stock_in_blue))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        })
        return row
    }

    private fun renderToolbarActions(order: RestockOrder) {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.sendPurchaseEmail)?.isVisible = true
        menu.findItem(R.id.restorePurchase)?.isVisible = order.isArchived
        menu.findItem(R.id.deletePurchase)?.isVisible = !order.isArchived
        menu.findItem(R.id.deletePurchase)?.title =
            getString(if (order.isDraft()) R.string.delete else R.string.archive)
        menu.findItem(R.id.cancelPurchase)?.isVisible =
            !order.isArchived &&
                (
                    order.normalizedStatus == RestockStatus.ORDERED ||
                        order.normalizedStatus == RestockStatus.PARTIALLY_RECEIVED
                    )
    }

    private fun renderStatusChip(status: RestockStatus) {
        binding.statusChip.text = getString(status.purchaseStatusLabelRes())
        binding.statusChip.setChipBackgroundColorResource(status.purchaseStatusBackgroundColorRes())
        binding.statusChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.inventory_text_primary))
    }

    private fun setTab(tab: PurchaseDetailTab) {
        currentTab = tab
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.inventory_primary)
        val unselectedColor = ContextCompat.getColor(requireContext(), R.color.inventory_text_secondary)
        binding.detailsTabTextView.setTextColor(if (tab == PurchaseDetailTab.DETAILS) selectedColor else unselectedColor)
        binding.statusTabTextView.setTextColor(if (tab == PurchaseDetailTab.STATUS) selectedColor else unselectedColor)
        binding.detailsTabIndicator.setBackgroundColor(
            if (tab == PurchaseDetailTab.DETAILS) selectedColor else ContextCompat.getColor(requireContext(), android.R.color.transparent)
        )
        binding.statusTabIndicator.setBackgroundColor(
            if (tab == PurchaseDetailTab.STATUS) selectedColor else ContextCompat.getColor(requireContext(), android.R.color.transparent)
        )
        itemsAdapter.tab = tab
        binding.totalsCard.isVisible = tab == PurchaseDetailTab.DETAILS
        binding.statusSummaryContainer.isVisible = tab == PurchaseDetailTab.STATUS
    }

    private fun openReceivingTransaction(event: PurchaseReceivingEvent) {
        if (event.linkedStockInTransactionId.isBlank()) return
        findNavController().navigate(
            R.id.action_purchaseDetailFragment_to_transactionDetailsFragment,
            android.os.Bundle().apply { putString("transactionId", event.linkedStockInTransactionId) }
        )
    }

    private fun viewModelStartPartialReceive(order: RestockOrder) {
        if (order.purchaseItems.none { it.remainingQuantity() > 0.0 }) {
            Snackbar.make(binding.root, R.string.purchase_already_received, Snackbar.LENGTH_SHORT).show()
            return
        }
        listViewModel.startPartialReceive(order.id)
        findNavController().navigate(
            R.id.action_purchaseDetailFragment_to_purchaseReceiveFragment,
            android.os.Bundle().apply { putString(PurchaseReceiveFragment.ARG_PURCHASE_ID, order.id) }
        )
    }

    private fun confirmDelete(order: RestockOrder) {
        if (!order.isDraft()) {
            Snackbar.make(binding.root, R.string.purchase_delete_only_draft, Snackbar.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_purchase_message, order.fallbackOrderLabel))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteInProgress = true
                listViewModel.deleteDraft(order)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmArchive(order: RestockOrder) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.archive))
            .setMessage(getString(R.string.archive_purchase_message, order.fallbackOrderLabel))
            .setPositiveButton(getString(R.string.archive)) { _, _ ->
                deleteInProgress = true
                listViewModel.archivePurchase(order)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmCancel(order: RestockOrder) {
        if (order.normalizedStatus == RestockStatus.RECEIVED) {
            Snackbar.make(binding.root, R.string.purchase_cancel_received_not_allowed, Snackbar.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cancel_order))
            .setMessage(getString(R.string.cancel_purchase_message, order.fallbackOrderLabel))
            .setPositiveButton(getString(R.string.cancel_order)) { _, _ ->
                listViewModel.cancelOrder(order)
            }
            .setNegativeButton(getString(R.string.keep_purchase), null)
            .show()
    }

    private fun sendPurchaseEmail(order: RestockOrder) {
        val subject = getString(R.string.purchase_email_subject, order.fallbackOrderLabel)
        val body = buildString {
            appendLine(getString(R.string.purchase_email_intro, order.supplierName))
            appendLine()
            appendLine("${getString(R.string.purchase_filter_order_number)}: ${order.fallbackOrderLabel}")
            appendLine("${getString(R.string.order_date)}: ${order.orderDate.toPurchaseDateTimeText()}")
            appendLine("${getString(R.string.receiving_location)}: ${order.receivingLocationName.ifBlank { "-" }}")
            appendLine()
            appendLine(getString(R.string.items))
            order.purchaseItems.forEach { item ->
                appendLine("- ${item.itemName} x ${item.orderedQuantity.toPurchaseQuantityText()} ${item.unit}")
            }
            if (order.memo.isNotBlank()) {
                appendLine()
                appendLine("${getString(R.string.memo)}: ${order.memo}")
            }
        }
        try {
            startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
            )
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.no_app_for_email, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        binding.itemsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_PURCHASE_ID = "purchaseId"
    }
}

private fun android.content.Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
