package com.example.pantryhub_assignment3_fy.util

import android.util.Log
import com.example.pantryhub_assignment3_fy.BuildConfig

/**
 * Small shared Logcat helper so the app uses one readable pattern everywhere.
 *
 * Pattern:
 * `InventoryHub:<Area>` as the tag
 * `[event] message | key=value, key=value`
 */
object AppLogger {
    fun debug(area: String, event: String, message: String, vararg metadata: Pair<String, Any?>) {
        if (BuildConfig.DEBUG) {
            Log.d(tag(area), format(event, message, metadata))
        }
    }

    fun info(area: String, event: String, message: String, vararg metadata: Pair<String, Any?>) {
        if (BuildConfig.DEBUG) {
            Log.i(tag(area), format(event, message, metadata))
        }
    }

    fun warn(area: String, event: String, message: String, vararg metadata: Pair<String, Any?>) {
        Log.w(tag(area), format(event, message, metadata))
    }

    fun error(
        area: String,
        event: String,
        message: String,
        throwable: Throwable? = null,
        vararg metadata: Pair<String, Any?>
    ) {
        Log.e(tag(area), format(event, message, metadata), throwable)
    }

    private fun tag(area: String): String = "InventoryHub:${area.trim()}"

    private fun format(event: String, message: String, metadata: Array<out Pair<String, Any?>>): String {
        val metaText = metadata
            .filter { it.first.isNotBlank() }
            .joinToString(", ") { (key, value) -> "$key=${value ?: "null"}" }
        return buildString {
            append("[")
            append(event.trim())
            append("] ")
            append(message.trim())
            if (metaText.isNotBlank()) {
                append(" | ")
                append(metaText)
            }
        }
    }
}
