package com.example.pantryhub_assignment3_fy.ui.movement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemStockMovementDateHeaderBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemStockMovementBinding
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.model.StockMovementType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockMovementAdapter(
    private val onClick: (StockMovementListItem) -> Unit = {},
    private val showDateHeaders: Boolean = false
) : ListAdapter<StockMovementAdapterItem, RecyclerView.ViewHolder>(StockMovementDiff) {

    fun submitMovementList(items: List<StockMovementListItem>) {
        submitList(if (showDateHeaders) items.withDateHeaders() else items.map { StockMovementAdapterItem.MovementRow(it) })
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is StockMovementAdapterItem.DateHeader -> VIEW_TYPE_DATE_HEADER
        is StockMovementAdapterItem.MovementRow -> VIEW_TYPE_MOVEMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(
                ItemStockMovementDateHeaderBinding.inflate(inflater, parent, false)
            )
            else -> StockMovementViewHolder(
                ItemStockMovementBinding.inflate(inflater, parent, false),
                onClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StockMovementAdapterItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is StockMovementAdapterItem.MovementRow -> (holder as StockMovementViewHolder).bind(item.item)
        }
    }

    private fun List<StockMovementListItem>.withDateHeaders(): List<StockMovementAdapterItem> {
        val result = mutableListOf<StockMovementAdapterItem>()
        var lastDateLabel: String? = null
        forEach { item ->
            val occurredAt = item.representativeMovement.transactionAt.takeIf { it > 0L }
                ?: item.representativeMovement.createdAt
            val label = occurredAt.toMovementDateHeaderText()
            if (label != lastDateLabel) {
                result += StockMovementAdapterItem.DateHeader(label)
                lastDateLabel = label
            }
            result += StockMovementAdapterItem.MovementRow(item)
        }
        return result
    }

    class DateHeaderViewHolder(
        private val binding: ItemStockMovementDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StockMovementAdapterItem.DateHeader) {
            binding.dateHeaderTextView.text = item.label
        }
    }

    class StockMovementViewHolder(
        private val binding: ItemStockMovementBinding,
        private val onClick: (StockMovementListItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StockMovementListItem) {
            val movement = item.representativeMovement
            binding.itemNameTextView.text = movement.itemName
            binding.typeIconImageView.setImageResource(TransactionTypeVisuals.iconForMovement(movement.movementType))
            val typeColor = TransactionTypeVisuals.colorForMovement(movement.movementType)
            binding.typeIconImageView.setColorFilter(ContextCompat.getColor(binding.root.context, typeColor))
            binding.summaryTextView.text = item.summaryLine()
            binding.detailTextView.text = item.detailLine()
            binding.changeTextView.text = item.changeText()
            binding.changeTextView.setTextColor(ContextCompat.getColor(binding.root.context, typeColor))
            val occurredAt = movement.transactionAt.takeIf { it > 0L } ?: movement.createdAt
            binding.metaTextView.text = "${movement.performedByName.ifBlank { "Unknown staff" }} • ${occurredAt.toMovementDateTimeText()}"
            binding.root.setOnClickListener { onClick(item) }
        }

        private fun String.toDisplay(): String =
            lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }

        private fun StockMovementListItem.summaryLine(): String {
            val movement = representativeMovement
            return if (pairedTransferMovement != null) {
                "${movement.movementType.toReadableType()} • ${movement.branchName.ifBlank { "Unassigned location" }}"
            } else {
                "${movement.movementType.toReadableType()} • ${movement.branchName.ifBlank { "Unassigned location" }}"
            }
        }

        private fun StockMovementListItem.detailLine(): String {
            val movement = representativeMovement
            val paired = pairedTransferMovement
            return if (paired != null) {
                listOf(
                    "${movement.quantityBefore.toMovementQuantityText()} → ${movement.quantityAfter.toMovementQuantityText()}",
                    "Transferred from ${paired.branchName.ifBlank { "Unknown location" }}"
                ).joinToString(" • ")
            } else {
                listOf(
                    "${movement.quantityBefore.toMovementQuantityText()} → ${movement.quantityAfter.toMovementQuantityText()}",
                    movement.counterpartyName.takeIf { it.isNotBlank() },
                    movement.note.takeIf { movement.movementType in transferTypes && it.isNotBlank() }
                ).filterNotNull().joinToString(" • ")
            }
        }

        private fun StockMovementListItem.changeText(): String {
            val movement = representativeMovement
            return when (movement.movementType) {
                StockMovementType.STOCK_IN.name,
                StockMovementType.RETURN.name,
                StockMovementType.RESTOCK_RECEIVED.name -> "+${movement.quantity.toMovementQuantityText()} ${movement.unit}".trim()
                StockMovementType.STOCK_OUT.name,
                StockMovementType.DAMAGE.name,
                StockMovementType.EXPIRED.name,
                StockMovementType.WASTE.name,
                StockMovementType.SALES_DEDUCTION.name -> "-${movement.quantity.toMovementQuantityText()} ${movement.unit}".trim()
                StockMovementType.ADJUST_STOCK.name -> "→ ${movement.quantityAfter.toMovementQuantityText()}"
                else -> "${movement.quantity.toMovementQuantityText()} ${movement.unit}".trim()
            }
        }

        private fun String.toReadableType(): String = when (this) {
            StockMovementType.STOCK_IN.name -> "Stock in"
            StockMovementType.STOCK_OUT.name -> "Stock out"
            StockMovementType.RESTOCK_RECEIVED.name -> "Restock received"
            StockMovementType.SALES_DEDUCTION.name -> "Sales deduction"
            StockMovementType.BRANCH_TRANSFER_IN.name,
            StockMovementType.BRANCH_TRANSFER_OUT.name -> "Move stock"
            StockMovementType.ADJUST_STOCK.name -> "Adjust stock"
            else -> toDisplay()
        }

        companion object {
            private val transferTypes = setOf(
                StockMovementType.BRANCH_TRANSFER_IN.name,
                StockMovementType.BRANCH_TRANSFER_OUT.name
            )
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_MOVEMENT = 1
    }
}

sealed class StockMovementAdapterItem {
    data class DateHeader(val label: String) : StockMovementAdapterItem()
    data class MovementRow(val item: StockMovementListItem) : StockMovementAdapterItem()
}

private object StockMovementDiff : DiffUtil.ItemCallback<StockMovementAdapterItem>() {
    override fun areItemsTheSame(oldItem: StockMovementAdapterItem, newItem: StockMovementAdapterItem): Boolean =
        when {
            oldItem is StockMovementAdapterItem.DateHeader && newItem is StockMovementAdapterItem.DateHeader ->
                oldItem.label == newItem.label
            oldItem is StockMovementAdapterItem.MovementRow && newItem is StockMovementAdapterItem.MovementRow ->
                oldItem.item.stableId == newItem.item.stableId
            else -> false
        }

    override fun areContentsTheSame(oldItem: StockMovementAdapterItem, newItem: StockMovementAdapterItem): Boolean = oldItem == newItem
}

private fun Long.toMovementDateHeaderText(): String =
    if (this <= 0L) "Pending date" else MOVEMENT_DATE_HEADER_FORMAT.format(Date(this))

private val MOVEMENT_DATE_HEADER_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
