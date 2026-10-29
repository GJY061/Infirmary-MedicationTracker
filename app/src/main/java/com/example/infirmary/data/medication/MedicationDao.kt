package com.example.infirmary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room operations for medications; Flow reads are observable and suspend reads are snapshots. */
@Dao
interface MedicationDao {
    /** Observes one patient's medications, sorted by fixed-width HH:mm time then name. */
    @Query("SELECT * FROM medications WHERE patientId = :patientId ORDER BY scheduledTime, name")
    fun observeMedicationsForPatient(patientId: String): Flow<List<MedicationEntity>>

    /** Loads a one-time medication list for the personalised-tip prompt. */
    @Query("SELECT * FROM medications WHERE patientId = :patientId ORDER BY scheduledTime, name")
    suspend fun getMedicationsForPatient(patientId: String): List<MedicationEntity>

    /** Counts all medication rows for the clinician average calculation. */
    @Query("SELECT COUNT(*) FROM medications")
    suspend fun countMedications(): Int

    /** Bulk inserts seed/import rows, replacing records on primary-key conflicts. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<MedicationEntity>)

    /** Inserts a form-created medication; Room generates its numeric primary key. */
    @Insert
    suspend fun insertMedication(medication: MedicationEntity)

    /** Removes medications during seeding; related taken-status rows cascade on deletion. */
    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()
}
