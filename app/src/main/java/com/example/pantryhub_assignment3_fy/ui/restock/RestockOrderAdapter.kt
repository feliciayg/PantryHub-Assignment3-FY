package com.example.pantryhub_assignment3_fy.ui.restock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemPurchaseOrderBinding
import com.example.pantryhub_assignment3_fy.model.RestockOrder

class PurchaseOrderAdapter(
    private val onOpen: (RestockOrder) -> Unit
) : ListAdapter<RestockOrder, PurchaseOrderAdapter.PurchaseViewHolder>(PurchaseOrderDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseViewHolder {
        return PurchaseViewHolder(
            ItemPurchaseOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: PurchaseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PurchaseViewHolder(
        private val binding: ItemPurchaseOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RestockOrder) {
            binding.orderNumberTextView.text = item.fallbackOrderLabel
            binding.statusChip.text = binding.root.context.getString(item.normalizedStatus.purchaseStatusLabelRes())
            binding.statusChip.setChipBackgroundColorResource(item.normalizedStatus.purchaseStatusBackgroundColorRes())
            binding.statusChip.setTextColor(binding.root.context.getColor(R.color.inventory_text_primary))
            binding.archivedBadgeTextView.visibility = if (item.isArchived) android.view.View.VISIBLE else android.view.View.GONE
            binding.orderDateValueTextView.text = item.orderDate.toPurchaseDateTimeText(emptyValue = "Pending")
            binding.supplierValueTextView.text = item.supplierName.ifBlank { "Not set" }
            binding.locationValueTextView.text = item.receivingLocationName.ifBlank { "Not set" }
            binding.itemsValueTextView.text = item.itemCount.toString()
            binding.totalValueTextView.text = item.totalCost.toPurchaseMoneyText()
            binding.root.setOnClickListener { onOpen(item) }
        }
    }
}

private object PurchaseOrderDiff : DiffUtil.ItemCallback<RestockOrder>() {
    override fun areItemsTheSame(oldItem: RestockOrder, newItem: RestockOrder): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: RestockOrder, newItem: RestockOrder): Boolean = oldItem == newItem
}
