package com.example.infirmary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room generates this patient-access interface; parameter names after : are bound query values. */
@Dao
interface PatientDao {
    /** Observes one patient and emits again when Room invalidates the patients table. */
    @Query("SELECT * FROM patients WHERE patientId = :patientId LIMIT 1")
    fun observePatient(patientId: String): Flow<PatientEntity?>

    /** Returns one patient snapshot for authentication/context lookup, or null when absent. */
    @Query("SELECT * FROM patients WHERE patientId = :patientId LIMIT 1")
    suspend fun getPatient(patientId: String): PatientEntity?

    /** Returns a matching phone record for the registration duplicate check. */
    @Query("SELECT * FROM patients WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getPatientByPhone(phoneNumber: String): PatientEntity?

    /** Returns IDs for the repository's next-local-ID calculation. */
    @Query("SELECT patientId FROM patients")
    suspend fun getAllPatientIds(): List<String>

    /** Counts all patient records, including seeded accounts that have not been claimed. */
    @Query("SELECT COUNT(*) FROM patients")
    suspend fun countPatients(): Int

    /** Bulk seed insert; a primary-key conflict uses SQLite REPLACE semantics. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(patients: List<PatientEntity>)

    /**
     * Inserts or replaces a patient; despite the name, this uses REPLACE rather than @Upsert.
     * REPLACE deletes the conflicting row before insertion and can cascade to its child records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatient(patient: PatientEntity)

    /** Updates only the password column so claiming a patient preserves their related records. */
    @Query("UPDATE patients SET password = :password WHERE patientId = :patientId")
    suspend fun updatePassword(patientId: String, password: String)

    /** Clears patient rows during initialisation; foreign-key cascades remove their children. */
    @Query("DELETE FROM patients")
    suspend fun deleteAllPatients()
}
