package com.example.infirmary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One daily status per medication: medicationId plus date forms the composite primary key.
 * Patient/medication deletion cascades to this row; it does not distinguish multiple doses in one day.
 */
@Entity(
    tableName = "medication_taken",
    primaryKeys = ["medicationId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("medicationId")]
)
data class MedicationTakenEntity(
    val medicationId: Long,
    val patientId: String,
    val date: String,
    val taken: Boolean
)
