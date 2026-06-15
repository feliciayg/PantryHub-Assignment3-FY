package com.example.pantryhub_assignment3_fy.ui.storage

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryhub_assignment3_fy.R
import com.example.pantryhub_assignment3_fy.databinding.ItemInventoryItemBinding
import com.example.pantryhub_assignment3_fy.util.loadInventoryImage
import com.example.pantryhub_assignment3_fy.util.DateUtils
import com.example.pantryhub_assignment3_fy.util.ExpiryLotRules
import com.example.pantryhub_assignment3_fy.util.StockLevelRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InventoryAdapter(
    private val onClick: (InventoryDisplayRow) -> Unit,
    private val onEdit: (InventoryDisplayRow) -> Unit,
    private val onDelete: (InventoryDisplayRow) -> Unit
) : ListAdapter<InventoryDisplayRow, InventoryAdapter.InventoryItemViewHolder>(InventoryItemDiffCallback) {
    private var openInventoryItemId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryItemViewHolder {
        val binding = ItemInventoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InventoryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InventoryItemViewHolder, position: Int) {
        holder.bind(
            row = getItem(position),
            isOpen = getItem(position).realInventoryItemId == openInventoryItemId,
            onClick = { row -> closeOpenItem(); onClick(row) },
            onEdit = { row -> closeOpenItem(); onEdit(row) },
            onDelete = { row -> closeOpenItem(); onDelete(row) },
            onOpened = { row -> row.realInventoryItemId?.let(::setOpenInventoryItem) },
            onClosed = { row -> if (openInventoryItemId == row.realInventoryItemId) openInventoryItemId = null },
            groupHeader = null
        )
    }

    override fun onViewRecycled(holder: InventoryItemViewHolder) {
        holder.closeActions(animate = false)
        super.onViewRecycled(holder)
    }

    fun closeOpenItem(): Boolean {
        val openedId = openInventoryItemId ?: return false
        val index = currentList.indexOfFirst { it.realInventoryItemId == openedId }
        openInventoryItemId = null
        if (index != -1) notifyItemChanged(index)
        return true
    }

    fun setOpenInventoryItem(inventoryItemId: String) {
        val previousId = openInventoryItemId
        openInventoryItemId = inventoryItemId
        val currentIndex = currentList.indexOfFirst { it.realInventoryItemId == inventoryItemId }
        if (previousId != null && previousId != inventoryItemId) {
            val previousIndex = currentList.indexOfFirst { it.realInventoryItemId == previousId }
            if (previousIndex != -1) notifyItemChanged(previousIndex)
        }
        // Keep the newly revealed row bound as open so RecyclerView redraws do not immediately close it.
        if (currentIndex != -1) notifyItemChanged(currentIndex)
    }

    class InventoryItemViewHolder(
        private val binding: ItemInventoryItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var isRevealed = false
        private var downX = 0f
        private var downY = 0f
        private var startTranslationX = 0f
        private var isDraggingSwipe = false

        fun bind(
            row: InventoryDisplayRow,
            isOpen: Boolean,
            onClick: (InventoryDisplayRow) -> Unit,
            onEdit: (InventoryDisplayRow) -> Unit,
            onDelete: (InventoryDisplayRow) -> Unit,
            onOpened: (InventoryDisplayRow) -> Unit,
            onClosed: (InventoryDisplayRow) -> Unit,
            groupHeader: String?
        ) {
            val context = binding.root.context
            binding.nameTextView.text = row.name
            binding.supportingTextView.text = row.supportingText()
            binding.archivedBadgeTextView.isVisible = row.isArchived
            bindExpiry(row)
            binding.quantityTextView.text = row.quantity.toStorageQuantityText()
            binding.quantityTextView.setTextColor(ContextCompat.getColor(context, quantityColor(row)))
            binding.unitTextView.text = row.unit
            binding.unitTextView.isVisible = row.unit.isNotBlank()
            binding.groupHeaderTextView.text = groupHeader
            binding.groupHeaderTextView.isVisible = groupHeader != null
            binding.inventoryImageView.alpha = 1f
            binding.inventoryImageView.loadInventoryImage(row.imageUrl)

            if (isOpen) revealActions(animate = false) else closeActions(animate = false)

            binding.inventoryItemCard.setOnClickListener {
                if (isRevealed) {
                    closeActions()
                    onClosed(row)
                } else {
                    onClick(row)
                }
            }
            val actionsEnabled = row.realInventoryItemId != null && !row.isArchived
            binding.editAction.alpha = if (actionsEnabled) 1f else 0.35f
            binding.deleteAction.alpha = if (row.realInventoryItemId == null) 0.35f else 1f
            binding.editAction.setOnClickListener { if (actionsEnabled) onEdit(row) }
            binding.deleteAction.setOnClickListener { if (row.realInventoryItemId != null) onDelete(row) }

            setupSwipeTouch(row, onOpened, onClosed)
        }

        private fun setSwipeOffset(offset: Float) {
            val clampedOffset = offset.coerceIn(-revealWidth(), 0f)
            binding.swipeActionContainer.isVisible = clampedOffset < 0f
            binding.inventoryItemCard.translationX = clampedOffset
            isRevealed = clampedOffset < 0f
        }

        private fun shouldRevealAfterSwipe(): Boolean =
            kotlin.math.abs(binding.inventoryItemCard.translationX) > revealWidth() * 0.25f

        fun revealActions(animate: Boolean = true) {
            isRevealed = true
            binding.swipeActionContainer.isVisible = true
            moveForegroundTo(-revealWidth(), animate)
        }

        fun closeActions(animate: Boolean = true) {
            isRevealed = false
            moveForegroundTo(0f, animate)
        }

        private fun moveForegroundTo(targetTranslation: Float, animate: Boolean) {
            binding.inventoryItemCard.animate().cancel()
            if (animate) {
                binding.inventoryItemCard.animate()
                    .translationX(targetTranslation)
                    .setDuration(160L)
                    .withEndAction {
                        binding.swipeActionContainer.isVisible = targetTranslation < 0f
                    }
                    .start()
            } else {
                binding.inventoryItemCard.translationX = targetTranslation
                binding.swipeActionContainer.isVisible = targetTranslation < 0f
            }
        }

        private fun revealWidth(): Float {
            val actionWidth = binding.root.resources.getDimensionPixelSize(R.dimen.inventory_swipe_action_width)
            return actionWidth * 2f
        }

        private fun setupSwipeTouch(
            row: InventoryDisplayRow,
            onOpened: (InventoryDisplayRow) -> Unit,
            onClosed: (InventoryDisplayRow) -> Unit
        ) {
            val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
            binding.inventoryItemCard.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startTranslationX = binding.inventoryItemCard.translationX
                        isDraggingSwipe = false
                        binding.inventoryItemCard.animate().cancel()
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!isDraggingSwipe && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            isDraggingSwipe = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (isDraggingSwipe) {
                            // Own the reveal translation here instead of ItemTouchHelper, because
                            // ItemTouchHelper always settles the row after release in this layout.
                            setSwipeOffset(startTranslationX + dx)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingSwipe) {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (shouldRevealAfterSwipe()) {
                                revealActions()
                                onOpened(row)
                            } else {
                                closeActions()
                                onClosed(row)
                            }
                            isDraggingSwipe = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        private fun quantityColor(row: InventoryDisplayRow): Int = when {
            row.quantity <= 0.0 -> R.color.inventory_danger
            row.quantity <= StockLevelRules.effectiveReorderPoint(row.representativeItem) -> R.color.inventory_warning
            else -> R.color.inventory_primary
        }

        private fun bindExpiry(row: InventoryDisplayRow) {
            val lot = ExpiryLotRules.nearestUrgent(row.expiryLots)
            binding.expiryTextView.isVisible = lot != null
            if (lot == null) return

            val expiryDate = lot.expiryDate ?: return
            val expiryLocalDate = Instant.ofEpochMilli(expiryDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val today = LocalDate.now()
            binding.expiryTextView.text = when {
                expiryLocalDate.isBefore(today) ->
                    "Expired ${DateUtils.formatDisplayDate(expiryDate)} (${lot.quantity.toStorageQuantityText()})"
                expiryLocalDate == today ->
                    "Expires today (${lot.quantity.toStorageQuantityText()})"
                else ->
                    "Expires ${DateUtils.formatDisplayDate(expiryDate)} (${lot.quantity.toStorageQuantityText()})"
            }
            val color = if (expiryLocalDate.isBefore(today)) {
                R.color.inventory_danger
            } else {
                R.color.inventory_warning
            }
            binding.expiryTextView.setTextColor(ContextCompat.getColor(binding.root.context, color))
        }

        private fun InventoryDisplayRow.supportingText(): String = listOf(
            category.ifBlank { "Uncategorised" },
            brand.ifBlank { "No brand" },
            "Safety stock: ${StockLevelRules.effectiveReorderPoint(representativeItem).toStorageQuantityText()}"
        ).joinToString(" | ")
    }

    object InventoryItemDiffCallback : DiffUtil.ItemCallback<InventoryDisplayRow>() {
        override fun areItemsTheSame(oldItem: InventoryDisplayRow, newItem: InventoryDisplayRow): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: InventoryDisplayRow, newItem: InventoryDisplayRow): Boolean = oldItem == newItem
    }
}
