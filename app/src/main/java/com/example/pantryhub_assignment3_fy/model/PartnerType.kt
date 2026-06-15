package com.example.pantryhub_assignment3_fy.model

enum class PartnerType(val value: String) {
    SUPPLIER("supplier"),
    CUSTOMER("customer");

    companion object {
        fun fromValue(value: String?): PartnerType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: SUPPLIER
    }
}
