package com.example.pantryhub_assignment3_fy.ui.movement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInPickerBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage

class StockInPickerAdapter(
    private val onClick: (InventoryItem) -> Unit
) : ListAdapter<StockInPickerRow, StockInPickerAdapter.ViewHolder>(DiffCallback) {
    var mode: TransactionMode = TransactionMode.STOCK_IN
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemStockInPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), mode)
    }

    class ViewHolder(
        private val binding: ItemStockInPickerBinding,
        private val onClick: (InventoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: StockInPickerRow, mode: TransactionMode) {
            val item = row.item
            binding.nameTextView.text = item.name
            binding.metaTextView.text = item.metaText(row.selectedExpiryDate.takeIf { mode == TransactionMode.STOCK_IN })
            binding.currentQuantityTextView.text = item.quantity.toMovementQuantityWithUnit(item.unit)
            binding.addedQuantityTextView.isVisible = row.isSelected
            binding.addedQuantityTextView.text = "${mode.quantityPrefix}${row.selectedQuantity.toMovementQuantityWithUnit(item.unit)}"
            binding.addedQuantityTextView.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    binding.root.context,
                    row.pendingQuantityColor(mode)
                )
            )
            binding.imageView.loadInventoryImage(item.imageUrl)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StockInPickerRow>() {
        override fun areItemsTheSame(oldItem: StockInPickerRow, newItem: StockInPickerRow): Boolean =
            oldItem.item.id == newItem.item.id

        override fun areContentsTheSame(oldItem: StockInPickerRow, newItem: StockInPickerRow): Boolean =
            oldItem == newItem
    }
}

class StockInSelectedAdapter(
    private val onEdit: (InventoryItem) -> Unit,
    private val onRemove: (InventoryItem) -> Unit
) : ListAdapter<StockInLine, StockInSelectedAdapter.ViewHolder>(SelectedDiffCallback) {
    var mode: TransactionMode = TransactionMode.STOCK_IN
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            com.example.pantryhub_assignment3_fy.databinding.ItemStockInSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onEdit,
            onRemove
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), mode)
    }

    class ViewHolder(
        private val binding: com.example.pantryhub_assignment3_fy.databinding.ItemStockInSelectedBinding,
        private val onEdit: (InventoryItem) -> Unit,
        private val onRemove: (InventoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: StockInLine, mode: TransactionMode) {
            binding.nameTextView.text = line.item.name
            binding.metaTextView.text = line.item.metaText(line.expiryDate.takeIf { mode == TransactionMode.STOCK_IN })
            binding.quantityTextView.text = "${mode.quantityPrefix}${line.quantity.toMovementQuantityWithUnit(line.item.unit)}"
            binding.quantityTextView.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    binding.root.context,
                    line.pendingQuantityColor(mode)
                )
            )
            binding.imageView.loadInventoryImage(line.item.imageUrl)
            binding.root.setOnClickListener { onEdit(line.item) }
            binding.removeButton.setOnClickListener { onRemove(line.item) }
        }
    }

    private object SelectedDiffCallback : DiffUtil.ItemCallback<StockInLine>() {
        override fun areItemsTheSame(oldItem: StockInLine, newItem: StockInLine): Boolean =
            oldItem.item.id == newItem.item.id

        override fun areContentsTheSame(oldItem: StockInLine, newItem: StockInLine): Boolean =
            oldItem == newItem
    }
}

private fun InventoryItem.metaText(pendingExpiryDate: Long? = null): String =
    listOf(
        category.takeIf { it.isNotBlank() },
        brand.takeIf { it.isNotBlank() },
        StockLevelRules.effectiveReorderPoint(this).takeIf { it > 0 }?.let { it.toString() },
        pendingExpiryDate?.let { "Expires ${DateUtils.formatDisplayDate(it)}" }
            ?: expiryDate.takeIf { it > 0L }?.let { DateUtils.formatDisplayDate(it) }
    ).filterNotNull().joinToString(" | ")

private fun StockInPickerRow.pendingQuantityColor(mode: TransactionMode): Int = when {
    mode == TransactionMode.ADJUST_STOCK && selectedQuantity == item.quantity ->
        com.example.pantryhub_assignment3_fy.R.color.inventory_text_secondary
    else -> TransactionTypeVisuals.colorFor(mode)
}

private fun StockInLine.pendingQuantityColor(mode: TransactionMode): Int = when {
    mode == TransactionMode.ADJUST_STOCK && quantity == item.quantity ->
        com.example.pantryhub_assignment3_fy.R.color.inventory_text_secondary
    else -> TransactionTypeVisuals.colorFor(mode)
}
