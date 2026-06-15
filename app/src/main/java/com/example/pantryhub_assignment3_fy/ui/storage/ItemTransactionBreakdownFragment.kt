package com.example.pantryhub_assignment3_fy.ui.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.FragmentItemTransactionBreakdownBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.StockMovement
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import com.example.pantryhub_assignment3_fy.ui.calendar.CalendarDayAdapter
import com.example.pantryhub_assignment3_fy.ui.calendar.CalendarDayUi
import com.example.pantryhub_assignment3_fy.ui.calendar.CalendarMarker
import com.example.pantryhub_assignment3_fy.ui.common.RadioSheetOption
import com.example.pantryhub_assignment3_fy.ui.common.showRadioSheet
import com.example.pantryhub_assignment3_fy.ui.movement.StockMovementAdapter
import com.example.pantryhub_assignment3_fy.ui.movement.StockMovementListItem
import com.example.pantryhub_assignment3_fy.ui.movement.StockMovementViewModel
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionDetailsViewModel
import com.example.pantryhub_assignment3_fy.ui.movement.TransactionTypeVisuals
import com.example.pantryhub_assignment3_fy.util.AppLogger
import com.example.pantryhub_assignment3_fy.util.ProductIdentity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class ItemTransactionBreakdownFragment : Fragment() {
    private var _binding: FragmentItemTransactionBreakdownBinding? = null
    private val binding get() = _binding!!
    private val inventoryViewModel: InventoryViewModel by activityViewModels()
    private val movementViewModel: StockMovementViewModel by activityViewModels()
    private val calendarDayAdapter = CalendarDayAdapter { day ->
        selectedDate = day.date
        visibleMonth = YearMonth.from(day.date)
        AppLogger.info(
            area = "Expiry",
            event = "calendar_date_selected",
            message = "Item transaction calendar date selected.",
            "date" to day.date
        )
        renderMovements()
    }
    private val adapter = StockMovementAdapter(onClick = { movement ->
        findNavController().navigate(
            R.id.action_itemTransactionBreakdownFragment_to_transactionDetailsFragment,
            Bundle().apply {
                putString(
                    TransactionDetailsViewModel.ARG_TRANSACTION_ID,
                    movement.transactionId.ifBlank { movement.representativeMovement.transactionId.ifBlank { movement.representativeMovement.id } }
                )
            }
        )
    })
    private var productKey: String = ""
    private var selectedBranchId: String = ""
    private var selectedDate: LocalDate = LocalDate.now()
    private var visibleMonth: YearMonth = YearMonth.now()
    private var productItems: List<InventoryItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentItemTransactionBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        productKey = arguments?.getString(ARG_PRODUCT_KEY).orEmpty()
        selectedBranchId = arguments?.getString(ARG_BRANCH_ID).orEmpty()
        AppLogger.info(
            area = "Expiry",
            event = "item_calendar_opened",
            message = "Item transaction calendar opened.",
            "locationFilter" to selectedBranchId.ifBlank { "All Locations" }
        )
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarRecyclerView.adapter = calendarDayAdapter
        binding.transactionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionsRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.locationButton.setOnClickListener { showLocationMenu() }
        binding.previousMonthButton.setOnClickListener {
            visibleMonth = visibleMonth.minusMonths(1)
            selectedDate = visibleMonth.atDay(1)
            logCalendarMonthChanged()
            renderMovements()
        }
        binding.nextMonthButton.setOnClickListener {
            visibleMonth = visibleMonth.plusMonths(1)
            selectedDate = visibleMonth.atDay(1)
            logCalendarMonthChanged()
            renderMovements()
        }
        binding.todayButton.setOnClickListener {
            selectedDate = LocalDate.now()
            visibleMonth = YearMonth.now()
            renderMovements()
        }

        inventoryViewModel.uiState.observe(viewLifecycleOwner) { state ->
            productItems = state.inventoryItems.filter { ProductIdentity.key(it) == productKey }
            updateLocationLabel()
            renderMovements()
        }
        movementViewModel.uiState.observe(viewLifecycleOwner) {
            renderMovements()
        }
    }

    private fun renderMovements() {
        val allMatchingMovements = productMovements()
        val movements = allMatchingMovements.filter { movement ->
            movement.occurredAt().toLocalDate() == selectedDate
        }
        val displayedMovements = movements.toDisplayedMovements()
        renderCalendar(allMatchingMovements)
        adapter.submitMovementList(displayedMovements)
        AppLogger.info(
            area = "Expiry",
            event = "calendar_transactions_loaded",
            message = "Transactions loaded for selected item date.",
            "date" to selectedDate,
            "count" to displayedMovements.size,
            "locationFilter" to selectedBranchId.ifBlank { "All Locations" }
        )
        binding.transactionsRecyclerView.isVisible = displayedMovements.isNotEmpty()
        binding.emptyTextView.isVisible = displayedMovements.isEmpty()
        binding.emptyTextView.text = if (allMatchingMovements.isEmpty()) {
            getString(R.string.no_transactions_for_this_item_yet)
        } else {
            getString(R.string.no_transactions_on_this_date)
        }
        binding.selectedDateTitleTextView.text = selectedDate.format(
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        )
    }

    private fun showLocationMenu() {
        val options = listOf(RadioSheetOption("", getString(R.string.all_locations))) + productItems
            .distinctBy { it.branchId }
            .sortedBy { it.branchName.lowercase() }
            .map { item ->
                RadioSheetOption(item.branchId, item.branchName.ifBlank { getString(R.string.unassigned_branch) })
            }
        showRadioSheet(
            title = getString(R.string.select_location),
            options = options,
            selectedKey = selectedBranchId,
            onSelected = { key ->
                selectedBranchId = key
                AppLogger.info(
                    area = "Locations",
                    event = "location_selected",
                    message = "Item transaction calendar location selected.",
                    "locationFilter" to key.ifBlank { "All Locations" }
                )
                updateLocationLabel()
                renderMovements()
            }
        )
    }

    private fun logCalendarMonthChanged() {
        AppLogger.info(
            area = "Expiry",
            event = "calendar_month_changed",
            message = "Item transaction calendar month changed.",
            "month" to visibleMonth
        )
    }

    private fun updateLocationLabel() {
        binding.locationButton.text = productItems
            .firstOrNull { it.branchId == selectedBranchId }
            ?.branchName
            ?.ifBlank { getString(R.string.unassigned_branch) }
            ?: getString(R.string.all_locations)
    }

    private fun productMovements(): List<StockMovement> {
        val productItemIds = productItems.map { it.id }.toSet()
        val visibleItemIds = productItems
            .filter { selectedBranchId.isBlank() || it.branchId == selectedBranchId }
            .map { it.id }
            .toSet()
        val movements = movementViewModel.uiState.value?.movements.orEmpty()
        val transferTypes = setOf(
            StockMovementType.BRANCH_TRANSFER_IN.name,
            StockMovementType.BRANCH_TRANSFER_OUT.name
        )
        val regularMovements = movements
            .filterNot { it.movementType in transferTypes }
            .filter { it.inventoryItemId in visibleItemIds }
        val transferMovements = movements
            .filter { it.movementType in transferTypes }
            .groupBy { it.transactionId.ifBlank { it.id } }
            .values
            .filter { group ->
                group.any { it.inventoryItemId in productItemIds } &&
                    (selectedBranchId.isBlank() || group.any { it.inventoryItemId in productItemIds && it.branchId == selectedBranchId })
            }
            .flatten()
        return regularMovements + transferMovements
    }

    private fun renderCalendar(movements: List<StockMovement>) {
        binding.monthTitleTextView.text = visibleMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        )
        val movementsByDate = movements.groupBy { it.occurredAt().toLocalDate() }
        calendarDayAdapter.submitList(buildMonthDays(movementsByDate))
    }

    private fun buildMonthDays(movementsByDate: Map<LocalDate, List<StockMovement>>): List<CalendarDayUi> {
        val firstDay = visibleMonth.atDay(1)
        val start = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
        return (0 until 42).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dateMovements = movementsByDate[date].orEmpty()
            val markerColors = markerColorsFor(dateMovements)
            CalendarDayUi(
                date = date,
                isInVisibleMonth = YearMonth.from(date) == visibleMonth,
                isToday = date == LocalDate.now(),
                isSelected = date == selectedDate,
                marker = if (dateMovements.isEmpty()) CalendarMarker.NONE else CalendarMarker.UPCOMING,
                markerColorResList = markerColors
            )
        }
    }

    private fun markerColorsFor(movements: List<StockMovement>): List<Int> {
        if (movements.isEmpty()) return emptyList()
        val movementTypes = movements.map { it.movementType }.toSet()
        return buildList {
            if (movementTypes.any { it in STOCK_IN_MARKER_TYPES }) {
                add(TransactionTypeVisuals.colorForMovement(StockMovementType.STOCK_IN.name))
            }
            if (movementTypes.any { it in STOCK_OUT_MARKER_TYPES }) {
                add(TransactionTypeVisuals.colorForMovement(StockMovementType.STOCK_OUT.name))
            }
            if (movementTypes.any { it in MOVE_MARKER_TYPES }) {
                add(TransactionTypeVisuals.colorForMovement(StockMovementType.BRANCH_TRANSFER_IN.name))
            }
            if (StockMovementType.ADJUST_STOCK.name in movementTypes) {
                add(TransactionTypeVisuals.colorForMovement(StockMovementType.ADJUST_STOCK.name))
            }
        }.take(4)
    }

    private fun com.example.pantryhub_assignment3_fy.model.StockMovement.occurredAt(): Long =
        transactionAt.takeIf { it > 0L } ?: createdAt

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun List<StockMovement>.toDisplayedMovements(): List<StockMovementListItem> {
        val transferTypes = setOf(
            StockMovementType.BRANCH_TRANSFER_IN.name,
            StockMovementType.BRANCH_TRANSFER_OUT.name
        )
        val nonTransfers = filterNot { it.movementType in transferTypes }
            .map { movement ->
                StockMovementListItem(
                    stableId = movement.id,
                    transactionId = movement.transactionId.ifBlank { movement.id },
                    representativeMovement = movement
                )
            }
        val groupedTransfers = filter { it.movementType in transferTypes }
            .groupBy { it.transactionId.ifBlank { it.id } }
            .values
            .map { transferGroup ->
                val incoming = transferGroup.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_IN.name }
                val outgoing = transferGroup.firstOrNull { it.movementType == StockMovementType.BRANCH_TRANSFER_OUT.name }
                val representative = incoming ?: outgoing ?: transferGroup.first()
                StockMovementListItem(
                    stableId = "transfer:${representative.transactionId.ifBlank { representative.id }}",
                    transactionId = representative.transactionId.ifBlank { representative.id },
                    representativeMovement = representative,
                    pairedTransferMovement = if (representative === incoming) outgoing else incoming
                )
            }
        return (nonTransfers + groupedTransfers).sortedByDescending {
            it.representativeMovement.occurredAt()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_PRODUCT_KEY = "productKey"
        const val ARG_BRANCH_ID = "branchId"
        private val STOCK_IN_MARKER_TYPES = setOf(
            StockMovementType.STOCK_IN.name,
            StockMovementType.RESTOCK_RECEIVED.name,
            StockMovementType.RETURN.name
        )
        private val STOCK_OUT_MARKER_TYPES = setOf(
            StockMovementType.STOCK_OUT.name,
            StockMovementType.SALES_DEDUCTION.name,
            StockMovementType.DAMAGE.name,
            StockMovementType.EXPIRED.name,
            StockMovementType.WASTE.name
        )
        private val MOVE_MARKER_TYPES = setOf(
            StockMovementType.BRANCH_TRANSFER_IN.name,
            StockMovementType.BRANCH_TRANSFER_OUT.name
        )
    }
}
