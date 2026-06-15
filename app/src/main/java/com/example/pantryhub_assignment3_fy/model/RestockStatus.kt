package com.example.pantryhub_assignment3_fy.model

/**
 * Purchase statuses use the new enterprise workflow.
 *
 * Legacy values remain in the enum so older Firestore records can still deserialize safely
 * before repository mapping converts them to the new user-facing flow.
 */
enum class RestockStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    TO_ORDER,
    IN_TRANSIT
}
