package com.example.pantryhub_assignment3_fy.ui.home

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.databinding.ItemHomeInsightCardBinding

data class HomeInsightPage(
    val title: String,
    val subtitle: String,
    val metrics: List<HomeInsightMetric>
)

data class HomeInsightMetric(
    val value: String,
    val label: String,
    val statusFilter: String? = null
)

class HomeInsightsAdapter(
    private val onMetricClick: (HomeInsightMetric) -> Unit
) : RecyclerView.Adapter<HomeInsightsAdapter.InsightViewHolder>() {
    private var pages: List<HomeInsightPage> = emptyList()

    fun submitPages(nextPages: List<HomeInsightPage>) {
        pages = nextPages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsightViewHolder =
        InsightViewHolder(ItemHomeInsightCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: InsightViewHolder, position: Int) = holder.bind(pages[position], onMetricClick)

    override fun getItemCount(): Int = pages.size

    class InsightViewHolder(private val binding: ItemHomeInsightCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: HomeInsightPage, onMetricClick: (HomeInsightMetric) -> Unit) {
            val metrics = page.metrics + List((3 - page.metrics.size).coerceAtLeast(0)) { HomeInsightMetric("0", "") }
            binding.titleTextView.text = page.title
            binding.subtitleTextView.text = page.subtitle
            binding.valueOneTextView.text = metrics[0].value
            binding.labelOneTextView.text = metrics[0].label
            binding.valueTwoTextView.text = metrics[1].value
            binding.labelTwoTextView.text = metrics[1].label
            binding.valueThreeTextView.text = metrics[2].value
            binding.labelThreeTextView.text = metrics[2].label

            bindMetric(binding.metricOneContainer, metrics[0], onMetricClick)
            bindMetric(binding.metricTwoContainer, metrics[1], onMetricClick)
            bindMetric(binding.metricThreeContainer, metrics[2], onMetricClick)
            bindMetricLabel(binding.labelOneTextView, metrics[0])
            bindMetricLabel(binding.labelTwoTextView, metrics[1])
            bindMetricLabel(binding.labelThreeTextView, metrics[2])
        }

        private fun bindMetric(
            container: ViewGroup,
            metric: HomeInsightMetric,
            onMetricClick: (HomeInsightMetric) -> Unit
        ) {
            val isClickable = !metric.statusFilter.isNullOrBlank()
            container.isClickable = isClickable
            container.isFocusable = isClickable
            container.alpha = if (isClickable) 1f else 0.9f
            container.setOnClickListener(if (isClickable) {
                { onMetricClick(metric) }
            } else {
                null
            })
        }

        private fun bindMetricLabel(
            labelView: android.widget.TextView,
            metric: HomeInsightMetric
        ) {
            labelView.paintFlags = if (metric.statusFilter.isNullOrBlank()) {
                labelView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            } else {
                labelView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}
