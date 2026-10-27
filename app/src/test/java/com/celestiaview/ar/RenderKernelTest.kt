package com.celestiaview.ar

import com.celestiaview.ar.ui.render.ConstellationArtRenderer
import com.celestiaview.ar.ui.render.Planet3DRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染内核纯函数测试：3D 球面逐像素着色（脱盘透明 / 中心着色 / 外缘衰减）
 * 与古典白描线稿库覆盖。
 */
class RenderKernelTest {

    private val optics = Planet3DRenderer.PlanetOptics(
        spinDegPerSec = 5.0, tiltDeg = 25.2, atmoArgb = 0x50C1441E
    )

    /** 合成纹理：左半洋红、右半青色，便于检验 UV 采样取到了纹理。 */
    private fun syntheticTexture(w: Int = 64, h: Int = 32): IntArray {
        val tex = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            tex[y * w + x] = if (x < w / 2) 0xFFFF00FF.toInt() else 0xFF00FFFF.toInt()
        }
        return tex
    }

    @Test
    fun `球面着色——盘外全透明且中心不透明着色`() {
        val size = 64
        val tex = syntheticTexture()
        val out = IntArray(size * size)
        Planet3DRenderer.shadeSphere(out, size, tex, 64, 32, 0f, optics, -0.65f, 0.25f)
        // 盘外（角）像素 alpha=0
        assertEquals(0, out[0])
        assertEquals(0, out[size * size - 1])
        // 盘心像素不透明且带纹理色
        val c = out[(size / 2) * size + size / 2]
        val a = c ushr 24
        assertTrue("盘心应为不透明像素: a=$a", a > 200)
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        val b = c and 0xFF
        assertTrue("盘心应有纹理颜色而非纯黑: ($r,$g,$b)", r + g + b > 60)
    }

    @Test
    fun `球面着色——外缘半径 0_96 到 1_0 平滑衰减到透明`() {
        val size = 128
        val tex = syntheticTexture()
        val out = IntArray(size * size)
        Planet3DRenderer.shadeSphere(out, size, tex, 64, 32, 0f, optics, -0.65f, 0.25f)
        val half = size * 0.5f
        val rIn = (0.94f * half).toInt()
        val rOut = (0.995f * half).toInt()
        val aIn = out[(size / 2) * size + size / 2 + rIn] ushr 24
        val aOut = out[(size / 2) * size + size / 2 + rOut] ushr 24
        assertTrue("内缘应接近不透明: $aIn", aIn > 180)
        assertTrue("外缘应接近透明: $aOut", aOut < 60)
    }

    @Test
    fun `古典白描线稿覆盖黄道十二宫与北天名宿`() {
        val zodiac = listOf(
            "ARI", "TAU", "GEM", "CNC", "LEO", "VIR",
            "LIB", "SCO", "SGR", "CAP", "AQR", "PSC"
        )
        val northern = listOf("UMA", "UMI", "ORI", "CAS", "CYG", "LYR", "AQL", "PEG", "BOO", "DRA")
        for (abbr in zodiac + northern) {
            assertNotNull("缺少 $abbr 白描线稿", ConstellationArtRenderer.strokesFor(abbr))
        }
        assertEquals(22, ConstellationArtRenderer.size)
    }

    @Test
    fun `白描线稿坐标全部归一化在 0_1 区间`() {
        for (abbr in listOf("CNC", "ORI", "UMA", "CYG", "SCO")) {
            for (stroke in ConstellationArtRenderer.strokesFor(abbr)!!) {
                for (v in stroke) {
                    assertTrue("$abbr 线稿坐标越界: $v", v in -1e-4f..1f + 1e-4f)
                }
            }
        }
    }
}
