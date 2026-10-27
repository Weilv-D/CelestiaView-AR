package com.celestiaview.ar.data.catalog

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.data.model.HorizonPoint

/**
 * 银河系银道面光带：沿银道坐标 (l, b) 生成的天球多边形轮廓 + 银心方向星尘粒子云。
 * 银道→赤道转换采用 J2000 银极（RA 192.85948°, Dec 27.12825°）。
 */
object MilkyWayBandData {

    /** 银道面北缘（b = +8°）轮廓点，l 从 0° 到 355°，步长 5°。 */
    val northEdge: List<HorizonPoint> = bandEdge(+8.0)

    /** 银道面南缘（b = -8°）轮廓点。 */
    val southEdge: List<HorizonPoint> = bandEdge(-8.0)

    /** 银道中线（b = 0°），用于辉光带中心增强。 */
    val centerLine: List<HorizonPoint> = bandEdge(0.0)

    private fun bandEdge(bDeg: Double): List<HorizonPoint> =
        (0..355 step 5).map { l ->
            val eq = AstrometryEngine.galacticToEquatorial(l.toDouble(), bDeg)
            HorizonPoint(eq.raDeg, eq.decDeg)
        }

    /**
     * 完整光带多边形：北缘正向 + 南缘逆向闭合。
     * 渲染时逐边大圆细分，保证广角下的自然弯曲。
     */
    val bandPolygon: List<HorizonPoint> = northEdge + southEdge.reversed()

    /** 银心方向（人马座，l≈0°, b≈0°）赤道坐标。 */
    val galacticCenter: HorizonPoint =
        AstrometryEngine.galacticToEquatorial(0.0, 0.0).let { HorizonPoint(it.raDeg, it.decDeg) }

    /**
     * 银心方向高密度背景星尘粒子（确定性伪随机，编译期等价于固定种子）。
     * 分布在 l ∈ [330°, 30°]、b ∈ [-7°, +7°] 的银心团块。
     */
    val galacticCenterDust: List<HorizonPoint> = run {
        val pts = ArrayList<HorizonPoint>(160)
        var seed = 20261027L // 固定种子保证可复现
        fun rnd(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33).toDouble() / (1L shl 31).toDouble()).coerceIn(0.0, 0.999999)
        }
        repeat(160) {
            val lRaw = rnd() * 60.0 - 30.0
            val l = if (lRaw < 0.0) 360.0 + lRaw else lRaw
            val b = (rnd() * 2.0 - 1.0) * 7.0
            val eq = AstrometryEngine.galacticToEquatorial(l, b)
            pts.add(HorizonPoint(eq.raDeg, eq.decDeg))
        }
        pts
    }

    /** 银河各段相对亮暗权重（银心最亮），按银经采样。 */
    fun brightnessAt(lDeg: Double): Float {
        // 银心 (l≈0) 与盾牌-南十字段 (l≈330) 最亮，反银心 (l≈180) 最暗
        val distFromCenter = kotlin.math.min(
            AstrometryEngine.normalizeDeg(lDeg),
            360.0 - AstrometryEngine.normalizeDeg(lDeg)
        )
        return (1.0 - (distFromCenter / 180.0) * 0.75).toFloat().coerceIn(0.25f, 1f)
    }
}
