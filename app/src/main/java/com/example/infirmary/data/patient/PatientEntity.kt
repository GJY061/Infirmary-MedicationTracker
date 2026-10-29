package com.example.infirmary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row in patients, identified by the externally displayed patientId.
 * An empty password marks an unclaimed seed account; this prototype stores passwords as plain text.
 */
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val patientId: String,
    val phoneNumber: String,
    val name: String,
    val password: String = ""
)
