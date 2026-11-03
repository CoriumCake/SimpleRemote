package com.simpleremote.data

import android.content.Context
import com.simpleremote.ir.IrCodec
import com.simpleremote.ir.IrProtocols
import com.simpleremote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Kinds of device the app can control. AC has its own stateful remote; the rest are button remotes. */
enum class DeviceCategory(val id: String, val label: String, val plural: String) {
    AC("ac", "Air conditioner", "Air conditioners"),
    TV("tv", "TV", "TVs"),
    STB("stb", "Set-top box", "Set-top boxes"),
    STREAMING("streaming", "Streaming box", "Streaming & media boxes"),
    PROJECTOR("projector", "Projector", "Projectors"),
    FAN("fan", "Fan", "Fans"),
    AUDIO("audio", "Audio", "Soundbars, speakers & receivers"),
    DISC("disc", "DVD / Blu-ray", "DVD, Blu-ray & VCR"),
    LIGHT("light", "Light", "Lights & LED strips"),
    AIR_PURIFIER("air_purifier", "Air purifier", "Air purifiers & humidifiers"),
    HEATER("heater", "Heater", "Heaters & fireplaces"),
    CAMERA("camera", "Camera", "Cameras"),
    VACUUM("vacuum", "Robot vacuum", "Robot vacuums"),
    WATER_HEATER("water_heater", "Water heater", "Water heaters"),
    OTHER("other", "Other", "Other devices");

    companion object {
        fun of(id: String?): DeviceCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** One remote in assets/ir (flipper_index.json, irext_index.json). Remotes of a brand that share a test code have the same [g]. */
@Serializable
data class DeviceEntry(
    val id: String,
    val cat: String,
    val brand: String,
    val model: String = "",
    val src: String,
    /** Canonical key used to test this remote ("power", "mute", ...), or "" → [testLabel]. */
    val test: String = "",
    val testLabel: String = "",
    val g: String,
    /** Number of buttons. */
    val n: Int,
    /** IRext only: binary file and decoder sub-category. */
    val file: String? = null,
    val sub: Int = 0,
    /** IRext only: decoder category (ir_decode.h REMOTE_CATEGORY_*) and the keys verified to decode. */
    val ic: Int = 0,
    val k: List<Int> = emptyList(),
)

/** [mixed]: the "try every code" list of a category, entries from all brands. */
data class DeviceBrand(val name: String, val entries: List<DeviceEntry>, val mixed: Boolean = false) {
    val key: String get() = brandKey(name)

    /** One test step per distinct test code; the fullest remote of each group is the one saved. */
    val steps: List<List<DeviceEntry>> by lazy {
        entries.groupBy { it.g }.values
            .map { group -> group.sortedByDescending { it.n } }
            .sortedByDescending { it.size }
    }
}

data class RemoteButton(val label: String, val key: String, val signal: () -> IrSignal?)

/** A button remote (TV, fan, ...): canonical keys get a fixed place in the layout, the rest are extras. */
class DeviceRemote(val entry: DeviceEntry, val buttons: List<RemoteButton>) {
    private val byKey = buttons.filter { it.key.isNotEmpty() }.associateBy { it.key }
    fun button(key: String): RemoteButton? = byKey[key]
    fun has(key: String) = key in byKey
    val extras: List<RemoteButton> get() = buttons.filter { it.key.isEmpty() }

    val testButton: RemoteButton?
        get() = (if (entry.test.isNotEmpty()) byKey[entry.test] else buttons.firstOrNull { it.label == entry.testLabel })
            ?: buttons.firstOrNull()
}

/**
 * Button-remote databases from assets/ir:
 *  - flipper_index.json + r/fl_<id>.json   Flipper-IRDB (tools/convert_flipper.py)
 *  - irext_index.json   + ../ir_irext/   IRext non-AC binaries (tools/convert_irext_devices.py)
 */
class DeviceCatalog(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var entries: List<DeviceEntry>? = null
    private val cache = object : LinkedHashMap<String, DeviceRemote>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DeviceRemote>?) = size > 8
    }

    private fun load(): List<DeviceEntry> = entries ?: synchronized(this) {
        entries ?: listOf("ir/flipper_index.json", "ir/irext_index.json").flatMap { path ->
            runCatching {
                json.decodeFromString<List<DeviceEntry>>(context.assets.open(path).bufferedReader().use { it.readText() })
            }.getOrDefault(emptyList())
        }.also { entries = it }
    }

    suspend fun counts(): Map<String, Int> = withContext(Dispatchers.IO) { load().groupingBy { it.cat }.eachCount() }

    suspend fun brands(category: DeviceCategory): List<DeviceBrand> = withContext(Dispatchers.IO) {
        load().filter { it.cat == category.id }
            .groupBy { brandKey(it.brand).ifEmpty { it.brand } }
            .map { (_, list) ->
                // Flipper files first (real captures with many buttons), then IRext.
                val name = list.groupingBy { it.brand }.eachCount().maxBy { it.value }.key
                DeviceBrand(name, list.sortedBy { if (it.src == "flipper") 0 else 1 })
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Every code of a category from all brands; a step per distinct code, the most widely used first. */
    suspend fun allCodes(category: DeviceCategory): DeviceBrand = withContext(Dispatchers.IO) {
        DeviceBrand("All brands", load().filter { it.cat == category.id }, mixed = true)
    }

    fun entry(id: String): DeviceEntry? = load().firstOrNull { it.id == id }

    suspend fun remote(id: String): DeviceRemote? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[id] } ?: run {
            val e = entry(id) ?: return@withContext null
            val r = when (e.src) {
                "irext" -> IrextDevice.load(context, e)
                else -> loadFlipper(e)
            } ?: return@withContext null
            synchronized(cache) { cache[id] = r }
            r
        }
    }

    private fun loadFlipper(e: DeviceEntry): DeviceRemote? = runCatching {
        val root = json.parseToJsonElement(context.assets.open("ir/r/${e.id}.json").bufferedReader().use { it.readText() }) as JsonObject
        val buttons = root["b"]!!.jsonArray.map { b ->
            val a = b.jsonArray
            val sig = a[2].jsonArray
            RemoteButton(a[0].jsonPrimitive.content, a[1].jsonPrimitive.content) { flipperSignal(sig) }
        }
        DeviceRemote(e, buttons)
    }.getOrNull()

    companion object {
        /** ["NECext", address, command] or [frequency, [timings...]] → transmittable signal. */
        fun flipperSignal(sig: JsonArray): IrSignal? = runCatching {
            val first = sig[0] as JsonPrimitive
            if (first.isString) {
                val f = IrProtocols.encode(first.content, sig[1].jsonPrimitive.long, sig[2].jsonPrimitive.long) ?: return null
                IrCodec.build(f.timings, repeat = 0, frequencyHz = f.frequency)
            } else {
                IrCodec.build(sig[1].jsonArray.map { it.jsonPrimitive.int }, repeat = 0, frequencyHz = first.int)
            }
        }.getOrNull()
    }
}
