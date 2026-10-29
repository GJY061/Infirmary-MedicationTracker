package com.example.infirmary.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.infirmary.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Data layer shared by the ViewModels: coordinates Room, preferences, CSV assets, and HTTP APIs.
 * Screens receive results or observe Flows instead of knowing how data is stored or fetched.
 */
class MedTrackRepository private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val patientDao: PatientDao,
    private val medicationDao: MedicationDao,
    private val symptomDao: SymptomDao,
    private val medCoachTipDao: MedCoachTipDao,
    private val medicationTakenDao: MedicationTakenDao,
    private val openFdaApi: OpenFdaApi,
    private val geminiApi: GeminiApi,
    private val geminiApiKey: String
) {
    // Small flags and the session ID live in preferences; structured patient data lives in Room.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Initialises Room from CSV and legacy preferences once, on the IO dispatcher.
     * The database writes form one transaction; the seeded preference is recorded after it succeeds.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (prefs.getBoolean(KEY_DB_SEEDED, false)) return@withContext

        // Seeded accounts start unclaimed, regardless of any password column in the CSV.
        val patients = readPatientsFromCsv().map { it.copy(password = "") }
        val medications = readMedicationsFromCsv()
        val symptoms = readSymptomsFromCsv()
        val migratedUsers = readLegacyUsers()
        val migratedMedications = readLegacyMedications()

        // Clear children before parents, then insert parents before child rows to satisfy foreign keys.
        // If a write fails, Room rolls back the database changes together.
        database.withTransaction {
            symptomDao.deleteAllSymptoms()
            medicationDao.deleteAllMedications()
            patientDao.deleteAllPatients()

            patientDao.insertPatients(patients)
            migratedUsers.forEach { patientDao.upsertPatient(it) }
            medicationDao.insertMedications(medications + migratedMedications)
            symptomDao.insertSymptoms(symptoms)
        }

        // Mark seeding complete only after the database transaction has committed.
        prefs.edit { putBoolean(KEY_DB_SEEDED, true) }
    }

    /** Reads the saved local session; a missing or blank patient ID means logged out. */
    fun getLoggedInPatientId(): String? {
        return prefs.getString(KEY_LOGGED_IN_PATIENT_ID, null)?.takeIf { it.isNotBlank() }
    }

    /** Persists the patient ID so the next app launch can restore the local session. */
    fun saveLoggedInPatientId(patientId: String) {
        prefs.edit().putString(KEY_LOGGED_IN_PATIENT_ID, patientId).apply()
    }

    /** Removes the saved session without deleting the patient or their health records. */
    fun clearLoggedInPatientId() {
        prefs.edit().remove(KEY_LOGGED_IN_PATIENT_ID).apply()
    }

    /** Streams the selected patient record, including later Room updates or deletion. */
    fun observePatient(patientId: String): Flow<PatientEntity?> = patientDao.observePatient(patientId)

    /** Streams a patient's medications in scheduled-time/name order. */
    fun observeMedications(patientId: String): Flow<List<MedicationEntity>> =
        medicationDao.observeMedicationsForPatient(patientId)

    /** Streams a patient's symptom history with the newest records first. */
    fun observeSymptoms(patientId: String): Flow<List<SymptomEntity>> =
        symptomDao.observeSymptomsForPatient(patientId)

    /** Streams saved tips so the history dialog updates after a successful insertion. */
    fun observeMedCoachTips(patientId: String): Flow<List<MedCoachTipEntity>> =
        medCoachTipDao.observeTipsForPatient(patientId)

    /**
     * Observes statuses for the date when this function is called.
     * The query does not automatically switch to a new date at midnight.
     */
    fun observeTodayTakenStatuses(patientId: String): Flow<List<MedicationTakenEntity>> =
        medicationTakenDao.observeTakenForPatientOnDate(patientId, LocalDate.now().toString())

    /** Streams symptoms oldest first, matching the chart's left-to-right record order. */
    fun observeSymptomsForTrend(patientId: String): Flow<List<SymptomEntity>> =
        symptomDao.observeSymptomsForTrend(patientId)

    /**
     * Checks the local Room account and saves its ID on success.
     * A blank stored password identifies a seeded account that must be claimed first.
     */
    suspend fun login(patientId: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val patient = patientDao.getPatient(patientId.trim())
            ?: return@withContext AuthResult.Error("No account found with that PatientID")
        if (patient.password.isBlank()) {
            return@withContext AuthResult.Error("This account has not been claimed yet")
        }
        // This prototype compares locally stored plain-text passwords; it is not server authentication.
        if (patient.password != password.trim()) {
            return@withContext AuthResult.Error("Incorrect password")
        }
        saveLoggedInPatientId(patient.patientId)
        AuthResult.Success(patient.patientId)
    }

    /** Matches a seeded patient ID and phone number, assigns a password, then starts a session. */
    suspend fun claimAccount(
        patientId: String,
        phoneNumber: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val patient = patientDao.getPatient(patientId.trim())
            ?: return@withContext AuthResult.Error("No seeded account found with that PatientID")
        if (patient.phoneNumber != phoneNumber.trim()) {
            return@withContext AuthResult.Error("Phone number does not match this PatientID")
        }
        if (patient.password.isNotBlank()) {
            return@withContext AuthResult.Error("This account has already been claimed")
        }
        patientDao.updatePassword(patient.patientId, password.trim())
        saveLoggedInPatientId(patient.patientId)
        AuthResult.Success(patient.patientId)
    }

    /** Rejects a duplicate phone number, creates a local patient ID, and saves the new session. */
    suspend fun signUp(name: String, phoneNumber: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val normalizedPhone = phoneNumber.trim()
            if (patientDao.getPatientByPhone(normalizedPhone) != null) {
                return@withContext AuthResult.Error("Phone number already registered")
            }
            val patientId = generateNextPatientId()
            patientDao.upsertPatient(
                PatientEntity(
                    patientId = patientId,
                    phoneNumber = normalizedPhone,
                    name = name.trim(),
                    password = password.trim()
                )
            )
            saveLoggedInPatientId(patientId)
            AuthResult.Success(patientId)
        }

    /**
     * Trims form values and inserts a medication linked to the selected patient.
     * Validation belongs to MedicationViewModel; Room Flows notify observers after insertion.
     */
    suspend fun addMedication(
        patientId: String,
        name: String,
        dosage: String,
        frequency: String,
        scheduledTime: String,
        type: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        medicationDao.insertMedication(
            MedicationEntity(
                patientId = patientId,
                name = name.trim(),
                dosage = dosage.trim(),
                frequency = frequency.trim(),
                scheduledTime = scheduledTime.trim(),
                type = type.trim(),
                notes = notes.trim()
            )
        )
    }

    /** Inserts a patient's symptom with the timestamp supplied by SymptomViewModel. */
    suspend fun addSymptom(
        patientId: String,
        category: String,
        severity: Int,
        notes: String,
        dateTime: String
    ) = withContext(Dispatchers.IO) {
        symptomDao.insertSymptom(
            SymptomEntity(
                patientId = patientId,
                category = category.trim(),
                severity = severity,
                notes = notes.trim(),
                dateTime = dateTime.trim()
            )
        )
    }

    /** Inserts or updates today's status using the medication ID and date as the row key. */
    suspend fun setMedicationTaken(patientId: String, medicationId: Long, taken: Boolean) =
        withContext(Dispatchers.IO) {
            medicationTakenDao.upsertTakenStatus(
                MedicationTakenEntity(
                    medicationId = medicationId,
                    patientId = patientId,
                    date = LocalDate.now().toString(),
                    taken = taken
                )
            )
        }

    /**
     * Looks up an exact quoted brand name, falling back to a generic-name search.
     * Maps the first label to display text and converts request failures into DrugLookupResult.
     */
    suspend fun lookupDrugInfo(medicationName: String): DrugLookupResult = withContext(Dispatchers.IO) {
        val normalized = medicationName.trim()
        if (normalized.isBlank()) return@withContext DrugLookupResult.Error("Enter a medication name first")

        val escaped = normalized.replace("\"", "\\\"")
        return@withContext try {
            // The generic lookup runs only if the brand search has no results, avoiding a second request otherwise.
            val brandResult = searchDrugLabelOrEmpty("openfda.brand_name:\"$escaped\"")
            val genericResult = if (brandResult.isEmpty()) {
                searchDrugLabelOrEmpty("openfda.generic_name:\"$escaped\"")
            } else {
                emptyList()
            }

            val firstLabel = (brandResult + genericResult).firstOrNull()
                ?: return@withContext DrugLookupResult.Error("No drug information found for \"$normalized\"")

            DrugLookupResult.Success(
                DrugInfo(
                    purpose = firstOrNotAvailable(firstLabel.purpose),
                    warnings = firstOrNotAvailable(firstLabel.warnings),
                    dosageAndAdministration = firstOrNotAvailable(firstLabel.dosage_and_administration)
                )
            )
        } catch (e: IOException) {
            Log.e("MedTrackApi", "API request failed", e)
            DrugLookupResult.Error("Network unavailable. Check your connection and try again.")
        } catch (httpException: HttpException) {
            if (httpException.code() == 404) {
                DrugLookupResult.Error("No drug information found for \"$normalized\"")
            } else {
                DrugLookupResult.Error("Could not load drug information right now.")
            }
        } catch (_: Exception) {
            DrugLookupResult.Error("Could not load drug information right now.")
        }
    }

    /**
     * Loads patient context, asks Gemini for a tip, and saves usable text to Room.
     * Returns an explicit success/error result for the ViewModel to display.
     */
    suspend fun generatePersonalizedTip(patientId: String): TipGenerationResult = withContext(Dispatchers.IO) {
        val normalizedPatientId = patientId.trim()
        if (normalizedPatientId.isBlank()) {
            return@withContext TipGenerationResult.Error("No logged-in patient found.")
        }
        if (geminiApiKey.isBlank()) {
            return@withContext TipGenerationResult.Error(
                "Gemini API key is missing. Add GEMINI_API_KEY to local.properties."
            )
        }

        // Take database snapshots before the network request; recent history limits the prompt size.
        val patient = patientDao.getPatient(normalizedPatientId)
            ?: return@withContext TipGenerationResult.Error("Patient profile was not found.")
        val medications = medicationDao.getMedicationsForPatient(normalizedPatientId)
        val symptoms = symptomDao.getRecentSymptomsForPatient(normalizedPatientId, RECENT_SYMPTOM_LIMIT)
        val recentTips = medCoachTipDao.getRecentTipsForPatient(normalizedPatientId, RECENT_TIP_LIMIT)
        val prompt = buildPersonalizedTipPrompt(patient, medications, symptoms, recentTips)

        return@withContext try {
            val response = geminiApi.generateContent(
                model = GEMINI_MODEL,
                apiKey = geminiApiKey,
                request = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = prompt))
                        )
                    )
                )
            )
            val tipText = response.firstTextOrNull()
                ?: return@withContext TipGenerationResult.Error(
                    "The AI service returned an empty response. Please try again."
                )

            val cleanedTip = tipText.trim()
            if (cleanedTip.isBlank()) {
                return@withContext TipGenerationResult.Error(
                    "The AI service returned an empty response. Please try again."
                )
            }
            val personalizedTip = ensureTipMentionsPatientContext(
                rawTip = cleanedTip,
                medications = medications,
                recentSymptoms = symptoms
            )

            // Persist the tip before reporting success so history and the latest-tip result agree.
            medCoachTipDao.insertTip(
                MedCoachTipEntity(
                    patientId = normalizedPatientId,
                    tipText = personalizedTip,
                    timestamp = LocalDateTime.now().format(TIP_TIMESTAMP_FORMATTER)
                )
            )
            TipGenerationResult.Success(personalizedTip)
        } catch (_: IOException) {
            TipGenerationResult.Error("Network unavailable. Check your connection and try again.")
        // Authentication/request errors get a configuration message; other HTTP statuses share a generic error.
        } catch (httpException: HttpException) {
            TipGenerationResult.Error(
                if (httpException.code() == 400 || httpException.code() == 401 || httpException.code() == 403) {
                    "Gemini request failed. Check your API key configuration."
                } else {
                    "Could not generate a tip right now."
                }
            )
        // The current broad fallback also catches coroutine cancellation exceptions.
        } catch (_: Exception) {
            TipGenerationResult.Error("Could not generate a tip right now.")
        }
    }

    /**
     * Loads a one-time aggregate snapshot across all patients, medications, and symptoms.
     * Empty data uses zero/default values and avoids division by zero.
     */
    suspend fun getClinicianStats(): ClinicianStats = withContext(Dispatchers.IO) {
        val patientCount = patientDao.countPatients()
        val medicationCount = medicationDao.countMedications()
        ClinicianStats(
            totalPatients = patientCount,
            averageMedicationsPerPatient = if (patientCount == 0) 0.0 else medicationCount.toDouble() / patientCount,
            mostCommonSymptomCategory = symptomDao.getMostCommonSymptomCategory() ?: "Not available",
            averageSymptomSeverity = symptomDao.getAverageSymptomSeverity() ?: 0.0
        )
    }

    /**
     * Asks Gemini for observations using aggregate statistics, then parses up to three items.
     * These insights are returned to the dashboard; they are not saved to Room.
     */
    suspend fun generateClinicianInsights(stats: ClinicianStats): ClinicianInsightsResult =
        withContext(Dispatchers.IO) {
            if (geminiApiKey.isBlank()) {
                return@withContext ClinicianInsightsResult.Error(
                    "Gemini API key is missing. Add GEMINI_API_KEY to local.properties."
                )
            }

            val prompt = buildClinicianInsightsPrompt(stats)
            return@withContext try {
                val response = geminiApi.generateContent(
                    model = GEMINI_MODEL,
                    apiKey = geminiApiKey,
                    request = GeminiGenerateContentRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(GeminiPart(text = prompt))
                            )
                        )
                    )
                )
                val text = response.firstTextOrNull()
                    ?: return@withContext ClinicianInsightsResult.Error(
                        "The AI service returned an empty response. Please try again."
                    )
                val insights = parseInsightList(text)
                if (insights.isEmpty()) {
                    ClinicianInsightsResult.Error("The AI service did not return usable insights.")
                } else {
                    ClinicianInsightsResult.Success(insights)
                }
            } catch (_: IOException) {
                ClinicianInsightsResult.Error("Network unavailable. Check your connection and try again.")
            } catch (httpException: HttpException) {
                ClinicianInsightsResult.Error(
                    if (httpException.code() == 400 || httpException.code() == 401 || httpException.code() == 403) {
                        "Gemini request failed. Check your API key configuration."
                    } else {
                        "Could not generate insights right now."
                    }
                )
            } catch (_: Exception) {
                ClinicianInsightsResult.Error("Could not generate insights right now.")
            }
        }

    /** Treats openFDA HTTP 404 as no matches; other HTTP errors reach the caller. */
    private suspend fun searchDrugLabelOrEmpty(search: String): List<OpenFdaLabelResult> {
        return try {
            openFdaApi.searchDrugLabel(search).results.orEmpty()
        } catch (httpException: HttpException) {
            if (httpException.code() == 404) {
                emptyList()
            } else {
                throw httpException
            }
        }
    }

    /**
     * Finds the largest numeric P-prefixed ID and increments it, starting at P1001 if none exist.
     * This read-then-write scheme assumes account creation requests do not run concurrently.
     */
    private suspend fun generateNextPatientId(): String {
        val maxId = patientDao.getAllPatientIds()
            .mapNotNull { it.removePrefix("P").toIntOrNull() }
            .maxOrNull() ?: 1000
        return "P${maxId + 1}"
    }

    /**
     * Formats medications, recent symptoms, and previous tips into one Gemini prompt.
     * The request includes the patient name and ID; wording/length instructions are prompts, not validation.
     */
    private fun buildPersonalizedTipPrompt(
        patient: PatientEntity,
        medications: List<MedicationEntity>,
        recentSymptoms: List<SymptomEntity>,
        recentTips: List<MedCoachTipEntity>
    ): String {
        val medicationSummary = if (medications.isEmpty()) {
            "- No medications recorded yet."
        } else {
            medications.joinToString(separator = "\n") {
                "- ${it.name} (${it.dosage}), ${it.frequency}, scheduled ${it.scheduledTime}"
            }
        }
        val symptomSummary = if (recentSymptoms.isEmpty()) {
            "- No recent symptoms recorded."
        } else {
            recentSymptoms.joinToString(separator = "\n") {
                "- ${it.category}, severity ${it.severity}/10, noted ${it.dateTime}"
            }
        }
        val recentTipSummary = if (recentTips.isEmpty()) {
            "- No previous tips."
        } else {
            recentTips.joinToString(separator = "\n") { "- ${it.tipText}" }
        }
        return """
            Generate one short, encouraging medication-adherence tip for this patient.
            Keep it supportive, specific, and practical, not generic.
            Use 2-3 sentences and less than 80 words total.
            Do not give diagnosis or emergency advice.
            If medications exist, mention at least one medication name from the list.
            If recent symptoms exist, mention one relevant symptom category.
            Avoid repeating wording from previous tips.
            
            Patient name: ${patient.name}
            Patient ID: ${patient.patientId}
            
            Current medications:
            $medicationSummary
            
            Recent symptoms:
            $symptomSummary

            Previous tips:
            $recentTipSummary
            
            Return only the tip text.
        """.trimIndent()
    }

    /**
     * Normalises whitespace and appends a medication/symptom reminder when names are missing.
     * This checks text mentions only; it does not validate the generated advice.
     */
    private fun ensureTipMentionsPatientContext(
        rawTip: String,
        medications: List<MedicationEntity>,
        recentSymptoms: List<SymptomEntity>
    ): String {
        val base = rawTip.trim().replace(Regex("\\s+"), " ")
        val hasMedicationMention = medications.any { med ->
            base.contains(med.name.trim(), ignoreCase = true)
        }
        val hasSymptomMention = recentSymptoms.any { symptom ->
            base.contains(symptom.category.trim(), ignoreCase = true)
        }

        val additions = mutableListOf<String>()
        if (medications.isNotEmpty() && !hasMedicationMention) {
            val med = medications.first()
            additions += "For today, stay consistent with ${med.name} around ${med.scheduledTime}."
        }
        if (recentSymptoms.isNotEmpty() && !hasSymptomMention) {
            val symptom = recentSymptoms.first()
            additions += "Keep tracking ${symptom.category} as you follow your plan."
        }

        if (additions.isEmpty()) return base
        return (listOf(base) + additions).joinToString(" ")
    }

    /** Formats aggregate values into a prompt requesting three concise observations. */
    private fun buildClinicianInsightsPrompt(stats: ClinicianStats): String {
        return """
            You are reviewing aggregate medication-management data for a clinician dashboard.
            Return exactly 3 concise, data-driven observations.
            Keep each observation one sentence.
            Do not invent patient-level details beyond these aggregate stats.
            
            Total patients: ${stats.totalPatients}
            Average medications per patient: ${String.format(Locale.US, "%.1f", stats.averageMedicationsPerPatient)}
            Most common symptom category: ${stats.mostCommonSymptomCategory}
            Average symptom severity: ${String.format(Locale.US, "%.1f", stats.averageSymptomSeverity)} out of 10
            
            Return only a numbered list with 3 items.
        """.trimIndent()
    }

    /** Removes common bullet/number prefixes, discards blank lines, and keeps at most three lines. */
    private fun parseInsightList(text: String): List<String> {
        return text
            .lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(3)
    }

    /** Skips the CSV header, ignores rows missing required columns, and maps patient fields. */
    private fun readPatientsFromCsv(): List<PatientEntity> {
        return readCsv("patients.csv").drop(1).mapNotNull { parts ->
            if (parts.size < 3) return@mapNotNull null
            PatientEntity(
                patientId = parts[0].trim(),
                phoneNumber = parts[1].trim(),
                name = parts[2].trim(),
                password = parts.getOrNull(3)?.trim().orEmpty()
            )
        }
    }

    /** Maps seven CSV columns to patient-linked medications, skipping incomplete rows. */
    private fun readMedicationsFromCsv(): List<MedicationEntity> {
        return readCsv("medications.csv").drop(1).mapNotNull { parts ->
            if (parts.size < 7) return@mapNotNull null
            MedicationEntity(
                patientId = parts[0].trim(),
                name = parts[1].trim(),
                dosage = parts[2].trim(),
                frequency = parts[3].trim(),
                scheduledTime = parts[4].trim(),
                type = parts[5].trim(),
                notes = parts[6].trim()
            )
        }
    }

    /** Maps five CSV columns to symptoms; an unparseable severity currently defaults to zero. */
    private fun readSymptomsFromCsv(): List<SymptomEntity> {
        return readCsv("symptoms.csv").drop(1).mapNotNull { parts ->
            if (parts.size < 5) return@mapNotNull null
            SymptomEntity(
                patientId = parts[0].trim(),
                category = parts[1].trim(),
                severity = parts[2].trim().toIntOrNull() ?: 0,
                notes = parts[3].trim(),
                dateTime = parts[4].trim()
            )
        }
    }

    /**
     * Reads nonblank asset lines and splits their fields; asset/read failures produce an empty list.
     * Parsing is line-based, so a quoted field containing a newline is not supported.
     */
    private fun readCsv(assetName: String): List<List<String>> {
        return runCatching {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.map { splitCsvLine(it.trim()) }.toList()
            }
        }.getOrElse { emptyList() }
    }

    /** Converts the older SharedPreferences JSON user list into Room patient records. */
    private fun readLegacyUsers(): List<PatientEntity> {
        val json = prefs.getString("users", "").orEmpty()
        if (json.isBlank()) return emptyList()
        // TypeToken retains the list element type that JVM generic type erasure would otherwise hide.
        val type = object : TypeToken<List<LegacyPatient>>() {}.type
        return runCatching { Gson().fromJson<List<LegacyPatient>>(json, type) }
            .getOrElse { emptyList() }
            .map {
                PatientEntity(
                    patientId = it.patientId,
                    phoneNumber = it.phoneNumber,
                    name = it.name,
                    password = it.password
                )
            }
    }

    /** Converts the older SharedPreferences JSON medication list into Room entities. */
    private fun readLegacyMedications(): List<MedicationEntity> {
        val json = prefs.getString("medications", "").orEmpty()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<LegacyMedication>>() {}.type
        return runCatching { Gson().fromJson<List<LegacyMedication>>(json, type) }
            .getOrElse { emptyList() }
            .map {
                MedicationEntity(
                    patientId = it.patientId,
                    name = it.name,
                    dosage = it.dosage,
                    frequency = it.frequency,
                    scheduledTime = it.scheduledTime,
                    type = it.type,
                    notes = it.notes
                )
            }
    }

    /** Splits one CSV record while preserving commas inside quotes and decoding doubled quotes. */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char == '"') {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    // Two quotes inside a quoted field represent one literal quote, not the field boundary.
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            // A comma separates fields only when it is outside quoted text.
            } else if (char == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(char)
            }
            index++
        }
        // Flush the final field too, including an empty field after a trailing comma.
        result.add(current.toString())
        return result
    }

    /** JSON shape used only to import accounts saved by the earlier preferences-based version. */
    private data class LegacyPatient(
        val patientId: String,
        val phoneNumber: String,
        val name: String,
        val password: String
    )

    /** JSON shape used only to import medications saved by the earlier version. */
    private data class LegacyMedication(
        val patientId: String,
        val name: String,
        val dosage: String,
        val frequency: String,
        val scheduledTime: String,
        val type: String,
        val notes: String
    )

    companion object {
        private const val PREFS_NAME = "medtrack_prefs"
        private const val KEY_DB_SEEDED = "db_seeded"
        private const val KEY_LOGGED_IN_PATIENT_ID = "logged_in_patient_id"
        // Both tip and clinician requests use this model identifier; provider availability can change.
        private const val GEMINI_MODEL = "gemini-3.5-flash-lite"
        // Bound the context sent with each personalised-tip request.
        private const val RECENT_SYMPTOM_LIMIT = 5
        private const val RECENT_TIP_LIMIT = 3
        private val TIP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /**
         * Builds Retrofit services and injects them with the shared Room database into a repository.
         * Gson maps JSON bodies; BuildConfig supplies the key generated by Gradle.
         */
        fun create(context: Context): MedTrackRepository {
            val db = AppDatabase.getInstance(context)
            val openFdaApi = Retrofit.Builder()
                .baseUrl("https://api.fda.gov/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenFdaApi::class.java)
            val geminiApi = Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiApi::class.java)
            return MedTrackRepository(
                context.applicationContext,
                db,
                db.patientDao(),
                db.medicationDao(),
                db.symptomDao(),
                db.medCoachTipDao(),
                db.medicationTakenDao(),
                openFdaApi,
                geminiApi,
                BuildConfig.GEMINI_API_KEY
            )
        }
    }
}

/** Returns the first list entry if it is nonblank, otherwise the label "Not available". */
private fun firstOrNotAvailable(values: List<String>?): String {
    return values?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "Not available"
}

/** Closed set of authentication outcomes: a patient ID on success or a message on failure. */
sealed class AuthResult {
    data class Success(val patientId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** Separates successfully mapped drug information from a user-facing lookup error. */
sealed class DrugLookupResult {
    data class Success(val info: DrugInfo) : DrugLookupResult()
    data class Error(val message: String) : DrugLookupResult()
}

/** Carries the saved tip text or the reason generation could not finish. */
sealed class TipGenerationResult {
    data class Success(val tipText: String) : TipGenerationResult()
    data class Error(val message: String) : TipGenerationResult()
}

/** Carries parsed dashboard observations or a generation error. */
sealed class ClinicianInsightsResult {
    data class Success(val insights: List<String>) : ClinicianInsightsResult()
    data class Error(val message: String) : ClinicianInsightsResult()
}
