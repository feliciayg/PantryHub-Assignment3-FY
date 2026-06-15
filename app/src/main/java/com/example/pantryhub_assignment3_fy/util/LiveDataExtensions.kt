package com.example.pantryhub_assignment3_fy.util

import androidx.lifecycle.MutableLiveData

inline fun <T> MutableLiveData<T>.update(transform: (T) -> T) {
    // Keeps LiveData state updates as a single atomic-looking copy operation inside ViewModels.
    val current = value ?: error("MutableLiveData must be initialized before update.")
    value = transform(current)
}
