package de.clopcounter.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small Supabase client (Auth + PostgREST) on plain HttpURLConnection.
 * Accounts are username + password; internally they use a pseudo e-mail (see supabase/schema.sql).
 */
class SupabaseApi(baseUrl: String, private val anonKey: String, private val session: SessionStore) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val refreshLock = Mutex()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    // ---------------------------------------------------------------- accounts

    /** Creates the account and returns the new user number. */
    suspend fun register(username: String, password: String, recoveryCode: String): Long {
        val body = JSONObject()
            .put("p_username", username)
            .put("p_password", password)
            .put("p_recovery_code", recoveryCode)
        return request("POST", "/rest/v1/rpc/register", body.toString(), signedIn = false).trim().toLong()
    }

    suspend fun signIn(username: String, password: String): Account {
        val body = JSONObject().put("email", emailFor(username)).put("password", password)
        val response = try {
            rawRequest("POST", "/auth/v1/token?grant_type=password", body.toString(), bearer = anonKey)
        } catch (e: SupabaseException) {
            if (e.status == 400) throw SupabaseException(400, "Nutzername oder Passwort falsch")
            throw e
        }
        val json = JSONObject(response)
        saveTokens(json)
        val userId = json.getJSONObject("user").getString("id")
        val profile = JSONArray(request("GET", "/rest/v1/profiles?id=eq.$userId&select=username,user_number"))
            .optJSONObject(0) ?: throw SupabaseException(404, "Profil nicht gefunden")
        return Account(userId, profile.getString("username"), profile.getLong("user_number"))
    }

    suspend fun signOut() {
        runCatching { request("POST", "/auth/v1/logout", "{}") }
    }

    suspend fun resetPassword(username: String, recoveryCode: String, newPassword: String): Boolean {
        val body = JSONObject()
            .put("p_username", username)
            .put("p_recovery_code", recoveryCode)
            .put("p_new_password", newPassword)
        return request("POST", "/rest/v1/rpc/reset_password", body.toString(), signedIn = false).trim() == "true"
    }

    suspend fun deleteAccount() {
        request("POST", "/rest/v1/rpc/delete_account", "{}")
    }

    // ---------------------------------------------------------------- entries

    suspend fun upsertEntries(entries: List<RemoteEntry>) {
        if (entries.isEmpty()) return
        val rows = JSONArray(
            entries.map { e ->
                JSONObject()
                    .put("uuid", e.uuid)
                    .put("ts", e.timestamp)
                    .put("epoch_day", e.epochDay)
                    .put("tags", JSONArray(e.tags))
                    .put("links", JSONArray(e.links.map { it.toJson() }))
                    .put("updated_at", e.updatedAt)
            },
        )
        request("POST", "/rest/v1/entries?on_conflict=uuid", rows.toString(), headers = UPSERT)
        val notes = JSONArray(entries.map { JSONObject().put("uuid", it.uuid).put("note", it.note) })
        request("POST", "/rest/v1/entry_notes?on_conflict=uuid", notes.toString(), headers = UPSERT)
    }

    suspend fun deleteEntries(uuids: List<String>) {
        uuids.chunked(100).forEach { chunk ->
            request("DELETE", "/rest/v1/entries?uuid=in.(${chunk.joinToString(",")})", headers = MINIMAL)
        }
    }

    /** All entries of [userId] (own or a friend's). Notes are only included for the own account. */
    suspend fun fetchEntries(userId: String, withNotes: Boolean = false): List<RemoteEntry> {
        val entries = fetchAll("/rest/v1/entries?user_id=eq.$userId&select=uuid,ts,epoch_day,tags,links,updated_at&order=ts.asc")
            .map { it.toRemoteEntry() }
        if (!withNotes) return entries
        val notes = fetchAll("/rest/v1/entry_notes?select=uuid,note&order=uuid.asc")
            .associate { it.getString("uuid") to it.optString("note") }
        return entries.map { it.copy(note = notes[it.uuid].orEmpty()) }
    }

    // ---------------------------------------------------------------- friends

    suspend fun friends(): List<Friend> {
        val rows = JSONArray(request("POST", "/rest/v1/rpc/my_friends", "{}"))
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            Friend(
                userId = row.getString("user_id"),
                username = row.getString("username"),
                userNumber = row.getLong("user_number"),
                status = when (row.getString("status")) {
                    "friend" -> FriendStatus.FRIEND
                    "outgoing" -> FriendStatus.OUTGOING
                    else -> FriendStatus.INCOMING
                },
            )
        }
    }

    /** Returns "sent", "accepted", "already_sent", "not_found" or "self". */
    suspend fun sendFriendRequest(target: String): String {
        val result = request("POST", "/rest/v1/rpc/send_friend_request", JSONObject().put("p_target", target).toString())
        return JSONTokener(result).nextValue() as? String ?: result
    }

    suspend fun respondFriendRequest(userId: String, accept: Boolean) {
        val body = JSONObject().put("p_other", userId).put("p_accept", accept)
        request("POST", "/rest/v1/rpc/respond_friend_request", body.toString())
    }

    suspend fun removeFriend(userId: String) {
        request("POST", "/rest/v1/rpc/remove_friend", JSONObject().put("p_other", userId).toString())
    }

    // ---------------------------------------------------------------- http

    private suspend fun fetchAll(path: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val page = JSONArray(request("GET", "$path&limit=$PAGE_SIZE&offset=$offset"))
            for (i in 0 until page.length()) result += page.getJSONObject(i)
            if (page.length() < PAGE_SIZE) return result
            offset += PAGE_SIZE
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        signedIn: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val bearer = if (signedIn) validAccessToken() else anonKey
        return rawRequest(method, path, body, bearer, headers)
    }

    private suspend fun validAccessToken(): String = refreshLock.withLock {
        val token = session.accessToken ?: throw NotSignedInException()
        if (System.currentTimeMillis() < session.expiresAt - 60_000) return token
        val refresh = session.refreshToken ?: throw NotSignedInException()
        val response = try {
            rawRequest(
                "POST",
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh).toString(),
                bearer = anonKey,
            )
        } catch (e: SupabaseException) {
            if (e.status in 400..499) {
                session.clear()
                throw NotSignedInException()
            }
            throw e
        }
        saveTokens(JSONObject(response))
        session.accessToken!!
    }

    private fun saveTokens(json: JSONObject) {
        session.saveTokens(json.getString("access_token"), json.getString("refresh_token"), json.optLong("expires_in", 3600))
    }

    private suspend fun rawRequest(
        method: String,
        path: String,
        body: String?,
        bearer: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw SupabaseException(0, "Server ist nicht eingerichtet")
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) throw SupabaseException(status, errorMessage(text))
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.ifBlank { "Serverfehler" }
        val raw = listOf("message", "error_description", "msg", "error")
            .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } } ?: "Serverfehler"
        return when (raw) {
            "username_taken" -> "Dieser Nutzername ist schon vergeben"
            "invalid_username" -> "Nutzername: 3–20 Zeichen, nur Buchstaben, Zahlen, _ . -"
            "weak_password" -> "Das Passwort braucht mindestens 6 Zeichen"
            else -> raw
        }
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private val UPSERT = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        private val MINIMAL = mapOf("Prefer" to "return=minimal")

        /** Internal pseudo e-mail; must match register() in supabase/schema.sql. */
        fun emailFor(username: String) = username.trim().lowercase() + "@users.clopcounter.invalid"
    }
}

private fun RemoteLink.toJson(): JSONObject = JSONObject()
    .put("url", url)
    .put("title", title ?: JSONObject.NULL)
    .put("preview", previewUrl ?: JSONObject.NULL)

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.toRemoteEntry(): RemoteEntry {
    val tags = optJSONArray("tags")
    val links = optJSONArray("links")
    return RemoteEntry(
        uuid = getString("uuid"),
        timestamp = getLong("ts"),
        epochDay = getLong("epoch_day"),
        tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
        links = if (links == null) emptyList() else (0 until links.length()).mapNotNull { links.optJSONObject(it) }
            .mapNotNull { l ->
                l.stringOrNull("url")?.let { RemoteLink(it, l.stringOrNull("title"), l.stringOrNull("preview")) }
            },
        updatedAt = getLong("updated_at"),
    )
}
