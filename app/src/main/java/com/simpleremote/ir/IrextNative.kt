package com.simpleremote.ir

/** Native IRext AC decoder. All calls are serialised because the C library keeps global state. */
object IrextNative {
    // IRext enum values (ir_ac_control.h / ir_decode.h)
    const val POWER_ON = 0
    const val POWER_OFF = 1
    const val MODE_COOL = 0
    const val MODE_HEAT = 1
    const val MODE_AUTO = 2
    const val MODE_FAN = 3
    const val MODE_DRY = 4
    const val SPEED_AUTO = 0
    const val SPEED_LOW = 1
    const val SPEED_MEDIUM = 2
    const val SPEED_HIGH = 3
    const val SWING_ON = 0
    const val SWING_OFF = 1
    const val KEY_POWER = 0
    const val KEY_MODE = 1
    const val KEY_TEMP_UP = 2
    const val KEY_TEMP_DOWN = 3
    const val KEY_WIND_SPEED = 9
    const val KEY_WIND_SWING = 10
    const val KEY_WIND_FIX = 11

    private val loaded: Boolean = runCatching { System.loadLibrary("irext") }.isSuccess

    @JvmStatic
    private external fun decodeAc(binary: ByteArray, key: Int, power: Int, mode: Int, temp: Int, speed: Int, swing: Int): IntArray?

    /** Returns raw µs timings, or null if the library is missing or the state has no code. */
    fun decode(binary: ByteArray, key: Int, power: Int, mode: Int, tempIndex: Int, speed: Int, swing: Int): IntArray? {
        if (!loaded) return null
        return synchronized(this) { decodeAc(binary, key, power, mode, tempIndex, speed, swing) }
    }

    @JvmStatic
    private external fun decodeKey(binary: ByteArray, category: Int, sub: Int, key: Int): IntArray?

    /** Non-AC remote key (TV, fan, ...) → raw µs timings, or null. */
    fun decodeButton(binary: ByteArray, category: Int, sub: Int, key: Int): IntArray? {
        if (!loaded) return null
        return synchronized(this) { decodeKey(binary, category, sub, key) }
    }
}
