package com.celestiaview.ar

import com.celestiaview.ar.core.math.SpringAnimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二阶临界阻尼弹簧相机测试：临界阻尼无过冲、收敛性、帧率无关节奏。
 */
class SpringAnimatorTest {

    @Test
    fun `临界阻尼收敛无过冲`() {
        val s = SpringAnimator(omega0 = 8.5, zeta = 1.0)
        s.jumpTo(0.0)
        s.target = 30.0
        var maxOvershoot = 0.0
        var frames = 0
        while (s.update(1.0 / 60.0) && frames < 600) {
            maxOvershoot = maxOf(maxOvershoot, s.x - 30.0)
            frames++
        }
        assertTrue("临界阻尼不应过冲: $maxOvershoot", maxOvershoot <= 1e-6)
        assertEquals(30.0, s.x, 1e-3)
        assertTrue("应在 3 秒内收敛", frames < 180)
        assertFalse(s.update(1.0 / 60.0))
    }

    @Test
    fun `帧率无关——逐帧积分与单步长积分收敛位置一致`() {
        val fine = SpringAnimator()
        fine.jumpTo(10.0)
        fine.target = 50.0
        repeat(120) { fine.update(1.0 / 60.0) }

        val coarse = SpringAnimator()
        coarse.jumpTo(10.0)
        coarse.target = 50.0
        coarse.update(2.0) // 单个 2 秒长帧（子步切分）

        assertEquals(fine.x, coarse.x, 0.5)
    }

    @Test
    fun `速度与位置连续——切换目标不产生跳变`() {
        val s = SpringAnimator()
        s.jumpTo(62.0)
        s.target = 18.0
        repeat(30) { s.update(1.0 / 60.0) }
        val mid = s.x
        assertTrue("应已离开起点", mid < 62.0 && mid > 18.0)
        // 反向：目标回到起点方向，位置从当前值继续（无瞬移）
        s.target = 40.0
        s.update(1.0 / 240.0)
        assertTrue(kotlin.math.abs(s.x - mid) < 2.0)
    }
}
