package app.fitnova.data

import app.fitnova.BuildConfig
import app.fitnova.domain.Exercise
import app.fitnova.domain.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

class ApiException(val code: Int, message: String) : Exception(message)

data class Rank(val userId: String, val name: String, val points: Int, val workouts: Int, val rank: Int)

/**
 * Talks to the existing Fit Nova Supabase project.
 * Accounts go through the old `nova-auth` edge function (same usernames and passwords as the old app);
 * workouts and leaderboards use the new `fn_*` tables from supabase/migrations.
 */
// ponytail: HttpURLConnection + org.json (both built into Android) instead of OkHttp/Retrofit/serialization.
class Api(private val session: SessionStore) {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private val refreshLock = Mutex()

    suspend fun login(username: String, password: String) {
        val body = JSONObject().put("action", "login").put("username", username).put("password", password)
        accept(JSONObject(call("POST", "/functions/v1/nova-auth", body.toString())), username)
    }

    /** Returns the one-time recovery code the server issues for new accounts (if any). */
    suspend fun register(username: String, password: String): String? {
        val body = JSONObject()
            .put("action", "register")
            .put("username", username)
            .put("password", password)
            .put("confirm_password", password)
        val json = JSONObject(call("POST", "/functions/v1/nova-auth", body.toString()))
        accept(json, username)
        return json.optString("recovery_code").ifBlank { null }
    }

    suspend fun saveProfile(displayName: String) {
        val id = session.userId ?: return
        val body = JSONObject().put("id", id).put("display_name", displayName.take(40))
        authed("POST", "/rest/v1/fn_profiles?on_conflict=id", body.toString(), UPSERT)
    }

    suspend fun pushWorkouts(workouts: List<Workout>) {
        val owner = session.userId ?: throw ApiException(401, "Sign in to sync")
        val rows = JSONArray()
        for (w in workouts) {
            rows.put(
                JSONObject()
                    .put("id", w.id)
                    .put("owner", owner)
                    .put("exercise", w.exercise.id)
                    .put("reps", w.reps)
                    .put("seconds", w.seconds)
                    .put("form_score", w.form)
                    .put("tracked", w.tracked)
                    .put("started_at", Instant.ofEpochMilli(w.startedAt).toString()),
            )
        }
        authed("POST", "/rest/v1/fn_workouts?on_conflict=id", rows.toString(), UPSERT)
    }

    suspend fun pullWorkouts(): List<Workout> {
        val text = authed(
            "GET",
            "/rest/v1/fn_workouts?select=id,exercise,reps,seconds,form_score,tracked,started_at&order=started_at.desc&limit=1000",
            null,
        )
        val rows = JSONArray(text)
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.getJSONObject(i)
            val exercise = Exercise.of(o.getString("exercise")) ?: return@mapNotNull null
            Workout(
                id = o.getString("id"),
                exercise = exercise,
                reps = o.optInt("reps"),
                seconds = o.optInt("seconds"),
                form = o.optInt("form_score"),
                tracked = o.optBoolean("tracked"),
                startedAt = OffsetDateTime.parse(o.getString("started_at")).toInstant().toEpochMilli(),
                synced = true,
            )
        }
    }

    suspend fun leaderboard(period: String, zone: String): List<Rank> {
        val body = JSONObject().put("period", period).put("tz", zone)
        val rows = JSONArray(authed("POST", "/rest/v1/rpc/fn_leaderboard", body.toString()))
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            Rank(o.getString("user_id"), o.optString("display_name", "Athlete"), o.optInt("points"), o.optInt("workouts"), o.optInt("rank"))
        }
    }

    private fun accept(json: JSONObject, username: String) {
        val user = json.optJSONObject("user") ?: throw ApiException(500, json.optString("message", "Unexpected sign-in response"))
        session.save(user.getString("id"), username, json.getString("access_token"), json.getString("refresh_token"))
    }

    /** Authenticated call; on an expired token, refreshes once and retries. */
    private suspend fun authed(method: String, path: String, body: String?, prefer: String? = null): String {
        val token = session.accessToken ?: throw ApiException(401, "Sign in to sync")
        return try {
            call(method, path, body, token, prefer)
        } catch (e: ApiException) {
            if (e.code != 401) throw e
            call(method, path, body, refresh(token), prefer)
        }
    }

    private suspend fun refresh(stale: String): String = refreshLock.withLock {
        // Another call may have refreshed while we waited for the lock.
        session.accessToken?.takeIf { it != stale }?.let { return it }
        val refresh = session.refreshToken ?: throw ApiException(401, "Session expired. Sign in again.")
        val json = try {
            JSONObject(call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh).toString()))
        } catch (e: ApiException) {
            if (e.code in 400..499) session.clear()
            throw ApiException(401, "Session expired. Sign in again; your workouts are still on this phone.")
        }
        val access = json.getString("access_token")
        session.updateTokens(access, json.getString("refresh_token"))
        access
    }

    private suspend fun call(method: String, path: String, body: String?, token: String? = null, prefer: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    setRequestProperty("apikey", key)
                    setRequestProperty("Content-Type", "application/json")
                    if (token != null) setRequestProperty("Authorization", "Bearer $token")
                    if (prefer != null) setRequestProperty("Prefer", prefer)
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
            } catch (e: IOException) {
                throw ApiException(0, "You're offline. Workouts stay on this phone until you reconnect.")
            }
            try {
                val code = connection.responseCode
                val stream = if (code < 400) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code >= 400) throw ApiException(code, errorMessage(code, text))
                text
            } catch (e: IOException) {
                throw ApiException(0, "You're offline. Workouts stay on this phone until you reconnect.")
            } finally {
                connection.disconnect()
            }
        }

    private fun errorMessage(code: Int, text: String): String {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val pgCode = json?.optString("code").orEmpty()
        // Tables or RPC from the M1 migration aren't on the server yet.
        if (pgCode == "PGRST202" || pgCode == "PGRST205" || pgCode == "42P01") {
            return "Cloud sync isn't set up on the server yet."
        }
        val message = json?.let {
            listOf("error_description", "message", "msg", "error").firstNotNullOfOrNull { k -> it.optString(k).ifBlank { null } }
        }
        return message ?: "Request failed ($code)"
    }

    private companion object {
        const val UPSERT = "resolution=merge-duplicates,return=minimal"
    }
}
