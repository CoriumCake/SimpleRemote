package com.simpleremote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IrProtocolsTest {
    /** Reads back a pulse-distance frame (MSB-first as printed by most IR tools). */
    private fun pulseDistanceValue(t: List<Int>, bits: Int): Long {
        var v = 0L
        for (i in 0 until bits) {
            val space = t[3 + 2 * i]
            v = (v shl 1) or (if (space > 1000) 1L else 0L)
        }
        return v
    }

    @Test
    fun necMatchesKnownLgPowerCode() {
        // LG TV power: NEC address 0x04, command 0x08 == 0x20DF10EF in the usual MSB-first notation.
        val f = IrProtocols.encode("NEC", 0x04, 0x08)!!
        assertEquals(0x20DF10EFL, pulseDistanceValue(f.timings, 32))
        assertEquals(9000, f.timings[0]); assertEquals(4500, f.timings[1])
        assertEquals(3 + 64, f.timings.size)
    }

    @Test
    fun samsung32MatchesKnownPowerCode() {
        // Samsung TV power: address 0x07, command 0x02 == 0xE0E040BF.
        val f = IrProtocols.encode("Samsung32", 0x07, 0x02)!!
        assertEquals(0xE0E040BFL, pulseDistanceValue(f.timings, 32))
    }

    @Test
    fun everyProtocolProducesValidFrames() {
        for (p in IrProtocols.supported) {
            val f = IrProtocols.encode(p, 0x1A, 0x15)
            assertNotNull(p, f)
            f!!
            assertTrue(p, f.timings.size % 2 == 1 && f.timings.all { it > 0 })
            assertTrue(p, f.timings.sum() < 2_000_000)
        }
    }

    /**
     * Writes frames for the host round-trip check:
     *   ./gradlew testDebugUnitTest && tools/ir_decode < app/build/ir_frames.txt
     */
    @Test
    fun dumpFramesForHostDecoder() {
        val cases = listOf(
            Triple("NEC", 0x04L, 0x08L), Triple("NECext", 0x7F00L, 0xEA15L), Triple("Samsung32", 0x07L, 0x02L),
            Triple("RC5", 0x00L, 0x0CL), Triple("RC5X", 0x05L, 0x45L), Triple("RC6", 0x00L, 0x0CL),
            Triple("SIRC", 0x01L, 0x15L), Triple("SIRC15", 0x97L, 0x15L), Triple("SIRC20", 0x10E7L, 0x15L),
            Triple("Kaseikyo", 0x02002080L, 0x3D0L), Triple("RCA", 0x0FL, 0x54L), Triple("Pioneer", 0xAAL, 0x1CL),
            Triple("NEC42", 0x1C3L, 0x12L),
        )
        val out = File("build/ir_frames.txt")
        out.parentFile.mkdirs()
        out.writeText(cases.joinToString("\n") { (p, a, c) ->
            "$p:${a.toString(16)}:${c.toString(16)} " + IrProtocols.encode(p, a, c)!!.timings.joinToString(" ")
        } + "\n")
    }
}
