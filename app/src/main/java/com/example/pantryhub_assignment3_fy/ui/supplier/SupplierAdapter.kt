package com.example.pantryhub_assignment3_fy.ui.supplier

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemSupplierBinding
import com.example.pantryhub_assignment3_fy.model.PartnerType
import com.example.pantryhub_assignment3_fy.model.Supplier

class SupplierAdapter(
    private val onClick: (Supplier) -> Unit,
    private val onFavoriteClick: (Supplier) -> Unit
) : ListAdapter<Supplier, SupplierAdapter.SupplierViewHolder>(SupplierDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SupplierViewHolder {
        val binding = ItemSupplierBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SupplierViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SupplierViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onFavoriteClick)
    }

    class SupplierViewHolder(
        private val binding: ItemSupplierBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            supplier: Supplier,
            onClick: (Supplier) -> Unit,
            onFavoriteClick: (Supplier) -> Unit
        ) {
            val partnerType = PartnerType.fromValue(supplier.partnerType)
            val context = binding.root.context
            binding.nameTextView.text = supplier.name
            binding.contactTextView.text = listOfNotNull(
                supplier.contactPerson.takeIf { it.isNotBlank() },
                supplier.phone.takeIf { it.isNotBlank() },
                supplier.email.takeIf { it.isNotBlank() },
                supplier.address.takeIf { it.isNotBlank() }
            ).joinToString(" • ").ifBlank {
                if (partnerType == PartnerType.CUSTOMER) "No customer details added." else "No supplier details added."
            }
            binding.archivedBadgeTextView.visibility = if (supplier.isArchived) android.view.View.VISIBLE else android.view.View.GONE
            binding.partnerTypeIconView.setImageResource(
                if (partnerType == PartnerType.CUSTOMER) R.drawable.ic_person else R.drawable.ic_restock
            )
            binding.partnerTypeIconView.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (partnerType == PartnerType.CUSTOMER) R.color.inventory_danger else R.color.transaction_stock_in_blue
                )
            )
            binding.favoriteButton.setImageResource(
                if (supplier.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.favoriteButton.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (supplier.isFavorite) R.color.inventory_amber else R.color.inventory_outline
                )
            )
            binding.root.setOnClickListener { onClick(supplier) }
            binding.favoriteButton.setOnClickListener { onFavoriteClick(supplier) }
        }
    }

    object SupplierDiffCallback : DiffUtil.ItemCallback<Supplier>() {
        override fun areItemsTheSame(oldItem: Supplier, newItem: Supplier): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Supplier, newItem: Supplier): Boolean = oldItem == newItem
    }
}
