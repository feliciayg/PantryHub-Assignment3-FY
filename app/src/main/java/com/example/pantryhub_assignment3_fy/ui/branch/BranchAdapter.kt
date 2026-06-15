package com.example.pantryhub_assignment3_fy.ui.branch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemBranchBinding
import com.example.pantryhub_assignment3_fy.model.Branch

class BranchAdapter(
    private val onClick: (Branch) -> Unit
) : ListAdapter<Branch, BranchAdapter.BranchViewHolder>(BranchDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BranchViewHolder {
        val binding = ItemBranchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BranchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BranchViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class BranchViewHolder(
        private val binding: ItemBranchBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(branch: Branch, onClick: (Branch) -> Unit) {
            binding.nameTextView.text = branch.name
            binding.memoTextView.text = branch.notes.ifBlank { "No memo added." }
            binding.archivedBadgeTextView.visibility = if (branch.isArchived) android.view.View.VISIBLE else android.view.View.GONE
            binding.root.setOnClickListener { onClick(branch) }
        }
    }

    object BranchDiffCallback : DiffUtil.ItemCallback<Branch>() {
        override fun areItemsTheSame(oldItem: Branch, newItem: Branch): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Branch, newItem: Branch): Boolean = oldItem == newItem
    }
}
