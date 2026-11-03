package com.simpleremote.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Brand with all the code sets that belong to it, best candidates first. */
data class Brand(val name: String, val entries: List<AcIndexEntry>) {
    val key: String get() = brandKey(name)
}

/** "Saijo-Denki", "saijo denki" and "SAIJO DENKI" all match. */
fun brandKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Reads the bundled AC code databases from assets:
 *  - assets/ac/        SmartIR tables   (tools/convert_smartir.py)
 *  - assets/ac_irext/  IRext binaries   (tools/convert_irext.py)
 *  - assets/ac_extra/  brand-specific captures/protocols from other projects (tools/build_extra.py)
 *  - assets/ac_esp/    IRremoteESP8266 protocols, encoded natively at runtime (tools/convert_esp.py)
 */
class AcCatalog(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var entries: Map<String, AcIndexEntry>? = null
    private val cache = object : LinkedHashMap<String, AcModel>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AcModel>?) = size > 8
    }

    suspend fun brands(): List<Brand> = withContext(Dispatchers.IO) {
        loadEntries().values
            .groupBy { brandKey(it.manufacturer).ifEmpty { it.manufacturer } }
            .map { (_, list) ->
                // Prefer the SmartIR spelling ("Daikin" over "DaiKin"). Order: brand-specific captures,
                // then IRremoteESP8266 (full-state encoders checked against real units), then IRext,
                // then SmartIR tables.
                val name = (list.firstOrNull { it.source == "smartir" } ?: list.first()).manufacturer
                Brand(name, list.sortedBy { when (it.source) { "github" -> 0; "esp" -> 1; "irext" -> 2; else -> 3 } })
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * One code set per AC protocol family across every brand (assets/scan.json, built by
     * tools/build_scan_list.py), most widely used first. An AC beeps for any valid frame of its
     * protocol, so this finds a working family in a few hundred tries instead of ~1,700.
     * Families of the code sets in [tried] (the brand the user just tested) are skipped.
     */
    suspend fun allProtocols(tried: Collection<String> = emptyList()): Brand = withContext(Dispatchers.IO) {
        val all = loadEntries()
        val order = runCatching {
            json.decodeFromString<List<String>>(context.assets.open("scan.json").bufferedReader().use { it.readText() })
        }.getOrDefault(all.keys.toList())
        val triedFiles = tried.mapNotNull { all[it]?.file }.toSet()
        Brand(ALL_BRANDS, order.mapNotNull { all[it] }.filter { it.id !in tried && (it.source != "irext" || it.file !in triedFiles) })
    }

    /** "smartir", "irext", "esp", "github" — where a code set comes from. */
    fun entrySource(id: String): String = loadEntries()[normaliseId(id)]?.source.orEmpty()

    suspend fun model(id: String): AcModel = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[id] } ?: run {
            val fullId = normaliseId(id)
            val entry = loadEntries()[fullId] ?: error("Unknown remote $id")
            val m: AcModel = when (entry.source) {
                "irext" -> IrextAc(entry, context.assets.open("ac_irext/${entry.file}.bin").use { it.readBytes() })
                "esp" -> EspAc(entry)
                else -> {
                    val dir = if (entry.source == "github") "ac_extra" else "ac"
                    val text = context.assets.open("$dir/${entry.file ?: fullId.substringAfter(':')}.json").bufferedReader().use { it.readText() }
                    json.decodeFromString(AcDevice.serializer(), text)
                }
            }
            synchronized(cache) { cache[id] = m }
            m
        }
    }

    private fun loadEntries(): Map<String, AcIndexEntry> = entries ?: synchronized(this) {
        entries ?: buildMap {
            for (dir in listOf("ac", "ac_irext", "ac_extra", "ac_esp")) {
                val text = runCatching {
                    context.assets.open("$dir/index.json").bufferedReader().use { it.readText() }
                }.getOrNull() ?: continue
                json.decodeFromString<List<AcIndexEntry>>(text).forEach { put(it.id, it) }
            }
        }.also { entries = it }
    }

    companion object {
        const val ALL_BRANDS = "All brands"

        /** Saved remotes from v0.1 stored bare SmartIR ids ("1000"). */
        fun normaliseId(id: String) = if (':' in id) id else "smartir:$id"
    }
}
