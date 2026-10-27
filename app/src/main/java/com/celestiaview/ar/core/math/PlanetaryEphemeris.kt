package com.celestiaview.ar.core.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 太阳系天体解析星历：JPL J2000 轨道根数（每儒略世纪变率）+ 开普勒方程牛顿迭代。
 * 精度约数角分（1800-2050 年），足以满足目视观星引导。
 */
object PlanetaryEphemeris {

    enum class PlanetId { MERCURY, VENUS, EARTH, MARS, JUPITER, SATURN }

    data class OrbitalElements(
        val a: Double,        // 半长轴 AU
        val e: Double,        // 偏心率
        val iDeg: Double,     // 轨道倾角
        val LDeg: Double,     // 平黄经
        val varpiDeg: Double, // 近日点黄经
        val omegaDeg: Double  // 升交点黄经
    )

    data class GeoEphemeris(
        val raDeg: Double,
        val decDeg: Double,
        val distanceAu: Double,
        val eclipticLonDeg: Double,
        val eclipticLatDeg: Double
    )

    data class MoonEphemeris(
        val raDeg: Double,
        val decDeg: Double,
        val distanceKm: Double,
        val eclipticLonDeg: Double,
        val eclipticLatDeg: Double,
        val phaseAngleDeg: Double,      // 0=新月 … 180=满月
        val illuminationFraction: Double, // 0..1
        val phaseNameZh: String,
        val phaseNameEn: String,
        val phaseIndex: Int             // 0..7，8 阶段
    )

    /** JPL Keplerian elements for approximate positions (J2000 + rates/century)。 */
    private fun elements(p: PlanetId, t: Double): OrbitalElements = when (p) {
        PlanetId.MERCURY -> OrbitalElements(
            0.38709927, 0.20563593 + 0.00001906 * t,
            7.00497902 - 0.00594749 * t,
            AstrometryEngine.normalizeDeg(252.25032350 + 149472.67411175 * t),
            77.45779628 + 0.16047689 * t, 48.33076593 - 0.12534081 * t
        )
        PlanetId.VENUS -> OrbitalElements(
            0.72333566, 0.00677672 - 0.00004107 * t,
            3.39467605 - 0.00078890 * t,
            AstrometryEngine.normalizeDeg(181.97909950 + 58517.81538729 * t),
            131.60246718 + 0.00268329 * t, 76.67984255 - 0.27769418 * t
        )
        PlanetId.EARTH -> OrbitalElements(
            1.00000261, 0.01671123 - 0.00004392 * t,
            -0.00001531 - 0.01294668 * t,
            AstrometryEngine.normalizeDeg(100.46457166 + 35999.37244981 * t),
            102.93768193 + 0.32327364 * t, 0.0
        )
        PlanetId.MARS -> OrbitalElements(
            1.52371034, 0.09339410 + 0.00007882 * t,
            1.84969142 - 0.00813131 * t,
            AstrometryEngine.normalizeDeg(-4.55343205 + 19140.30268499 * t),
            -23.94362959 + 0.44441088 * t, 49.55953891 - 0.29257343 * t
        )
        PlanetId.JUPITER -> OrbitalElements(
            5.20288700, 0.04838624 - 0.00013253 * t,
            1.30439695 - 0.00183714 * t,
            AstrometryEngine.normalizeDeg(34.39644051 + 3034.74612775 * t),
            14.72847983 + 0.21252668 * t, 100.47390909 + 0.20469106 * t
        )
        PlanetId.SATURN -> OrbitalElements(
            9.53667594, 0.05386179 - 0.00050991 * t,
            2.48599187 + 0.00193609 * t,
            AstrometryEngine.normalizeDeg(49.95424423 + 1222.49362201 * t),
            92.59887831 - 0.41897216 * t, 113.66242448 - 0.28867794 * t
        )
    }

    /** 行星日心黄道直角坐标（AU）。 */
    fun heliocentricEcliptic(p: PlanetId, t: Double): Vec3 {
        val el = elements(p, t)
        // 平近点角与开普勒方程（牛顿迭代 5 次）
        val mRad = AstrometryEngine.DEG2RAD * AstrometryEngine.normalizeDeg180(el.LDeg - el.varpiDeg)
        var e = mRad
        repeat(5) {
            e -= (e - el.e * sin(e) - mRad) / (1.0 - el.e * cos(e))
        }
        // 轨道平面坐标
        val xp = el.a * (cos(e) - el.e)
        val yp = el.a * sqrt(1.0 - el.e * el.e) * sin(e)

        // 旋转至黄道坐标：ω = ϖ − Ω
        val wRad = AstrometryEngine.DEG2RAD * (el.varpiDeg - el.omegaDeg)
        val nRad = AstrometryEngine.DEG2RAD * el.omegaDeg
        val iRad = AstrometryEngine.DEG2RAD * el.iDeg

        val cw = cos(wRad); val sw = sin(wRad)
        val cn = cos(nRad); val sn = sin(nRad)
        val ci = cos(iRad); val si = sin(iRad)

        val x = (cw * cn - sw * sn * ci) * xp + (-sw * cn - cw * sn * ci) * yp
        val y = (cw * sn + sw * cn * ci) * xp + (-sw * sn + cw * cn * ci) * yp
        val z = (sw * si) * xp + (cw * si) * yp
        return Vec3(x, y, z)
    }

    /**
     * 地心黄道直角坐标（AU）。
     * 契约：[PlanetId.EARTH] 为地心参考原点，不得作为目标查询（返回零向量 → NaN），
     * 调用方（如 SkyViewModel.computeFrame 的行星过滤）负责排除。
     */
    fun geocentricEcliptic(p: PlanetId, jd: Double): Vec3 {
        val t = AstrometryEngine.julianCenturies(jd)
        return heliocentricEcliptic(p, t) - heliocentricEcliptic(PlanetId.EARTH, t)
    }

    private fun eclipticVecToGeo(v: Vec3, t: Double): GeoEphemeris {
        val eps = AstrometryEngine.obliquityDeg(t)
        val eq = AstrometryEngine.eclipticToEquatorial(
            AstrometryEngine.normalizeDeg(atan2(v.y, v.x) * AstrometryEngine.RAD2DEG),
            AstrometryEngine.normalizeDeg(
                kotlin.math.asin((v.z / v.length()).coerceIn(-1.0, 1.0)) * AstrometryEngine.RAD2DEG
            ),
            eps
        )
        return GeoEphemeris(
            raDeg = eq.raDeg,
            decDeg = eq.decDeg,
            distanceAu = v.length(),
            eclipticLonDeg = AstrometryEngine.normalizeDeg(atan2(v.y, v.x) * AstrometryEngine.RAD2DEG),
            eclipticLatDeg = kotlin.math.asin((v.z / v.length()).coerceIn(-1.0, 1.0)) * AstrometryEngine.RAD2DEG
        )
    }

    /** 行星观测星历（地心赤道坐标）。 */
    fun planetEphemeris(p: PlanetId, jd: Double): GeoEphemeris =
        eclipticVecToGeo(geocentricEcliptic(p, jd), AstrometryEngine.julianCenturies(jd))

    /** 太阳星历 = 地球日心坐标取反。 */
    fun sunEphemeris(jd: Double): GeoEphemeris {
        val t = AstrometryEngine.julianCenturies(jd)
        return eclipticVecToGeo(heliocentricEcliptic(PlanetId.EARTH, t) * -1.0, t)
    }

    // ------------------------------------------------------------------
    // 月球：低阶解析理论（中心差/出差/二均差/周年差等 10 项黄经摄动）
    // ------------------------------------------------------------------

    /** 月球黄经/黄纬/距离（含主要周期项）。 */
    fun moonEcliptic(jd: Double): Triple<Double, Double, Double> {
        val t = AstrometryEngine.julianCenturies(jd)
        // 基本辐角
        val lm = AstrometryEngine.normalizeDeg(218.316 + 481267.881 * t)   // 平黄经
        val mm = AstrometryEngine.normalizeDeg(134.963 + 477198.868 * t)   // 月平近点角
        val ms = AstrometryEngine.normalizeDeg(357.529 + 35999.050 * t)    // 日平近点角
        val d = AstrometryEngine.normalizeDeg(297.850 + 445267.111 * t)    // 平距角
        val f = AstrometryEngine.normalizeDeg(93.272 + 483202.018 * t)     // 纬度参数

        fun sinD(deg: Double) = sin(AstrometryEngine.DEG2RAD * deg)

        // 黄经：主项 + 中心差 + 出差 + 二均差 + 周年差 + 变差…
        val lon = lm +
            6.289 * sinD(mm) +                    // 中心差 (evection 主体)
            1.274 * sinD(2 * d - mm) +            // 出差 evection
            0.658 * sinD(2 * d) +                 // 二均差 variation
            0.214 * sinD(2 * mm) -
            0.186 * sinD(ms) -                    // 周年差
            0.114 * sinD(2 * f) +
            0.059 * sinD(2 * d - 2 * mm) +
            0.057 * sinD(2 * d - mm - ms) +
            0.053 * sinD(2 * d + mm) +
            0.046 * sinD(2 * d - ms) +
            0.041 * sinD(mm - ms) +
            0.035 * sinD(d)

        // 黄纬
        val lat = 5.128 * sinD(f) +
            0.281 * sinD(mm + f) +
            0.278 * sinD(mm - f) +
            0.173 * sinD(2 * d - f) +
            0.055 * sinD(2 * d - mm + f) -
            0.046 * sinD(2 * d - mm - f)

        // 距离（km）
        val distKm = 385000.56 -
            20905.355 * cos(AstrometryEngine.DEG2RAD * mm) -
            3699.111 * cos(AstrometryEngine.DEG2RAD * (2 * d - mm)) -
            2955.968 * cos(AstrometryEngine.DEG2RAD * 2 * d) -
            569.925 * cos(AstrometryEngine.DEG2RAD * 2 * mm)

        return Triple(AstrometryEngine.normalizeDeg(lon), lat, distKm)
    }

    /** 8 阶段月相名称。 */
    private val PHASE_NAMES_ZH = listOf(
        "新月", "蛾眉月", "上弦月", "盈凸月", "满月", "亏凸月", "下弦月", "残月"
    )
    private val PHASE_NAMES_EN = listOf(
        "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent"
    )

    fun moonEphemeris(jd: Double): MoonEphemeris {
        val t = AstrometryEngine.julianCenturies(jd)
        val (lonMoon, latMoon, distKm) = moonEcliptic(jd)
        val lonSun = sunEphemeris(jd).eclipticLonDeg

        val phase = AstrometryEngine.normalizeDeg(lonMoon - lonSun)
        val illum = (1.0 - cos(AstrometryEngine.DEG2RAD * phase)) / 2.0
        val idx = ((phase + 22.5) / 45.0).toInt() % 8

        val eq = AstrometryEngine.eclipticToEquatorial(lonMoon, latMoon, AstrometryEngine.obliquityDeg(t))
        return MoonEphemeris(
            raDeg = eq.raDeg,
            decDeg = eq.decDeg,
            distanceKm = distKm,
            eclipticLonDeg = lonMoon,
            eclipticLatDeg = latMoon,
            phaseAngleDeg = phase,
            illuminationFraction = illum,
            phaseNameZh = PHASE_NAMES_ZH[idx],
            phaseNameEn = PHASE_NAMES_EN[idx],
            phaseIndex = idx
        )
    }

    // ------------------------------------------------------------------
    // 视星等（展示级近似）
    // ------------------------------------------------------------------

    /**
     * 行星视星等（展示级近似）：V ≈ H + 5·log10(r·Δ) + C·FV，
     * FV 为相位角（太阳-行星-地球），并按实测范围截断保证展示合理。
     */
    fun apparentMagnitude(p: PlanetId, jd: Double): Double {
        val t = AstrometryEngine.julianCenturies(jd)
        val r = heliocentricEcliptic(p, t).length()
        val delta = geocentricEcliptic(p, jd).length()
        val sunEarth = heliocentricEcliptic(PlanetId.EARTH, t).length()

        // 相位角 FV：cos FV = (r² + Δ² − R²) / (2rΔ)
        val cosFv = ((r * r + delta * delta - sunEarth * sunEarth) / (2.0 * r * delta))
            .coerceIn(-1.0, 1.0)
        val fvDeg = kotlin.math.acos(cosFv) * AstrometryEngine.RAD2DEG

        val raw = when (p) {
            PlanetId.MERCURY -> -0.36 + 5.0 * lg(r * delta) + 0.027 * fvDeg
            PlanetId.VENUS -> -4.29 + 5.0 * lg(r * delta) + 0.013 * fvDeg
            PlanetId.EARTH -> -3.99
            PlanetId.MARS -> -1.38 + 5.0 * lg(r * delta) + 0.016 * fvDeg
            PlanetId.JUPITER -> -9.28 + 5.0 * lg(r * delta) + 0.005 * fvDeg
            PlanetId.SATURN -> -8.55 + 5.0 * lg(r * delta) + 0.044 * fvDeg
        }
        val lo = when (p) {
            PlanetId.MERCURY -> -2.9; PlanetId.VENUS -> -4.95; PlanetId.EARTH -> -3.99
            PlanetId.MARS -> -2.9; PlanetId.JUPITER -> -2.9; PlanetId.SATURN -> 0.4
        }
        val hi = when (p) {
            PlanetId.MERCURY -> 5.7; PlanetId.VENUS -> -3.7; PlanetId.EARTH -> -3.99
            PlanetId.MARS -> 1.9; PlanetId.JUPITER -> -1.3; PlanetId.SATURN -> 1.7
        }
        return raw.coerceIn(lo, hi)
    }

    private fun lg(x: Double): Double = kotlin.math.log10(x)

    /** 恒星绝对星等（已知视星等与距离光年）。 */
    fun absoluteMagnitude(apparentMag: Double, distanceLy: Double): Double {
        val parsecs = distanceLy / 3.26156
        return if (parsecs > 0.0) apparentMag - 5.0 * (kotlin.math.log10(parsecs) - 1.0)
        else apparentMag
    }

    // ------------------------------------------------------------------
    // 行星展示元数据
    // ------------------------------------------------------------------

    data class PlanetMeta(
        val id: PlanetId,
        val key: String,
        val nameZh: String,
        val nameEn: String,
        val description: String,
        val colorHex: Long
    )

    val planetMeta: List<PlanetMeta> = listOf(
        PlanetMeta(PlanetId.MERCURY, "MERCURY", "水星", "Mercury",
            "距太阳最近的行星，只在晨昏蒙影中短暂可见。表面昼夜温差超过 600°C，酷似月球的多坑地貌。",
            0xFFB8B8C0),
        PlanetMeta(PlanetId.VENUS, "VENUS", "金星", "Venus",
            "地球的“姊妹星”，浓密二氧化碳大气造就 465°C 的失控温室效应。夜空中除月亮外最亮的天体，中国古代称“启明”与“长庚”。",
            0xFFF5E9D3),
        PlanetMeta(PlanetId.EARTH, "EARTH", "地球", "Earth",
            "我们的家园——观测者脚下的蓝色星球，日心轨道计算的参考基准点。",
            0xFF6EB5FF),
        PlanetMeta(PlanetId.MARS, "MARS", "火星", "Mars",
            "荧荧如火的红行星，古称“荧惑”。拥有太阳系最高的奥林帕斯火山与最深的峡谷，是人类星际移民的首选目标。",
            0xFFE0663A),
        PlanetMeta(PlanetId.JUPITER, "JUPITER", "木星", "Jupiter",
            "太阳系巨无霸，质量是其他行星总和的 2.5 倍。大红斑风暴已肆虐数百年，四颗伽利略卫星肉眼可辨。",
            0xFFE8B87A),
        PlanetMeta(PlanetId.SATURN, "SATURN", "土星", "Saturn",
            "戴着壮丽光环的气态巨行星，古称“镇星”。光环由无数冰粒构成，行星密度低到能浮在水面之上。",
            0xFFEFD9A0)
    )
}
