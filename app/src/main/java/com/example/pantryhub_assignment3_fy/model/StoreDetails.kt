package com.example.pantryhub_assignment3_fy.model

data class StoreDetails(
    val store: Store = Store(),
    val currentUser: UserProfile = UserProfile(),
    val currentStaff: StaffMember = StaffMember(),
    val staff: List<StaffMember> = emptyList()
)
