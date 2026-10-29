package com.example.infirmary.data

/** Read-only aggregate snapshot for the dashboard and AI prompt; this is not a Room entity. */
data class ClinicianStats(
    val totalPatients: Int,
    val averageMedicationsPerPatient: Double,
    val mostCommonSymptomCategory: String,
    val averageSymptomSeverity: Double
)
