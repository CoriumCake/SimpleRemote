package com.simpleremote.ir

/**
 * Encoders for the common consumer IR protocols, in the address/command convention used by
 * Flipper-IRDB files (address and command as little-endian integers). Timings and bit layouts
 * follow the Flipper Zero infrared library; frames are verified on the host by decoding them
 * with IRremoteESP8266 (tools/ir_decode.cpp).
 *
 * Every function returns alternating µs durations starting with a mark (no trailing gap).
 */
object IrProtocols {
    data class Frame(val frequency: Int, val timings: List<Int>)

    val supported = setOf(
        "NEC", "NECext", "NEC42", "NEC42ext", "Samsung32", "RC5", "RC5X", "RC6",
        "SIRC", "SIRC15", "SIRC20", "Kaseikyo", "RCA", "Pioneer",
    )

    fun encode(protocol: String, address: Long, command: Long): Frame? = when (protocol) {
        "NEC" -> nec(bitsLsb(listOf(
            address and 0xFF to 8, address.inv() and 0xFF to 8, command and 0xFF to 8, command.inv() and 0xFF to 8,
        )))
        "NECext" -> nec(bitsLsb(listOf(address and 0xFFFF to 16, command and 0xFFFF to 16)))
        "NEC42" -> nec(bitsLsb(listOf(
            address and 0x1FFF to 13, address.inv() and 0x1FFF to 13, command and 0xFF to 8, command.inv() and 0xFF to 8,
        )))
        "NEC42ext" -> nec(bitsLsb(listOf(address and 0x3FFFFFF to 26, command and 0xFFFF to 16)))
        "Samsung32" -> pulseDistance(
            38000, 4500, 4500, 550, 1650, 550, 550,
            bitsLsb(listOf(address and 0xFF to 8, address and 0xFF to 8, command and 0xFF to 8, command.inv() and 0xFF to 8)),
        )
        "RC5", "RC5X" -> rc5(protocol == "RC5X", address, command)
        "RC6" -> rc6(address, command)
        "SIRC" -> sirc(bitsLsb(listOf(command and 0x7F to 7, address and 0x1F to 5)))
        "SIRC15" -> sirc(bitsLsb(listOf(command and 0x7F to 7, address and 0xFF to 8)))
        "SIRC20" -> sirc(bitsLsb(listOf(command and 0x7F to 7, address and 0x1FFF to 13)))
        "Kaseikyo" -> kaseikyo(address, command)
        "RCA" -> pulseDistance(
            38000, 4000, 4000, 500, 2000, 500, 1000,
            bitsLsb(listOf(address and 0xF to 4, command and 0xFF to 8, address.inv() and 0xF to 4, command.inv() and 0xFF to 8)),
        )
        "Pioneer" -> {
            // 32 data bits + a zero 33rd bit, sent twice 26 ms apart.
            val one = pulseDistance(
                40000, 8500, 4225, 500, 1500, 500, 500,
                bitsLsb(listOf(address and 0xFF to 8, address.inv() and 0xFF to 8, command and 0xFF to 8, command.inv() and 0xFF to 8)) + false,
            ).timings
            Frame(40000, one + listOf(26000) + one)
        }
        else -> null
    }

    private fun bitsLsb(fields: List<Pair<Long, Int>>): List<Boolean> =
        fields.flatMap { (value, n) -> (0 until n).map { (value shr it) and 1L == 1L } }

    private fun pulseDistance(
        freq: Int, headMark: Int, headSpace: Int, oneMark: Int, oneSpace: Int, zeroMark: Int, zeroSpace: Int,
        bits: List<Boolean>, stopMark: Int = zeroMark,
    ): Frame {
        val t = ArrayList<Int>(bits.size * 2 + 3)
        t += headMark; t += headSpace
        for (b in bits) { t += if (b) oneMark else zeroMark; t += if (b) oneSpace else zeroSpace }
        t += stopMark
        return Frame(freq, t)
    }

    private fun nec(bits: List<Boolean>) = pulseDistance(38000, 9000, 4500, 560, 1690, 560, 560, bits)

    /** Sony: pulse-width bits, sent 3 times with a 45 ms frame period. */
    private fun sirc(bits: List<Boolean>): Frame {
        val one = ArrayList<Int>()
        one += 2400
        for (b in bits) { one += 600; one += if (b) 1200 else 600 }
        val frameLen = one.sum()
        val out = ArrayList<Int>()
        repeat(3) { i ->
            if (i > 0) out += 45000 - frameLen
            out += one
        }
        return Frame(40000, out)
    }

    private fun kaseikyo(address: Long, command: Long): Frame {
        val id = ((address shr 24) and 3).toInt()
        val vendor = ((address shr 8) and 0xFFFF).toInt()
        val genre1 = ((address shr 4) and 0xF).toInt()
        val genre2 = (address and 0xF).toInt()
        val cmd = command.toInt()
        val d = IntArray(6)
        d[0] = vendor and 0xFF
        d[1] = vendor shr 8
        var parity = d[0] xor d[1]
        parity = (parity and 0xF) xor (parity shr 4)
        d[2] = (parity and 0xF) or (genre1 shl 4)
        d[3] = (genre2 and 0xF) or ((cmd and 0xF) shl 4)
        d[4] = ((id shl 6) or (cmd shr 4)) and 0xFF
        d[5] = d[2] xor d[3] xor d[4]
        val u = 432
        return pulseDistance(38000, 8 * u, 4 * u, u, 3 * u, u, u, bitsLsb(d.map { it.toLong() to 8 }))
    }

    /** Manchester halves as levels (true = mark), merged into durations; leading space dropped. */
    private fun manchester(levels: List<Pair<Boolean, Int>>): List<Int> {
        val out = ArrayList<Int>()
        var cur: Boolean? = null
        for ((mark, d) in levels) {
            if (out.isEmpty() && !mark) continue
            if (mark == cur) out[out.lastIndex] += d else { out += d; cur = mark }
        }
        if (cur == false) out.removeAt(out.lastIndex)
        return out
    }

    /** Philips RC5: 1 = space→mark, 0 = mark→space, 889 µs halves, MSB first. */
    private fun rc5(extended: Boolean, address: Long, command: Long): Frame {
        val bits = ArrayList<Boolean>()
        bits += true
        bits += !extended // field bit = inverted command bit 6
        bits += false // toggle
        for (i in 4 downTo 0) bits += (address shr i) and 1L == 1L
        for (i in 5 downTo 0) bits += (command shr i) and 1L == 1L
        val h = 889
        return Frame(36000, manchester(bits.flatMap { if (it) listOf(false to h, true to h) else listOf(true to h, false to h) }))
    }

    /** Philips RC6 mode 0: leader 2666/889, 1 = mark→space, 444 µs halves, double-width toggle bit. */
    private fun rc6(address: Long, command: Long): Frame {
        val h = 444
        val levels = ArrayList<Pair<Boolean, Int>>()
        levels += true to 2666; levels += false to 889
        fun bit(v: Boolean, w: Int = h) { if (v) { levels += true to w; levels += false to w } else { levels += false to w; levels += true to w } }
        bit(true) // start
        repeat(3) { bit(false) } // mode 0
        bit(false, 2 * h) // toggle
        for (i in 7 downTo 0) bit((address shr i) and 1L == 1L)
        for (i in 7 downTo 0) bit((command shr i) and 1L == 1L)
        return Frame(36000, manchester(levels))
    }
}
