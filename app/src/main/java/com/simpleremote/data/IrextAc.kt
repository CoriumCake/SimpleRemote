package com.simpleremote.data

import com.simpleremote.ir.IrCodec
import com.simpleremote.ir.IrSignal
import com.simpleremote.ir.IrextNative as N

/**
 * An AC remote from the IRext database: a tiny protocol binary that the native
 * IRext decoder turns into the IR frame for any state.
 */
class IrextAc(private val entry: AcIndexEntry, private val binary: ByteArray) : AcModel {
    override val id: String get() = entry.id
    override val manufacturer: String get() = entry.manufacturer
    override val fahrenheit: Boolean get() = false
    override val modes: List<String> get() = entry.modes
    override val extraKeys: List<String> get() = emptyList()

    override fun capabilities(mode: String): ModeCapabilities {
        val c = entry.caps?.get(mode) ?: return ModeCapabilities(emptyList(), emptyList(), emptyList())
        return ModeCapabilities(
            temps = c.temps.map { it.toDouble() },
            fans = c.fans,
            swings = if (c.swing) listOf("swing", "fixed") else emptyList(),
        )
    }

    override fun signals(state: AcState, action: AcAction): List<IrSignal> {
        val key = when (action) {
            AcAction.POWER_ON, AcAction.POWER_OFF -> N.KEY_POWER
            AcAction.MODE -> N.KEY_MODE
            AcAction.TEMP_UP -> N.KEY_TEMP_UP
            AcAction.TEMP_DOWN -> N.KEY_TEMP_DOWN
            AcAction.FAN -> N.KEY_WIND_SPEED
            AcAction.SWING -> if (state.swing == "swing") N.KEY_WIND_SWING else N.KEY_WIND_FIX
        }
        val mode = when (state.mode) {
            "heat" -> N.MODE_HEAT
            "auto" -> N.MODE_AUTO
            "fan_only" -> N.MODE_FAN
            "dry" -> N.MODE_DRY
            else -> N.MODE_COOL
        }
        val speed = when (state.fan) {
            "low" -> N.SPEED_LOW
            "mid" -> N.SPEED_MEDIUM
            "high" -> N.SPEED_HIGH
            else -> N.SPEED_AUTO
        }
        val temp = (state.temp.toInt() - 16).coerceIn(0, 14)
        val swing = if (state.swing == "swing") N.SWING_ON else N.SWING_OFF
        val power = if (state.power) N.POWER_ON else N.POWER_OFF
        val raw = N.decode(binary, key, power, mode, temp, speed, swing) ?: return emptyList()
        return runCatching { listOf(IrCodec.build(raw.toList(), repeat = 0, frequencyHz = IrCodec.DEFAULT_FREQUENCY)) }
            .getOrDefault(emptyList())
    }

    override fun extraSignal(key: String): IrSignal? = null
}
