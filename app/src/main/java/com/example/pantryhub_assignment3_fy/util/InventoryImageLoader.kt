package com.example.pantryhub_assignment3_fy.util

import android.net.Uri
import android.widget.ImageView
import com.example.pantryhub_assignment3_fy.R

// Loads a saved inventoryItem image URI into an ImageView and falls back to the provided icon when no photo exists.
fun ImageView.loadInventoryImage(imageUrl: String, fallbackDrawableRes: Int = R.drawable.ic_leaf) {
    if (imageUrl.isBlank()) {
        setImageResource(fallbackDrawableRes)
        val padding = resources.getDimensionPixelSize(R.dimen.space_md)
        setPadding(padding, padding, padding, padding)
        return
    }

    setPadding(0, 0, 0, 0)
    setImageURI(Uri.parse(imageUrl))
}
