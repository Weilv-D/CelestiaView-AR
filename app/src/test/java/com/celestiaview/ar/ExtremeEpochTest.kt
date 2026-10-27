package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.core.math.SatelliteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * 时间机器可跨越 ±1000 年：公元 1000 / 3000 年纪元下，儒略日、恒星时、
 * 开普勒迭代、月球摄动级数与人造天体模型保持有限、有界且收敛。
 */
class ExtremeEpochTest {

    private val year1000Millis = Instant.parse("1000-06-15T12:00:00Z").toEpochMilli()
    private val year3000Millis = Instant.parse("3000-06-15T12:00:00Z").toEpochMilli()

    private fun assertTrueFinite(msg: String, v: Double) {
        assertTrue("$msg 应为有限值: $v", !v.isNaN() && !v.isInfinite())
    }

    @Test
    fun julianDayRemainsFiniteAndMonotonicAcrossMillennia() {
        val jd0 = AstrometryEngine.julianDate(year1000Millis)
        val jd1 = AstrometryEngine.julianDate(year3000Millis)
        assertTrueFinite("JD(1000年)", jd0)
        assertTrueFinite("JD(3000年)", jd1)
        assertTrue("JD 必须随时间单调递增", jd1 > jd0)
        // 年 1000（格里历 6-15）→ JD 2086468.0；年 3000 → JD 2816953.0（±0.5 天容差）
        assertEquals(2086468.0, jd0, 0.5)
        assertEquals(2816953.0, jd1, 0.5)
        val t0 = AstrometryEngine.julianCenturies(jd0)
        val t1 = AstrometryEngine.julianCenturies(jd1)
        assertEquals(-10.0, t0, 0.05)
        assertEquals(10.0, t1, 0.05)
        // GMST 恒在 [0,360) 且有限
        for (jd in listOf(jd0, jd1)) {
            val g = AstrometryEngine.gmstDeg(jd)
            assertTrueFinite("GMST@$jd", g)
            assertTrue("GMST 越界: $g", g in 0.0..360.0)
        }
    }

    @Test
    fun planetaryKeplerSolverConvergesAtExtremeEpochs() {
        for ((label, ms) in listOf("公元1000年" to year1000Millis, "公元3000年" to year3000Millis)) {
            val jd = AstrometryEngine.julianDate(ms)
            for (p in PlanetaryEphemeris.PlanetId.entries) {
                // EARTH 为地心参考原点，无地心星历（调用侧已过滤）
                if (p == PlanetaryEphemeris.PlanetId.EARTH) continue
                val e = PlanetaryEphemeris.planetEphemeris(p, jd)
                assertTrueFinite("$label ${p} RA", e.raDeg)
                assertTrueFinite("$label ${p} Dec", e.decDeg)
                assertTrue("$label ${p} RA 越界: ${e.raDeg}", e.raDeg in 0.0..360.0)
                assertTrue("$label ${p} Dec 越界: ${e.decDeg}", e.decDeg in -90.0..90.0)
            }
            // 地球日心距离应保持在轨率半径物理范围内（开普勒迭代未发散的直接证据）
            val rEarth = PlanetaryEphemeris.heliocentricEcliptic(
                PlanetaryEphemeris.PlanetId.EARTH, AstrometryEngine.julianCenturies(jd)
            ).length()
            assertTrueFinite("$label 地球日心距", rEarth)
            assertTrue("$label 地球日心距异常: $rEarth AU", rEarth in 0.90..1.10)
        }
    }

    @Test
    fun lunarSeriesStaysFiniteAtExtremeEpochs() {
        for ((label, ms) in listOf("公元1000年" to year1000Millis, "公元3000年" to year3000Millis)) {
            val e = PlanetaryEphemeris.moonEphemeris(AstrometryEngine.julianDate(ms))
            assertTrueFinite("$label 月球 RA", e.raDeg)
            assertTrueFinite("$label 月球 Dec", e.decDeg)
            assertTrueFinite("$label 月相角", e.phaseAngleDeg)
            assertTrue("$label 月相角越界: ${e.phaseAngleDeg}", e.phaseAngleDeg in 0.0..360.0)
        }
    }

    @Test
    fun horizonTransformStableAtExtremeLst() {
        for (ms in listOf(year1000Millis, year3000Millis)) {
            val jd = AstrometryEngine.julianDate(ms)
            val lst = AstrometryEngine.lstDeg(jd, 116.43)
            val h = AstrometryEngine.equatorialToHorizontal(83.0, 22.0, lst, 39.91)
            assertTrueFinite("alt", h.altDeg)
            assertTrueFinite("az", h.azDeg)
            assertTrue("高度角越界: ${h.altDeg}", h.altDeg in -90.0..90.0)
            assertTrue("方位角越界: ${h.azDeg}", h.azDeg in 0.0..360.0)
        }
    }

    @Test
    fun riseTransitSetStaysFiniteAtExtremeEpochs() {
        for (ms in listOf(year1000Millis, year3000Millis)) {
            val rts = AstrometryEngine.riseTransitSet(
                83.0, 22.0, 39.91, ms, 116.43
            )
            assertNotNull("中天时刻不应为 null", rts.transitMillis)
            assertTrueFinite("中天最大高度", rts.transitMaxAltDeg)
            // 中天时刻必须折回请求日窗口内
            val dayMs = 86_400_000L
            assertTrue(
                "中天时刻未折回当日窗口",
                rts.transitMillis!! >= ms - dayMs && rts.transitMillis <= ms + dayMs
            )
        }
    }

    @Test
    fun satelliteModelStaysFiniteAtExtremeEpochs() {
        for (ms in listOf(year1000Millis, year3000Millis)) {
            val jd = AstrometryEngine.julianDate(ms)
            for (e in SatelliteTracker.all) {
                val info = SatelliteTracker.observe(e, jd, 39.91, 116.43)
                assertTrueFinite("${e.key} 高度角", info.altDeg)
                assertTrueFinite("${e.key} 方位角", info.azDeg)
                assertTrue("${e.key} 方位角越界: ${info.azDeg}", info.azDeg in 0.0..360.0)
            }
        }
    }

    @Test
    fun obliquityAndRefractionStableAtExtremeEpochs() {
        for (t in listOf(-10.0, 10.0)) {
            val eps = AstrometryEngine.obliquityDeg(t)
            assertTrueFinite("黄赤交角@$t", eps)
            assertTrue("黄赤交角异常: $eps", eps in 20.0..26.0)
        }
        val ref = AstrometryEngine.bennettRefractionArcmin(10.0)
        assertTrueFinite("折射角", ref)
        assertTrue("折射角异常: $ref", ref in 0.0..30.0)
        // 蒙气差在极低高度角下仍有限（时间机器低空扫掠稳定性）
        assertTrueFinite("地平折射角", AstrometryEngine.bennettRefractionArcmin(0.5))
        // 角距公式在任意象限下稳定
        val sep = AstrometryEngine.angularSeparationDeg(359.9, 89.9, 0.1, -89.9)
        assertTrueFinite("极点角距", sep)
        assertTrue("极点角距异常: $sep", abs(sep - 179.8) < 0.5)
    }
}
