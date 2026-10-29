package com.example.infirmary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room access for persisted personalised tips and the MedCoach history dialog. */
@Dao
interface MedCoachTipDao {
    /** Observes newest tips first; descending ID breaks ties within the same timestamp minute. */
    @Query("SELECT * FROM medcoach_tips WHERE patientId = :patientId ORDER BY timestamp DESC, id DESC")
    fun observeTipsForPatient(patientId: String): Flow<List<MedCoachTipEntity>>

    /** Loads a bounded recent-tip snapshot to discourage repeated wording in the next prompt. */
    @Query("SELECT * FROM medcoach_tips WHERE patientId = :patientId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentTipsForPatient(patientId: String, limit: Int): List<MedCoachTipEntity>

    /** Saves one generated tip; Room assigns its ID and invalidates observing history queries. */
    @Insert
    suspend fun insertTip(tip: MedCoachTipEntity)
}
