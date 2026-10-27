package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.SatelliteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 空间站轨道追踪：圆轨道进动模型 + 地影判定 + 过境预报完整性。 */
class SatelliteTrackerTest {

    private val epochMillis = java.time.Instant.parse("2026-10-27T12:00:00Z").toEpochMilli()

    @Test
    fun subpointLiesOnOrbitalInclinationBand() {
        val jd = AstrometryEngine.julianDate(epochMillis)
        for (e in SatelliteTracker.all) {
            val jd0 = AstrometryEngine.julianDate(
                java.time.Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()
            )
            var t = jd0
            repeat(200) {
                val sat = stateSubLat(e, t)
                assertTrue("${e.key} 星下点纬度越界: $sat", abs(sat) <= e.inclinationDeg + 1e-6)
                t += 600.0 / 86400.0 // 每 10 分钟
            }
        }
    }

    private fun stateSubLat(
        e: SatelliteTracker.Elements, jd: Double
    ): Double = SatelliteTracker.observe(e, jd, 39.9, 116.4).subLatDeg

    @Test
    fun orbitalPeriodMatchesRevolutionRate() {
        // 一个周期后星下点应回到近似相同纬度（圆轨道回归）
        val e = SatelliteTracker.ISS
        val jd = AstrometryEngine.julianDate(epochMillis)
        val a = SatelliteTracker.observe(e, jd, 0.0, 0.0)
        val jdNext = jd + e.periodMin / 1440.0
        val b = SatelliteTracker.observe(e, jdNext, 0.0, 0.0)
        assertEquals(a.subLatDeg, b.subLatDeg, 0.6) // 进动残差容忍
    }

    @Test
    fun altitudeAboveHorizonYieldsPositiveAlt() {
        val jd = AstrometryEngine.julianDate(epochMillis)
        // 取样 24h，至少存在一个卫星在某个地面点可见
        var seenVisible = false
        var t = jd
        repeat(288) { // 5 分钟步进
            for (e in SatelliteTracker.all) {
                val info = SatelliteTracker.observe(e, t, 39.9, 116.4)
                if (info.altDeg > 0.0) {
                    seenVisible = true
                    assertTrue(info.rangeKm > 350.0 && info.rangeKm < 3000.0)
                }
                assertTrue(info.angularSpeedDegPerSec in 0.0..2.0)
            }
            t += 300.0 / 86400.0
        }
        assertTrue("24h 内北京应至少一次看到卫星", seenVisible)
    }

    @Test
    fun passPredictionReturnsConsistentPasses() {
        val passes = SatelliteTracker.nextPasses(
            SatelliteTracker.ISS, epochMillis, 39.91, 116.43,
            hoursAhead = 48.0, minAltDeg = 10.0, visibleOnly = false
        )
        assertTrue("48h 应有多次过境", passes.size >= 2)
        for (p in passes) {
            assertTrue(p.startMillis < p.maxMillis && p.maxMillis < p.endMillis)
            assertTrue(p.maxAltDeg >= 10.0)
            assertTrue((p.endMillis - p.startMillis) in 1..15 * 60_000L * 3)
        }
    }

    @Test
    fun shadowTestSunlitAtNoonSide() {
        // 轨道位置恰在日侧时必被照亮：直接验证 isSunlit 圆柱模型
        val jd = AstrometryEngine.julianDate(epochMillis)
        val sun = com.celestiaview.ar.core.math.PlanetaryEphemeris.sunEphemeris(jd)
        val a = Math.toRadians(sun.raDeg)
        val d = Math.toRadians(sun.decDeg)
        val pos = com.celestiaview.ar.core.math.Vec3(
            6800.0 * kotlin.math.cos(d) * kotlin.math.cos(a),
            6800.0 * kotlin.math.cos(d) * kotlin.math.sin(a),
            6800.0 * kotlin.math.sin(d)
        )
        assertTrue(SatelliteTracker.isSunlit(pos, jd))
        // 反向一点必在地影中
        val anti = pos * -1.0
        assertTrue(!SatelliteTracker.isSunlit(anti, jd))
    }

    private fun abs(v: Double): Double = kotlin.math.abs(v)
}
