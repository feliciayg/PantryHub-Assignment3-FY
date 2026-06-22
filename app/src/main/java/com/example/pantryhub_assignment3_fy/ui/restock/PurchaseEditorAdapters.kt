package com.example.pantryhub_assignment3_fy.ui.restock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInPickerBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInSelectedBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage

class PurchaseSelectedItemAdapter(
    private val onEdit: (PurchaseOrderItem) -> Unit,
    private val onRemove: (PurchaseOrderItem) -> Unit
) : ListAdapter<PurchaseOrderItem, PurchaseSelectedItemAdapter.SelectedItemViewHolder>(PurchaseItemDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedItemViewHolder {
        return SelectedItemViewHolder(
            ItemStockInSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SelectedItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SelectedItemViewHolder(
        private val binding: ItemStockInSelectedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PurchaseOrderItem) {
            binding.imageView.loadInventoryImage(item.imageUrl, R.drawable.ic_storage)
            binding.nameTextView.text = item.itemName
            binding.metaTextView.text = listOf(item.category, item.brand, item.sku)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            binding.quantityTextView.text = "${item.orderedQuantity.toPurchaseQuantityText()} ${item.unit}"
            binding.root.setOnClickListener { onEdit(item) }
            binding.removeButton.setOnClickListener { onRemove(item) }
        }
    }
}

class PurchaseItemPickerAdapter(
    private val onSelect: (InventoryItem) -> Unit
) : ListAdapter<InventoryItem, PurchaseItemPickerAdapter.PickerViewHolder>(PurchasePickerInventoryItemDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
        return PickerViewHolder(
            ItemStockInPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PickerViewHolder(
        private val binding: ItemStockInPickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InventoryItem) {
            val context = binding.root.context
            binding.imageView.loadInventoryImage(item.imageUrl, R.drawable.ic_storage)
            binding.nameTextView.text = item.name
            binding.metaTextView.text = listOf(
                item.category.ifBlank { context.getString(R.string.uncategorised) },
                item.brand.ifBlank { context.getString(R.string.no_brand) },
                item.costPrice.toPurchaseMoneyText()
            )
                .joinToString(" | ")
            binding.currentQuantityTextView.text = listOf(
                item.quantity.toPurchaseQuantityText(),
                item.unit
            ).filter { it.isNotBlank() }.joinToString(" ")
            binding.currentQuantityTextView.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        StockLevelRules.isOutOfStock(item) -> R.color.inventory_danger
                        StockLevelRules.isLowStock(item) -> R.color.inventory_warning
                        else -> R.color.inventory_primary
                    }
                )
            )
            binding.addedQuantityTextView.visibility = View.GONE
            binding.root.setOnClickListener { onSelect(item) }
        }
    }
}

private object PurchaseItemDiff : DiffUtil.ItemCallback<PurchaseOrderItem>() {
    override fun areItemsTheSame(oldItem: PurchaseOrderItem, newItem: PurchaseOrderItem): Boolean =
        oldItem.inventoryItemId == newItem.inventoryItemId && oldItem.itemName == newItem.itemName

    override fun areContentsTheSame(oldItem: PurchaseOrderItem, newItem: PurchaseOrderItem): Boolean = oldItem == newItem
}

private object PurchasePickerInventoryItemDiff : DiffUtil.ItemCallback<InventoryItem>() {
    override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean = oldItem == newItem
}
