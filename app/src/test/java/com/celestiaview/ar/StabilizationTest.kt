package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.sensor.AttitudeTracker
import com.celestiaview.ar.core.sensor.OrbitTouchController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长焦稳像纯函数测试：陀螺仪平滑系数的视场耦合降档、轨道控制器
 * 指数阻尼的帧率无关性、聚焦滑航收敛与 24h 高度角采样。
 */
class StabilizationTest {

    @Test
    fun `陀螺仪稳态平滑系数随视场降档至超稳态区间`() {
        // 广角常规防抖
        assertEquals(0.08, AttitudeTracker.stableAlphaFor(62.0), 0.0)
        assertEquals(0.08, AttitudeTracker.stableAlphaFor(25.0), 0.0)
        // 长焦区间线性降档：12.5° 为中点
        val mid = AttitudeTracker.stableAlphaFor(15.0)
        assertTrue("15° 系数应落在 (0.02, 0.08)：$mid", mid in 0.02..0.08)
        // ≤5° 深空微距压到 0.02 下限（含钳制）
        assertEquals(0.02, AttitudeTracker.stableAlphaFor(5.0), 1e-9)
        assertEquals(0.02, AttitudeTracker.stableAlphaFor(2.0), 1e-9)
        // 单调递减
        assertTrue(AttitudeTracker.stableAlphaFor(25.0) > AttitudeTracker.stableAlphaFor(10.0))
    }

    @Test
    fun `视场阻尼推进与帧率无关`() {
        // 60 帧 × 16.7ms 与单步 1s 的指数逼近应在容差内一致
        val a = OrbitTouchController()
        a.zoomTo(10.0)
        repeat(60) { a.update(1.0 / 60) }
        val b = OrbitTouchController()
        b.zoomTo(10.0)
        b.update(1.0)
        assertTrue(
            "帧率无关性破坏：60帧=${a.fovYDeg} 单步=${b.fovYDeg}",
            kotlin.math.abs(a.fovYDeg - b.fovYDeg) < 0.5
        )
        assertTrue("视场应单调逼近目标", a.fovYDeg < 40.0)
    }

    @Test
    fun `聚焦滑航收敛到目标且方位走最短弧`() {
        val c = OrbitTouchController()
        // 从 az=10° 滑航到 az=350°：应沿 -20° 最短弧而不是 +340° 长弧
        c.lookTowards(35.0, 10.0)
        c.glideTo(50.0, 350.0, 18.0)
        var residual = true
        var steps = 0
        while (residual && steps < 600) {
            residual = c.update(1.0 / 60)
            steps++
        }
        assertEquals(false, residual)
        assertEquals(50.0, c.centerAltDeg, 0.05)
        assertEquals(350.0, c.centerAzDeg, 0.05)
        assertEquals(18.0, c.fovYDeg, 0.05)
    }

    @Test
    fun `24h 高度角采样符合天体几何`() {
        // 北京（39.91N, 116.43E）观测北极星：恒显圈，全天高度 ≈ 纬度
        val dayStart = 1_798_516_800_000L // 2026-10-27 00:00 UTC，任意基准日
        val polaris = AstrometryEngine.altitudeCurve24h(
            37.95, 89.26, 39.91, 116.43, dayStart
        )
        assertTrue("北极星最低高度异常：${polaris.min()}",
            polaris.min() in 37.0..41.5)
        assertTrue("北极星最高高度异常：${polaris.max()}",
            polaris.max() in 38.5..42.5)

        // 天狼星（dec -16.7）：北京上中天高度 ≈ 90-|39.9-(-16.7)| ≈ 33.4°，
        // 下中天高度 = φ+δ-90 ≈ -66.8°
        val sirius = AstrometryEngine.altitudeCurve24h(
            101.287, -16.716, 39.91, 116.43, dayStart
        )
        assertTrue("天狼星最高高度异常：${sirius.max()}",
            sirius.max() in 31.0..36.0)
        assertTrue("天狼星最低高度异常：${sirius.min()}",
            sirius.min() in -69.0..-64.0)
        assertEquals(49, sirius.size)
    }
}
