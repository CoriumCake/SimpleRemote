package com.simpleremote.data

import com.simpleremote.ir.IrCodec
import com.simpleremote.ir.IrSignal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

/** Per-mode capabilities stored in the IRext index. */
@Serializable
data class IndexModeCaps(val temps: List<Int> = emptyList(), val fans: List<String> = emptyList(), val swing: Boolean = false)

/** One entry of an index.json — enough to list and describe a remote without loading its codes. */
@Serializable
data class AcIndexEntry(
    val id: String,
    val source: String = "smartir",
    /** IRext only: binary file name in assets/ac_irext (identical binaries are shared). */
    val file: String? = null,
    val manufacturer: String,
    val models: List<String> = emptyList(),
    val encoding: String,
    val minTemp: Double,
    val maxTemp: Double,
    val precision: Double = 1.0,
    val fahrenheit: Boolean = false,
    val modes: List<String>,
    val fanModes: List<String> = emptyList(),
    val swingModes: List<String> = emptyList(),
    val extras: List<String> = emptyList(),
    val caps: Map<String, IndexModeCaps>? = null,
    /** IRremoteESP8266 only: decode_type_t value, its name, and remote model enum (-1 = none). */
    val protocol: Int? = null,
    val protocolName: String? = null,
    val espModel: Int = -1,
)

/**
 * SmartIR code table for one AC remote (assets/ac/<n>.json).
 *
 * AC remotes are stateful: every press sends the complete state
 * (mode + fan + swing + temperature), so codes are looked up by state, not by key.
 */
@Serializable
data class AcDevice(
    override val id: String,
    override val manufacturer: String,
    val models: List<String> = emptyList(),
    val encoding: String,
    val minTemp: Double,
    val maxTemp: Double,
    val precision: Double = 1.0,
    override val fahrenheit: Boolean = false,
    override val modes: List<String>,
    val fanModes: List<String> = emptyList(),
    val swingModes: List<String> = emptyList(),
    val off: JsonElement,
    val on: JsonElement? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
    val commands: Map<String, JsonElement>,
) : AcModel {

    override val extraKeys: List<String> get() = extras.keys.toList()

    @kotlinx.serialization.Transient
    private val capsCache = HashMap<String, ModeCapabilities>()

    private enum class Level { FAN, SWING, TEMP }

    /**
     * SmartIR nests codes as mode -> fan -> [swing] -> temp, but levels are often skipped
     * (e.g. "fan_only" has no temperature, "dry" may have no fan level). Swing names can
     * collide with fan names ("auto", "high"...), so classify each level by its keys and
     * by which levels were already consumed, following the SmartIR order.
     */
    private fun levelOf(obj: JsonObject, fanUsed: Boolean, swingUsed: Boolean): Level = when {
        obj.keys.all { it.toDoubleOrNull() != null } -> Level.TEMP
        !fanUsed && obj.keys.any { it in fanModes } -> Level.FAN
        !swingUsed -> Level.SWING
        else -> Level.FAN
    }

    override fun capabilities(mode: String): ModeCapabilities = capsCache.getOrPut(mode) {
        val temps = HashSet<Double>(); val fans = HashSet<String>(); val swings = HashSet<String>()
        fun walk(e: JsonElement, fanUsed: Boolean, swingUsed: Boolean) {
            if (e !is JsonObject) return
            when (levelOf(e, fanUsed, swingUsed)) {
                Level.TEMP -> e.forEach { (k, v) -> temps += k.toDouble(); walk(v, fanUsed, swingUsed) }
                Level.FAN -> e.forEach { (k, v) -> fans += k; walk(v, true, swingUsed) }
                Level.SWING -> e.forEach { (k, v) -> swings += k; walk(v, fanUsed, true) }
            }
        }
        commands[mode]?.let { walk(it, false, false) }
        ModeCapabilities(
            temps = temps.sorted(),
            fans = fanModes.filter { it in fans } + fans.filter { it !in fanModes }.sorted(),
            swings = swingModes.filter { it in swings } + swings.filter { it !in swingModes }.sorted(),
        )
    }

    /** Finds the code for a full AC state, or null if this remote has no code for it. */
    fun codeFor(state: AcState): JsonElement? {
        var node: JsonElement = commands[state.mode] ?: return null
        var fanUsed = false; var swingUsed = false
        while (node is JsonObject) {
            val obj = node
            val key = when (levelOf(obj, fanUsed, swingUsed)) {
                Level.TEMP -> tempKey(state.temp)
                Level.FAN -> { fanUsed = true; state.fan }
                Level.SWING -> { swingUsed = true; state.swing }
            }
            node = obj[key ?: return null] ?: return null
        }
        return node
    }

    fun signalFor(state: AcState): IrSignal? = codeFor(state)?.let(::toSignal)

    override fun signals(state: AcState, action: AcAction): List<IrSignal> {
        if (!state.power) return listOfNotNull(toSignal(off))
        val stateSignal = signalFor(state) ?: return emptyList()
        val onSignal = if (action == AcAction.POWER_ON) on?.let(::toSignal) else null
        return listOfNotNull(onSignal, stateSignal)
    }

    override fun extraSignal(key: String): IrSignal? = extras[key]?.let(::toSignal)

    fun toSignal(code: JsonElement): IrSignal? = runCatching {
        when {
            code is JsonNull -> null
            encoding == "raw" && code is JsonArray -> IrCodec.decodeRaw(code.map { (it as JsonPrimitive).int })
            code is JsonPrimitive && code.isString -> IrCodec.decodeBroadlink(code.content)
            else -> null
        }
    }.getOrNull()
}
