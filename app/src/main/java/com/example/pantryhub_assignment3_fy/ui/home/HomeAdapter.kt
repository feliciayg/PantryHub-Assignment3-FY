package com.example.pantryhub_assignment3_fy.ui.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemStockInPickerBinding
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage

class HomePriorityAdapter(
    private val onClick: (InventoryItem) -> Unit
) : ListAdapter<InventoryItem, HomePriorityAdapter.PriorityViewHolder>(InventoryItemDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriorityViewHolder {
        return PriorityViewHolder(
            ItemStockInPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onClick
        )
    }

    override fun onBindViewHolder(holder: PriorityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PriorityViewHolder(
        private val binding: ItemStockInPickerBinding,
        private val onClick: (InventoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(inventoryItem: InventoryItem) {
            val context = binding.root.context
            binding.nameTextView.text = inventoryItem.name
            binding.metaTextView.text = listOf(
                inventoryItem.storageLocation.takeIf { it.isNotBlank() },
                inventoryItem.unit.takeIf { it.isNotBlank() }?.let { "Unit: $it" }
            ).joinToString(" | ").ifBlank { "No location added" }

            val countdown = DateUtils.countdownText(inventoryItem.expiryDate)
            binding.currentQuantityTextView.text = countdown.uppercase()
            binding.currentQuantityTextView.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (countdown == "Today") R.color.inventory_coral_light else R.color.chip_yellow)
            )
            binding.currentQuantityTextView.setTextColor(
                ContextCompat.getColor(context, if (countdown == "Today") R.color.inventory_coral else R.color.inventory_text_primary)
            )
            binding.imageView.loadInventoryImage(inventoryItem.imageUrl)
            binding.addedQuantityTextView.isVisible = false
            binding.root.setOnClickListener { onClick(inventoryItem) }
        }
    }
}

private object InventoryItemDiff : DiffUtil.ItemCallback<InventoryItem>() {
    override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean = oldItem == newItem
}
