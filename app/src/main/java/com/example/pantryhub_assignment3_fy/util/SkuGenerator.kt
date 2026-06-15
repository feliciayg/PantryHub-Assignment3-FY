package com.example.pantryhub_assignment3_fy.util

import com.example.pantryhub_assignment3_fy.model.InventoryItem
import java.util.Locale
import kotlin.random.Random

object SkuGenerator {
    private const val RANDOM_LENGTH = 8
    private const val RANDOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateRandomSku(existingSkus: Collection<String>): String {
        val used = existingSkus.map { it.trim().uppercase(Locale.US) }.toSet()
        repeat(100) {
            val suffix = buildString(RANDOM_LENGTH) {
                repeat(RANDOM_LENGTH) {
                    append(RANDOM_ALPHABET[Random.nextInt(RANDOM_ALPHABET.length)])
                }
            }
            val candidate = "SKU-$suffix"
            if (candidate !in used) return candidate
        }
        error("Could not generate a unique SKU. Please enter one manually.")
    }

    fun generateUniqueSku(input: InventoryItem, existingSkus: Collection<String>): String {
        val nameWords = input.name.alphanumericWords()
        val hasSpecificCategory = input.category.isNotBlank() && !input.category.equals("Other", ignoreCase = true)
        val category = if (hasSpecificCategory) {
            input.category.toSkuComponent(fallback = "GEN")
        } else {
            nameWords.firstOrNull()?.toSkuComponent(fallback = "GEN") ?: "GEN"
        }
        val brandOrName = when {
            input.brand.isNotBlank() -> input.brand.toSkuComponent(fallback = "ITEM")
            !hasSpecificCategory && nameWords.size > 1 -> nameWords[1].toSkuComponent(fallback = "ITEM")
            else -> input.name.toSkuComponent(fallback = "ITEM")
        }
        val prefix = "$category-$brandOrName"
        val used = existingSkus.filter { it.isNotBlank() }.map { it.trim().uppercase(Locale.US) }.toSet()
        var sequence = 1

        // Sequence generation checks the complete current-store SKU set so generated identifiers
        // remain readable while avoiding collisions with existing manual or generated SKUs.
        while (true) {
            val candidate = "$prefix-${sequence.toString().padStart(4, '0')}"
            if (candidate !in used) return candidate
            sequence++
        }
    }

    private fun String.toSkuComponent(fallback: String): String {
        // SKU components use product details first, then remove punctuation/spaces for consistency.
        return uppercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .take(3)
            .ifBlank { fallback }
    }

    private fun String.alphanumericWords(): List<String> {
        return trim()
            .split(Regex("\\s+"))
            .map { word -> word.filter { it.isLetterOrDigit() } }
            .filter { it.isNotBlank() }
    }
}
