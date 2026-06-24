package com.example.pantryhub_assignment3_fy.util

import com.example.pantryhub_assignment3_fy.model.ExpiryLot
import com.example.pantryhub_assignment3_fy.model.InventoryItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ExpiryLotRules {
    const val EXPIRING_SOON_DAYS = 7L

    fun sorted(lots: List<ExpiryLot>): List<ExpiryLot> = lots
        .filter { it.quantity > 0.0 }
        .sortedWith(
            compareBy<ExpiryLot> { it.expiryDate == null }
                .thenBy { it.expiryDate ?: Long.MAX_VALUE }
                .thenBy { it.createdAt }
        )

    fun nearestUrgent(lots: List<ExpiryLot>, today: LocalDate = LocalDate.now()): ExpiryLot? {
        return combineByExpiry(lots)
            .filter { lot ->
                val expiry = lot.expiryDate?.toLocalDate() ?: return@filter false
                !expiry.isAfter(today.plusDays(EXPIRING_SOON_DAYS))
            }
            .minByOrNull { it.expiryDate ?: Long.MAX_VALUE }
    }

    fun combineByExpiry(lots: List<ExpiryLot>): List<ExpiryLot> {
        return lots.filter { it.quantity > 0.0 }
            .groupBy { it.expiryDate }
            .map { (expiryDate, matching) ->
                matching.first().copy(
                    id = matching.joinToString("_") { it.id },
                    expiryDate = expiryDate,
                    quantity = matching.sumOf { it.quantity },
                    createdAt = matching.minOfOrNull { it.createdAt } ?: 0L,
                    updatedAt = matching.maxOfOrNull { it.updatedAt } ?: 0L
                )
            }
            .let(::sorted)
    }

    /** Keeps branch-level expiry quantities separate for multi-location detail screens. */
    fun combineByLocationAndExpiry(lots: List<ExpiryLot>): List<ExpiryLot> {
        return lots.filter { it.quantity > 0.0 }
            .groupBy { lot ->
                val locationKey = lot.branchId.ifBlank { lot.branchName.trim().lowercase() }
                locationKey to lot.expiryDate
            }
            .map { entry ->
                val expiryDate = entry.key.second
                val matching = entry.value
                matching.first().copy(
                    id = matching.joinToString("_") { it.id },
                    expiryDate = expiryDate,
                    quantity = matching.sumOf { it.quantity },
                    createdAt = matching.minOfOrNull { it.createdAt } ?: 0L,
                    updatedAt = matching.maxOfOrNull { it.updatedAt } ?: 0L
                )
            }
            .let(::sorted)
    }

    fun expandForExpiryUi(items: List<InventoryItem>): List<InventoryItem> {
        return items.flatMap { item ->
            effectiveLots(item)
                .filter { it.expiryDate != null }
                .map { lot ->
                    item.copy(
                        quantity = lot.quantity,
                        expiryDate = lot.expiryDate ?: 0L,
                        expiryLots = listOf(lot)
                    )
                }
        }
    }

    /**
     * Normalizes expiry lots for UI summaries so lot quantities never exceed actual on-hand stock.
     * This keeps Home and expiry views aligned with the item detail breakdown logic.
     */
    fun effectiveLots(item: InventoryItem): List<ExpiryLot> {
        val realLots = item.expiryLots.filter { it.quantity > 0.0 }
        val lotQuantity = realLots.sumOf { it.quantity }.coerceAtMost(item.quantity)
        val remainingLegacyQuantity = (item.quantity - lotQuantity).coerceAtLeast(0.0)
        val compatibleLots = buildList {
            addAll(realLots)
            if (remainingLegacyQuantity > 0.0) {
                add(
                    ExpiryLot(
                        inventoryItemId = item.id,
                        branchId = item.branchId,
                        branchName = item.branchName,
                        expiryDate = item.expiryDate.takeIf { it > 0L },
                        quantity = remainingLegacyQuantity,
                        createdAt = item.createdAt
                    )
                )
            }
        }
        return combineByExpiry(compatibleLots)
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
