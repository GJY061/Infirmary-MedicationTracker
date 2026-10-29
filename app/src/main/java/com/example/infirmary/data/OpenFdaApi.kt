package com.example.infirmary.data

import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit declaration for drug-label lookup; query parameters are URL-encoded by Retrofit. */
interface OpenFdaApi {
    /** GETs label records matching openFDA search syntax, requesting one match by default. */
    @GET("drug/label.json")
    suspend fun searchDrugLabel(
        @Query("search") search: String,
        @Query("limit") limit: Int = 1
    ): OpenFdaResponse
}

/** Incoming search envelope; missing results are handled as an empty list by the repository. */
data class OpenFdaResponse(
    val results: List<OpenFdaLabelResult>? = null
)

/**
 * Relevant JSON label fields, each potentially absent or containing multiple strings.
 * The dosage_and_administration name matches the API JSON field used by Gson.
 */
data class OpenFdaLabelResult(
    val purpose: List<String>? = null,
    val warnings: List<String>? = null,
    val dosage_and_administration: List<String>? = null
)

/** UI-ready strings mapped from the remote label; this object is not persisted in Room. */
data class DrugInfo(
    val purpose: String,
    val warnings: String,
    val dosageAndAdministration: String
)
