package com.simpleremote.data

import android.content.Context
import com.simpleremote.ir.IrCodec
import com.simpleremote.ir.IrextNative

/**
 * IRext non-AC remotes (TV, set-top box, fan, ...): binaries in assets/ir_irext, decoded natively key by key.
 * Only keys listed in [DeviceEntry.k] are decoded: tools/convert_irext_devices.py verified each of them
 * with the same decoder under sanitizers.
 */
object IrextDevice {
    /** label to canonical key ("" = extra button), indexed by IRext key code (ir_decode.h KEY_*). */
    private fun k(label: String, key: String = "") = label to key

    private val NAV = listOf(k("Up", "up"), k("Down", "down"), k("Left", "left"), k("Right", "right"), k("OK", "ok"))
    private val DIGITS = (0..9).map { k("$it", "$it") }

    private val TV = listOf(k("Power", "power"), k("Mute", "mute")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Back", "back"), k("Input", "input"),
            k("Menu", "menu"), k("Home", "home"), k("Settings")) + DIGITS
    private val STB = listOf(k("Power", "power"), k("Mute", "mute")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Back", "back"), k("Input", "input"),
            k("Menu", "menu"), k("Page up", "ch_up"), k("Page down", "ch_down")) + DIGITS
    private val NETBOX = listOf(k("Power", "power")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Back", "back"), k("Menu", "menu"), k("Home", "home"))
    private val DVD = listOf(k("Power", "power")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Play", "play"), k("Pause", "pause"), k("Eject"),
            k("Rewind", "rew"), k("Fast forward", "ff"), k("Menu", "menu"))
    private val FAN = mapOf(0 to k("Power", "power"), 6 to k("Speed +", "speed_up"), 7 to k("Speed −", "speed_down"),
        8 to k("Swing", "oscillate"), 9 to k("Speed", "speed"), 10 to k("Wind mode", "mode"))
    private val PROJECTOR = listOf(k("Power", "power")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Zoom −"), k("Menu", "menu"), k("Zoom +"), k("Back", "back"))
    private val STEREO = listOf(k("Power", "power")) + NAV +
        listOf(k("Vol +", "vol_up"), k("Vol −", "vol_down"), k("Mute", "mute"), k("Menu", "menu"))
    private val LIGHT = listOf(k("Power", "power"), k("Color 1"), k("Color 2"), k("Color 3"), k("Color 4"), k("White"),
        k("Bright +", "bright_up"), k("Bright −", "bright_down"), k("On", "power_on"), k("Rainbow"), k("Off", "power_off"))
    private val ROBOT = mapOf(0 to k("Power", "power"), 1 to k("Forward", "up"), 2 to k("Backward", "down"),
        3 to k("Left", "left"), 4 to k("Right", "right"), 5 to k("Start", "play"), 6 to k("Stop", "stop"),
        8 to k("Auto"), 9 to k("Spot"), 10 to k("Suction", "speed"), 11 to k("Timer", "timer"), 12 to k("Dock"),
        13 to k("Schedule"))
    private val AIR_CLEANER = mapOf(0 to k("Power", "power"), 5 to k("Ionizer"), 8 to k("Auto"),
        9 to k("Speed", "speed"), 10 to k("Mode", "mode"), 11 to k("Timer", "timer"), 12 to k("Light", "light"))

    private fun List<Pair<String, String>>.byCode() = withIndex().associate { it.index to it.value }

    /** IRext category (REMOTE_CATEGORY_*) → key code → (label, canonical key). */
    val KEYS: Map<Int, Map<Int, Pair<String, String>>> = mapOf(
        2 to TV.byCode(), 3 to STB.byCode(), 4 to NETBOX.byCode(), 5 to STB.byCode(), 6 to DVD.byCode(),
        7 to FAN, 8 to PROJECTOR.byCode(), 9 to STEREO.byCode(), 10 to LIGHT.byCode(), 11 to STB.byCode(),
        12 to ROBOT, 13 to AIR_CLEANER, 14 to FAN,
    )

    fun load(context: Context, e: DeviceEntry): DeviceRemote? = runCatching {
        val file = e.file ?: return null
        val binary = context.assets.open("ir_irext/$file.bin").use { it.readBytes() }
        val names = KEYS[e.ic] ?: return null
        val buttons = e.k.mapNotNull { code ->
            val (label, key) = names[code] ?: return@mapNotNull null
            RemoteButton(label, key) {
                IrextNative.decodeButton(binary, e.ic, e.sub, code)?.let {
                    IrCodec.build(it.toList(), repeat = 0, frequencyHz = IrCodec.DEFAULT_FREQUENCY)
                }
            }
        }
        if (buttons.isEmpty()) null else DeviceRemote(e, buttons)
    }.getOrNull()
}
