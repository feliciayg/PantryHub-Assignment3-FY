package com.example.pantryhub_assignment3_fy.ui.help

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemHelpTopicBinding

data class HelpTopicUiModel(
    val title: String,
    val answer: String,
    var isExpanded: Boolean = false
)

class HelpTopicAdapter(
    private val topics: List<HelpTopicUiModel>
) : RecyclerView.Adapter<HelpTopicAdapter.HelpTopicViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HelpTopicViewHolder {
        val binding = ItemHelpTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HelpTopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HelpTopicViewHolder, position: Int) {
        holder.bind(topics[position]) {
            topics[position].isExpanded = !topics[position].isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = topics.size

    class HelpTopicViewHolder(
        private val binding: ItemHelpTopicBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(topic: HelpTopicUiModel, onToggle: () -> Unit) {
            binding.titleTextView.text = topic.title
            binding.answerTextView.text = topic.answer
            binding.answerTextView.visibility = if (topic.isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.chevronImageView.rotation = if (topic.isExpanded) 180f else 0f
            binding.topicContainer.setOnClickListener { onToggle() }
        }
    }
}
