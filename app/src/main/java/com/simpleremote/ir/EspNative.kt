package com.simpleremote.ir

/** Native IRremoteESP8266 A/C encoder (libirac). Calls are serialised: the capture buffer is global. */
object EspNative {
    // stdAc enum values (IRsend.h)
    const val MODE_AUTO = 0
    const val MODE_COOL = 1
    const val MODE_HEAT = 2
    const val MODE_DRY = 3
    const val MODE_FAN = 4
    const val FAN_AUTO = 0
    const val FAN_LOW = 2
    const val FAN_MEDIUM = 3
    const val FAN_HIGH = 4
    const val SWING_OFF = -1
    const val SWING_AUTO = 0

    private val loaded: Boolean = runCatching { System.loadLibrary("irac") }.isSuccess

    @JvmStatic
    private external fun encodeAc(
        protocol: Int, model: Int, power: Int, mode: Int, temp: Int, fan: Int, swingv: Int,
        prevPower: Int, prevSwingv: Int,
    ): IntArray?

    /** Carrier (Hz) and signed µs timings (+mark / -space), or null if unsupported. */
    fun encode(
        protocol: Int, model: Int, power: Boolean, mode: Int, temp: Int, fan: Int, swingv: Int,
        prevPower: Boolean, prevSwingv: Int,
    ): Pair<Int, IntArray>? {
        if (!loaded) return null
        val r = synchronized(this) {
            encodeAc(protocol, model, if (power) 1 else 0, mode, temp, fan, swingv, if (prevPower) 1 else 0, prevSwingv)
        } ?: return null
        return r[0] to r.copyOfRange(1, r.size)
    }
}
