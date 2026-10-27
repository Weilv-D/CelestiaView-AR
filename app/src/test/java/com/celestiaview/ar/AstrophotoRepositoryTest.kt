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
    fun `羽化曲线在 0_55 内保持全透明度且平滑衰减到 1 归零`() {
        assertEquals(1f, AstrophotoRepository.featherAlpha01(0f), 0f)
        assertEquals(1f, AstrophotoRepository.featherAlpha01(0.55f), 1e-6f)
        assertEquals(0.5f, AstrophotoRepository.featherAlpha01(0.775f), 1e-3f) // cos(π/2)=0
        assertEquals(0f, AstrophotoRepository.featherAlpha01(1f), 1e-6f)
        assertEquals(0f, AstrophotoRepository.featherAlpha01(1.41f), 0f) // 角点钳制
        // 单调递减
        var prev = 1f
        var d = 0.56f
        while (d <= 1f) {
            val a = AstrophotoRepository.featherAlpha01(d)
            assertTrue("alpha 应单调递减 d=$d", a <= prev + 1e-6f)
            prev = a
            d += 0.02f
        }
    }

    @Test
    fun `死黑底色亮度阈值映射`() {
        assertEquals(0f, AstrophotoRepository.darkSkyAlpha01(0), 0f)
        assertEquals(0f, AstrophotoRepository.darkSkyAlpha01(8), 0f)
        assertEquals(0.5f, AstrophotoRepository.darkSkyAlpha01(28), 1e-6f)
        assertEquals(1f, AstrophotoRepository.darkSkyAlpha01(48), 1e-6f)
        assertEquals(1f, AstrophotoRepository.darkSkyAlpha01(230), 0f)
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
