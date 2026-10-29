package com.example.infirmary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One patient-linked symptom observation with a severity and sortable local timestamp string. */
@Entity(
    tableName = "symptoms",
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
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val category: String,
    val severity: Int,
    val notes: String,
    val dateTime: String
)
