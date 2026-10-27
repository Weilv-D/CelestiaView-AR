package com.celestiaview.ar.core.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 天体测量引擎：儒略日 / 恒星时 / 坐标变换 / Bennett 大气折射 / 升落中天 / 银道坐标。
 * 全部公式基于 J2000.0 历元，角度单位为度，弧度仅存在于局部计算中。
 */
object AstrometryEngine {

    const val DEG2RAD: Double = Math.PI / 180.0
    const val RAD2DEG: Double = 180.0 / Math.PI
    const val JD_J2000: Double = 2451545.0
    const val MS_PER_DAY: Double = 86_400_000.0

    /** 地球自转（相对恒星）角速度：度/小时 = 360.98564736629 / 24。 */
    const val SIDEREAL_DEG_PER_HOUR: Double = 15.0410686

    /** 标准地平折射地平高度：恒星/行星 -0.5667°，太阳 -0.8333°（视半径+折射）。 */
    const val H0_STAR_DEG: Double = -0.5667
    const val H0_SUN_DEG: Double = -0.8333

    // ------------------------------------------------------------------
    // 时间系统
    // ------------------------------------------------------------------

    /** UTC 时间戳 → 儒略日。 */
    fun julianDate(timeMillisUtc: Long): Double = timeMillisUtc / MS_PER_DAY + 2440587.5

    /** 儒略日 → 自 J2000.0 起的儒略世纪数。 */
    fun julianCenturies(jd: Double): Double = (jd - JD_J2000) / 36525.0

    /** 格林威治平恒星时 GMST（度），IAU 1982 级数。 */
    fun gmstDeg(jd: Double): Double {
        val d = jd - JD_J2000
        val t = d / 36525.0
        val gmst = 280.46061837 +
            360.98564736629 * d +
            0.000387933 * t * t -
            t * t * t / 38_710_000.0
        return normalizeDeg(gmst)
    }

    /** 地方恒星时 LST（度），[longitudeEastDeg] 为东经（西经取负）。 */
    fun lstDeg(jd: Double, longitudeEastDeg: Double): Double =
        normalizeDeg(gmstDeg(jd) + longitudeEastDeg)

    /** 黄赤交角（度），IAU 1980 级数取主项。 */
    fun obliquityDeg(t: Double): Double =
        23.43929111 - 0.013004167 * t - 1.6389e-7 * t * t + 5.0361e-7 * t * t * t

    // ------------------------------------------------------------------
    // 坐标变换
    // ------------------------------------------------------------------

    /** 地平坐标：alt 高度角（度），az 方位角（度，正北 0，东 90）。 */
    data class Horizontal(val altDeg: Double, val azDeg: Double)

    /**
     * 地平坐标可复用容器：60fps 渲染热路径（每帧 2000+ 次坐标变换）专用，
     * 配合 [equatorialToHorizontal] 的 out 变体实现零分配。
     */
    class HorPos {
        @JvmField var altDeg: Double = 0.0
        @JvmField var azDeg: Double = 0.0
    }

    data class RaDec(val raDeg: Double, val decDeg: Double)

    /** 赤道坐标 → 地平坐标。经典球面三角 + ENU 分量法。 */
    fun equatorialToHorizontal(
        raDeg: Double,
        decDeg: Double,
        lstDeg: Double,
        latitudeDeg: Double
    ): Horizontal {
        val out = HorPos()
        fillHorizontal(raDeg, decDeg, lstDeg, latitudeDeg, out)
        return Horizontal(out.altDeg, out.azDeg)
    }

    /** 零分配变体：结果写入 [out] 并返回同一引用，渲染循环中复用。 */
    fun equatorialToHorizontal(
        raDeg: Double,
        decDeg: Double,
        lstDeg: Double,
        latitudeDeg: Double,
        out: HorPos
    ): HorPos {
        fillHorizontal(raDeg, decDeg, lstDeg, latitudeDeg, out)
        return out
    }

    private fun fillHorizontal(
        raDeg: Double,
        decDeg: Double,
        lstDeg: Double,
        latitudeDeg: Double,
        out: HorPos
    ) {
        val hRad = DEG2RAD * normalizeDeg180(lstDeg - raDeg)
        val decRad = DEG2RAD * decDeg
        val latRad = DEG2RAD * latitudeDeg

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(hRad)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))

        // ENU 分量：E = -cosδ·sinH（西为正取负），N = sinδ·cosφ - cosδ·sinφ·cosH
        val east = -cos(decRad) * sin(hRad)
        val north = sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(hRad)
        val az = atan2(east, north)

        out.altDeg = alt * RAD2DEG
        out.azDeg = normalizeDeg(az * RAD2DEG)
    }

    /** 地平坐标 → 赤道坐标（逆向变换，供罗盘寻星反解使用）。 */
    fun horizontalToEquatorial(
        altDeg: Double,
        azDeg: Double,
        lstDeg: Double,
        latitudeDeg: Double
    ): RaDec {
        val altRad = DEG2RAD * altDeg
        val azRad = DEG2RAD * azDeg
        val latRad = DEG2RAD * latitudeDeg

        val sinDec = sin(altRad) * sin(latRad) + cos(altRad) * cos(latRad) * cos(azRad)
        val dec = asin(sinDec.coerceIn(-1.0, 1.0))

        // 时角：cosH = (sinAlt - sinφ·sinδ) / (cosφ·cosδ)
        val cosH = (sin(altRad) - sin(latRad) * sinDec) / (cos(latRad) * cos(dec))
        val h = acos(cosH.coerceIn(-1.0, 1.0))
        // 方位角 > 180°（西半球）时时角为正
        val hSigned = if (azRad > Math.PI) h else -h

        return RaDec(normalizeDeg(lstDeg + hSigned * RAD2DEG), dec * RAD2DEG)
    }

    /** 地平坐标 (Alt, Az) → ENU 单位向量：X 东、Y 北、Z 天顶。 */
    fun altAzToEnu(altDeg: Double, azDeg: Double): Vec3 {
        val alt = DEG2RAD * altDeg
        val az = DEG2RAD * azDeg
        return Vec3(
            cos(alt) * sin(az),
            cos(alt) * cos(az),
            sin(alt)
        )
    }

    /** ENU 向量 → 地平坐标。 */
    fun enuToAltAz(v: Vec3): Horizontal {
        val u = v.normalized()
        val alt = asin(u.z.coerceIn(-1.0, 1.0))
        val az = atan2(u.x, u.y)
        return Horizontal(alt * RAD2DEG, normalizeDeg(az * RAD2DEG))
    }

    /** 黄道坐标 → 赤道坐标（绕 X 轴旋转黄赤交角）。 */
    fun eclipticToEquatorial(
        lonDeg: Double,
        latDeg: Double,
        obliquityDeg: Double
    ): RaDec {
        val l = DEG2RAD * lonDeg
        val b = DEG2RAD * latDeg
        val eps = DEG2RAD * obliquityDeg

        val x = cos(b) * cos(l)
        val y = cos(b) * sin(l) * cos(eps) - sin(b) * sin(eps)
        val z = cos(b) * sin(l) * sin(eps) + sin(b) * cos(eps)

        val ra = atan2(y, x)
        val dec = asin(z.coerceIn(-1.0, 1.0))
        return RaDec(normalizeDeg(ra * RAD2DEG), dec * RAD2DEG)
    }

    /** 赤道坐标 → 黄道坐标。 */
    fun equatorialToEcliptic(
        raDeg: Double,
        decDeg: Double,
        obliquityDeg: Double
    ): Pair<Double, Double> {
        val ra = DEG2RAD * raDeg
        val dec = DEG2RAD * decDeg
        val eps = DEG2RAD * obliquityDeg

        val x = cos(dec) * cos(ra)
        val y = cos(dec) * sin(ra) * cos(eps) + sin(dec) * sin(eps)
        val z = -cos(dec) * sin(ra) * sin(eps) + sin(dec) * cos(eps)

        val lon = atan2(y, x)
        val lat = asin(z.coerceIn(-1.0, 1.0))
        return Pair(normalizeDeg(lon * RAD2DEG), lat * RAD2DEG)
    }

    // ------------------------------------------------------------------
    // 大气折射
    // ------------------------------------------------------------------

    /**
     * Bennett (1982) 大气折射公式：输入真高度角（度），返回折射角增量（角分）。
     * 标准大气（1010 mbar / 10°C），精度约 ±0.07'（h > 5° 时）。
     */
    fun bennettRefractionArcmin(trueAltDeg: Double): Double {
        val h = trueAltDeg
        return 1.02 / tan(DEG2RAD * (h + 10.3 / (h + 5.11)))
    }

    /** 视高度角 = 真高度角 + 折射抬升（仅当真高度 > -1° 时修正）。 */
    fun apparentAltitudeDeg(trueAltDeg: Double): Double =
        if (trueAltDeg > -1.0) trueAltDeg + bennettRefractionArcmin(trueAltDeg) / 60.0
        else trueAltDeg

    // ------------------------------------------------------------------
    // 球面角距
    // ------------------------------------------------------------------

    /** 两天体球面角距（度），Haversine 公式。 */
    fun angularSeparationDeg(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
        val r1 = DEG2RAD * ra1
        val d1 = DEG2RAD * dec1
        val r2 = DEG2RAD * ra2
        val d2 = DEG2RAD * dec2
        val sdh = sin((r2 - r1) / 2.0)
        val sdd = sin((d2 - d1) / 2.0)
        val a = sdd * sdd + cos(d1) * cos(d2) * sdh * sdh
        return 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0))) * RAD2DEG
    }

    // ------------------------------------------------------------------
    // 银道坐标 → 赤道坐标（J2000）
    // ------------------------------------------------------------------

    private const val GAL_POLE_RA_DEG = 192.85948
    private const val GAL_POLE_DEC_DEG = 27.12825
    private const val GAL_LON_NCP_DEG = 122.93192

    fun galacticToEquatorial(lonDeg: Double, latDeg: Double): RaDec {
        val l = DEG2RAD * lonDeg
        val b = DEG2RAD * latDeg
        val raG = DEG2RAD * GAL_POLE_RA_DEG
        val decG = DEG2RAD * GAL_POLE_DEC_DEG
        val lonNcpMinusL = DEG2RAD * GAL_LON_NCP_DEG - l

        val sinDec = sin(decG) * sin(b) + cos(decG) * cos(b) * cos(lonNcpMinusL)
        val dec = asin(sinDec.coerceIn(-1.0, 1.0))

        val y = cos(b) * sin(lonNcpMinusL)
        val x = cos(decG) * sin(b) - sin(decG) * cos(b) * cos(lonNcpMinusL)
        val ra = raG + atan2(y, x)

        return RaDec(normalizeDeg(ra * RAD2DEG), dec * RAD2DEG)
    }

    // ------------------------------------------------------------------
    // 升起 / 中天 / 落下
    // ------------------------------------------------------------------

    data class RiseTransitSet(
        val riseMillis: Long?,
        val transitMillis: Long?,
        val transitMaxAltDeg: Double,
        val setMillis: Long?,
        val circumpolar: Boolean,
        val neverRises: Boolean
    )

    /**
     * 计算天体相对 [dayStartMillis] 所在窗口的中天时刻（折回 [t0, t0+24h)），
     * 以及对应的升起/落下时刻（不折回，可能跨前后日，保证 rise < transit < set 时序）。
     * h0 为标准地平高度（含折射）。|cosH0| > 1 时判定拱极/永不升起。
     */
    fun riseTransitSet(
        raDeg: Double,
        decDeg: Double,
        latitudeDeg: Double,
        dayStartMillis: Long,
        longitudeEastDeg: Double = 0.0,
        h0Deg: Double = H0_STAR_DEG
    ): RiseTransitSet {
        val dayMs = MS_PER_DAY.toLong()
        val latRad = DEG2RAD * latitudeDeg
        val decRad = DEG2RAD * decDeg

        // —— 中天：迭代求解 LST(t) ≡ RA
        var transit = dayStartMillis.toDouble()
        repeat(2) {
            val jd = julianDate(transit.toLong())
            val lst = lstDeg(jd, longitudeEastDeg)
            val offsetDeg = normalizeDeg(raDeg - lst)
            transit += offsetDeg / SIDEREAL_DEG_PER_HOUR * 3_600_000.0
        }
        // 折回 [t0, t0+24h)
        while (transit < dayStartMillis) transit += dayMs
        while (transit >= dayStartMillis + dayMs) transit -= dayMs

        // —— 半昼弧
        val cosH0 = (sin(DEG2RAD * h0Deg) - sin(latRad) * sin(decRad)) /
            (cos(latRad) * cos(decRad))
        val transitAlt = 90.0 - abs(latitudeDeg - decDeg)

        if (cosH0 > 1.0) {
            return RiseTransitSet(null, transit.toLong(), transitAlt, null, circumpolar = false, neverRises = true)
        }
        if (cosH0 < -1.0) {
            return RiseTransitSet(null, transit.toLong(), transitAlt, null, circumpolar = true, neverRises = false)
        }
        val h0 = acos(cosH0) * RAD2DEG
        val halfDayMs = h0 / SIDEREAL_DEG_PER_HOUR * 3_600_000.0

        return RiseTransitSet(
            riseMillis = (transit - halfDayMs).toLong(),
            transitMillis = transit.toLong(),
            transitMaxAltDeg = transitAlt,
            setMillis = (transit + halfDayMs).toLong(),
            circumpolar = false,
            neverRises = false
        )
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 归一化到 [0, 360)。 */
    fun normalizeDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0.0) d += 360.0
        return d
    }

    /** 归一化到 [-180, 180)。 */
    fun normalizeDeg180(deg: Double): Double {
        var d = normalizeDeg(deg)
        if (d >= 180.0) d -= 360.0
        return d
    }
}
