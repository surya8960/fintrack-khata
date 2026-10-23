package com.example.fintrack.data.supabase

import com.example.fintrack.data.model.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Thrown for any non-2xx Supabase response. [status] lets callers react to 401 (expired token). */
class SupabaseException(val status: Int, message: String) : Exception(message)

/**
 * Thin Supabase client built on the public REST APIs:
 *  - GoTrue (`/auth/v1`) for email/password auth
 *  - PostgREST (`/rest/v1`) for table access, always scoped by Row Level Security
 */
class SupabaseService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    fun close() = client.close()

    // ---------------------------------------------------------------- auth

    suspend fun signIn(supabaseUrl: String, anonKey: String, email: String, password: String): Result<UserSession> =
        auth(supabaseUrl, anonKey, "/auth/v1/token?grant_type=password", email, password, requireToken = true)

    /**
     * Registers a new user. When the project requires e-mail confirmation Supabase returns no
     * token; the result then has `isAuthenticated = false` and the user must confirm by e-mail.
     */
    suspend fun signUp(supabaseUrl: String, anonKey: String, email: String, password: String): Result<UserSession> =
        auth(supabaseUrl, anonKey, "/auth/v1/signup", email, password, requireToken = false)

    suspend fun refresh(session: UserSession): Result<UserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val refreshToken = session.refreshToken ?: throw SupabaseException(401, "Session expired. Please sign in again.")
            val response = client.post("${session.supabaseUrl}/auth/v1/token?grant_type=refresh_token") {
                header("apikey", session.supabaseKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("refresh_token", refreshToken) }.toString())
            }
            parseSession(response, session.email.orEmpty(), session.supabaseUrl, session.supabaseKey, requireToken = true)
        }
    }

    private suspend fun auth(
        supabaseUrl: String,
        anonKey: String,
        path: String,
        email: String,
        password: String,
        requireToken: Boolean
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(supabaseUrl)
            val key = anonKey.trim()
            val response = client.post(base + path) {
                header("apikey", key)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", email.trim())
                    put("password", password)
                }.toString())
            }
            parseSession(response, email.trim(), base, key, requireToken)
        }
    }

    private suspend fun parseSession(
        response: HttpResponse,
        email: String,
        baseUrl: String,
        anonKey: String,
        requireToken: Boolean
    ): UserSession {
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw SupabaseException(response.status.value, errorMessage(body))
        val parsed = json.decodeFromString<JsonObject>(body)
        val token = parsed["access_token"]?.jsonPrimitive?.content
        if (requireToken && token == null) throw SupabaseException(500, "Server did not return a session")
        val userId = (parsed["user"] as? JsonObject)?.get("id")?.jsonPrimitive?.content
            ?: parsed["id"]?.jsonPrimitive?.content
        return UserSession(
            email = email,
            token = token,
            refreshToken = parsed["refresh_token"]?.jsonPrimitive?.content,
            userId = userId,
            isAuthenticated = token != null,
            supabaseUrl = baseUrl,
            supabaseKey = anonKey
        )
    }

    // ------------------------------------------------------------ database

    /** Inserts or updates [rows] (matched on the `id` primary key). */
    suspend fun upsert(session: UserSession, table: String, rows: JsonArray) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            val response = client.post("${session.supabaseUrl}/rest/v1/$table?on_conflict=id") {
                authHeaders(session)
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(rows.toString())
            }
            ensureOk(response)
        }
    }

    /**
     * Downloads a whole table. PostgREST caps every response at 1000 rows (Supabase default),
     * so the table is read page by page; otherwise ledgers larger than 1000 rows would be
     * silently truncated on a new device.
     */
    suspend fun fetchAll(session: UserSession, table: String): JsonArray = withContext(Dispatchers.IO) {
        val all = ArrayList<JsonElement>()
        var offset = 0
        while (true) {
            val response = client.get(
                "${session.supabaseUrl}/rest/v1/$table?select=*&order=id.asc&limit=$PAGE_SIZE&offset=$offset"
            ) {
                authHeaders(session)
            }
            val page = json.decodeFromString(JsonArray.serializer(), ensureOk(response))
            all.addAll(page)
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        JsonArray(all)
    }

    suspend fun deleteById(session: UserSession, table: String, id: String) {
        withContext(Dispatchers.IO) {
            val response = client.delete("${session.supabaseUrl}/rest/v1/$table") {
                authHeaders(session)
                url.parameters.append("id", "eq.$id")
            }
            ensureOk(response)
        }
    }

    // --------------------------------------------------------------- utils

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(session: UserSession) {
        header("apikey", session.supabaseKey)
        header(HttpHeaders.Authorization, "Bearer ${session.token}")
        header(HttpHeaders.Accept, "application/json")
    }

    private suspend fun ensureOk(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw SupabaseException(response.status.value, errorMessage(body))
        return body
    }

    private fun errorMessage(body: String): String = runCatching {
        val obj = json.decodeFromString<JsonObject>(body)
        listOf("msg", "message", "error_description", "error")
            .firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.content }
    }.getOrNull() ?: body.take(200).ifBlank { "Unexpected server response" }

    companion object {
        const val PAGE_SIZE = 1000

        /** Accepts `abc.supabase.co`, with/without scheme or trailing slash. HTTPS is enforced. */
        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { "Supabase URL is required" }
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            require(withScheme.startsWith("https://")) { "Supabase URL must use https" }
            return withScheme
        }
    }
}
