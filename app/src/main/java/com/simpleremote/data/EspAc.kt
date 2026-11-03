package com.simpleremote.data

import com.simpleremote.ir.EspNative as N
import com.simpleremote.ir.IrCodec
import com.simpleremote.ir.IrSignal
import kotlin.math.abs

/**
 * An AC protocol from IRremoteESP8266: the native library builds the full-state frame for
 * any setting, the way the real remote does.
 */
class EspAc(private val entry: AcIndexEntry) : AcModel {
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
        val protocol = entry.protocol ?: return emptyList()
        val mode = when (state.mode) {
            "heat" -> N.MODE_HEAT
            "auto" -> N.MODE_AUTO
            "fan_only" -> N.MODE_FAN
            "dry" -> N.MODE_DRY
            else -> N.MODE_COOL
        }
        val fan = when (state.fan) {
            "low" -> N.FAN_LOW
            "mid" -> N.FAN_MEDIUM
            "high" -> N.FAN_HIGH
            else -> N.FAN_AUTO
        }
        val swing = if (state.swing == "swing") N.SWING_AUTO else N.SWING_OFF
        // The previous state tells toggle-style protocols (power on Daikin128, swing on
        // Coolix/Midea…) whether to flip that setting with this press.
        val prevPower = when (action) {
            AcAction.POWER_ON -> false
            AcAction.POWER_OFF -> true
            else -> state.power
        }
        val prevSwing = if (action == AcAction.SWING) (if (swing == N.SWING_OFF) N.SWING_AUTO else N.SWING_OFF) else swing
        // In modes without a temperature setting, keep the frame stable at 24 °C.
        val temps = capabilities(state.mode).temps
        val temp = if (temps.isEmpty()) 24 else temps.minBy { abs(it - state.temp) }.toInt()
        val (freq, timings) = N.encode(protocol, entry.espModel, state.power, mode, temp, fan, swing, prevPower, prevSwing)
            ?: return emptyList()
        return runCatching { listOf(IrCodec.build(timings.map { abs(it) }, repeat = 0, frequencyHz = freq)) }
            .getOrDefault(emptyList())
    }

    override fun extraSignal(key: String): IrSignal? = null
}
