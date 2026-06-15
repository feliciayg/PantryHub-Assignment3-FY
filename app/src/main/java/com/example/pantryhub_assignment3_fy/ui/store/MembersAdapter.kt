package com.example.pantryhub_assignment3_fy.ui.store

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemStaffMemberBinding
import com.example.pantryhub_assignment3_fy.model.StaffMember

class MembersAdapter : ListAdapter<StaffMember, MembersAdapter.MemberViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemStaffMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MemberViewHolder(
        private val binding: ItemStaffMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: StaffMember) {
            val fallbackName = member.email.substringBefore("@").ifBlank { "Member" }
            val displayName = member.displayName.ifBlank { fallbackName }
            binding.memberInitialTextView.text = displayName.firstOrNull()?.uppercase() ?: "M"
            binding.memberNameTextView.text = displayName
            binding.memberRoleTextView.text = member.role.replaceFirstChar { char -> char.uppercase() }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StaffMember>() {
        override fun areItemsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean = oldItem == newItem
    }
}
