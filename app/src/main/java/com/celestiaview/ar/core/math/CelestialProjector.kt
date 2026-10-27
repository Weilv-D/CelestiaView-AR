package com.celestiaview.ar.core.math

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 针孔相机视锥体投影器。
 * 相机空间约定：+x_c 屏幕右，+y_c 屏幕上，+z_c 沿视线向前；
 * 背面剔除阈值 z_c > 0.05；支持大圆弧球面细分（每 3° 一采样点）。
 */
class CelestialProjector {

    /** 屏幕像素尺寸。 */
    var width: Float = 1080f
    var height: Float = 2340f

    /** 垂直视场角（度），双指捏合可缩放。 */
    var fovYDeg: Double = 62.0

    /** 世界(ENU)→相机 正当旋转矩阵（det=+1）：行 0/1/2 分别为相机 right/up/backward 基向量。 */
    var cameraRotation: Mat3 = Mat3.IDENTITY
        private set

    fun setAttitude(worldToCamera: Quaternion) {
        cameraRotation = worldToCamera.toRotationMatrix()
    }

    /**
     * 虚拟星图模式：由视线中心 (alt, az) 构造右手相机基。
     * 屏幕系 (x右, y上, z向后) 与手机自然坐标一致，故第三行取反视线方向。
     */
    fun setVirtualCamera(centerAltDeg: Double, centerAzDeg: Double) {
        val forward = AstrometryEngine.altAzToEnu(centerAltDeg, centerAzDeg)
        val worldUp = Vec3.UNIT_Z
        val right = if (forward.cross(worldUp).length() < 1e-6) {
            // 视线接近天顶/天底：以方位角退化为水平右向
            AstrometryEngine.altAzToEnu(0.0, centerAzDeg + 90.0)
        } else forward.cross(worldUp).normalized()
        val up = right.cross(forward)
        val backward = right.cross(up)
        cameraRotation = Mat3.fromRows(right, up, backward)
    }

    private fun focalPx(): Float {
        val f = (height * 0.5f) / tan(AstrometryEngine.DEG2RAD * fovYDeg * 0.5).toFloat()
        return f
    }

    /** 世界方向 → 屏幕坐标；不可见（视线前方深度 ≤ 0.05）返回 null。 */
    fun project(world: Vec3): ScreenPos? {
        val cam = cameraRotation * world
        val zc = -cam.z // 屏幕系 z 向后，取负得到视线向前深度
        if (zc <= 0.05) return null
        val f = focalPx()
        return ScreenPos(
            x = width * 0.5f + f * (cam.x / zc).toFloat(),
            y = height * 0.5f - f * (cam.y / zc).toFloat()
        )
    }

    /** 屏幕坐标可复用容器：渲染热路径零分配投影用。 */
    class ScreenPosMut(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f)

    /**
     * 零分配投影：地平坐标 → 屏幕坐标写入 [out]，不构造 Vec3/ScreenPos。
     * @return 是否可见（视线前方深度 > 0.05）
     */
    fun projectAltAzInto(altDeg: Double, azDeg: Double, out: ScreenPosMut): Boolean {
        val altR = AstrometryEngine.DEG2RAD * altDeg
        val azR = AstrometryEngine.DEG2RAD * azDeg
        val cosAlt = cos(altR)
        return projectEnuInto(cosAlt * sin(azR), cosAlt * cos(azR), sin(altR), out)
    }

    /** 零分配投影：ENU 单位向量分量 → 屏幕坐标写入 [out]。 */
    fun projectEnuInto(ex: Double, ey: Double, ez: Double, out: ScreenPosMut): Boolean {
        val m = cameraRotation
        val cx = m.m00 * ex + m.m01 * ey + m.m02 * ez
        val cy = m.m10 * ex + m.m11 * ey + m.m12 * ez
        val zc = -(m.m20 * ex + m.m21 * ey + m.m22 * ez)
        if (zc <= 0.05) return false
        val f = focalPx()
        out.x = width * 0.5f + f * (cx / zc).toFloat()
        out.y = height * 0.5f - f * (cy / zc).toFloat()
        return true
    }

    /** 地平坐标方向投影。 */
    fun projectAltAz(altDeg: Double, azDeg: Double): ScreenPos? =
        project(AstrometryEngine.altAzToEnu(altDeg, azDeg))

    /** 赤道坐标方向投影（先经地平坐标转 ENU）。 */
    fun projectEquatorial(raDeg: Double, decDeg: Double, lstDeg: Double, latDeg: Double): ScreenPos? {
        val h = AstrometryEngine.equatorialToHorizontal(raDeg, decDeg, lstDeg, latDeg)
        return projectAltAz(h.altDeg, h.azDeg)
    }

    /**
     * 大圆弧球面细分：将球面线段 A→B 以 [stepDeg] 步长 Slerp 细分，
     * 返回可见屏幕点序列；被剔除处插入 [ScreenPos.BREAK] 断点标记，
     * 调用方据此分段绘制 Path，杜绝广角下的跨屏折返伪影。
     */
    fun projectGreatCircle(a: Vec3, b: Vec3, stepDeg: Double = 3.0): List<ScreenPos> {
        val total = a.angleBetweenDeg(b)
        val n = ceil(total / stepDeg).toInt().coerceAtLeast(1)
        val out = ArrayList<ScreenPos>(n + 3)
        var lastVisible = false
        for (k in 0..n) {
            val v = if (k == 0) a else if (k == n) b else a.slerp(b, k.toDouble() / n)
            val p = project(v)
            if (p == null) {
                if (lastVisible) out.add(ScreenPos.BREAK)
                lastVisible = false
            } else {
                out.add(p)
                lastVisible = true
            }
        }
        return out
    }

    /**
     * 天球上的一个圆（如地平纬线/赤纬圈）：给定圆心方向 [center]（单位向量）
     * 与角半径 [radiusDeg]，返回细分后的完整环线 ENU 向量序列。
     */
    fun circleOnSphere(center: Vec3, radiusDeg: Double, segments: Int = 120): List<Vec3> {
        val c = center.normalized()
        // 构造圆平面的正交基
        val any = if (kotlin.math.abs(c.z) < 0.9) Vec3.UNIT_Z else Vec3.UNIT_X
        val e1 = c.cross(any).normalized()
        val e2 = c.cross(e1)
        val rRad = AstrometryEngine.DEG2RAD * radiusDeg
        val cosR = cos(rRad)
        val sinR = sin(rRad)
        val out = ArrayList<Vec3>(segments)
        for (k in 0 until segments) {
            val theta = 2.0 * Math.PI * k / segments
            out.add(
                e1 * (sinR * cos(theta)) +
                e2 * (sinR * sin(theta)) +
                c * cosR
            )
        }
        return out
    }

    /** 目标方向在相机平面上的方位角 θ = atan2(y_c, x_c)，用于 360° 离轴寻星箭头。 */
    fun cameraPlaneAngleDeg(world: Vec3): Double? {
        val cam = cameraRotation * world
        val zc = -cam.z
        if (zc <= 0.05) {
            // 视野外（含背后）：返回屏幕平面上的指向角
            return atan2(cam.y, cam.x) * AstrometryEngine.RAD2DEG
        }
        return null // 在视野内，无需引导
    }

    data class ScreenPos(val x: Float, val y: Float) {
        companion object {
            val BREAK = ScreenPos(Float.NaN, Float.NaN)
            val isBreakMarker: (ScreenPos) -> Boolean = { it.x.isNaN() }
        }
    }
}
