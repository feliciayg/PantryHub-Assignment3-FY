package com.example.pantryhub_assignment3_fy.ui.restock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemPurchaseDetailEntryBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInPickerBinding
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInSelectedBinding
import com.example.pantryhub_assignment3_fy.model.PurchaseOrderItem
import com.example.pantryhub_assignment3_fy.model.remainingQuantity
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage

enum class PurchaseDetailTab {
    DETAILS,
    STATUS
}

class PurchaseDetailItemsAdapter : ListAdapter<PurchaseOrderItem, PurchaseDetailItemsAdapter.ViewHolder>(PurchaseOrderItemDiff) {
    var tab: PurchaseDetailTab = PurchaseDetailTab.DETAILS
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemPurchaseDetailEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), tab)
    }

    class ViewHolder(
        private val binding: ItemPurchaseDetailEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PurchaseOrderItem, tab: PurchaseDetailTab) {
            binding.imageView.loadInventoryImage(item.imageUrl, R.drawable.ic_storage)
            binding.nameTextView.text = item.itemName
            binding.metaTextView.text = listOf(item.category, item.brand, item.sku)
                .filter { it.isNotBlank() }
                .joinToString(" | ")

            when (tab) {
                PurchaseDetailTab.DETAILS -> {
                    binding.primaryValueTextView.text = item.unitCost.toPurchaseMoneyText()
                    binding.secondaryValueTextView.text = "x ${item.orderedQuantity.toPurchaseQuantityText()}"
                }
                PurchaseDetailTab.STATUS -> {
                    binding.primaryValueTextView.text = "${item.receivedQuantity.toPurchaseQuantityText()}/${item.orderedQuantity.toPurchaseQuantityText()}"
                    binding.secondaryValueTextView.text = item.remainingQuantity().takeIf { it > 0.0 }
                        ?.let { "${it.toPurchaseQuantityText()} remaining" }
                        ?: binding.root.context.getString(R.string.received)
                }
            }
        }
    }
}

class PurchaseReceiveSelectedAdapter(
    private val onEdit: (PurchaseReceivePickerRow) -> Unit
) : ListAdapter<PurchaseReceivePickerRow, PurchaseReceiveSelectedAdapter.ViewHolder>(PurchaseReceivePickerRowDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemStockInSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false), onEdit)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemStockInSelectedBinding,
        private val onEdit: (PurchaseReceivePickerRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PurchaseReceivePickerRow) {
            val item = row.item
            binding.imageView.loadInventoryImage(item.imageUrl, R.drawable.ic_storage)
            binding.nameTextView.text = item.itemName
            binding.metaTextView.text = listOf(item.category, item.brand, item.sku)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
            binding.quantityTextView.text = "${row.selectedQuantity.toPurchaseQuantityText()} ${item.unit}".trim()
            binding.root.setOnClickListener { onEdit(row) }
            binding.removeButton.setOnClickListener { onEdit(row.copy(selectedQuantity = 0.0)) }
        }
    }
}

class PurchaseReceivePickerAdapter(
    private val onEdit: (PurchaseReceivePickerRow) -> Unit
) : ListAdapter<PurchaseReceivePickerRow, PurchaseReceivePickerAdapter.ViewHolder>(PurchaseReceivePickerRowDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemStockInPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false), onEdit)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemStockInPickerBinding,
        private val onEdit: (PurchaseReceivePickerRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PurchaseReceivePickerRow) {
            val item = row.item
            binding.imageView.loadInventoryImage(item.imageUrl, R.drawable.ic_storage)
            binding.nameTextView.text = item.itemName
            binding.metaTextView.text = listOf(
                item.category,
                item.brand,
                item.remainingQuantityLabel()
            ).filter { it.isNotBlank() }.joinToString(" | ")
            binding.currentQuantityTextView.text = row.remainingQuantity.toPurchaseQuantityText()
            binding.addedQuantityTextView.text = row.selectedQuantity.takeIf { it > 0.0 }?.toPurchaseQuantityText().orEmpty()
            binding.addedQuantityTextView.visibility =
                if (row.selectedQuantity > 0.0) android.view.View.VISIBLE else android.view.View.GONE
            binding.root.setOnClickListener { onEdit(row) }
        }
    }
}

private object PurchaseOrderItemDiff : DiffUtil.ItemCallback<PurchaseOrderItem>() {
    override fun areItemsTheSame(oldItem: PurchaseOrderItem, newItem: PurchaseOrderItem): Boolean =
        oldItem.inventoryItemId == newItem.inventoryItemId && oldItem.itemName == newItem.itemName

    override fun areContentsTheSame(oldItem: PurchaseOrderItem, newItem: PurchaseOrderItem): Boolean =
        oldItem == newItem
}

private object PurchaseReceivePickerRowDiff : DiffUtil.ItemCallback<PurchaseReceivePickerRow>() {
    override fun areItemsTheSame(oldItem: PurchaseReceivePickerRow, newItem: PurchaseReceivePickerRow): Boolean =
        oldItem.item.inventoryItemId == newItem.item.inventoryItemId && oldItem.item.itemName == newItem.item.itemName

    override fun areContentsTheSame(oldItem: PurchaseReceivePickerRow, newItem: PurchaseReceivePickerRow): Boolean =
        oldItem == newItem
}

private fun PurchaseOrderItem.remainingQuantityLabel(): String =
    "${remainingQuantity().toPurchaseQuantityText()} ${unit.ifBlank { "items" }} remaining".trim()
