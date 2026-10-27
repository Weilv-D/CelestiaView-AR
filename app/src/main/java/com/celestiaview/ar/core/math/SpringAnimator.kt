package com.celestiaview.ar.core.math

/**
 * 二阶临界阻尼胡克弹簧相机（纯 Kotlin，JVM 单测覆盖）：
 *
 *     a = ω₀²(x_target − x) − 2ζω₀v
 *     v' = v + a·Δt,  x' = x + v'·Δt
 *
 * 默认 ω₀ = 8.5 rad/s、ζ = 1.0（临界阻尼）：加速柔和、减速丝滑，
 * 绝无机械顿挫或过冲震荡。积分按最大 16ms 子步切分，掉帧时仍保持
 * 数值稳定；收敛阈值由 [settleEps] 与速度阈值共同判定。
 */
class SpringAnimator(
    val omega0: Double = 8.5,
    val zeta: Double = 1.0
) {
    /** 当前位置（ 角度 / 视场等任意标量）。 */
    var x: Double = 0.0

    /** 目标位置。 */
    var target: Double = 0.0

    private var v: Double = 0.0

    /** 收敛位置阈值。 */
    var settleEps: Double = 0.01
    /** 收敛速度阈值（单位/秒）。 */
    var settleVelEps: Double = 0.5

    /** 立即跳到目标并清零速度（不做动画）。 */
    fun jumpTo(value: Double) {
        x = value
        target = value
        v = 0.0
    }

    /** 仅改变速度（惯性滑动接管等）。 */
    fun setVelocity(value: Double) { v = value }

    val velocity: Double get() = v

    /**
     * 推进一步（秒）。返回 true 表示仍在运动（未收敛）。
     * 大 Δt 切分为 ≤16ms 子步，避免长帧爆炸；长帧完整推进不截断。
     */
    fun update(dtSec: Double): Boolean {
        if (dtSec <= 0.0) return false
        var remaining = dtSec
        val w2 = omega0 * omega0
        val damp = 2.0 * zeta * omega0
        while (remaining > 0.0) {
            val h = minOf(remaining, 0.016)
            val a = w2 * (target - x) - damp * v
            v += a * h
            x += v * h
            remaining -= h
        }
        val settled = kotlin.math.abs(target - x) < settleEps &&
            kotlin.math.abs(v) < settleVelEps
        if (settled) {
            x = target
            v = 0.0
            return false
        }
        return true
    }

    /** 是否已收敛在目标上。 */
    val settled: Boolean
        get() = kotlin.math.abs(target - x) < settleEps && kotlin.math.abs(v) < settleVelEps
}
