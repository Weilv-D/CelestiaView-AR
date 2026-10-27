package com.celestiaview.ar.core.media

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 高精度触觉反馈：准星掠星轻颤、目标锁定重击、时间滚轮刻度轻点。
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val hasVibrator: Boolean get() = vibrator.hasVibrator()

    /** 准星掠过亮星：羽毛级轻颤。 */
    fun starGlance() {
        if (!hasVibrator) return
        vibrate(12, 120)
    }

    /** 锁定天体：单次干脆重击。 */
    fun lockPulse() {
        if (!hasVibrator) return
        when {
            // 原语组合 API 30（R）起可用；PRIMITIVE_THUD 需 31（S）
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 40)
                        .compose()
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                        .compose()
                )
            }
            else -> vibrate(38, 200)
        }
    }

    /** 时间滚轮刻度：TextHandleMove 级微点。 */
    fun timeTick() {
        if (!hasVibrator) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            vibrate(8, 90)
        }
    }

    /** 仪式感长震（如拍照快门）。 */
    fun shutter() {
        if (!hasVibrator) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            )
        } else {
            vibrate(50, 255)
        }
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        // minSdk 26（O）：VibrationEffect 恒可用，无需版本分支
        vibrator.vibrate(
            VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255))
        )
    }
}
