package com.simpleremote.ir

/** A ready-to-send IR signal: carrier frequency + alternating on/off durations in microseconds. */
class IrSignal(val frequencyHz: Int, val pattern: IntArray) {
    val durationMicros: Long get() = pattern.fold(0L) { acc, v -> acc + v }
}

/**
 * Decodes the IR code formats found in the bundled databases into [IrSignal]s
 * suitable for android.hardware.ConsumerIrManager.transmit().
 */
object IrCodec {
    const val DEFAULT_FREQUENCY = 38_000

    /** Android's ConsumerIrService rejects patterns longer than 2 seconds. */
    const val MAX_PATTERN_MICROS = 2_000_000L

    /** Broadlink time unit: 2^-15 s ≈ 30.52 µs (269/8192 ticks per µs as used by python-broadlink). */
    private const val BROADLINK_US_PER_TICK = 8192.0 / 269.0

    private const val MAX_TRAILING_GAP = 100_000 // µs
    private const val REPEAT_GAP = 40_000 // µs

    /**
     * Broadlink packet layout:
     *   [0]    0x26 = IR (0xb2/0xd7 are RF and unsupported)
     *   [1]    repeat count (0 = send once)
     *   [2..3] payload length, little endian
     *   then pulse lengths, 1 byte each; 0x00 means the next 2 bytes are a big-endian value.
     *   The payload normally ends with 0x00 0x0d 0x05 (a ~100 ms trailing gap).
     */
    fun decodeBroadlink(base64: String): IrSignal {
        val bytes = Base64Lite.decode(base64)
        require(bytes.size >= 4) { "Broadlink code too short" }
        val type = bytes[0].toInt() and 0xff
        require(type == 0x26) { "Not an IR Broadlink code (type 0x${type.toString(16)})" }
        val repeat = bytes[1].toInt() and 0xff
        val length = (bytes[2].toInt() and 0xff) or ((bytes[3].toInt() and 0xff) shl 8)
        val end = minOf(bytes.size, 4 + length)

        val ticks = ArrayList<Int>(length)
        var i = 4
        while (i < end) {
            var v = bytes[i++].toInt() and 0xff
            if (v == 0) {
                if (i + 1 >= bytes.size) break
                v = ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
                i += 2
            }
            ticks += v
        }
        val micros = ticks.map { (it * BROADLINK_US_PER_TICK).toInt() }
        return build(micros, repeat, DEFAULT_FREQUENCY)
    }

    /** ESPHome/raw format: signed µs values, positive = mark (on), negative = space (off). */
    fun decodeRaw(values: List<Int>, frequencyHz: Int = DEFAULT_FREQUENCY): IrSignal {
        val firstMark = values.indexOfFirst { it > 0 }
        require(firstMark >= 0) { "Raw code contains no marks" }
        // Merge consecutive same-sign values so on/off strictly alternate.
        val merged = ArrayList<Int>()
        var lastSign = 0
        for (v in values.drop(firstMark)) {
            if (v == 0) continue
            val sign = if (v > 0) 1 else -1
            if (sign == lastSign) merged[merged.lastIndex] += kotlin.math.abs(v)
            else merged += kotlin.math.abs(v)
            lastSign = sign
        }
        return build(merged, 0, frequencyHz)
    }

    /** Clean up a mark/space list and expand repeats while staying under the 2 s HAL limit. */
    internal fun build(microsIn: List<Int>, repeat: Int, frequencyHz: Int): IrSignal {
        val frame = microsIn.map { it.coerceAtLeast(1) }.toMutableList()
        require(frame.isNotEmpty()) { "Empty IR code" }
        // Trailing space (even length) is only useful as an inter-frame gap; cap it.
        if (frame.size % 2 == 0) frame[frame.lastIndex] = frame.last().coerceAtMost(MAX_TRAILING_GAP)

        val out = ArrayList<Int>(frame.size * (repeat + 1))
        var total = 0L
        for (r in 0..repeat) {
            val next = ArrayList<Int>(frame.size + 1)
            if (out.isNotEmpty() && out.size % 2 == 1) next += REPEAT_GAP // need a space before the next mark
            next += frame
            val nextTotal = total + next.sumOf { it.toLong() }
            if (out.isNotEmpty() && nextTotal > MAX_PATTERN_MICROS) break
            out += next
            total = nextTotal
        }
        // Drop a final trailing space: it only delays the next transmission.
        if (out.size % 2 == 0 && out.size > 1) out.removeAt(out.lastIndex)
        return IrSignal(frequencyHz, out.toIntArray())
    }
}

/** Minimal Base64 decoder (android.util.Base64 is unavailable in JVM unit tests, java.util.Base64 needs API 26). */
internal object Base64Lite {
    private val table = IntArray(128) { -1 }.also { t ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> t[c.code] = i }
        t['-'.code] = 62; t['_'.code] = 63 // URL-safe variant
    }

    fun decode(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length * 3 / 4)
        var buf = 0
        var bits = 0
        for (c in s) {
            if (c == '=') break
            val v = if (c.code < 128) table[c.code] else -1
            if (v < 0) continue // skip whitespace / junk
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}
