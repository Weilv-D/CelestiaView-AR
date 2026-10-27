package com.celestiaview.ar.core.sensor

import com.celestiaview.ar.core.math.SpringAnimator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟星图模式的手势轨道控制器：
 * 拖拽平移视线中心、Fling 惯性衰减（指数阻尼）、双指 FOV 弹簧缩放、
 * 聚焦滑航（二阶临界阻尼弹簧转向目标天体 + 视场临界阻尼推进）。
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

    // 聚焦滑航与视场的临界阻尼弹簧（ω₀=8.5, ζ=1.0：柔和加速、丝滑减速、零过冲）
    private val azSpring = SpringAnimator()
    private val altSpring = SpringAnimator()
    private val fovSpring = SpringAnimator(omega0 = 7.0)

    init {
        azSpring.jumpTo(centerAzDeg)
        altSpring.jumpTo(centerAltDeg)
        fovSpring.jumpTo(fovYDeg)
    }

    /**
     * 拖拽一步。[degPerPx] 由调用方按 degPerPx = fovY / 视口高度换算——
     * 手势转角随视场线性降档（scale = fov/62），6° 深空微距下单像素
     * 对应天球转角仅为 62° 广角的约 1/10，长焦构图自然放缓。
     */
    fun onDrag(dxPx: Float, dyPx: Float, degPerPxY: Double, degPerPxX: Double) {
        centerAzDeg = wrapAz(centerAzDeg + dxPx * degPerPxX)
        centerAltDeg = clampAlt(centerAltDeg - dyPx * degPerPxY)
        // 用户接管视线：清除惯性与聚焦滑航
        velAz = 0.0
        velAlt = 0.0
        azSpring.jumpTo(centerAzDeg)
        altSpring.jumpTo(centerAltDeg)
    }

    /** 记录甩动惯性（速度单位：px/s → 度/s）。 */
    fun onFling(vxPxPerSec: Float, vyPxPerSec: Float, degPerPxY: Double, degPerPxX: Double) {
        velAz = (vxPxPerSec * degPerPxX).toDouble().coerceIn(-400.0, 400.0)
        velAlt = (-vyPxPerSec * degPerPxY).toDouble().coerceIn(-400.0, 400.0)
    }

    /** 双指缩放：scale > 1 放大（FOV 减小）。临界阻尼弹簧逼近目标值。 */
    fun onZoom(scaleFactor: Float) {
        val target = (targetFov / scaleFactor).coerceIn(MIN_FOV, MAX_FOV)
        setFovTarget(target)
    }

    /** 聚焦：视线中心临界阻尼弹簧滑向目标（方位走最短弧），FOV 弹簧推进。 */
    fun glideTo(altDeg: Double, azDeg: Double, fovDeg: Double) {
        azSpring.jumpTo(centerAzDeg)
        azSpring.target = centerAzDeg + normalizeDeg180(wrapAz(azDeg) - centerAzDeg)
        altSpring.jumpTo(centerAltDeg)
        altSpring.target = clampAlt(altDeg)
        setFovTarget(fovDeg.coerceIn(MIN_FOV, MAX_FOV))
    }

    /** 仅改变 FOV 目标（退出聚焦回广角等）。 */
    fun zoomTo(fovDeg: Double) {
        setFovTarget(fovDeg.coerceIn(MIN_FOV, MAX_FOV))
    }

    /**
     * 站点/坐标基准变更时的纯重瞄：只重定视线中心目标，不触碰视场弹簧——
     * 避免捕获到飞行中的视场值而把变焦冻结在中途。
     */
    fun reAimTo(altDeg: Double, azDeg: Double) {
        azSpring.jumpTo(centerAzDeg)
        azSpring.target = centerAzDeg + normalizeDeg180(wrapAz(azDeg) - centerAzDeg)
        altSpring.jumpTo(centerAltDeg)
        altSpring.target = clampAlt(altDeg)
    }

    private fun setFovTarget(v: Double) {
        fovSpring.target = v
    }

    private val targetFov: Double get() = fovSpring.target

    /** 每帧推进：惯性衰减 + 弹簧滑航 + 视场弹簧。返回是否有残余运动。 */
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
        // 聚焦滑航弹簧推进（掉帧时按子步积分，收敛节奏与帧率无关）
        val azMoving = azSpring.update(dtSec)
        val altMoving = altSpring.update(dtSec)
        if (azMoving || altMoving) {
            centerAzDeg = wrapAz(azSpring.x)
            centerAltDeg = clampAlt(altSpring.x)
        }
        fovSpring.update(dtSec)
        fovYDeg = fovSpring.x
        return velAz != 0.0 || velAlt != 0.0 || azMoving || altMoving ||
            !fovSpring.settled
    }

    /** 寻星引导：把视线中心平滑转向目标。 */
    fun lookTowards(altDeg: Double, azDeg: Double) {
        // 直接跳转（由引导箭头完成渐进），仅设定中心
        centerAltDeg = clampAlt(altDeg)
        centerAzDeg = wrapAz(azDeg)
        azSpring.jumpTo(centerAzDeg)
        altSpring.jumpTo(centerAltDeg)
    }

    fun resetFovTarget() {
        fovSpring.target = fovYDeg
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

        /** 视场弹簧固有频率（rad/s）：62°→18° 约 0.5s 的电影式变焦节奏。 */
        const val FOV_OMEGA = 7.0
    }
}
