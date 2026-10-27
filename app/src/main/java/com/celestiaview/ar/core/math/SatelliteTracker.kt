package com.celestiaview.ar.core.math

import com.celestiaview.ar.core.math.AstrometryEngine.normalizeDeg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 近地轨道人造天体（国际空间站 ISS / 中国空间站 天宫 CSS）实时追踪器。
 *
 * 模型：圆轨道 + J2 升交点赤经进动（一阶近似）。
 *   r(t) = Rz(Ω(t)) · Rx(i) · r0·(cos u, sin u, 0)
 *   Ω(t) = Ω0 + dΩ/dt · Δt，dΩ/dt ≈ -1.5·n·J2·(Re/p)²·cos i（RETRO 进动，约 -5°/天）
 *   u(t) = u0 + n·Δt（纬度幅角，圆轨道即相位）
 * 该简化模型与真实 TLE 在数天尺度内可保持大致方位一致性（规格允许近似圆轨道模型）。
 */
object SatelliteTracker {

    /** 圆轨道根数（历元锚定：2026-10-27 00:00 UTC）。 */
    data class Elements(
        val key: String,
        val nameZh: String,
        val nameEn: String,
        val inclinationDeg: Double,
        val periodMin: Double,
        val meanAltKm: Double,
        val raanAtEpochDeg: Double,
        val argOfLatitudeAtEpochDeg: Double,
        /** J2 升交点进动速率（度/天）。 */
        val raanPrecessionDegPerDay: Double
    )

    data class SatelliteInfo(
        val elements: Elements,
        val subLatDeg: Double,
        val subLonDeg: Double,
        val altKm: Double,
        val altDeg: Double,
        val azDeg: Double,
        val rangeKm: Double,
        val angularSpeedDegPerSec: Double,
        val sunlit: Boolean
    ) {
        /** 观测者可目视：在地平线上、卫星被阳光照亮。 */
        val visuallyObservable: Boolean get() = altDeg > 0.0 && sunlit
    }

    /** 一次过境事件。 */
    data class Pass(
        val elements: Elements,
        val startMillis: Long,
        val maxMillis: Long,
        val endMillis: Long,
        val maxAltDeg: Double,
        val startAzDeg: Double,
        val maxAzDeg: Double,
        val endAzDeg: Double,
        /** 全程卫星被照亮且观测者天光足够暗（可在肉眼中看到移动的“星”）。 */
        val visiblyBright: Boolean
    )

    private const val RE_KM = 6378.137
    private val EPOCH_MILLIS: Long =
        java.time.Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()

    val ISS = Elements(
        key = "ISS", nameZh = "国际空间站", nameEn = "ISS (Zarya)",
        inclinationDeg = 51.6443, periodMin = 92.68, meanAltKm = 420.0,
        raanAtEpochDeg = 247.5, argOfLatitudeAtEpochDeg = 0.0,
        raanPrecessionDegPerDay = -5.00
    )

    val CSS = Elements(
        key = "CSS", nameZh = "中国空间站·天宫", nameEn = "Tiangong CSS",
        inclinationDeg = 41.4725, periodMin = 91.48, meanAltKm = 389.0,
        raanAtEpochDeg = 131.8, argOfLatitudeAtEpochDeg = 45.0,
        raanPrecessionDegPerDay = -4.13
    )

    val all: List<Elements> = listOf(ISS, CSS)

    // ------------------------------------------------------------------
    // 轨道几何
    // ------------------------------------------------------------------

    /** ECI 坐标（km）与速度（km/s），ECI ≈ J2000 赤道惯性系（忽略岁差，数天尺度足够）。 */
    private fun stateVector(e: Elements, jd: Double): Pair<Vec3, Vec3> {
        val dtDays = (jd - AstrometryEngine.julianDate(EPOCH_MILLIS))
        val nRadPerDay = 2.0 * PI / (e.periodMin / 1440.0)
        val u = Math.toRadians(e.argOfLatitudeAtEpochDeg) + nRadPerDay * dtDays
        val om = Math.toRadians(e.raanAtEpochDeg + e.raanPrecessionDegPerDay * dtDays)
        val inc = Math.toRadians(e.inclinationDeg)
        val r = RE_KM + e.meanAltKm

        val cu = cos(u); val su = sin(u)
        val co = cos(om); val so = sin(om)
        val ci = cos(inc); val si = sin(inc)

        val pos = Vec3(
            r * (co * cu - so * su * ci),
            r * (so * cu + co * su * ci),
            r * (su * si)
        )
        // 轨道角速度矢量 ω = n·n̂，n̂ = (sin i sinΩ, -sin i cosΩ, cos i)；v = ω × r（km/s）
        val nRadPerSec = nRadPerDay / 86400.0
        val omega = Vec3(nRadPerSec * si * so, -nRadPerSec * si * co, nRadPerSec * ci)
        val vel = Vec3(
            omega.y * pos.z - omega.z * pos.y,
            omega.z * pos.x - omega.x * pos.z,
            omega.x * pos.y - omega.y * pos.x
        )
        return pos to vel
    }

    /** 太阳单位方向（ECI 赤道系）。 */
    private fun sunDirectionEci(jd: Double): Vec3 {
        val eph = PlanetaryEphemeris.sunEphemeris(jd)
        val a = Math.toRadians(eph.raDeg)
        val d = Math.toRadians(eph.decDeg)
        return Vec3(cos(d) * cos(a), cos(d) * sin(a), sin(d))
    }

    /** 卫星是否被阳光照亮（圆柱地影模型）。 */
    fun isSunlit(satPosEciKm: Vec3, jd: Double): Boolean {
        val s = sunDirectionEci(jd)
        val along = satPosEciKm.dot(s)
        if (along >= 0.0) return true
        val perp2 = satPosEciKm.dot(satPosEciKm) - along * along
        return perp2 > RE_KM * RE_KM
    }

    /** 观测地平坐标解算。 */
    fun observe(e: Elements, jd: Double, latitudeDeg: Double, longitudeEastDeg: Double): SatelliteInfo {
        val (sat, vel) = stateVector(e, jd)
        val gmstRad = Math.toRadians(AstrometryEngine.gmstDeg(jd))
        val theta = gmstRad + Math.toRadians(longitudeEastDeg)
        val phi = Math.toRadians(latitudeDeg)

        val cp = cos(phi); val sp = sin(phi)
        val ct = cos(theta); val st = sin(theta)
        // 观测者 ECI 位置
        val obs = Vec3(RE_KM * cp * ct, RE_KM * cp * st, RE_KM * sp)
        val rho = sat - obs
        val range = rho.length()

        // ECI → ENU 基
        val east = Vec3(-st, ct, 0.0)
        val north = Vec3(-sp * ct, -sp * st, cp)
        val up = Vec3(cp * ct, cp * st, sp)
        val ecomp = rho.dot(east)
        val ncomp = rho.dot(north)
        val ucomp = rho.dot(up)

        val altDeg = Math.toDegrees(asin((ucomp / range).coerceIn(-1.0, 1.0)))
        val azDeg = normalizeDeg(Math.toDegrees(atan2(ecomp, ncomp)))

        // 视角速度 |ρ × ρ̇| / |ρ|²
        val rhoDot = vel
        val cross = Vec3(
            rho.y * rhoDot.z - rho.z * rhoDot.y,
            rho.z * rhoDot.x - rho.x * rhoDot.z,
            rho.x * rhoDot.y - rho.y * rhoDot.x
        )
        val appRate = Math.toDegrees(cross.length() / (range * range))

        val subLat = Math.toDegrees(asin((sat.z / sat.length()).coerceIn(-1.0, 1.0)))
        val subLon = normalizeDeg(Math.toDegrees(atan2(sat.y, sat.x)) - AstrometryEngine.gmstDeg(jd))
        val subLonEast = if (subLon > 180.0) subLon - 360.0 else subLon

        return SatelliteInfo(
            elements = e,
            subLatDeg = subLat, subLonDeg = subLonEast,
            altKm = sat.length() - RE_KM,
            altDeg = altDeg, azDeg = azDeg,
            rangeKm = range,
            angularSpeedDegPerSec = appRate,
            sunlit = isSunlit(sat, jd)
        )
    }

    // ------------------------------------------------------------------
    // 过境预报
    // ------------------------------------------------------------------

    /**
     * 预报未来 [hoursAhead] 小时内高度角峰值超过 [minAltDeg] 的过境。
     * [visibleOnly] 只保留“观测者晨昏蒙影以下 + 卫星被照亮”的可目视过境。
     */
    fun nextPasses(
        e: Elements,
        fromMillis: Long,
        latitudeDeg: Double,
        longitudeEastDeg: Double,
        hoursAhead: Double = 24.0,
        minAltDeg: Double = 10.0,
        visibleOnly: Boolean = true,
        maxPasses: Int = 4
    ): List<Pass> {
        val out = ArrayList<Pass>()
        val stepMs = 30_000L
        val total = (hoursAhead * 3600.0 * 1000.0).toLong()
        var inPass = false
        var startMs = 0L
        var startAz = 0.0
        var maxAlt = Double.MIN_VALUE
        var maxMs = 0L
        var maxAz = 0.0
        var prevAz = 0.0
        var sunlitAtMax = false

        var t = fromMillis
        val end = fromMillis + total
        while (t <= end && out.size < maxPasses) {
            val jd = AstrometryEngine.julianDate(t)
            val info = observe(e, jd, latitudeDeg, longitudeEastDeg)
            if (info.altDeg >= minAltDeg) {
                if (!inPass) {
                    inPass = true
                    startMs = t
                    startAz = info.azDeg
                    maxAlt = info.altDeg
                    maxMs = t
                    maxAz = info.azDeg
                    sunlitAtMax = info.sunlit
                } else if (info.altDeg > maxAlt) {
                    maxAlt = info.altDeg
                    maxMs = t
                    maxAz = info.azDeg
                    sunlitAtMax = info.sunlit
                }
                prevAz = info.azDeg
            } else if (inPass) {
                inPass = false
                var visibly = sunlitAtMax
                if (visibly) {
                    val sun = PlanetaryEphemeris.sunEphemeris(AstrometryEngine.julianDate(maxMs))
                    val sunH = AstrometryEngine.equatorialToHorizontal(
                        sun.raDeg, sun.decDeg,
                        AstrometryEngine.lstDeg(AstrometryEngine.julianDate(maxMs), longitudeEastDeg),
                        latitudeDeg
                    )
                    visibly = sunH.altDeg < -6.0
                }
                if (!visibleOnly || visibly) {
                    out.add(
                        Pass(e, startMs, maxMs, t, maxAlt, startAz, maxAz, prevAz, visibly)
                    )
                }
            }
            t += stepMs
        }
        return out
    }

    /** 两空间站未来可目视过境合并排序（供今夜向导）。 */
    fun tonightVisiblePasses(
        fromMillis: Long, latitudeDeg: Double, longitudeEastDeg: Double
    ): List<Pass> =
        all.flatMap { nextPasses(it, fromMillis, latitudeDeg, longitudeEastDeg) }
            .filter { it.visiblyBright }
            .sortedBy { it.startMillis }

    /** 金色太阳能翼图标提示语。 */
    fun magnitudeEstimate(info: SatelliteInfo): Double {
        // 简化：标准 ISS 峰值 -3 等随距离/角度衰减
        val ref = if (info.elements.key == "ISS") -3.2 else -2.0
        val rangeFactor = 460.0 / info.rangeKm.coerceAtLeast(200.0)
        return (ref - 5.0 * kotlin.math.log10(rangeFactor.coerceAtLeast(0.2)))
            .coerceIn(-1.0, 5.0)
    }
}
