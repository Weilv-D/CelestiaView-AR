package com.celestiaview.ar

import com.celestiaview.ar.ui.render.PlateAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天体摄影贴图天球对齐纯函数测试：视差旋转角、S 曲线溶解、角直径物理缩放。
 */
class PlateAlignmentTest {

    @Test
    fun `天球北极在正上方时贴图零旋转`() {
        assertEquals(0f, PlateAlignment.screenRotationDeg(500f, 800f, 500f, 300f), 1e-3f)
    }

    @Test
    fun `北极参考点方位决定旋转角方向`() {
        // 北极在正右方 → 贴图顺时针转 90°（屏幕 y 向下）
        assertEquals(90f, PlateAlignment.screenRotationDeg(500f, 800f, 900f, 800f), 1e-3f)
        // 北极在正左方 → -90°
        assertEquals(-90f, PlateAlignment.screenRotationDeg(500f, 800f, 100f, 800f), 1e-3f)
        // 北极在正下方 → ±180°（贴图倒挂）
        val south = PlateAlignment.screenRotationDeg(500f, 800f, 500f, 1200f)
        assertEquals(180f, kotlin.math.abs(south), 1e-3f)
    }

    @Test
    fun `S 曲线两端为零且单调`() {
        assertEquals(0f, PlateAlignment.smoothstep01(-0.5f), 0f)
        assertEquals(0f, PlateAlignment.smoothstep01(0f), 0f)
        assertEquals(0.5f, PlateAlignment.smoothstep01(0.5f), 1e-6f)
        assertEquals(1f, PlateAlignment.smoothstep01(1f), 0f)
        assertEquals(1f, PlateAlignment.smoothstep01(1.5f), 0f)
        var prev = 0f
        var t = 0.05f
        while (t <= 1f) {
            val v = PlateAlignment.smoothstep01(t)
            assertTrue("S 曲线应单调递增 t=$t", v >= prev)
            prev = v
            t += 0.05f
        }
    }

    @Test
    fun `贴图半径随角直径与视场物理缩放并托底钳制`() {
        val minSide = 1080f
        val minPx = 78f
        val maxPx = 367f
        // 物理区：M31 角直径 3° @ 18° 视场 → 3/18×1080 = 180px
        assertEquals(180f, PlateAlignment.plateRadiusPx(3.0, 18.0, minSide, minPx, maxPx), 0.5f)
        // 小角径行星 @ 3° 微距：物理值过小 → 托底下限
        assertEquals(
            minPx,
            PlateAlignment.plateRadiusPx(0.011, 3.0, minSide, minPx, maxPx),
            0.5f
        )
        // 大角径天体 @ 18°：钳在上限
        assertEquals(
            maxPx,
            PlateAlignment.plateRadiusPx(20.0, 18.0, minSide, minPx, maxPx),
            0.5f
        )
        // 非法输入回落下限
        assertEquals(minPx, PlateAlignment.plateRadiusPx(0.0, 18.0, minSide, minPx, maxPx), 0f)
        assertEquals(minPx, PlateAlignment.plateRadiusPx(3.0, 0.0, minSide, minPx, maxPx), 0f)
    }
}
