package com.celestiaview.ar.data.model

/**
 * 全部天体数据模型（纯 Kotlin，可被 JVM 单元测试直接覆盖）。
 */

/** 恒星（J2000 赤道坐标，raHours 以小时计）。 */
data class Star(
    val id: Int,
    val raHours: Double,
    val decDeg: Double,
    val mag: Double,
    val bv: Double,
    val distLy: Double,
    val spectral: String,
    val nameEn: String,
    val nameZh: String,
    val constellation: String,
    val description: String = ""
) {
    val raDeg: Double get() = raHours * 15.0
}

/** 行星静态展示元数据（实时坐标由星历引擎解算）。 */
data class Planet(
    val key: String,
    val nameEn: String,
    val nameZh: String,
    val colorHex: Long,
    val description: String
)

/** 深空天体类型。 */
enum class DsoType { GALAXY, NEBULA, GLOBULAR_CLUSTER, OPEN_CLUSTER, PLANETARY_NEBULA, SUPERNOVA_REMNANT }

/** 梅西耶深空天体。 */
data class DeepSkyObject(
    val catalogId: String,
    val type: DsoType,
    val raHours: Double,
    val decDeg: Double,
    val mag: Double,
    val majorAxisArcmin: Double,
    val minorAxisArcmin: Double,
    val distanceKly: Double,
    val nameEn: String,
    val nameZh: String,
    val constellation: String,
    val description: String
) {
    val raDeg: Double get() = raHours * 15.0
}

/** 星座拓扑骨架：恒星连线对（引用 BrightStarCatalog 中的恒星 id）。 */
data class Constellation(
    val abbr: String,
    val nameEn: String,
    val nameZh: String,
    val storyWestern: String,
    val storyChinese: String,
    val edges: List<Pair<Int, Int>>,
    val memberStarIds: List<Int>
)

/** 天球多边形顶点（银河光带轮廓、黄道带等）。 */
data class HorizonPoint(
    val raDeg: Double,
    val decDeg: Double
)

/** 今夜观测事件条目（行星可见性、亮星推荐等）。 */
data class TonightEvent(
    val title: String,
    val subtitle: String,
    val raDeg: Double,
    val decDeg: Double,
    val mag: Double,
    val kind: Kind,
    val targetKey: String
) {
    enum class Kind { PLANET, STAR, MESSIER, MOON, SATELLITE, METEOR_SHOWER }
}

/** 观测地点预设。 */
data class ObservingSite(
    val nameZh: String,
    val nameEn: String,
    val latitudeDeg: Double,
    val longitudeEastDeg: Double,
    val note: String
)

/** 天体实时地平状态。 */
data class SkyPosition(
    val altDeg: Double,
    val azDeg: Double,
    val apparentAltDeg: Double
) {
    val isAboveHorizon: Boolean get() = altDeg > 0.0
}

/** 锁定/选中的可指向天体统一句柄。 */
sealed interface CelestialTarget {
    val displayName: String
    val raDeg: Double
    val decDeg: Double

    data class StarTarget(val star: Star) : CelestialTarget {
        override val displayName get() = star.nameZh
        override val raDeg get() = star.raDeg
        override val decDeg get() = star.decDeg
    }

    data class PlanetTarget(val key: String, override val displayName: String,
                            override val raDeg: Double, override val decDeg: Double) : CelestialTarget

    data class MoonTarget(override val raDeg: Double, override val decDeg: Double) : CelestialTarget {
        override val displayName get() = "月球 Moon"
    }

    data class SunTarget(override val raDeg: Double, override val decDeg: Double) : CelestialTarget {
        override val displayName get() = "太阳 Sun"
    }

    data class MessierTarget(val dso: DeepSkyObject) : CelestialTarget {
        override val displayName get() = "${dso.catalogId} ${dso.nameZh}"
        override val raDeg get() = dso.raDeg
        override val decDeg get() = dso.decDeg
    }
}
