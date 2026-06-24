package com.example.pantryhub_assignment3_fy.ui.movement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemTransactionDetailLineBinding
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage

class TransactionDetailsAdapter :
    ListAdapter<TransactionItemLineUiModel, TransactionDetailsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTransactionDetailLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemTransactionDetailLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: TransactionItemLineUiModel) {
            binding.itemNameTextView.text = line.itemName
            binding.identifierTextView.text = line.identifier
            binding.identifierTextView.isVisible = line.identifier.isNotBlank()
            binding.itemImageView.loadInventoryImage(line.imageUrl)
            binding.secondaryTextView.text = line.secondaryText
            binding.secondaryTextView.isVisible = line.secondaryText.isNotBlank()
            binding.balanceSummaryTextView.text = line.balanceSummary
            binding.balanceSummaryTextView.isVisible = line.balanceSummary.isNotBlank()
            // The ViewModel supplies the correct meaning for each mode. A transfer is a neutral
            // moved quantity, so the adapter must not automatically present it as stock-in.
            binding.quantityHighlightTextView.text = line.quantityHighlight
            binding.quantityHighlightTextView.setTextColor(
                ContextCompat.getColor(binding.root.context, line.accentColorRes)
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TransactionItemLineUiModel>() {
        override fun areItemsTheSame(
            oldItem: TransactionItemLineUiModel,
            newItem: TransactionItemLineUiModel
        ): Boolean = oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(
            oldItem: TransactionItemLineUiModel,
            newItem: TransactionItemLineUiModel
        ): Boolean = oldItem == newItem
    }
}
