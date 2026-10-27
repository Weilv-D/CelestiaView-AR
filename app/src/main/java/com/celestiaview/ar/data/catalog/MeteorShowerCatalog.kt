package com.celestiaview.ar.data.catalog

import java.util.Calendar

/**
 * 八大著名流星雨目录：真实辐射点赤经赤纬（J2000，忽略周年漂移）、
 * 活跃期、极大期与天顶每小时流量 ZHR。
 */
data class MeteorShower(
    val key: String,
    val nameZh: String,
    val nameEn: String,
    val abbreviation: String,
    val radiantRaHours: Double,
    val radiantDecDeg: Double,
    /** 活跃期（月, 日）。 */
    val activeFromMonth: Int, val activeFromDay: Int,
    val activeToMonth: Int, val activeToDay: Int,
    val peakMonth: Int, val peakDay: Int,
    val zhr: Int,
    val parentBody: String,
    val noteZh: String
) {
    val radiantRaDeg: Double get() = radiantRaHours * 15.0

    /** 距极大期（天）绝对值，供强度权重。 */
    fun daysFromPeak(calendar: Calendar): Int {
        val now = Calendar.getInstance().apply {
            clear()
            set(Calendar.MONTH, calendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
        }
        val peak = Calendar.getInstance().apply {
            clear()
            set(Calendar.MONTH, peakMonth - 1)
            set(Calendar.DAY_OF_MONTH, peakDay)
        }
        var diff = (now.timeInMillis - peak.timeInMillis) / 86_400_000L
        if (diff > 182) diff -= 365
        if (diff < -182) diff += 365
        return diff.toInt()
    }

    /** 当日是否处于活跃期（跨年区间安全处理）。 */
    fun isActiveOn(month1based: Int, day1based: Int): Boolean {
        val cur = month1based * 100 + day1based
        val from = activeFromMonth * 100 + activeFromDay
        val to = activeToMonth * 100 + activeToDay
        return if (from <= to) cur in from..to else cur >= from || cur <= to
    }

    /** 视觉强度权重 0..1：ZHR 与距极大日数的组合。 */
    fun intensityWeight(calendar: Calendar): Double {
        val zhrW = (zhr / 150.0).coerceIn(0.05, 1.0)
        val dayFalloff = Math.exp(-Math.abs(daysFromPeak(calendar)) / 9.0)
        return (0.35 + 0.65 * dayFalloff) * zhrW
    }
}

object MeteorShowerCatalog {

    val showers: List<MeteorShower> = listOf(
        MeteorShower(
            "QUA", "象限仪座流星雨", "Quadrantids", "QUA",
            15.28, 49.5, 12, 28, 1, 12, 1, 3, 120,
            "小行星 2003 EH1",
            "北半球三大流星雨之一，极大仅持续约 6 小时，辐射点位于牧夫座与天龙座之间（旧象限仪座）。"
        ),
        MeteorShower(
            "LYR", "天琴座流星雨", "Lyrids", "LYR",
            18.17, 34.0, 4, 14, 4, 30, 4, 22, 18,
            "撒切尔彗星 C/1861 G1",
            "人类观测史最悠久的流星雨之一，公元前 687 年《春秋》已有“星陨如雨”的记载。"
        ),
        MeteorShower(
            "ETA", "宝瓶座η流星雨", "Eta Aquariids", "ETA",
            22.52, -1.0, 4, 19, 5, 28, 5, 6, 50,
            "哈雷彗星 1P/Halley",
            "哈雷彗星馈赠的南天流星雨，流星速度极快（66 km/s），常留持久余迹。"
        ),
        MeteorShower(
            "PER", "英仙座流星雨", "Perseids", "PER",
            3.20, 58.0, 7, 17, 8, 24, 8, 12, 100,
            "斯威夫特-塔特尔彗星 109P",
            "北半球三大流星雨之首，夏季夜晚观测条件最舒适，火流星比例高。"
        ),
        MeteorShower(
            "ORI", "猎户座流星雨", "Orionids", "ORI",
            6.32, 15.8, 10, 2, 11, 7, 10, 21, 20,
            "哈雷彗星 1P/Halley",
            "哈雷彗星的另一场馈赠，辐射点在猎户座棒槌与双子之间，午夜后升起。"
        ),
        MeteorShower(
            "LEO", "狮子座流星雨", "Leonids", "LEO",
            10.28, 21.6, 11, 6, 11, 30, 11, 17, 15,
            "坦普尔-塔特尔彗星 55P",
            "1833 年“流星暴”一夜万星坠落，催生了现代流星天文学；每约 33 年可能爆发流星暴。"
        ),
        MeteorShower(
            "GEM", "双子座流星雨", "Geminids", "GEM",
            7.48, 32.3, 12, 4, 12, 20, 12, 14, 150,
            "法厄同小行星 3200 Phaethon",
            "年度最稳定、流量最大的流星雨，流星速度中等、亮度高、色彩丰富，冬至前后整夜可见。"
        ),
        MeteorShower(
            "URS", "小熊座流星雨", "Ursids", "URS",
            14.47, 75.8, 12, 17, 12, 26, 12, 22, 10,
            "塔特尔彗星 8P/Tuttle",
            "冬至前后的压轴小流星雨，辐射点拱极整夜不落，适合高纬度地区观测。"
        )
    )

    val byKey: Map<String, MeteorShower> = showers.associateBy { it.key }

    /** 当前模拟时刻活跃的流星雨（含强度权重），按权重降序。 */
    fun activeShowers(simTimeMillis: Long): List<Pair<MeteorShower, Double>> {
        val cal = Calendar.getInstance().apply { timeInMillis = simTimeMillis }
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return showers.filter { it.isActiveOn(m, d) }
            .map { it to it.intensityWeight(cal) }
            .sortedByDescending { it.second }
    }

    /** 期望流星目视频率（颗/分钟，单观测者目视经验值 ≈ ZHR/60·sinAlt²）。 */
    fun expectedPerMinute(shower: MeteorShower, radiantAltDeg: Double): Double {
        if (radiantAltDeg <= 0.0) return 0.0
        val sinAlt = kotlin.math.sin(kotlin.math.PI * radiantAltDeg / 180.0)
        return shower.zhr / 60.0 * sinAlt * sinAlt
    }
}
