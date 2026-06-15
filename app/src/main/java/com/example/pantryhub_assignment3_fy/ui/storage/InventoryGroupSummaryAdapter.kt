package com.example.pantryhub_assignment3_fy.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemInventoryGroupSummaryBinding
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.google.android.material.color.MaterialColors

class InventoryGroupSummaryAdapter(
    private val onClick: (GroupSummaryUiModel) -> Unit
) : ListAdapter<GroupSummaryUiModel, InventoryGroupSummaryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemInventoryGroupSummaryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onClick
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemInventoryGroupSummaryBinding,
        private val onClick: (GroupSummaryUiModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: GroupSummaryUiModel) {
            binding.groupNameTextView.text = group.displayName
            binding.percentageTextView.text = "${group.percentage}%"
            binding.itemCountTextView.text = group.itemCount.toString()
            binding.quantityTextView.text = group.totalQuantity.toStorageQuantityText()
            applyPercentageColors(group)

            val previews = listOf(binding.previewImageOne, binding.previewImageTwo, binding.previewImageThree)
            previews.forEachIndexed { index, imageView ->
                imageView.isVisible = index < minOf(3, group.itemCount)
                if (imageView.isVisible) {
                    imageView.loadInventoryImage(group.previewImageUrls.getOrNull(index).orEmpty())
                }
            }
            binding.root.setOnClickListener { onClick(group) }
        }

        private fun applyPercentageColors(group: GroupSummaryUiModel) {
            val context = binding.root.context
            val (backgroundColor, textColor) = when (bindingAdapterPosition % 4) {
                0 -> R.color.inventory_coral_light to R.color.inventory_danger
                1 -> R.color.inventory_primary_container to R.color.inventory_primary
                2 -> R.color.chip_yellow to R.color.transaction_move_stock_amber
                else -> R.color.inventory_success_container to R.color.inventory_success_dark
            }
            binding.percentageTextView.backgroundTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor))
            binding.percentageTextView.setTextColor(ContextCompat.getColor(context, textColor))
            val ripple = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface, 0)
            binding.root.rippleColor = android.content.res.ColorStateList.valueOf(ripple)
        }

    }

    private object DiffCallback : DiffUtil.ItemCallback<GroupSummaryUiModel>() {
        override fun areItemsTheSame(oldItem: GroupSummaryUiModel, newItem: GroupSummaryUiModel): Boolean =
            oldItem.groupKey == newItem.groupKey

        override fun areContentsTheSame(oldItem: GroupSummaryUiModel, newItem: GroupSummaryUiModel): Boolean =
            oldItem == newItem
    }
}
