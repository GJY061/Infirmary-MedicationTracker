package com.example.infirmary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room entry point for five entity tables and their DAOs; schema version is currently 3.
 * Room generates the implementation using KSP during the build.
 */
@Database(
    entities = [
        PatientEntity::class,
        MedicationEntity::class,
        SymptomEntity::class,
        MedCoachTipEntity::class,
        MedicationTakenEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /** Provides generated queries and writes for patient profiles and local credentials. */
    abstract fun patientDao(): PatientDao

    /** Provides generated queries and writes for patient-linked medications. */
    abstract fun medicationDao(): MedicationDao

    /** Provides symptom history queries, inserts, and aggregate calculations. */
    abstract fun symptomDao(): SymptomDao

    /** Provides saved-tip history queries and insertion. */
    abstract fun medCoachTipDao(): MedCoachTipDao

    /** Provides date-specific status observations and insert-or-update writes. */
    abstract fun medicationTakenDao(): MedicationTakenDao

    companion object {
        // Volatile makes the published instance visible across threads; synchronized protects initial creation.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns one shared database instance, synchronising first creation across callers.
         * applicationContext avoids holding an Activity; migrations upgrade installed databases in place.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtrack.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // Version 1 -> 2: add persisted AI tips while keeping existing patient/medication/symptom rows.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            /** Creates the tips table and patient lookup index when upgrading a version-1 database. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medcoach_tips` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `tipText` TEXT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medcoach_tips_patientId` ON `medcoach_tips` (`patientId`)"
                )
            }
        }

        // Version 2 -> 3: add one taken-status row per medication/date, preserving existing tables.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            /** Creates daily taken statuses and foreign-key indexes when upgrading version 2. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medication_taken` (
                        `medicationId` INTEGER NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `taken` INTEGER NOT NULL,
                        PRIMARY KEY(`medicationId`, `date`),
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medication_taken_patientId` ON `medication_taken` (`patientId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medication_taken_medicationId` ON `medication_taken` (`medicationId`)"
                )
            }
        }
    }
}
