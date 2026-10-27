package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class PlanetaryEphemerisTest {

    private val nowJd = AstrometryEngine.julianDate(System.currentTimeMillis())

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    // ------------------------------------------------------------------
    // 有效性：太阳、月球与五大行星
    // ------------------------------------------------------------------

    @Test
    fun planetEphemeridesAreValid() {
        val planets = listOf(
            PlanetaryEphemeris.PlanetId.MERCURY,
            PlanetaryEphemeris.PlanetId.VENUS,
            PlanetaryEphemeris.PlanetId.MARS,
            PlanetaryEphemeris.PlanetId.JUPITER,
            PlanetaryEphemeris.PlanetId.SATURN
        )
        for (p in planets) {
            val e = PlanetaryEphemeris.planetEphemeris(p, nowJd)
            assertTrue("${p} ra=${e.raDeg}", e.raDeg in 0.0..360.0)
            assertTrue("${p} dec=${e.decDeg}", e.decDeg in -90.0..90.0)
            assertTrue("${p} dist=${e.distanceAu}", e.distanceAu > 0.1)
            assertTrue("${p} eclLon=${e.eclipticLonDeg}", e.eclipticLonDeg in 0.0..360.0)
        }
    }

    @Test
    fun planetHeliocentricDistancesNearSemiMajorAxes() {
        val t = 0.0
        val checks = mapOf(
            PlanetaryEphemeris.PlanetId.MERCURY to 0.387,
            PlanetaryEphemeris.PlanetId.VENUS to 0.723,
            PlanetaryEphemeris.PlanetId.EARTH to 1.000,
            PlanetaryEphemeris.PlanetId.MARS to 1.524,
            PlanetaryEphemeris.PlanetId.JUPITER to 5.203,
            PlanetaryEphemeris.PlanetId.SATURN to 9.537
        )
        val ecc = mapOf(
            PlanetaryEphemeris.PlanetId.MERCURY to 0.206,
            PlanetaryEphemeris.PlanetId.VENUS to 0.007,
            PlanetaryEphemeris.PlanetId.EARTH to 0.017,
            PlanetaryEphemeris.PlanetId.MARS to 0.093,
            PlanetaryEphemeris.PlanetId.JUPITER to 0.048,
            PlanetaryEphemeris.PlanetId.SATURN to 0.054
        )
        for ((p, a) in checks) {
            val r = PlanetaryEphemeris.heliocentricEcliptic(p, t).length()
            val tol = a * ecc.getValue(p) + 0.02
            assertTrue("$p r=$r a=$a tol=$tol", kotlin.math.abs(r - a) < tol)
        }
    }

    // ------------------------------------------------------------------
    // 太阳：春分/夏至锚点（黄经 0° / 90°）
    // ------------------------------------------------------------------

    @Test
    fun sunAtVernalEquinox2000() {
        // 2000-03-20 07:35 UTC 春分：太阳黄经 = 0°
        val jd = AstrometryEngine.julianDate(utcMillis(2000, 3, 20, 7, 35))
        val sun = PlanetaryEphemeris.sunEphemeris(jd)
        val lon = AstrometryEngine.normalizeDeg180(sun.eclipticLonDeg)
        assertTrue("sun lon=${sun.eclipticLonDeg}", kotlin.math.abs(lon) < 1.0)
    }

    @Test
    fun sunAtSummerSolstice2000() {
        // 2000-06-21 01:48 UTC 夏至：太阳黄经 = 90°
        val jd = AstrometryEngine.julianDate(utcMillis(2000, 6, 21, 1, 48))
        val sun = PlanetaryEphemeris.sunEphemeris(jd)
        val lon = AstrometryEngine.normalizeDeg180(sun.eclipticLonDeg - 90.0)
        assertTrue("sun lon=${sun.eclipticLonDeg}", kotlin.math.abs(lon) < 1.0)
    }

    @Test
    fun sunDistanceAboutOneAu() {
        val sun = PlanetaryEphemeris.sunEphemeris(nowJd)
        assertTrue(sun.distanceAu in 0.98..1.02)
    }

    // ------------------------------------------------------------------
    // 月球：满月/新月锚点 + 月相机
    // ------------------------------------------------------------------

    @Test
    fun moonFullAtLunarEclipseJan2000() {
        // 2000-01-21 04:40 UTC 月全食食甚：必然满月
        val jd = AstrometryEngine.julianDate(utcMillis(2000, 1, 21, 4, 40))
        val moon = PlanetaryEphemeris.moonEphemeris(jd)
        assertTrue("illum=${moon.illuminationFraction}", moon.illuminationFraction > 0.95)
        assertTrue("phase=${moon.phaseAngleDeg}", moon.phaseAngleDeg in 175.0..185.0)
        assertEquals("满月", moon.phaseNameZh)
    }

    @Test
    fun moonNewAtSolarEclipseFeb2000() {
        // 2000-02-05 12:49 UTC 日偏食：必然新月
        val jd = AstrometryEngine.julianDate(utcMillis(2000, 2, 5, 12, 49))
        val moon = PlanetaryEphemeris.moonEphemeris(jd)
        assertTrue("illum=${moon.illuminationFraction}", moon.illuminationFraction < 0.05)
        assertEquals("新月", moon.phaseNameZh)
    }

    @Test
    fun moonEphemerisValidRanges() {
        val moon = PlanetaryEphemeris.moonEphemeris(nowJd)
        assertTrue(moon.raDeg in 0.0..360.0)
        assertTrue(moon.decDeg in -90.0..90.0)
        // 近点 356500 km ~ 远点 406700 km
        assertTrue("dist=${moon.distanceKm}", moon.distanceKm in 350_000.0..410_000.0)
        assertTrue(moon.illuminationFraction in 0.0..1.0)
        assertTrue(moon.phaseIndex in 0..7)
    }

    @Test
    fun moonMovesAlongZodiac() {
        // 月球黄纬始终 |β| < 5.5°
        for (dayOffset in 0 until 29) {
            val jd = nowJd + dayOffset
            val (lon, lat, _) = PlanetaryEphemeris.moonEcliptic(jd)
            assertTrue("lat=$lat", kotlin.math.abs(lat) < 5.5)
            assertTrue(lon in 0.0..360.0)
        }
    }

    // ------------------------------------------------------------------
    // 星等
    // ------------------------------------------------------------------

    @Test
    fun apparentMagnitudesReasonable() {
        val v = PlanetaryEphemeris.apparentMagnitude(PlanetaryEphemeris.PlanetId.VENUS, nowJd)
        assertTrue("venus mag=$v", v in -5.0..-3.0)
        val j = PlanetaryEphemeris.apparentMagnitude(PlanetaryEphemeris.PlanetId.JUPITER, nowJd)
        assertTrue("jupiter mag=$j", j in -3.0..-1.0)
    }

    @Test
    fun absoluteMagnitudeOfVegaNearZero() {
        // 织女星：m=0.03, d=25 ly → M ≈ 0.58
        val m = PlanetaryEphemeris.absoluteMagnitude(0.03, 25.0)
        assertEquals(0.58, m, 0.1)
    }

    @Test
    fun planetMetaComplete() {
        assertEquals(6, PlanetaryEphemeris.planetMeta.size)
        assertEquals("金星", PlanetaryEphemeris.planetMeta[1].nameZh)
    }
}
