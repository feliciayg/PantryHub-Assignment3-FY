package com.example.pantryhub_assignment3_fy.data.firebase

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Provides the shared Firestore instance used by repositories.
 */
class FirestoreDataSource {
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
}
