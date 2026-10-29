package com.example.infirmary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room queries for patient symptom history, chart order, and clinician aggregates. */
@Dao
interface SymptomDao {
    /** Observes history newest first; yyyy-MM-dd HH:mm text sorts in chronological order. */
    @Query("SELECT * FROM symptoms WHERE patientId = :patientId ORDER BY dateTime DESC")
    fun observeSymptomsForPatient(patientId: String): Flow<List<SymptomEntity>>

    /** Loads at most limit recent symptom rows for the tip prompt. */
    @Query("SELECT * FROM symptoms WHERE patientId = :patientId ORDER BY dateTime DESC LIMIT :limit")
    suspend fun getRecentSymptomsForPatient(patientId: String, limit: Int): List<SymptomEntity>

    /** Observes history oldest first so chart points follow record chronology. */
    @Query("SELECT * FROM symptoms WHERE patientId = :patientId ORDER BY dateTime ASC")
    fun observeSymptomsForTrend(patientId: String): Flow<List<SymptomEntity>>

    /** Returns the most frequent category across all patients; ties sort alphabetically. */
    @Query("SELECT category FROM symptoms GROUP BY category ORDER BY COUNT(*) DESC, category ASC LIMIT 1")
    suspend fun getMostCommonSymptomCategory(): String?

    /** Calculates the all-patient severity average; SQL AVG is null for an empty table. */
    @Query("SELECT AVG(severity) FROM symptoms")
    suspend fun getAverageSymptomSeverity(): Double?

    /** Bulk inserts seeded symptoms, replacing records that conflict on their primary key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptoms(symptoms: List<SymptomEntity>)

    /** Inserts one form-created symptom with an automatically generated ID. */
    @Insert
    suspend fun insertSymptom(symptom: SymptomEntity)

    /** Clears existing symptom rows as part of the initial seed transaction. */
    @Query("DELETE FROM symptoms")
    suspend fun deleteAllSymptoms()
}
