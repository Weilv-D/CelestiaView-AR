package com.celestiaview.ar.core.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 程序化空灵音频引擎：
 * - 深空背景音：C 大调九和弦 (Cmaj9) 五声部正弦泛音叠加，每声部独立慢速呼吸 LFO 与微失谐拍频；
 * - 天体锁定提示：水晶泛音簇 (C6/G6/C7) 指数衰减包络 (ADSR-Release)。
 * 全部实时合成写入 AudioTrack，零采样素材。
 */
class ProceduralSoundEngine {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var running = false

    private var audioTrack: AudioTrack? = null
    private var synthThread: Thread? = null

    private val sampleRate = 44100

    // 大九和弦声部：频率(Hz)、增益、声像(-1..1)、LFO 速率、失谐
    private data class Voice(
        val freq: Double, val gain: Float, val pan: Float,
        val lfoHz: Double, val detuneHz: Double
    )

    private val voices = listOf(
        Voice(65.41, 0.30f, -0.6f, 0.030, 0.06),   // C2 根音
        Voice(98.00, 0.24f, 0.6f, 0.041, -0.05),   // G2 纯五
        Voice(164.81, 0.18f, -0.35f, 0.057, 0.04), // E3 大三
        Voice(246.94, 0.13f, 0.35f, 0.073, -0.03), // B3 大七
        Voice(293.66, 0.10f, 0.0f, 0.089, 0.025)   // D4 大九
    )

    private class Chime(val startTimeSec: Double) {
        val freqs = doubleArrayOf(1046.50, 1568.00, 2093.00) // C6 G6 C7
        val gains = floatArrayOf(0.28f, 0.18f, 0.12f)
        val durationSec = 1.35
    }

    private val pendingChimes = ConcurrentLinkedQueue<Chime>()
    private var synthTimeSec = 0.0

    fun start() {
        if (running) return
        running = true
        synthThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runSynthLoop()
        }, "CelestiaSynth").also { it.start() }
    }

    fun stop() {
        running = false
        // 先 stop 音轨：令合成线程阻塞中的 WRITE_BLOCKING 立刻返回，
        // join 只需等待数毫秒，杜绝主线程（onCleared）被阻塞数百毫秒的 ANR 风险
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
        }
        synthThread?.join(300)
        synthThread = null
        audioTrack?.let {
            try {
                it.release()
            } catch (_: IllegalStateException) {
            }
        }
        audioTrack = null
        pendingChimes.clear()
    }

    fun playLockChime() {
        if (!enabled || !running) return
        pendingChimes.add(Chime(synthTimeSec))
    }

    val isRunning: Boolean get() = running

    private fun ensureTrack(): AudioTrack {
        audioTrack?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            maxOf(minBuf, sampleRate), // ≥0.5s 缓冲
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
        audioTrack = track
        return track
    }

    private fun runSynthLoop() {
        val track = try {
            ensureTrack()
        } catch (e: Exception) {
            running = false
            return
        }
        val frames = 2048
        val buffer = FloatArray(frames * 2)
        val masterFadeSec = 3.0

        while (running) {
            for (i in 0 until frames) {
                val t = synthTimeSec
                val fadeIn = minOf(1.0, t / masterFadeSec).toFloat().coerceAtLeast(
                    0f
                )
                var left = 0f
                var right = 0f

                if (enabled) {
                    // —— 大九和弦 drone
                    for ((vi, v) in voices.withIndex()) {
                        val lfo = 0.72 + 0.28 * sin(2 * PI * v.lfoHz * t + vi * 1.7)
                        val beat = sin(2 * PI * (v.freq + v.detuneHz) * t + vi * 0.9)
                        val env = (lfo * beat * v.gain * fadeIn).toFloat()
                        left += env * (1f - maxOf(0f, v.pan))
                        right += env * (1f + minOf(0f, v.pan))
                    }
                }

                // —— 泛音锁定音（指数衰减）
                val it2 = pendingChimes.iterator()
                while (it2.hasNext()) {
                    val ch = it2.next()
                    val age = t - ch.startTimeSec
                    if (age < 0) continue
                    if (age > ch.durationSec) {
                        it2.remove()
                        continue
                    }
                    if (!enabled) continue
                    val env = exp(-age * 4.2).toFloat() *
                        minOf(1.0, age / 0.006).toFloat() // 极短 attack + 长衰减
                    for ((fi, f) in ch.freqs.withIndex()) {
                        val vib = 1.0 + 0.0015 * sin(2 * PI * 5.2 * age)
                        val s = (sin(2 * PI * f * vib * age) * env * ch.gains[fi] * 0.9).toFloat()
                        left += s * 0.8f
                        right += s * 0.8f
                    }
                }

                buffer[i * 2] = left.coerceIn(-1f, 1f)
                buffer[i * 2 + 1] = right.coerceIn(-1f, 1f)
                synthTimeSec += 1.0 / sampleRate
            }
            try {
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            } catch (_: Exception) {
                break
            }
        }
    }
}
