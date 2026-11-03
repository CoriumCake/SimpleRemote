package com.simpleremote.data

import com.simpleremote.ir.IrSignal
import kotlinx.serialization.Serializable

/** What the user just did. Some AC protocols (IRext) encode the pressed key into the frame. */
enum class AcAction { POWER_ON, POWER_OFF, MODE, TEMP_UP, TEMP_DOWN, FAN, SWING }

/** What a given mode lets the user change — drives which controls are rendered. */
data class ModeCapabilities(
    val temps: List<Double>,
    val fans: List<String>,
    val swings: List<String>,
)

@Serializable
data class AcState(
    val power: Boolean,
    val mode: String,
    val fan: String?,
    val swing: String?,
    val temp: Double,
)

/**
 * A controllable AC remote, regardless of where its codes come from
 * (SmartIR lookup tables or IRext protocol binaries).
 */
interface AcModel {
    val id: String
    val manufacturer: String
    val fahrenheit: Boolean
    val modes: List<String>
    val extraKeys: List<String>

    fun capabilities(mode: String): ModeCapabilities

    /** Signals to transmit for [state]; empty when this remote has no code for it. */
    fun signals(state: AcState, action: AcAction): List<IrSignal>

    fun extraSignal(key: String): IrSignal?
}

/** Picks a sensible initial state (cool, auto fan, 24 °C or nearest). */
fun AcModel.defaultState(): AcState {
    val mode = modes.firstOrNull { it == "cool" } ?: modes.first()
    return normalise(AcState(power = false, mode = mode, fan = null, swing = null, temp = if (fahrenheit) 75.0 else 24.0))
}

/** Coerces a state so every field is valid for the selected mode. */
fun AcModel.normalise(state: AcState): AcState {
    val mode = if (state.mode in modes) state.mode else modes.first()
    val caps = capabilities(mode)
    val fan = when {
        caps.fans.isEmpty() -> null
        state.fan in caps.fans -> state.fan
        "auto" in caps.fans -> "auto"
        else -> caps.fans.first()
    }
    val swing = when {
        caps.swings.isEmpty() -> null
        state.swing in caps.swings -> state.swing
        else -> caps.swings.first()
    }
    val temp = if (caps.temps.isEmpty()) state.temp else caps.temps.minBy { kotlin.math.abs(it - state.temp) }
    return state.copy(mode = mode, fan = fan, swing = swing, temp = temp)
}

fun tempKey(t: Double): String = if (t == Math.floor(t)) t.toLong().toString() else t.toString()
