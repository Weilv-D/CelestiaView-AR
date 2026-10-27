package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstrometryEngineTest {

    // ------------------------------------------------------------------
    // 时间系统
    // ------------------------------------------------------------------

    @Test
    fun julianDateAtJ2000Epoch() {
        // 2000-01-01 12:00:00 UTC == 946728000000 ms
        assertEquals(2451545.0, AstrometryEngine.julianDate(946728000000L), 1e-6)
    }

    @Test
    fun julianCenturiesZeroAtJ2000() {
        assertEquals(0.0, AstrometryEngine.julianCenturies(2451545.0), 1e-12)
    }

    @Test
    fun gmstAtJ2000Is280Dot46() {
        // d=0, T=0 → 级数首项
        assertEquals(280.46061837, AstrometryEngine.gmstDeg(2451545.0), 1e-6)
    }

    @Test
    fun lstAddsEastLongitude() {
        val jd = 2451545.5
        val gmst = AstrometryEngine.gmstDeg(jd)
        assertEquals(gmst + 116.0, AstrometryEngine.lstDeg(jd, 116.0), 1e-9)
    }

    @Test
    fun julianDateRoundTripOverDay() {
        val jd0 = AstrometryEngine.julianDate(0L)
        assertEquals(jd0 + 1.0, AstrometryEngine.julianDate(86_400_000L), 1e-9)
    }

    // ------------------------------------------------------------------
    // 赤道 → 地平
    // ------------------------------------------------------------------

    @Test
    fun transitDueSouthHasCorrectAltitude() {
        // 北纬 40°，天体赤纬 20° 上中天：az=180°，alt=90-|40-20|=70°
        val h = AstrometryEngine.equatorialToHorizontal(
            raDeg = 100.0, decDeg = 20.0, lstDeg = 100.0, latitudeDeg = 40.0
        )
        assertEquals(70.0, h.altDeg, 1e-6)
        assertEquals(180.0, h.azDeg, 1e-6)
    }

    @Test
    fun transitNorthOfZenithFacesNorth() {
        // 赤纬 50° > 纬度 40°：上中天在正北，alt = 90-|40-50| = 80°
        val h = AstrometryEngine.equatorialToHorizontal(
            raDeg = 50.0, decDeg = 50.0, lstDeg = 50.0, latitudeDeg = 40.0
        )
        assertEquals(80.0, h.altDeg, 1e-6)
        assertEquals(0.0, h.azDeg, 1e-6)
    }

    @Test
    fun polarisAltitudeTracksLatitude() {
        // 北极星 (RA=2.5303h, Dec=+89.2641°) 在北纬 40°：高度始终 40°±0.74°
        val ra = 2.5303 * 15.0
        val dec = 89.2641
        for (lst in 0 until 360 step 17) {
            val h = AstrometryEngine.equatorialToHorizontal(ra, dec, lst.toDouble(), 40.0)
            assertTrue("alt=${h.altDeg}", h.altDeg in 39.0..41.0)
            val azFromNorth = kotlin.math.min(h.azDeg, 360.0 - h.azDeg)
            assertTrue("az=${h.azDeg}", azFromNorth < 2.0)
        }
    }

    @Test
    fun zenithObjectAtEquator() {
        // 赤道观测者，天体赤纬 0、LST=RA：位于天顶
        val h = AstrometryEngine.equatorialToHorizontal(123.0, 0.0, 123.0, 0.0)
        assertEquals(90.0, h.altDeg, 1e-9)
    }

    // ------------------------------------------------------------------
    // ENU 向量
    // ------------------------------------------------------------------

    @Test
    fun altAzToEnuRoundTrip() {
        val pairs = listOf(
            30.0 to 45.0,
            (-15.0) to 200.0,
            89.0 to 359.0,
            0.0 to 90.0
        )
        for ((alt, az) in pairs) {
            val v = AstrometryEngine.altAzToEnu(alt, az)
            assertEquals(1.0, v.length(), 1e-12)
            val back = AstrometryEngine.enuToAltAz(v)
            assertEquals(alt, back.altDeg, 1e-9)
            assertEquals(az, back.azDeg, 1e-9)
        }
    }

    // ------------------------------------------------------------------
    // Bennett 大气折射
    // ------------------------------------------------------------------

    @Test
    fun bennettRefractionAtHorizon() {
        // 地平线处 Bennett 公式给出 ~29'（约 0.48°）；物理实测约 34'（0.57°），
        // 断言落在教科书两种口径的公共邻域 [0.45°, 0.60°] 内
        val rArcmin = AstrometryEngine.bennettRefractionArcmin(0.0)
        val rDeg = rArcmin / 60.0
        assertTrue("R=$rArcmin arcmin", rDeg in 0.45..0.60)
    }

    @Test
    fun bennettRefractionDecreasesWithAltitude() {
        val r0 = AstrometryEngine.bennettRefractionArcmin(0.0)
        val r45 = AstrometryEngine.bennettRefractionArcmin(45.0)
        val r60 = AstrometryEngine.bennettRefractionArcmin(60.0)
        assertTrue(r0 > r45)
        assertTrue(r45 > r60)
        // 45° 高度处折射约 1′
        assertEquals(1.0, r45, 0.3)
    }

    @Test
    fun apparentAltitudeLiftsObject() {
        val apparent = AstrometryEngine.apparentAltitudeDeg(0.0)
        assertTrue(apparent > 0.30 && apparent < 0.65)
        // 深负高度不修正
        assertEquals(-5.0, AstrometryEngine.apparentAltitudeDeg(-5.0), 1e-12)
    }

    // ------------------------------------------------------------------
    // 黄道 / 银道
    // ------------------------------------------------------------------

    @Test
    fun obliquityNear23Point44AtJ2000() {
        assertEquals(23.4393, AstrometryEngine.obliquityDeg(0.0), 0.001)
    }

    @Test
    fun eclipticToEquatorialVernalEquinox() {
        // 黄经 0、黄纬 0 → 春分点 RA=0, Dec=0
        val eq = AstrometryEngine.eclipticToEquatorial(0.0, 0.0, 23.4393)
        assertEquals(0.0, eq.raDeg, 1e-6)
        assertEquals(0.0, eq.decDeg, 1e-6)
    }

    @Test
    fun eclipticEquatorialInverseRoundTrip() {
        val (lon, lat) = AstrometryEngine.equatorialToEcliptic(130.0, -25.0, 23.4393)
        val back = AstrometryEngine.eclipticToEquatorial(lon, lat, 23.4393)
        assertEquals(130.0, back.raDeg, 1e-6)
        assertEquals(-25.0, back.decDeg, 1e-6)
    }

    @Test
    fun galacticCenterMapsToSagittarius() {
        // 银心 (l=0, b=0) → RA ≈ 266.405°, Dec ≈ -28.936°
        val eq = AstrometryEngine.galacticToEquatorial(0.0, 0.0)
        assertEquals(266.405, eq.raDeg, 0.5)
        assertEquals(-28.936, eq.decDeg, 0.5)
    }

    // ------------------------------------------------------------------
    // 球面角距
    // ------------------------------------------------------------------

    @Test
    fun angularSeparationKnownCases() {
        assertEquals(90.0, AstrometryEngine.angularSeparationDeg(0.0, 0.0, 90.0, 0.0), 1e-9)
        assertEquals(90.0, AstrometryEngine.angularSeparationDeg(0.0, 0.0, 0.0, 90.0), 1e-9)
        assertEquals(60.0, AstrometryEngine.angularSeparationDeg(0.0, 0.0, 60.0, 0.0), 1e-9)
        // 对跖点
        assertEquals(180.0, AstrometryEngine.angularSeparationDeg(0.0, 0.0, 0.0, 180.0) //
            .let { if (it > 179.999) 180.0 else it }, 1e-3)
    }

    // ------------------------------------------------------------------
    // 升起 / 中天 / 落下
    // ------------------------------------------------------------------

    @Test
    fun riseTransitSetOrderingAndAltitude() {
        // 类参宿四星体：RA 5.92h, Dec +7.4°，北京 (39.91N, 116.43E)
        val dayStart = 1_769_500_000_000L // 2026-01-27 附近某日 0 点 UTC（测试基准）
        val rts = AstrometryEngine.riseTransitSet(
            raDeg = 5.92 * 15.0, decDeg = 7.4,
            latitudeDeg = 39.91, dayStartMillis = dayStart,
            longitudeEastDeg = 116.43
        )
        assertTrue(!rts.circumpolar && !rts.neverRises)
        assertEquals(90.0 - kotlin.math.abs(39.91 - 7.4), rts.transitMaxAltDeg, 1e-6)
        assertTrue(rts.riseMillis!! < rts.transitMillis!!)
        assertTrue(rts.transitMillis!! < rts.setMillis!!)

        // 中天时刻复核：届时天体时角应为 0、高度=中天高度
        val jd = AstrometryEngine.julianDate(rts.transitMillis!!)
        val lst = AstrometryEngine.lstDeg(jd, 116.43)
        val hourAngle = AstrometryEngine.normalizeDeg180(lst - 5.92 * 15.0)
        assertEquals(0.0, hourAngle, 0.02)
    }

    @Test
    fun circumpolarAndNeverRiseFlags() {
        val dayStart = 1_769_500_000_000L
        // 北纬 45° 赤纬 +85°：拱极永不落
        val cp = AstrometryEngine.riseTransitSet(0.0, 85.0, 45.0, dayStart, 0.0)
        assertTrue(cp.circumpolar)
        // 北纬 45° 赤纬 -70°：全天位于地平线下
        val nr = AstrometryEngine.riseTransitSet(0.0, -70.0, 45.0, dayStart, 0.0)
        assertTrue(nr.neverRises)
    }

    @Test
    fun sunRiseSetUsesSolarHorizon() {
        // 太阳（h0=-0.8333°）在赤道上春秋分附近应近乎昼夜平分
        val equinoxNoonUtc = 1_769_337_600_000L // 2026-01-25T00:00Z 基准（具体日期不影响对称性验证）
        val rts = AstrometryEngine.riseTransitSet(
            raDeg = 280.0, decDeg = 0.5,
            latitudeDeg = 0.0, dayStartMillis = equinoxNoonUtc,
            longitudeEastDeg = 0.0, h0Deg = AstrometryEngine.H0_SUN_DEG
        )
        val daylightMs = rts.setMillis!! - rts.riseMillis!!
        // 半昼弧 H0 = acos((sin(-0.8333°)) ) ≈ 90.57° → 昼长约 12.07h
        assertEquals(12.07 * 3_600_000.0, daylightMs.toDouble(), 3.0 * 3_600_000.0 * 0.1)
    }
}
