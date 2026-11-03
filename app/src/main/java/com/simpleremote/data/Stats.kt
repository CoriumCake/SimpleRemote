package com.simpleremote.data

import android.content.Context
import android.os.Build
import com.simpleremote.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anonymous, opt-in code statistics: which code sets worked or not during setup.
 *
 * Nothing is recorded until the user agrees ([consent] == true). An event holds only the device
 * category, brand, code-set id, test result, phone model, Android version and app version — no
 * account, no install id, no location, no remote names. Events wait in a small local queue and
 * are POSTed to [BuildConfig.STATS_URL] (empty = never sent; set with -PstatsUrl=https://...).
 */
object Stats {
    @Serializable
    data class Event(
        /** "setup" (tested during setup) or "deleted" (saved remote removed). */
        val t: String,
        val cat: String,
        val brand: String,
        val code: String,
        val src: String,
        val ok: Boolean? = null,
        /** Step in the test list, and whether the list mixed all brands ("try every code"). */
        val step: Int? = null,
        val mixed: Boolean = false,
        /** Days the remote was kept before being deleted. */
        val days: Int? = null,
        val phone: String = "${Build.MANUFACTURER} ${Build.MODEL}".take(64),
        val sdk: Int = Build.VERSION.SDK_INT,
        val v: String = BuildConfig.VERSION_NAME,
        val ts: Long = System.currentTimeMillis() / 1000,
    )

    private const val PREFS = "stats"
    private const val MAX_QUEUE = 500
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** null = not asked yet. */
    fun consent(context: Context): Boolean? =
        prefs(context).let { if (it.contains("consent")) it.getBoolean("consent", false) else null }

    fun setConsent(context: Context, allowed: Boolean) {
        val edit = prefs(context).edit().putBoolean("consent", allowed)
        if (!allowed) edit.remove("queue")
        edit.apply()
        if (allowed) flush(context)
    }

    fun record(context: Context, event: Event) {
        if (consent(context) != true) return
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val q = (queue(app) + event).takeLast(MAX_QUEUE)
                prefs(app).edit().putString("queue", json.encodeToString(q)).apply()
            }
            send(app)
        }
    }

    /** Sends queued events, if any (called on app start and after each event). */
    fun flush(context: Context) {
        val app = context.applicationContext
        scope.launch { send(app) }
    }

    private fun queue(context: Context): List<Event> = runCatching {
        json.decodeFromString<List<Event>>(prefs(context).getString("queue", null) ?: return emptyList())
    }.getOrDefault(emptyList())

    private suspend fun send(context: Context) = lock.withLock {
        val url = BuildConfig.STATS_URL
        if (url.isBlank() || consent(context) != true) return@withLock
        val batch = queue(context).take(100)
        if (batch.isEmpty()) return@withLock
        val ok = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(json.encodeToString(mapOf("events" to batch)).toByteArray()) }
                c.responseCode in 200..299
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
        if (ok) {
            val rest = queue(context).drop(batch.size)
            prefs(context).edit().putString("queue", json.encodeToString(rest)).apply()
        }
    }
}
