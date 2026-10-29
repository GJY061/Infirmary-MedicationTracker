package com.example.infirmary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One patient-linked medication with an auto-generated ID.
 * The patient index supports filtering; CASCADE removes it when its patient is deleted.
 */
@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val name: String,
    val dosage: String,
    val frequency: String,
    val scheduledTime: String,
    val type: String,
    val notes: String
)
