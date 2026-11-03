package pl.aquadeck

import android.media.audiofx.Visualizer
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10

/**
 * Reads the phone's overall audio output spectrum (Visualizer on session 0).
 * Some phones / apps return silence here; then nothing is sent and the page
 * falls back to its simulated beat.
 */
class VizBridge(private val js: (String) -> Unit) {
    private var viz: Visualizer? = null
    private val bands = 16

    fun start(): Boolean {
        if (viz != null) return true
        return try {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val rate = minOf(Visualizer.getMaxCaptureRate(), 20000)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, wave: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft != null) handle(fft)
                }
            }, rate, false, true)
            v.enabled = true
            viz = v
            true
        } catch (e: Throwable) {
            viz = null
            false
        }
    }

    fun stop() {
        try { viz?.enabled = false; viz?.release() } catch (_: Throwable) {}
        viz = null
    }

    private fun handle(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 4) return
        val out = DoubleArray(bands)
        var sum = 0.0
        val maxBin = n - 1
        for (b in 0 until bands) {
            val lo = exp(ln(maxBin.toDouble()) * b / bands).toInt().coerceAtLeast(1)
            val hi = exp(ln(maxBin.toDouble()) * (b + 1) / bands).toInt().coerceIn(lo + 1, n)
            var peak = 0.0
            for (k in lo until hi) {
                val re = fft[2 * k].toDouble()
                val im = fft[2 * k + 1].toDouble()
                val m = hypot(re, im)
                if (m > peak) peak = m
            }
            val db = 20 * log10(1 + peak)
            val v = ((db - 8) / 34).coerceIn(0.0, 1.0)
            out[b] = v
            sum += v
        }
        if (sum < 0.15) return  // silent / blocked: let the page fake it
        js("HeadUnit.setLevels([" + out.joinToString(",") { "%.3f".format(java.util.Locale.US, it) } + "])")
    }
}
