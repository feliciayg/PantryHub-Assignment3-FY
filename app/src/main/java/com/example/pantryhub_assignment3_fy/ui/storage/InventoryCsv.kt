package com.example.pantryhub_assignment3_fy.ui.storage

import com.example.pantryhub_assignment3_fy.model.InventoryItem
import com.example.pantryhub_assignment3_fy.util.DateUtils

object InventoryCsv {
    private val exportHeaders = listOf(
        "id",
        "sku",
        "barcode",
        "name",
        "brand",
        "category",
        "branchId",
        "branchName",
        "quantity",
        "unit",
        "costPrice",
        "sellingPrice",
        "reorderPoint",
        "maximumStockLevel",
        "expiryDate",
        "batchNumber",
        "status",
        "tags",
        "notes"
    )

    fun exportInventoryItems(inventoryItems: List<InventoryItem>): String {
        return buildString {
            appendLine(exportHeaders.joinToString(","))
            inventoryItems.forEach { inventoryItem ->
                appendLine(
                    listOf(
                        inventoryItem.id,
                        inventoryItem.sku,
                        inventoryItem.barcode,
                        inventoryItem.name,
                        inventoryItem.brand,
                        inventoryItem.category,
                        inventoryItem.branchId,
                        inventoryItem.branchName,
                        inventoryItem.quantity.toStorageQuantityText(),
                        inventoryItem.unit,
                        inventoryItem.costPrice.toStorageQuantityText(),
                        inventoryItem.sellingPrice.toStorageQuantityText(),
                        inventoryItem.reorderPoint.toString(),
                        inventoryItem.maximumStockLevel.toString(),
                        inventoryItem.expiryDate.takeIf { it > 0L }?.let(DateUtils::formatInputDate).orEmpty(),
                        inventoryItem.batchNumber,
                        inventoryItem.status,
                        inventoryItem.tags.joinToString(", "),
                        inventoryItem.notes
                    ).joinToString(",") { it.csvEscaped() }
                )
            }
        }
    }

    fun parseRows(csv: String): CsvParseResult {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvParseResult(emptyList(), listOf("CSV file is empty."))

        // Header names are normalized so column order can vary while required names stay stable.
        val headers = parseLine(lines.first()).map { it.trim().lowercase() }
        val headerIndex = headers.withIndex().associate { it.value to it.index }
        val errors = mutableListOf<String>()
        REQUIRED_HEADERS.forEach { header ->
            if (header !in headerIndex) errors += "Missing required column: $header"
        }
        if (errors.isNotEmpty()) return CsvParseResult(emptyList(), errors)

        val rows = lines.drop(1).mapIndexed { index, line ->
            val values = parseLine(line)
            CsvRow(
                rowNumber = index + 2,
                values = headerIndex.mapValues { (_, columnIndex) -> values.getOrNull(columnIndex).orEmpty().trim() }
            )
        }
        return CsvParseResult(rows, emptyList())
    }

    fun parseSalesRows(csv: String): CsvSalesParseResult {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvSalesParseResult(emptyList(), listOf("CSV file is empty."))

        // Sales header mapping accepts itemId, SKU, barcode, or exact name plus quantitySold.
        val headers = parseLine(lines.first()).map { it.trim().lowercase() }
        val headerIndex = headers.withIndex().associate { it.value to it.index }
        val errors = mutableListOf<String>()
        if ("itemid" !in headerIndex && "sku" !in headerIndex && "barcode" !in headerIndex && "name" !in headerIndex) {
            errors += "Missing required column: itemId, sku, barcode, or name"
        }
        if ("quantitysold" !in headerIndex) errors += "Missing required column: quantitySold"
        if (errors.isNotEmpty()) return CsvSalesParseResult(emptyList(), errors)

        val rows = lines.drop(1).mapIndexed { index, line ->
            val values = parseLine(line)
            CsvSalesRow(
                rowNumber = index + 2,
                values = headerIndex.mapValues { (_, columnIndex) -> values.getOrNull(columnIndex).orEmpty().trim() }
            )
        }
        return CsvSalesParseResult(rows, emptyList())
    }

    private fun parseLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun String.csvEscaped(): String {
        val escaped = replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private val REQUIRED_HEADERS = setOf("name", "quantity", "unit")
}

data class CsvParseResult(
    val rows: List<CsvRow>,
    val errors: List<String>
)

data class CsvRow(
    val rowNumber: Int,
    val values: Map<String, String>
) {
    fun value(header: String): String = values[header.lowercase()].orEmpty()
}

data class CsvSalesParseResult(
    val rows: List<CsvSalesRow>,
    val errors: List<String>
)

data class CsvSalesRow(
    val rowNumber: Int,
    val values: Map<String, String>
) {
    fun value(header: String): String = values[header.lowercase()].orEmpty()
}
