package com.example.infirmary.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room access for one Boolean taken status per medication and calendar date. */
@Dao
interface MedicationTakenDao {
    /** Observes one patient/date combination; date remains fixed for this Flow instance. */
    @Query("SELECT * FROM medication_taken WHERE patientId = :patientId AND date = :date")
    fun observeTakenForPatientOnDate(patientId: String, date: String): Flow<List<MedicationTakenEntity>>

    /** Inserts or updates the row identified by the composite medicationId/date primary key. */
    @Upsert
    suspend fun upsertTakenStatus(status: MedicationTakenEntity)
}
