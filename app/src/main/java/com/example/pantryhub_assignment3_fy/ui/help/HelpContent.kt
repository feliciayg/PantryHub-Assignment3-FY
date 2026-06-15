package com.example.pantryhub_assignment3_fy.ui.help

import android.content.Context
import com.example.pantryhub_assignment3_fy.R

object HelpContent {
    fun topics(context: Context): List<HelpTopicUiModel> = listOf(
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_getting_started),
            answer = context.getString(R.string.help_answer_getting_started),
            isExpanded = true
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_add_item),
            answer = context.getString(R.string.help_answer_add_item)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_stock_in),
            answer = context.getString(R.string.help_answer_stock_in)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_stock_out),
            answer = context.getString(R.string.help_answer_stock_out)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_move_stock),
            answer = context.getString(R.string.help_answer_move_stock)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_adjust_stock),
            answer = context.getString(R.string.help_answer_adjust_stock)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_create_purchase_order),
            answer = context.getString(R.string.help_answer_create_purchase_order)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_receive_purchase_items),
            answer = context.getString(R.string.help_answer_receive_purchase_items)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_shortages),
            answer = context.getString(R.string.help_answer_shortages)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_expiry_dates),
            answer = context.getString(R.string.help_answer_expiry_dates)
        ),
        HelpTopicUiModel(
            title = context.getString(R.string.help_topic_csv_import_export),
            answer = context.getString(R.string.help_answer_csv_import_export)
        )
    )
}
