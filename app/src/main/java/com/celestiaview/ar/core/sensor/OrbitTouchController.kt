package com.celestiaview.ar.core.sensor

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟星图模式的手势轨道控制器：
 * 拖拽平移视线中心、Fling 惯性衰减（指数阻尼）、双指 FOV 阻尼缩放、
 * 聚焦滑航（指数阻尼转向目标天体 + FOV 阻尼推进）。
 */
class OrbitTouchController {

    var centerAltDeg: Double = 35.0
        private set
    var centerAzDeg: Double = 180.0
        private set

    /** 垂直视场（度），钳制在 3°（深空望远镜）到 110°（超广角）。 */
    var fovYDeg: Double = 62.0
        private set

    // Fling 惯性速度（度/秒）
    private var velAz = 0.0
    private var velAlt = 0.0

    // 双指缩放的平滑目标
    private var targetFov = 62.0

    // 聚焦滑航目标（NaN = 无滑航）
    private var glideAlt = Double.NaN
    private var glideAz = Double.NaN

    /**
     * 拖拽一步。[degPerPx] 由调用方按当前 FOV 与屏幕高度换算。
     */
    fun onDrag(dxPx: Float, dyPx: Float, degPerPxY: Double, degPerPxX: Double) {
        centerAzDeg = wrapAz(centerAzDeg + dxPx * degPerPxX)
        centerAltDeg = clampAlt(centerAltDeg - dyPx * degPerPxY)
        // 用户接管视线：清除惯性与聚焦滑航
        velAz = 0.0
        velAlt = 0.0
        glideAlt = Double.NaN
        glideAz = Double.NaN
    }

    /** 记录甩动惯性（速度单位：px/s → 度/s）。 */
    fun onFling(vxPxPerSec: Float, vyPxPerSec: Float, degPerPxY: Double, degPerPxX: Double) {
        velAz = (vxPxPerSec * degPerPxX).toDouble().coerceIn(-400.0, 400.0)
        velAlt = (-vyPxPerSec * degPerPxY).toDouble().coerceIn(-400.0, 400.0)
    }

    /** 双指缩放：scale > 1 放大（FOV 减小）。带阻尼逼近目标值。 */
    fun onZoom(scaleFactor: Float) {
        targetFov = (targetFov / scaleFactor).coerceIn(MIN_FOV, MAX_FOV)
    }

    /** 聚焦：视线中心指数阻尼滑向目标，FOV 阻尼推进到 [fovDeg]。 */
    fun glideTo(altDeg: Double, azDeg: Double, fovDeg: Double) {
        glideAlt = clampAlt(altDeg)
        glideAz = wrapAz(azDeg)
        targetFov = fovDeg.coerceIn(MIN_FOV, MAX_FOV)
    }

    /** 仅改变 FOV 目标（退出聚焦回广角等）。 */
    fun zoomTo(fovDeg: Double) {
        targetFov = fovDeg.coerceIn(MIN_FOV, MAX_FOV)
    }

    /** 每帧推进：惯性衰减 + 聚焦滑航 + FOV 阻尼。返回是否有残余运动。 */
    fun update(dtSec: Double): Boolean {
        if (dtSec <= 0) return false
        val decay = exp(-dtSec / FLING_TAU)
        velAz *= decay
        velAlt *= decay
        if (abs(velAz) > 0.4 || abs(velAlt) > 0.4) {
            centerAzDeg = wrapAz(centerAzDeg + velAz * dtSec)
            centerAltDeg = clampAlt(centerAltDeg + velAlt * dtSec)
        } else {
            velAz = 0.0
            velAlt = 0.0
        }
        // 聚焦滑航：方位走最短弧
        if (!glideAlt.isNaN()) {
            val k = min(1.0, dtSec / GLIDE_TAU)
            val azErr = normalizeDeg180(glideAz - centerAzDeg)
            centerAzDeg = wrapAz(centerAzDeg + azErr * k)
            centerAltDeg = clampAlt(centerAltDeg + (glideAlt - centerAltDeg) * k)
            if (abs(azErr) < 0.02 && abs(glideAlt - centerAltDeg) < 0.02) {
                glideAlt = Double.NaN
                glideAz = Double.NaN
            }
        }
        val fovGap = targetFov - fovYDeg
        if (abs(fovGap) > 0.01) {
            fovYDeg += fovGap * min(1.0, dtSec * 8.0)
        } else {
            fovYDeg = targetFov
        }
        return velAz != 0.0 || velAlt != 0.0 || !glideAlt.isNaN() || abs(fovGap) > 0.01
    }

    /** 寻星引导：把视线中心平滑转向目标。 */
    fun lookTowards(altDeg: Double, azDeg: Double) {
        // 直接跳转（由引导箭头完成渐进），仅设定中心
        centerAltDeg = clampAlt(altDeg)
        centerAzDeg = wrapAz(azDeg)
        targetFov = fovYDeg
    }

    fun resetFovTarget() {
        targetFov = fovYDeg
    }

    private fun wrapAz(az: Double): Double {
        var a = az % 360.0
        if (a < 0) a += 360.0
        return a
    }

    private fun normalizeDeg180(a: Double): Double {
        var x = a % 360.0
        if (x > 180.0) x -= 360.0
        if (x < -180.0) x += 360.0
        return x
    }

    private fun clampAlt(alt: Double): Double = max(-89.5, min(89.5, alt))

    companion object {
        const val MIN_FOV = 3.0
        const val MAX_FOV = 110.0
        const val FLING_TAU = 0.22

        /** 聚焦滑航时间常数（秒）。 */
        const val GLIDE_TAU = 0.28
    }
}
