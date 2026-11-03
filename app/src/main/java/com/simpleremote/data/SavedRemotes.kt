package com.simpleremote.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A remote the user has set up. IR is one-way, so we also remember the last state we sent. */
@Serializable
data class SavedRemote(
    val uid: String,
    val type: String = "ac",
    val deviceId: String,
    val name: String,
    val state: AcState? = null,
)

class SavedRemotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("remotes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<SavedRemote> = runCatching {
        json.decodeFromString<List<SavedRemote>>(prefs.getString(KEY, null) ?: return emptyList())
    }.getOrDefault(emptyList())

    fun save(list: List<SavedRemote>) {
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    private companion object { const val KEY = "saved_v1" }
}
