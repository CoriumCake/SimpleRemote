package com.simpleremote.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrCodecTest {
    @Test
    fun base64MatchesJdk() {
        val bytes = ByteArray(300) { (it * 37 + 11).toByte() }
        val enc = java.util.Base64.getEncoder().encodeToString(bytes)
        assertArrayEquals(bytes, Base64Lite.decode(enc))
        assertArrayEquals(bytes, Base64Lite.decode(enc.chunked(60).joinToString("\n")))
    }

    @Test
    fun broadlinkDecodesNecStyleLeader() {
        // Header 0x26 (IR), repeat 0, payload: 0x00 0x01 0x28 (296 ticks ≈ 9014 µs), 0x93 (147 ticks ≈ 4477 µs),
        // 0x15 (21 ticks ≈ 640 µs), then trailer 0x00 0x0d 0x05.
        val payload = byteArrayOf(0x00, 0x01, 0x28, 0x93.toByte(), 0x15, 0x00, 0x0d, 0x05)
        val pkt = byteArrayOf(0x26, 0x00, payload.size.toByte(), 0x00) + payload
        val sig = IrCodec.decodeBroadlink(java.util.Base64.getEncoder().encodeToString(pkt))
        // Trailing gap is dropped, leaving mark/space/mark.
        assertEquals(3, sig.pattern.size)
        assertTrue(sig.pattern[0] in 8900..9100)
        assertTrue(sig.pattern[1] in 4400..4550)
        assertTrue(sig.pattern[2] in 600..680)
        assertEquals(38_000, sig.frequencyHz)
    }

    @Test
    fun broadlinkRepeatAddsGapBetweenFrames() {
        val payload = byteArrayOf(0x20, 0x20, 0x20) // mark, space, mark
        val pkt = byteArrayOf(0x26, 0x02, payload.size.toByte(), 0x00) + payload
        val sig = IrCodec.decodeBroadlink(java.util.Base64.getEncoder().encodeToString(pkt))
        // 3 frames of 3 values + 2 inter-frame gaps
        assertEquals(11, sig.pattern.size)
    }

    @Test
    fun rawDropsLeadingSpaceAndMergesSameSign() {
        val sig = IrCodec.decodeRaw(listOf(-500, 9000, -4500, -100, 560, 0, -560, 560))
        assertArrayEquals(intArrayOf(9000, 4600, 560, 560, 560), sig.pattern)
    }

    @Test
    fun patternIsCappedAtTwoSeconds() {
        val frame = List(99) { 10_000 } // ~0.99 s
        val sig = IrCodec.build(frame, repeat = 5, frequencyHz = 38_000)
        assertTrue(sig.durationMicros <= IrCodec.MAX_PATTERN_MICROS)
        assertTrue(sig.pattern.size % 2 == 1)
    }
}
