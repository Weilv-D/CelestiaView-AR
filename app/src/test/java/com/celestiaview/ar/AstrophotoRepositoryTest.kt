package com.celestiaview.ar

import com.celestiaview.ar.ui.render.AstrophotoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天体摄影引擎纯函数测试：hips2fits URL 构造、径向羽化曲线、死黑透明化、
 * 解码采样率与精选图源表。
 */
class AstrophotoRepositoryTest {

    @Test
    fun `hips2fits URL 按规范模板构造`() {
        assertEquals(
            "https://alasky.cds.unistra.fr/hips-image-services/hips2fits" +
                "?hips=CDS%2FP%2FDSS2%2Fcolor&width=512&height=512&fov=0.35" +
                "&projection=TAN&coordsys=icrs&ra=101.287&dec=-16.716&format=jpg",
            AstrophotoRepository.hips2fitsUrl(101.287, -16.716, 0.35)
        )
        assertEquals(
            "https://alasky.cds.unistra.fr/hips-image-services/hips2fits" +
                "?hips=CDS%2FP%2FDSS2%2Fcolor&width=512&height=512&fov=2.5" +
                "&projection=TAN&coordsys=icrs&ra=10.6847&dec=41.2692&format=jpg",
            AstrophotoRepository.hips2fitsUrl(10.6847, 41.2692, 2.5)
        )
    }

    @Test
    fun `无痕脱底曲线中心饱和边缘归零且单调`() {
        // 中心全亮度：余弦窗口 = 1、亮度律 1^0.75×1.5 → 钳制为 1
        assertEquals(1f, AstrophotoRepository.seamlessAlpha01(0f, 1f), 1e-6f)
        // 边缘归零（余弦窗口收口）
        assertEquals(0f, AstrophotoRepository.seamlessAlpha01(1f, 1f), 1e-6f)
        // 死黑像素质地透明（脱黑底）
        assertEquals(0f, AstrophotoRepository.seamlessAlpha01(0f, 0f), 1e-6f)
        // 中段半亮度（亮度律 ^0.75×1.5 增益复合：0.25^0.75≈0.354×1.5≈0.53）
        val mid = AstrophotoRepository.seamlessAlpha01(0f, 0.25f)
        assertTrue("半亮度 alpha 应在 (0,0.6) 区间: $mid", mid in 0.01f..0.6f)
        // 单调递减
        var prev = 1f
        var d = 0f
        while (d <= 1f) {
            val a = AstrophotoRepository.seamlessAlpha01(d, 0.8f)
            assertTrue("alpha 应单调递减 d=$d", a <= prev + 1e-6f)
            prev = a
            d += 0.05f
        }
    }

    @Test
    fun `环境底色抬黑位与保底 38 取大`() {
        assertEquals(38, AstrophotoRepository.pedestalChannel(10))
        assertEquals(38, AstrophotoRepository.pedestalChannel(38))
        assertEquals(90, AstrophotoRepository.pedestalChannel(90))
    }

    @Test
    fun `解码采样率把最长边压到上限内`() {
        assertEquals(1, AstrophotoRepository.maxSample(768, 768))
        assertEquals(1, AstrophotoRepository.maxSample(500, 300))
        assertEquals(1, AstrophotoRepository.maxSample(1024, 1024)) // 512px 采样已低于上限
        assertEquals(2, AstrophotoRepository.maxSample(1536, 768))
        assertEquals(16, AstrophotoRepository.maxSample(18000, 18000))
    }

    @Test
    fun `行星与经典深空天体有精选图源与源标注`() {
        for (key in listOf("SUN", "MOON", "MERCURY", "VENUS", "MARS", "JUPITER", "SATURN")) {
            assertTrue("缺少 $key 精选图源", AstrophotoRepository.curatedUrlFor(key)!!.startsWith("https://"))
            assertTrue(AstrophotoRepository.curatedLabelFor(key)!!.isNotBlank())
        }
        for (key in listOf("M1", "M8", "M13", "M16", "M17", "M20", "M27", "M31", "M33",
                           "M42", "M45", "M51", "M57", "M81", "M104")) {
            assertTrue("缺少 $key 精选图源", AstrophotoRepository.curatedUrlFor(key)!!.startsWith("https://"))
        }
        assertNull(AstrophotoRepository.curatedUrlFor("M999"))
        assertTrue(AstrophotoRepository.curatedLabelFor("JUPITER")!!.contains("Hubble"))
    }

    @Test
    fun `内置离线首发贴图覆盖核心天体`() {
        val expected = setOf(
            "M31", "M42", "M45", "M1", "M13", "M51", "M57",
            "JUPITER", "SATURN", "MARS", "MOON", "SUN", "SIRIUS", "UMA"
        )
        assertEquals(expected, AstrophotoRepository.BUNDLED_KEYS)
        // 任务要求的核心 12 天体全部内置：月木土火日 + M31/M42/M45/M1/M13/M51/M57
        val coreTwelve = setOf(
            "MOON", "JUPITER", "SATURN", "MARS", "SUN",
            "M31", "M42", "M45", "M1", "M13", "M51", "M57"
        )
        assertTrue("核心天体缺内置图：${coreTwelve - AstrophotoRepository.BUNDLED_KEYS}",
            AstrophotoRepository.BUNDLED_KEYS.containsAll(coreTwelve))
    }
}
