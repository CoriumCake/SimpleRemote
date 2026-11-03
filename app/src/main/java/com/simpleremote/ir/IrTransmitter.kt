package com.simpleremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin wrapper over the platform ConsumerIrManager, which is what Xiaomi, Redmi, POCO,
 * Huawei/Honor, Vivo, Realme, TCL, older Samsung/LG/HTC, etc. expose for their IR blasters.
 */
class IrTransmitter(context: Context) {
    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val lock = Mutex()

    val hasEmitter: Boolean = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    /** Sends one or more signals back-to-back. transmit() blocks for the signal duration, so run off the main thread. */
    suspend fun send(vararg signals: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val m = manager
                check(m != null && hasEmitter) { "This phone has no IR blaster" }
                signals.forEachIndexed { i, s ->
                    // ACs ignore a second frame that follows too closely (SmartIR uses 0.5 s too).
                    if (i > 0) delay(MULTI_SIGNAL_GAP_MS)
                    val f = pickFrequency(m, s.frequencyHz)
                    android.util.Log.d("SimpleRemoteIR", "transmit f=$f n=${s.pattern.size} us=${s.durationMicros} ranges=${m.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency}" }} pattern=${s.pattern.joinToString(",")}")
                    m.transmit(f, s.pattern)
                }
            }
        }
    }

    private companion object { const val MULTI_SIGNAL_GAP_MS = 500L }

    private fun pickFrequency(m: ConsumerIrManager, wanted: Int): Int {
        val ranges = runCatching { m.carrierFrequencies }.getOrNull()
        if (ranges.isNullOrEmpty()) return wanted
        if (ranges.any { wanted in it.minFrequency..it.maxFrequency }) return wanted
        // Clamp to the closest supported frequency.
        return ranges.flatMap { listOf(it.minFrequency, it.maxFrequency) }
            .minBy { kotlin.math.abs(it - wanted) }
    }
}
