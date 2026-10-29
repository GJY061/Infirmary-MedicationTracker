package com.example.infirmary.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit declaration of Gemini generation requests; the repository supplies its base URL. */
interface GeminiApi {
    /**
     * POSTs a JSON prompt: @Path selects the model, @Header sends the key, and @Body is Gson-encoded.
     * Suspends while the request runs; non-success HTTP responses are surfaced as HttpException.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

/** Outgoing JSON envelope containing the messages supplied to Gemini. */
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>
)

/** One outgoing message, with a role and a list of text parts. */
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

/** One text fragment in the outgoing prompt. */
data class GeminiPart(
    val text: String
)

/** Incoming JSON envelope; candidates may be absent when no answer is returned. */
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

/** One possible generated answer; only the first candidate is used by this app. */
data class GeminiCandidate(
    val content: GeminiContentResponse? = null
)

/** Content inside a generated candidate, represented as optional text parts. */
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>? = null
)

/** One response part; nullable text allows parts without displayable text. */
data class GeminiPartResponse(
    val text: String? = null
)

/**
 * Extracts the first nonblank text part from the first candidate, or null if none exists.
 * Safe calls tolerate missing response fields; multiple parts are not concatenated.
 */
fun GeminiGenerateContentResponse.firstTextOrNull(): String? {
    return candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text?.trim() }
        ?.firstOrNull { it.isNotBlank() }
}
