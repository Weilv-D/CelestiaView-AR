package com.celestiaview.ar.ui.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.CelestialProjector
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.core.math.SatelliteTracker
import com.celestiaview.ar.core.math.Vec3
import com.celestiaview.ar.data.catalog.BrightStarCatalog
import com.celestiaview.ar.data.catalog.ChineseSkyCatalog
import com.celestiaview.ar.data.catalog.ConstellationCatalog
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.catalog.MessierCatalog
import com.celestiaview.ar.data.catalog.MilkyWayBandData
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.data.model.DsoType
import com.celestiaview.ar.data.model.Star
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.theme.CelestialPalette
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** ScreenPos → Compose Offset 的桥接扩展。 */
private fun CelestialProjector.projAltAz(altDeg: Double, azDeg: Double): Offset? =
    projectAltAz(altDeg, azDeg)?.let { Offset(it.x, it.y) }

private fun CelestialProjector.proj(v: Vec3): Offset? =
    project(v)?.let { Offset(it.x, it.y) }

/**
 * Canvas 星空渲染管线：
 * L1 大气天穹 → L2 银河光带 + 背景星砂场 → L3 坐标网格与黄道 → L3.5 三垣二十八宿 →
 * L4 梅西耶深空 → L5 星座骨架 → L6 恒星 PSF 光学 → L7 行星/月相/空间站 →
 * L8 选中轨迹 → L9 球面地平弧。地平线以下天体以 0.22 透明度透视呈现。
 *
 * 零分配渲染循环约定：
 * - 全部 Paint / Path / RectF / Brush 为字段预分配，逐帧 rewind + 原地改参复用；
 * - 天穹渐变 Brush 按屏幕高度缓存，仅在尺寸/模式变化时重建；
 * - 坐标热路径使用 [AstrometryEngine.HorPos] 与 [CelestialProjector.ScreenPosMut] 复用容器；
 * - 网格环 / 方位线 / 背景星砂的世界坐标在初始化时烘焙，逐帧仅投影；
 * - renderInto 以 synchronized 串行化 UI 帧与快门离屏渲染，消除共享缓存的并发竞争。
 */
class SkyCanvasRenderer(private val vm: SkyViewModel) {

    data class OverlayData(
        var snappedScreen: Offset? = null,
        var snappedLabel: String = "",
        var snappedDetail: String = "",
        var snappedSepDeg: Double = 0.0,
        var guideAngleDeg: Float? = null,
        var guideSepDeg: Double? = null,
        var guideName: String = "",
        var guideScreen: Offset? = null
    )

    val overlay = OverlayData()

    private val projector = CelestialProjector()

    // 预排序：暗星先画，亮星压轴
    private val starsFaintFirst: List<Star> =
        BrightStarCatalog.stars.sortedByDescending { it.mag }

    // 星座边（预解析为星体引用）
    private data class Edge(val a: Star, val b: Star)
    private val edgesByConstellation: Map<String, List<Edge>> =
        ConstellationCatalog.constellations.associate { c ->
            c.abbr to c.edges.map { (a, b) -> Edge(BrightStarCatalog.byId(a), BrightStarCatalog.byId(b)) }
        }

    // 轨迹缓存（字段级键比较，免逐帧字符串拼接）
    private var trajPoints: List<Triple<Double, Double, Long>> = emptyList()

    // ------------------------------------------------------------------
    // 零分配预分配池（渲染线程访问；离屏快门经 synchronized 串行化）
    // ------------------------------------------------------------------

    // 坐标变换复用容器
    private val hz = AstrometryEngine.HorPos()
    private val scr = CelestialProjector.ScreenPosMut()

    // 原生绘图对象池
    private val linePaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    }
    private val fillPaint: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val strokePaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    }
    private val dashPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    private val hudTextPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.SANS_SERIF }
    }
    private val badgePaint: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val badgeBorderPaint: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    private val tickPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
    }
    private val badgePath: Path by lazy { Path() }
    private val rectF: RectF by lazy { RectF() }

    // 复用路径池（compose Path 持有 android Path，rewind 复用）
    private val mwPaths: Array<Path> = Array(3) { Path() }
    private val eclipticPath: Path = Path()
    private val groundPath: androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path()
    private val ringPath: Path = Path()
    private val trajPath: Path = Path()

    // 天穹渐变缓存：仅当屏幕高度变化时重建
    private var domeCacheHeight = -1f
    private val domeBrushes = arrayOfNulls<Brush>(3) // 0 夜 1 晨昏 2 昼
    private var airglowBrush: Brush? = null
    private var dashCacheDp = -1f

    // 网格世界坐标烘焙：5 高度圈（96 段）+ 12 方位线（21 段），仅初始化时构建
    private var gridWorld: Array<DoubleArray>? = null
    private var equatorWorld: DoubleArray? = null
    private var equatorLat: Double = Double.NaN
    private val monthNames = arrayOf(
        "一月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "十一月", "十二月"
    )
    private val hourLabels = Array(24) { "%02d:00".format(it) }

    // 黄道逐帧临时数组（NaN = 断点）
    private val eclipticXY = FloatArray(73 * 2)

    // 背景星砂场：固定种子生成的 1500 颗暗星（视星等 4.2~6.2，60% 聚于银道面 ±18° 带），
    // 以纯色圆点绘制，填充天空死黑并让银河带呈现星尘质感
    private val bgRa = DoubleArray(BG_STAR_COUNT)
    private val bgDec = DoubleArray(BG_STAR_COUNT)
    private val bgRadiusDp = FloatArray(BG_STAR_COUNT)
    private val bgAlpha = FloatArray(BG_STAR_COUNT)

    init {
        val rnd = kotlin.random.Random(42)
        val cl = MilkyWayBandData.centerLine
        for (i in 0 until BG_STAR_COUNT) {
            if (i % 5 < 3) {
                // 沿银河中线随机段插值 + 垂直抖动，自然勾勒银道带
                val seg = rnd.nextInt(cl.size - 1)
                val t = rnd.nextDouble()
                val a = cl[seg]; val b = cl[seg + 1]
                bgRa[i] = AstrometryEngine.normalizeDeg(
                    a.raDeg + (b.raDeg - a.raDeg) * t + (rnd.nextDouble() - 0.5) * 16.0
                )
                bgDec[i] = (a.decDeg + (b.decDeg - a.decDeg) * t + (rnd.nextDouble() - 0.5) * 16.0)
                    .coerceIn(-89.5, 89.5)
            } else {
                // 均匀球面分布
                bgRa[i] = rnd.nextDouble() * 360.0
                bgDec[i] = Math.toDegrees(kotlin.math.asin(rnd.nextDouble() * 2.0 - 1.0))
            }
            val bright = rnd.nextDouble().toFloat() // 越亮半径与透明度越高
            bgRadiusDp[i] = 0.6f + 0.7f * bright
            bgAlpha[i] = 0.15f + 0.35f * bright
        }
    }

    // 地平弧逐帧采样缓存：alt=0 球面弧（±75° 方位、3° 步进）与地下暮光多边形
    private val horizonArcPath: Path = Path()
    private var horizonArcCount = 0
    private var horizonArcMinY = Float.MAX_VALUE
    private var horizonTwilightBucket = Int.MIN_VALUE
    private var horizonTwilightBrush: Brush? = null

    // 吸附详情节流：仅目标/数值变化时重建格式串
    private var lastSnapRef: Any? = null
    private var lastSnapAltQ = Int.MIN_VALUE
    private var lastSnapAzQ = Int.MIN_VALUE
    private var lastSnapSepQ = Int.MIN_VALUE

    // 卫星过境弧缓存键（免逐帧 joinToString 分配）
    private var satTrailCacheKey = SatTrailKey("", -1, 0)
    private val satTrailCache = HashMap<String, List<Pair<Double, Double>>>()
    private data class SatTrailKey(val key: String, val bucket: Long, val site: Long)
    private val satKeyScratch = ArrayList<String>(4)
    private val satKeyList = ArrayList<String>(4)

    // ------------------------------------------------------------------

    /** 供外部（Canvas / 离屏位图）调用的入口。 */
    fun renderInto(scope: DrawScope) {
        synchronized(this) { with(scope) { render() } }
    }

    fun DrawScope.render() {
        val f = vm.frame
        val w = size.width
        val h = size.height
        val dp = density

        projector.width = w
        projector.height = h
        projector.fovYDeg = vm.fovYDeg
        if (vm.sensorMode) projector.setAttitude(vm.worldToCamera)
        else projector.setVirtualCamera(vm.orbit.centerAltDeg, vm.orbit.centerAzDeg)

        val pxPerDeg = h.toFloat() / vm.fovYDeg.toFloat()
        val timeSec = (f.simTimeMillis % 86_400_000L) / 1000f

        // L0 AR 暗化
        if (vm.arMode) {
            drawRect(Color(0xFF030612).copy(alpha = vm.arDarkenAlpha))
        }

        // ---- L1 大气散射天穹 ----
        drawSkyDome(f)

        // ---- L2 银河光带 + 流星粒子 ----
        if (vm.showMilkyWay) drawMilkyWay(f)
        if (vm.showMeteorShowers && f.activeShowers.isNotEmpty()) {
            drawMeteors(f, dp, timeSec)
        }

        // ---- L2.2 背景星砂场 ----
        drawStarfield(f)

        // ---- L2.8 地下暮光渐变（沿球面地平弧）----
        drawGroundTwilight(f)

        // ---- L3 天球网格 / 天赤道 / 黄道 ----
        if (vm.showGrid) drawAltAzGrid()
        if (vm.showGrid) drawCelestialEquator(f)
        if (vm.showEcliptic) drawEcliptic(f, dp)

        // ---- L3.5 三垣二十八宿（文化星图层）----
        if (vm.cultureMode != CultureMode.IAU) drawChineseSky(f, dp)

        // ---- L4 梅西耶深空 ----
        if (vm.showMessier) drawMessier(f, dp, pxPerDeg, timeSec)

        // ---- L5 星座流光骨架（中国古星官模式下让位于宿线）----
        if (vm.showConstellations && vm.cultureMode != CultureMode.CHINESE) {
            drawConstellations(f, dp)
        }

        // ---- L6 恒星 ----
        drawStars(f, dp, timeSec)

        // ---- L7 太阳 / 月球 / 行星 / 空间站 ----
        drawSunAndMoon(f, dp)
        drawPlanets(f, dp, timeSec)
        if (vm.showSatellites) drawSatellites(f, dp, timeSec)
        if (vm.showMeteorShowers && f.activeShowers.isNotEmpty()) {
            drawMeteorRadiants(f, dp, timeSec)
        }

        // ---- L8 选中轨迹 ----
        if (vm.showTrajectory) drawTrajectory(f, dp)

        // ---- L9 球面地平弧 / 罗盘刻度 ----
        drawHorizon(f, dp)

        // ---- HUD 数据（准星吸附 / 引导箭头）----
        updateOverlayData(f, w, h, dp)

        // ---- 红光滤镜 ----
        if (vm.redLight) {
            drawRect(
                color = Color(0xCCFF2000),
                blendMode = BlendMode.Multiply
            )
        }
    }

    private fun DrawScope.nativeCanvas0(): Canvas = drawContext.canvas.nativeCanvas

    // ==================================================================
    // L1 天穹
    // ==================================================================

    private fun DrawScope.drawSkyDome(f: SkyViewModel.FrameData) {
        val sunAlt = f.sunAltDeg
        if (vm.arMode) return
        val mode = when {
            sunAlt > 0 -> 2
            sunAlt > -18 -> 1
            else -> 0
        }
        if (domeCacheHeight != size.height) {
            rebuildDomeBrushes(size.height)
        }
        domeBrushes[mode]?.let { drawRect(it) }
        // 地平线大气散射辉光（气辉）
        val airglowTop = size.height * 0.56f
        airglowBrush?.let {
            drawRect(
                it,
                topLeft = Offset(0f, airglowTop),
                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.18f)
            )
        }
        // 晨昏蒙影：太阳方位低空暖光晕（限制强度，避免冲淡全天底色）
        if (mode == 1) {
            val p = projector.projAltAz(sunAlt.coerceAtMost(-2.0), f.sunAzDeg)
            if (p != null) {
                StarOpticsShader.glowSpriteN(
                    nativeCanvas0(), p.x, p.y,
                    size.width * 0.42f, 0xFFFF6B4A.toInt(), 0.30f
                )
            }
        }
    }

    private fun rebuildDomeBrushes(h: Float) {
        domeCacheHeight = h
        // 0 深夜：天顶深渊 → 中天深海 → 地平微光
        domeBrushes[0] = Brush.verticalGradient(
            listOf(CelestialPalette.ZenithNight, CelestialPalette.MidSkyNight, CelestialPalette.HorizonNight),
            startY = 0f, endY = h
        )
        // 1 晨昏蒙影
        domeBrushes[1] = Brush.verticalGradient(
            listOf(Color(0xFF060B18), Color(0xFF0B1330), Color(0xFF3A2450)),
            startY = 0f, endY = h
        )
        // 2 白昼观星模式
        domeBrushes[2] = Brush.verticalGradient(
            listOf(Color(0xFF0A1830), Color(0xFF12264C), Color(0xFF1B3560)),
            startY = 0f, endY = h
        )
        airglowBrush = Brush.verticalGradient(
            listOf(Color.Transparent, CelestialPalette.HorizonAirglow),
            startY = h * 0.56f, endY = h * 0.74f
        )
    }

    // ==================================================================
    // L2 银河
    // ==================================================================

    private fun DrawScope.drawMilkyWay(f: SkyViewModel.FrameData) {
        // 辉光带：银道中线整条 Path 三层描边（外/中/内柔光）。
        // 用连续描边而非逐段 drawLine——分段圆头端帽叠加会产生串珠伪影；
        // 也不做整片多边形填充——广角或地平折返时多边形退化为横贯全屏的巨面。
        val pxPerDeg = size.height / vm.fovYDeg.toFloat()
        val halfW = size.width * 0.5f
        val centerLine = MilkyWayBandData.centerLine
        val widths = MW_WIDTHS
        val alphas = MW_ALPHAS
        for (k in 0 until 3) mwPaths[k].rewind()
        var prev: Offset? = null
        for (pt in centerLine) {
            AstrometryEngine.equatorialToHorizontal(pt.raDeg, pt.decDeg, f.lstDeg, f.latitudeDeg, hz)
            val s = if (projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) {
                Offset(scr.x, scr.y)
            } else null
            val p0 = prev
            if (s != null && p0 != null && abs(s.x - p0.x) < halfW) {
                for (k in 0 until 3) mwPaths[k].lineTo(s.x, s.y)
            } else if (s != null) {
                for (k in 0 until 3) mwPaths[k].moveTo(s.x, s.y)
            }
            prev = s
        }
        val n = nativeCanvas0()
        for (k in 0 until 3) {
            val p = linePaint
            p.color = MW_COLORS[k]
            p.alpha = (alphas[k] * 255f).toInt()
            p.strokeWidth = widths[k] * pxPerDeg
            n.drawPath(mwPaths[k], p)
        }

        // 银心方向增强辉光
        val gc = MilkyWayBandData.galacticCenter
        AstrometryEngine.equatorialToHorizontal(gc.raDeg, gc.decDeg, f.lstDeg, f.latitudeDeg, hz)
        if (projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) {
            StarOpticsShader.glowSpriteN(n, scr.x, scr.y, 22f * pxPerDeg, 0xFFB8C6E8.toInt(), 0.14f)
        }

        // 银心方向星尘
        fillPaint.color = 0x2E9FB8FF.toInt()
        for (d in MilkyWayBandData.galacticCenterDust) {
            AstrometryEngine.equatorialToHorizontal(d.raDeg, d.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) {
                n.drawCircle(scr.x, scr.y, 1.6f * density, fillPaint)
            }
        }
    }

    /**
     * 背景星砂场：1500 颗暗星逐帧批量投影，纯色 drawCircle 零 Shader 开销。
     * 地平线以下以 0.22 透明度透视呈现。
     */
    private fun DrawScope.drawStarfield(f: SkyViewModel.FrameData) {
        val n = nativeCanvas0()
        val p = fillPaint
        p.color = 0xFFC7D6F2.toInt()
        for (i in 0 until BG_STAR_COUNT) {
            AstrometryEngine.equatorialToHorizontal(bgRa[i], bgDec[i], f.lstDeg, f.latitudeDeg, hz)
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            var a = bgAlpha[i]
            if (hz.altDeg < 0.0) a *= 0.22f
            p.alpha = (a * 255f).toInt()
            n.drawCircle(scr.x, scr.y, bgRadiusDp[i] * density, p)
        }
    }

    // ==================================================================
    // L3 网格 / 天赤道 / 黄道
    // ==================================================================

    /** 世界坐标多段线投影绘制：NaN 间距 = 断点。 */
    private fun DrawScope.projectAndDraw(
        world: DoubleArray,
        segments: Int,
        colorArgb: Int,
        alpha255: Int,
        strokeWidthPx: Float,
        closeRing: Boolean
    ) {
        ringPath.rewind()
        var pen = false
        var lastX = 0f
        var firstX = 0f; var firstY = 0f; var lastY = 0f
        var firstVisible = false; var lastVisible = false
        for (k in 0 until segments) {
            val o = k * 3
            if (projector.projectEnuInto(world[o], world[o + 1], world[o + 2], scr)) {
                if (!pen) { ringPath.moveTo(scr.x, scr.y); pen = true; firstX = scr.x; firstY = scr.y; firstVisible = true }
                else if (abs(scr.x - lastX) < size.width * 0.5f) ringPath.lineTo(scr.x, scr.y)
                else ringPath.moveTo(scr.x, scr.y)
                lastX = scr.x; lastY = scr.y; lastVisible = true
            } else {
                pen = false; lastVisible = false
            }
        }
        if (closeRing && firstVisible && lastVisible && abs(lastX - firstX) < size.width * 0.5f) {
            ringPath.lineTo(firstX, firstY)
        }
        val p = linePaint
        p.color = colorArgb
        p.alpha = alpha255
        p.strokeWidth = strokeWidthPx
        nativeCanvas0().drawPath(ringPath, p)
    }

    /** 网格几何不随时间/姿态变化 → 初始化烘焙世界坐标，逐帧仅投影。 */
    private fun ensureGridWorld() {
        if (gridWorld != null) return
        val out = ArrayList<DoubleArray>(17)
        // 高度圈 15° 间隔：圆心天顶，角半径 90-alt，96 段
        for (alt in 15..75 step 15) {
            val ring = projector.circleOnSphere(Vec3.UNIT_Z, 90.0 - alt, 96)
            out.add(ringToDoubleArray(ring))
        }
        // 方位线 15° 间隔：88° → -8°，5° 步长（与旧 projectGreatCircle(stepDeg=5) 一致）
        for (az in 0 until 360 step 15) {
            val top = AstrometryEngine.altAzToEnu(88.0, az.toDouble())
            val bottom = AstrometryEngine.altAzToEnu(-8.0, az.toDouble())
            val total = top.angleBetweenDeg(bottom)
            val nSeg = kotlin.math.ceil(total / 5.0).toInt().coerceAtLeast(1)
            val arr = DoubleArray((nSeg + 1) * 3)
            for (k in 0..nSeg) {
                val v = if (k == 0) top else if (k == nSeg) bottom else top.slerp(bottom, k.toDouble() / nSeg)
                arr[k * 3] = v.x; arr[k * 3 + 1] = v.y; arr[k * 3 + 2] = v.z
            }
            out.add(arr)
        }
        gridWorld = out.toTypedArray()
    }

    private fun ringToDoubleArray(ring: List<Vec3>): DoubleArray {
        val arr = DoubleArray(ring.size * 3)
        for (i in ring.indices) {
            arr[i * 3] = ring[i].x; arr[i * 3 + 1] = ring[i].y; arr[i * 3 + 2] = ring[i].z
        }
        return arr
    }

    private fun DrawScope.drawAltAzGrid() {
        ensureGridWorld()
        val gw = gridWorld!!
        val widthPx = 0.7f * density
        val argb = CelestialPalette.GridLineAzAlt.toArgb255()
        for (i in 0 until 5) projectAndDraw(gw[i], gw[i].size / 3, argb, (argb ushr 24), widthPx, closeRing = false)
        for (i in 5 until gw.size) projectAndDraw(gw[i], gw[i].size / 3, argb, (argb ushr 24), widthPx, closeRing = false)
    }

    private fun DrawScope.drawCelestialEquator(f: SkyViewModel.FrameData) {
        val lat = f.latitudeDeg
        if (equatorWorld == null || abs(lat - equatorLat) > 0.01) {
            val ncp = Vec3(0.0, cos(Math.toRadians(lat)), sin(Math.toRadians(lat)))
            equatorWorld = ringToDoubleArray(projector.circleOnSphere(ncp, 90.0, 120))
            equatorLat = lat
        }
        projectAndDraw(equatorWorld!!, 120, 0x3D26A69A.toInt(), 0x3D, 1.0f * density, closeRing = true)
    }

    private fun DrawScope.drawEcliptic(f: SkyViewModel.FrameData, dp: Float) {
        eclipticPath.rewind()
        var pen = false
        var lastX = 0f
        var lastY = 0f
        for (i in 0 until 73) {
            val lonDeg = i * 5.0
            val eq = AstrometryEngine.eclipticToEquatorial(lonDeg, 0.0, f.obliquityDeg)
            AstrometryEngine.equatorialToHorizontal(eq.raDeg, eq.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) {
                eclipticXY[i * 2] = scr.x; eclipticXY[i * 2 + 1] = scr.y
            } else {
                eclipticXY[i * 2] = Float.NaN
            }
        }
        for (i in 0 until 73) {
            val x = eclipticXY[i * 2]
            if (x.isNaN()) { pen = false; continue }
            val y = eclipticXY[i * 2 + 1]
            if (!pen) { eclipticPath.moveTo(x, y); pen = true }
            else if (abs(x - lastX) < size.width * 0.5f) eclipticPath.lineTo(x, y)
            else eclipticPath.moveTo(x, y)
            lastX = x; lastY = y
        }
        if (dashCacheDp != dp) {
            dashCacheDp = dp
            dashPaint.pathEffect = DashPathEffect(floatArrayOf(12f * dp, 9f * dp), 0f)
        }
        val p = dashPaint
        p.color = CelestialPalette.EclipticLine.toArgb255()
        p.alpha = (CelestialPalette.EclipticLine.alpha * 255f).toInt()
        p.strokeWidth = 1.1f * dp
        nativeCanvas0().drawPath(eclipticPath, p)

        // 月份刻度与标签
        val n = nativeCanvas0()
        val tp = hudTextPaint
        for (m in 0 until 12) {
            val lonDeg = m * 30.0
            val eq = AstrometryEngine.eclipticToEquatorial(lonDeg, 0.0, f.obliquityDeg)
            AstrometryEngine.equatorialToHorizontal(eq.raDeg, eq.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            tp.color = android.graphics.Color.argb(140, 255, 209, 102)
            tp.textSize = 10f * dp
            n.drawText(monthNames[m], scr.x, scr.y - 6f * dp, tp)
        }
    }

    // ==================================================================
    // L4 梅西耶
    // ==================================================================

    private fun DrawScope.drawMessier(
        f: SkyViewModel.FrameData, dp: Float, pxPerDeg: Float, timeSec: Float
    ) {
        val n = nativeCanvas0()
        for (o in MessierCatalog.objects) {
            AstrometryEngine.equatorialToHorizontal(o.raDeg, o.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            val x = scr.x; val y = scr.y
            val sizePx = (o.majorAxisArcmin / 60.0 * pxPerDeg).toFloat()
                .coerceIn(4f * dp, 30f * dp)
            val underground = hz.altDeg < 0
            val alpha = if (underground) 0.35f else 1f
            val seed = StarOpticsShader.hash01(o.catalogId.hashCode())

            when (o.type) {
                DsoType.GALAXY -> {
                    // 暖白核心 + 紫晕双层柔光
                    StarOpticsShader.glowSpriteN(n, x, y, sizePx, 0xFFFFF3E0.toInt(), 0.75f * alpha)
                    StarOpticsShader.glowSpriteN(n, x, y, sizePx * 1.15f, 0x66B39DDB.toInt(), 0.60f * alpha)
                    // 椭圆拉伸盘面
                    n.save()
                    n.rotate(seed * 360f, x, y)
                    fillPaint.color = 0x334D7CFF.toInt()
                    fillPaint.alpha = (alpha * 255f).toInt()
                    rectF.set(x - sizePx, y - sizePx * 0.38f, x + sizePx, y + sizePx * 0.38f)
                    n.drawOval(rectF, fillPaint)
                    n.restore()
                }
                DsoType.NEBULA, DsoType.SUPERNOVA_REMNANT -> {
                    StarOpticsShader.glowSpriteN(n, x - sizePx * 0.15f, y + sizePx * 0.1f, sizePx, 0x4DFF4081.toInt(), alpha)
                    StarOpticsShader.glowSpriteN(n, x + sizePx * 0.15f, y - sizePx * 0.1f, sizePx * 0.8f, 0x4D00E5FF.toInt(), alpha)
                }
                DsoType.GLOBULAR_CLUSTER, DsoType.OPEN_CLUSTER -> {
                    val cnt = if (o.type == DsoType.GLOBULAR_CLUSTER) 16 else 10
                    fillPaint.color = 0xFFD6E4FF.toInt()
                    for (k in 0 until cnt) {
                        val a = seed * 37f + k * 2.39996f
                        val rr = if (o.type == DsoType.GLOBULAR_CLUSTER)
                            sizePx * 0.5f * kotlin.math.sqrt(k.toFloat() / cnt)
                        else sizePx * 0.55f * StarOpticsShader.hash01(k * 31 + o.catalogId.hashCode())
                        fillPaint.alpha = (0.8f * alpha * 255f).toInt()
                        n.drawCircle(x + rr * cos(a), y + rr * sin(a), 1.1f * dp, fillPaint)
                    }
                    StarOpticsShader.run { drawDashedRing(x, y, sizePx * 1.15f, Color(0x80B4C5FF).copy(alpha = alpha), 1.0f * dp) }
                }
                DsoType.PLANETARY_NEBULA -> {
                    fillPaint.color = 0x8026C6DA.toInt()
                    fillPaint.alpha = (alpha * 255f).toInt()
                    n.drawCircle(x, y, sizePx * 0.45f, fillPaint)
                    StarOpticsShader.run { drawDashedRing(x, y, sizePx * 0.8f, Color(0x9964FFDA).copy(alpha = alpha), 1.2f * dp) }
                }
            }

            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(
                (if (underground) 70 else 150), 180, 200, 255
            )
            tp.textSize = 9f * dp
            n.drawText(o.catalogId, x + sizePx + 3f * dp, y + 3f * dp, tp)
        }
    }

    // ==================================================================
    // L5 星座
    // ==================================================================

    private fun DrawScope.drawConstellations(
        f: SkyViewModel.FrameData, dp: Float
    ) {
        val n = nativeCanvas0()
        val coreIdle = CelestialPalette.ConstellationLineIdle
        val coreActive = CelestialPalette.ConstellationLineActive
        val halfW = size.width * 0.5f
        val labelPaint = hudTextPaint
        labelPaint.setShadowLayer(4f, 0f, 0f, 0xB4000000.toInt())
        for (c in ConstellationCatalog.constellations) {
            val activation = vm.constellationActivation[c.abbr] ?: 0f
            val edges = edgesByConstellation[c.abbr] ?: continue
            val active = activation > 0.05f

            for (e in edges) {
                AstrometryEngine.equatorialToHorizontal(e.a.raDeg, e.a.decDeg, f.lstDeg, f.latitudeDeg, hz)
                val altA = hz.altDeg
                if (!projector.projectAltAzInto(altA, hz.azDeg, scr)) continue
                val sax = scr.x; val say = scr.y
                AstrometryEngine.equatorialToHorizontal(e.b.raDeg, e.b.decDeg, f.lstDeg, f.latitudeDeg, hz)
                val altB = hz.altDeg
                if (!projector.projectAltAzInto(altB, hz.azDeg, scr)) continue
                val sbx = scr.x; val sby = scr.y
                if (abs(sax - sbx) > halfW) continue
                // 整条边位于地下时以透视透明度呈现
                val underMul = if (altA < 0.0 && altB < 0.0) 0.22f else 1f
                val p = linePaint
                p.strokeCap = Paint.Cap.ROUND
                p.color = (if (active) coreActive else coreIdle).toArgb255()
                if (active) {
                    p.alpha = ((0.30f + 0.35f * activation) * underMul * 255f).toInt()
                    p.strokeWidth = 1.0.dp.toPx()
                } else {
                    p.alpha = (0.25f * underMul * 255f).toInt()
                    p.strokeWidth = 0.6.dp.toPx()
                }
                n.drawLine(sax, say, sbx, sby, p)
            }

            // 双语铭牌：纯文本微阴影悬浮（激活时浮现）
            if (activation > 0.45f) {
                val center = ConstellationCatalog.centerRaDec(c)
                AstrometryEngine.equatorialToHorizontal(center.raDeg, center.decDeg, f.lstDeg, f.latitudeDeg, hz)
                if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
                val sx = scr.x; val sy = scr.y
                val label = "${c.nameZh} · ${c.nameEn}"
                val lx = sx.coerceIn(80f * dp, size.width - 80f * dp) // 防止铭牌被屏幕边缘裁切
                labelPaint.textSize = 12f * dp
                labelPaint.color = android.graphics.Color.argb(
                    (215 * activation).toInt(), 226, 236, 255
                )
                val tw = labelPaint.measureText(label)
                n.drawText(label, lx - tw / 2, sy - 8f * dp, labelPaint)
            }
        }
        labelPaint.clearShadowLayer()
    }

    // ==================================================================
    // L6 恒星
    // ==================================================================

    private fun DrawScope.drawStars(
        f: SkyViewModel.FrameData, dp: Float, timeSec: Float
    ) {
        val n = nativeCanvas0()
        for (star in starsFaintFirst) {
            AstrometryEngine.equatorialToHorizontal(star.raDeg, star.decDeg, f.lstDeg, f.latitudeDeg, hz)
            val underground = hz.altDeg < 0
            if (!projector.projectAltAzInto(
                    AstrometryEngine.apparentAltitudeDeg(hz.altDeg), hz.azDeg, scr
                )) continue

            val rPx = StarOpticsShader.coreRadiusDp(star.mag) * dp
            val twinkle = if (underground) 0.3f
            else StarOpticsShader.scintillation(hz.altDeg, timeSec, StarOpticsShader.hash01(star.id))
            val alpha = if (underground) 0.22f else 1f
            val argb = if (underground) 0xFF5A6B94.toInt() else StarOpticsShader.bvToColor(star.bv).toArgb255()

            StarOpticsShader.drawStarN(
                n,
                cx = scr.x,
                cy = scr.y,
                coreRadiusPx = rPx,
                colorArgb = argb,
                twinkleAlpha = twinkle * alpha,
                haloScale = if (underground) 0.6f else 1f,
                mag = star.mag
            )
        }
    }

    // ==================================================================
    // L7 太阳 / 月球 / 行星
    // ==================================================================

    private fun DrawScope.drawSunAndMoon(
        f: SkyViewModel.FrameData, dp: Float
    ) {
        val n = nativeCanvas0()
        // 太阳
        if (projector.projectAltAzInto(f.sunAltDeg, f.sunAzDeg, scr)) {
            val glowR = (if (f.isDay) 46f else 26f) * dp
            StarOpticsShader.glowSpriteN(n, scr.x, scr.y, glowR, 0xFFFFB300.toInt(), 0.55f)
            StarOpticsShader.glowSpriteN(n, scr.x, scr.y, glowR * 0.7f, 0xFFFFF3B0.toInt(), 0.9f)
            fillPaint.color = 0xFFFFF8E1.toInt()
            fillPaint.alpha = 255
            n.drawCircle(scr.x, scr.y, 7f * dp, fillPaint)
            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(200, 255, 224, 130)
            tp.textSize = 11f * dp
            n.drawText(SUN_LABEL, scr.x + 10f * dp, scr.y - 10f * dp, tp)
        }

        // 月球
        if (projector.projectAltAzInto(f.moonAltDeg, f.moonAzDeg, scr)) {
            val r = 8.5f * dp
            with(MoonPhaseRenderer) {
                drawMoon(Offset(scr.x, scr.y), r, f.moonEph.phaseAngleDeg)
            }
            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(210, 220, 228, 255)
            tp.textSize = 11f * dp
            val label = "月 ${f.moonEph.phaseNameZh}"
            n.drawText(label, scr.x + r + 4f * dp, scr.y - r, tp)
        }
    }

    private fun DrawScope.drawPlanets(
        f: SkyViewModel.FrameData, dp: Float, timeSec: Float
    ) {
        val n = nativeCanvas0()
        var sunX = 0f; var sunY = 0f; var sunVisible = false
        if (projector.projectAltAzInto(f.sunAltDeg, f.sunAzDeg, scr)) {
            sunX = scr.x; sunY = scr.y; sunVisible = true
        }
        for (p in f.planets) {
            if (!projector.projectAltAzInto(p.altDeg, p.azDeg, scr)) continue
            val x = scr.x; val y = scr.y
            val underground = p.altDeg < 0
            val alpha = if (underground) 0.35f else 1f
            val colorArgb = (p.meta.colorHex.toInt() and 0xFFFFFF) or 0xFF000000.toInt()
            val baseR = when (p.meta.key) {
                "JUPITER", "SATURN" -> 6.2f * dp
                "VENUS" -> 5.4f * dp
                else -> 4.6f * dp
            }

            // 光泽球体：向太阳一侧偏移高光（柔光精灵 + 实色球 + 暗缘）
            val hlX: Float; val hlY: Float
            if (sunVisible) {
                val dx = sunX - x; val dy = sunY - y
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                hlX = x + dx / len * baseR * 0.45f
                hlY = y + dy / len * baseR * 0.45f
            } else {
                hlX = x - baseR * 0.45f * 0.4f
                hlY = y + baseR * 0.45f * 0.5f
            }
            fillPaint.color = colorArgb
            fillPaint.alpha = (alpha * 255f).toInt()
            n.drawCircle(x, y, baseR, fillPaint)
            StarOpticsShader.glowSpriteN(n, hlX, hlY, baseR * 0.9f, 0xFFFFFFFF.toInt(), 0.85f * alpha)
            StarOpticsShader.glowSpriteN(n, x + baseR * 0.3f, y + baseR * 0.3f, baseR * 1.1f, 0xFF0A1020.toInt(), 0.5f * alpha)

            when (p.meta.key) {
                "JUPITER" -> { // 赤道条纹
                    linePaint.color = 0x667A5230.toInt()
                    linePaint.alpha = (alpha * 255f).toInt()
                    linePaint.strokeWidth = 1.1f * dp
                    for (k in -1..1) {
                        n.drawLine(x - baseR * 0.9f, y + k * baseR * 0.45f, x + baseR * 0.9f, y + k * baseR * 0.45f, linePaint)
                    }
                }
                "SATURN" -> {
                    n.save()
                    n.rotate(-18f, x, y)
                    strokePaint.color = 0xCCD9C078.toInt()
                    strokePaint.alpha = (alpha * 255f).toInt()
                    strokePaint.strokeWidth = 1.6f * dp
                    rectF.set(x - baseR * 1.9f, y - baseR * 0.55f, x + baseR * 1.9f, y + baseR * 0.55f)
                    n.drawOval(rectF, strokePaint)
                    n.restore()
                }
            }

            // 脉冲识别环
            val pulse = 1f + 0.12f * sin(timeSec * 2.4f + p.meta.key.hashCode() % 7)
            StarOpticsShader.run {
                drawDashedRing(x, y, baseR * 2.1f * pulse, Color(0x99FFD54F).copy(alpha = alpha), 1.0f * dp)
            }

            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(
                (if (underground) 80 else 225), 255, 224, 130
            )
            tp.textSize = 11f * dp
            val label = "${p.meta.nameZh} ${p.meta.nameEn}"
            n.drawText(label, x + baseR * 2.4f, y - baseR * 1.6f, tp)
        }
    }

    // ==================================================================
    // L3.5 三垣二十八宿（文化星图）
    // ==================================================================

    private fun DrawScope.drawChineseSky(f: SkyViewModel.FrameData, dp: Float) {
        val n = nativeCanvas0()
        val halfW = size.width * 0.5f

        // 二十八宿：四象配色宿线 + 古风菱形宿名徽章
        for (m in ChineseSkyCatalog.mansions) {
            val tint = Color(m.symbol.argb)
            val tintArgb = tint.toArgb255()
            for ((ia, ib) in m.edges) {
                val a = m.members[ia]
                AstrometryEngine.equatorialToHorizontal(a.raDeg, a.decDeg, f.lstDeg, f.latitudeDeg, hz)
                if (hz.altDeg < -0.5) continue
                if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
                val sax = scr.x; val say = scr.y
                val b = m.members[ib]
                AstrometryEngine.equatorialToHorizontal(b.raDeg, b.decDeg, f.lstDeg, f.latitudeDeg, hz)
                if (hz.altDeg < -0.5) continue
                if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
                val sbx = scr.x; val sby = scr.y
                if (abs(sax - sbx) > halfW) continue
                val p = linePaint
                p.strokeCap = Paint.Cap.ROUND
                p.color = tintArgb
                p.alpha = (0.16f * 255f).toInt()
                p.strokeWidth = 4.2f * dp
                n.drawLine(sax, say, sbx, sby, p)
                p.alpha = (0.62f * 255f).toInt()
                p.strokeWidth = 1.2f * dp
                n.drawLine(sax, say, sbx, sby, p)
            }
            // 宿名徽章（篆刻质感：菱形描边 + 单字）
            val center = ChineseSkyCatalog.centerRaDec(m.members)
            AstrometryEngine.equatorialToHorizontal(center.raDeg, center.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (hz.altDeg < -0.5) continue
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            val scx = scr.x; val scy = scr.y
            badgePaint.color = android.graphics.Color.argb(150, 10, 18, 36)
            badgeBorderPaint.color = tintArgb
            badgeBorderPaint.alpha = 200
            badgeBorderPaint.strokeWidth = 1.4f * dp
            val r = 11f * dp
            badgePath.rewind()
            badgePath.moveTo(scx, scy - r)
            badgePath.lineTo(scx + r, scy)
            badgePath.lineTo(scx, scy + r)
            badgePath.lineTo(scx - r, scy)
            badgePath.close()
            n.drawPath(badgePath, badgePaint)
            n.drawPath(badgePath, badgeBorderPaint)
            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(235, (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())
            tp.textSize = 11f * dp
            tp.isFakeBoldText = true
            val tw = tp.measureText(m.abbr)
            n.drawText(m.abbr, scx - tw / 2, scy + 4f * dp, tp)
        }

        // 三垣：垣墙折线 + 垣名
        for (enc in ChineseSkyCatalog.enclosures) {
            for ((ia, ib) in enc.edges) {
                val a = enc.members[ia]
                AstrometryEngine.equatorialToHorizontal(a.raDeg, a.decDeg, f.lstDeg, f.latitudeDeg, hz)
                if (hz.altDeg < -0.5) continue
                if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
                val sax = scr.x; val say = scr.y
                val b = enc.members[ib]
                AstrometryEngine.equatorialToHorizontal(b.raDeg, b.decDeg, f.lstDeg, f.latitudeDeg, hz)
                if (hz.altDeg < -0.5) continue
                if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
                val sbx = scr.x; val sby = scr.y
                if (abs(sax - sbx) > halfW) continue
                val p = linePaint
                p.strokeCap = Paint.Cap.ROUND
                p.color = 0xFFD4AF37.toInt()
                p.alpha = (0.13f * 255f).toInt()
                p.strokeWidth = 3.8f * dp
                n.drawLine(sax, say, sbx, sby, p)
                p.color = 0xFFE8C96A.toInt()
                p.alpha = (0.5f * 255f).toInt()
                p.strokeWidth = 1.0f * dp
                n.drawLine(sax, say, sbx, sby, p)
            }
            val center = ChineseSkyCatalog.centerRaDec(enc.members)
            AstrometryEngine.equatorialToHorizontal(center.raDeg, center.decDeg, f.lstDeg, f.latitudeDeg, hz)
            if (hz.altDeg < -0.5) continue
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            val scx = scr.x; val scy = scr.y
            val tp = hudTextPaint
            tp.setShadowLayer(4f, 0f, 0f, 0xB4000000.toInt())
            tp.color = android.graphics.Color.argb(200, 232, 201, 106)
            tp.textSize = 13f * dp
            tp.isFakeBoldText = true
            val label = enc.nameZh
            val tw = tp.measureText(label)
            n.drawText(label, scx - tw / 2, scy - 2f * dp, tp)
            tp.clearShadowLayer()
        }
    }

    // ==================================================================
    // L7.5 人造天体（ISS / 天宫）金色矢量图标 + 过境弧
    // ==================================================================

    private fun DrawScope.drawSatellites(f: SkyViewModel.FrameData, dp: Float, timeSec: Float) {
        // ±15 分钟过境弧缓存（30 秒桶刷新）；缓存键逐元素比较，免逐帧字符串分配
        val bucket = f.simTimeMillis / 30_000L
        satKeyScratch.clear()
        for (sat in f.satellites) satKeyScratch.add(sat.elements.key)
        val site = (f.latitudeDeg * 1000).toLong() * 10000 + f.longitudeEastDeg.toLong()
        var keyChanged = bucket != satTrailCacheKey.bucket || site != satTrailCacheKey.site ||
            satKeyScratch.size != satKeyList.size
        if (!keyChanged) {
            for (i in satKeyScratch.indices) {
                if (satKeyScratch[i] != satKeyList[i]) { keyChanged = true; break }
            }
        }
        if (keyChanged) {
            satKeyList.clear()
            satKeyList.addAll(satKeyScratch)
            satTrailCacheKey = SatTrailKey(satKeyScratch.joinToString(","), bucket, site)
            satTrailCache.clear()
            for (sat in f.satellites) {
                val pts = ArrayList<Pair<Double, Double>>(22)
                for (k in -10..10) {
                    val jd = AstrometryEngine.julianDate(f.simTimeMillis + k * 90_000L)
                    val info = SatelliteTracker.observe(
                        sat.elements, jd, f.latitudeDeg, f.longitudeEastDeg
                    )
                    if (info.altDeg > -5.0) pts.add(info.altDeg to info.azDeg)
                }
                satTrailCache[sat.elements.key] = pts
            }
        }

        val n = nativeCanvas0()
        for (sat in f.satellites) {
            // 过境弧（前后 15 分钟渐隐金色轨迹）
            val trail = satTrailCache[sat.elements.key] ?: emptyList()
            var lastX = 0f; var lastY = 0f; var hasLast = false
            for ((i, pt) in trail.withIndex()) {
                if (!projector.projectAltAzInto(pt.first, pt.second, scr)) { hasLast = false; continue }
                val sx = scr.x; val sy = scr.y
                if (hasLast && abs(sx - lastX) < size.width * 0.5f) {
                    val frac = i / (trail.size - 1).coerceAtLeast(1).toFloat()
                    val fade = 1f - kotlin.math.abs(frac - 0.5f) * 2f
                    val p = linePaint
                    p.color = 0xFFFFD54F.toInt()
                    p.alpha = ((0.10f + 0.30f * fade) * 255f).toInt()
                    p.strokeWidth = 1.6f * dp
                    n.drawLine(lastX, lastY, sx, sy, p)
                }
                lastX = sx; lastY = sy; hasLast = true
            }

            if (sat.altDeg < 0.0) continue
            if (!projector.projectAltAzInto(sat.altDeg, sat.azDeg, scr)) continue
            val sx = scr.x; val sy = scr.y

            // 本体：金色核心 + 两侧太阳能翼
            val bodyR = 3.2f * dp
            val wing = 9.5f * dp
            val wingAngle = 0.6155f // ~35°，视觉上区分于恒星
            val flicker = (0.85f + 0.15f * sin(timeSec * 7.0)).toFloat()
            val bodyColor = if (sat.sunlit) 0xFFFFD54F.toInt() else 0xFF8A93A8.toInt()
            val p = linePaint
            p.color = bodyColor
            p.alpha = (0.85f * flicker * 255f).toInt()
            p.strokeWidth = 1.7f * dp
            n.drawLine(
                sx - wing * cos(wingAngle), sy - wing * sin(wingAngle),
                sx + wing * cos(wingAngle), sy + wing * sin(wingAngle), p
            )
            fillPaint.color = 0x55FFD54F.toInt()
            fillPaint.alpha = 0x55
            n.drawCircle(sx, sy, bodyR * 2.6f, fillPaint)
            fillPaint.color = bodyColor
            fillPaint.alpha = 255
            n.drawCircle(sx, sy, bodyR, fillPaint)
            fillPaint.color = 0xFFFFFFFF.toInt()
            fillPaint.alpha = (0.9f * 255f).toInt()
            n.drawCircle(sx, sy, bodyR * 0.4f, fillPaint)

            val tp = hudTextPaint
            val status = if (sat.sunlit) SAT_SUNLIT else SAT_ECLIPSED
            tp.color = android.graphics.Color.argb(220, 255, 213, 79)
            tp.textSize = 10.5f * dp
            val label = "${sat.elements.nameZh} %.0f° · %s".format(sat.altDeg, status)
            n.drawText(label, sx + wing * 1.6f, sy - bodyR * 2f, tp)
        }
    }

    // ==================================================================
    // L2.5 流星雨辐射点 + 火流星粒子
    // ==================================================================

    private class MeteorParticle {
        var active = false
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var bornSec = 0f
        var lifeSec = 1.0f
        var len = 140f
        var bright = 1f
    }

    private val meteorPool: List<MeteorParticle> = List(24) { MeteorParticle() }
    private var meteorSpawnAccum = 0f
    private var meteorFrameSec = -1f

    private fun DrawScope.drawMeteors(f: SkyViewModel.FrameData, dp: Float, timeSec: Float) {
        // 帧推进（timeSec 每日回绕，用差值检测）
        if (meteorFrameSec < 0f) meteorFrameSec = timeSec
        var dt = timeSec - meteorFrameSec
        if (dt < 0f || dt > 1f) dt = 0.016f
        meteorFrameSec = timeSec

        // 生成：强度权重 → 期望间隔
        meteorSpawnAccum += dt
        val weight = f.activeShowers.maxOfOrNull { it.second } ?: 0.0
        val intervalSec = (9.0 / (0.25 + weight)).toFloat()
        if (meteorSpawnAccum > intervalSec && meteorPool.size > 0) {
            meteorSpawnAccum = 0f
            val p = meteorPool.firstOrNull { !it.active }
            if (p != null) {
                // 从激活辐射点附近向外辐射
                val shower = f.activeShowers.random()
                AstrometryEngine.equatorialToHorizontal(
                    shower.first.radiantRaDeg, shower.first.radiantDecDeg,
                    f.lstDeg, f.latitudeDeg, hz
                )
                if (projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) {
                    val angle = (StarOpticsShader.hash01((timeSec * 977).toInt()) * 2.0 * PI).toFloat()
                    val dist = 120f + 260f * StarOpticsShader.hash01((timeSec * 613).toInt() + 7)
                    p.active = true
                    p.x = scr.x + cos(angle) * dist
                    p.y = scr.y + sin(angle) * dist
                    val speed = 620f + 520f * StarOpticsShader.hash01((timeSec * 331).toInt() + 13)
                    // 辐射向外 + 轻微切向扰动
                    val dir = angle + (StarOpticsShader.hash01((timeSec * 227).toInt() + 31) - 0.5f) * 0.5f
                    p.vx = cos(dir) * speed
                    p.vy = sin(dir) * speed
                    p.bornSec = timeSec
                    p.lifeSec = 0.7f + 0.6f * StarOpticsShader.hash01((timeSec * 149).toInt() + 53)
                    p.len = 110f + 130f * StarOpticsShader.hash01((timeSec * 89).toInt() + 71)
                    p.bright = 0.55f + 0.45f * shower.second.toFloat()
                }
            }
        }

        // 绘制与老化（对象池复用，零新增分配）
        val n = nativeCanvas0()
        for (p in meteorPool) {
            if (!p.active) continue
            val age = timeSec - p.bornSec
            if (age < 0f || age > p.lifeSec) { p.active = false; continue }
            val t = age / p.lifeSec
            val hx = p.x + p.vx * age
            val hy = p.y + p.vy * age
            val mag = (1f - t) * (1f - t) * p.bright
            val dirLen = kotlin.math.sqrt(p.vx * p.vx + p.vy * p.vy)
            val ux = p.vx / dirLen
            val uy = p.vy / dirLen
            val q = linePaint
            q.strokeCap = Paint.Cap.ROUND
            // 电离余迹：宽淡 → 细亮
            q.color = 0xFFB3E5FC.toInt()
            q.alpha = (0.16f * mag * 255f).toInt()
            q.strokeWidth = 3.4f * dp
            n.drawLine(hx - ux * p.len * 1.35f, hy - uy * p.len * 1.35f, hx, hy, q)
            q.color = 0xFFE3F2FD.toInt()
            q.alpha = (0.75f * mag * 255f).toInt()
            q.strokeWidth = 1.5f * dp
            n.drawLine(hx - ux * p.len, hy - uy * p.len, hx, hy, q)
            fillPaint.color = 0xFFFFFFFF.toInt()
            fillPaint.alpha = (0.9f * mag * 255f).toInt()
            n.drawCircle(hx, hy, 1.7f * dp, fillPaint)
        }
    }

    private fun DrawScope.drawMeteorRadiants(
        f: SkyViewModel.FrameData, dp: Float, timeSec: Float
    ) {
        val n = nativeCanvas0()
        for ((shower, weight) in f.activeShowers) {
            AstrometryEngine.equatorialToHorizontal(
                shower.radiantRaDeg, shower.radiantDecDeg, f.lstDeg, f.latitudeDeg, hz
            )
            if (hz.altDeg < 0.0) continue
            if (!projector.projectAltAzInto(hz.altDeg, hz.azDeg, scr)) continue
            val sx = scr.x; val sy = scr.y
            val pulse = (1f + 0.10f * sin(timeSec * 2.0)).toFloat()
            val r = 12f * dp * pulse

            // 放射状星标：8 主辐条
            val gold = 0xFFFFCDD2.toInt()
            val q = linePaint
            q.strokeCap = Paint.Cap.ROUND
            q.color = gold
            q.alpha = (0.85f * 255f).toInt()
            q.strokeWidth = 1.3f * dp
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f + timeSec * 0.15f
                n.drawLine(
                    sx + cos(a) * r * 0.35f, sy + sin(a) * r * 0.35f,
                    sx + cos(a) * r, sy + sin(a) * r, q
                )
            }
            fillPaint.color = 0x66FF8A80.toInt()
            n.drawCircle(sx, sy, r * 0.3f, fillPaint)
            strokePaint.color = 0x33FF5252.toInt()
            strokePaint.alpha = 0x33
            strokePaint.strokeWidth = 1.0f * dp
            n.drawCircle(sx, sy, r * 1.5f, strokePaint)

            val tp = hudTextPaint
            tp.color = android.graphics.Color.argb(
                (140 + 100 * weight).toInt().coerceAtMost(240), 255, 138, 128
            )
            tp.textSize = 10f * dp
            val label = "☄ ${shower.nameZh} ZHR${shower.zhr}"
            n.drawText(label, sx + r * 1.7f, sy + r * 0.4f, tp)
        }
    }

    // ==================================================================
    // L8 选中轨迹
    // ==================================================================

    private fun DrawScope.drawTrajectory(f: SkyViewModel.FrameData, dp: Float) {
        val sel = vm.selected ?: return
        val bucket = f.simTimeMillis / 900_000
        // 字段级缓存键比较，免逐帧字符串拼接
        if (sel !== lastTrajSel || bucket != lastTrajBucket ||
            f.latitudeDeg != lastTrajLat || f.longitudeEastDeg != lastTrajLon || trajPoints.isEmpty()
        ) {
            lastTrajSel = sel
            lastTrajBucket = bucket
            lastTrajLat = f.latitudeDeg
            lastTrajLon = f.longitudeEastDeg
            val pts = ArrayList<Triple<Double, Double, Long>>()
            for (k in -48..48) { // ±12h，每 15 分钟
                val t = f.simTimeMillis + k * 900_000L
                val jd = AstrometryEngine.julianDate(t)
                val lst = AstrometryEngine.lstDeg(jd, f.longitudeEastDeg)
                // 缓存重建属低频路径（15 分钟桶/选目标切换），允许使用分配型精解
                val hzT = when (sel) {
                    is CelestialTarget.StarTarget ->
                        AstrometryEngine.equatorialToHorizontal(sel.raDeg, sel.decDeg, lst, f.latitudeDeg)
                    is CelestialTarget.MessierTarget ->
                        AstrometryEngine.equatorialToHorizontal(sel.raDeg, sel.decDeg, lst, f.latitudeDeg)
                    is CelestialTarget.PlanetTarget -> {
                        val id = planetaryEphemerisIdOf(sel.key)
                        if (id != null) {
                            val e = PlanetaryEphemeris.planetEphemeris(id, jd)
                            AstrometryEngine.equatorialToHorizontal(e.raDeg, e.decDeg, lst, f.latitudeDeg)
                        } else {
                            // 星座中心等静态目标：按固定赤道坐标解算
                            AstrometryEngine.equatorialToHorizontal(sel.raDeg, sel.decDeg, lst, f.latitudeDeg)
                        }
                    }
                    is CelestialTarget.MoonTarget -> {
                        val e = PlanetaryEphemeris.moonEphemeris(jd)
                        AstrometryEngine.equatorialToHorizontal(e.raDeg, e.decDeg, lst, f.latitudeDeg)
                    }
                    is CelestialTarget.SunTarget -> {
                        val e = PlanetaryEphemeris.sunEphemeris(jd)
                        AstrometryEngine.equatorialToHorizontal(e.raDeg, e.decDeg, lst, f.latitudeDeg)
                    }
                }
                pts.add(Triple(hzT.altDeg, hzT.azDeg, t))
            }
            trajPoints = pts
        }

        trajPath.rewind()
        val n = nativeCanvas0()
        for ((alt, az, t) in trajPoints) {
            if (!projector.projectAltAzInto(alt, az, scr)) continue
            trajPath.lineTo(scr.x, scr.y)
            // 整点刻度（偶数整点）
            val minuteOfDay = ((t + java.util.TimeZone.getDefault().getOffset(t)) / 60000L) % (24 * 60)
            if (minuteOfDay % 60 == 0L && minuteOfDay % 120 == 0L) {
                fillPaint.color = 0xFF80DEEA.toInt()
                n.drawCircle(scr.x, scr.y, 2.2f * dp, fillPaint)
                val tp = hudTextPaint
                tp.color = android.graphics.Color.argb(170, 128, 222, 234)
                tp.textSize = 9f * dp
                n.drawText(hourLabels[(minuteOfDay / 60).toInt().coerceIn(0, 23)], scr.x + 5f * dp, scr.y - 4f * dp, tp)
            }
        }
        if (dashCacheDp != dp) {
            dashCacheDp = dp
            dashPaint.pathEffect = DashPathEffect(floatArrayOf(10f * dp, 8f * dp), 0f)
        }
        val p = dashPaint
        p.color = 0xCC64B5F6.toInt()
        p.strokeWidth = 1.4f * dp
        n.drawPath(trajPath, p)
    }

    private var lastTrajSel: CelestialTarget? = null
    private var lastTrajBucket = Long.MIN_VALUE
    private var lastTrajLat = Double.NaN
    private var lastTrajLon = Double.NaN

    // ==================================================================
    // L2.8 + L9 球面地平弧与地下暮光
    // ==================================================================

    /**
     * 沿地平圈（alt=0）以 3° 方位角步进采样投影成自然弯曲的球面弧，
     * 并在弧下方填充柔和暮光渐变（半透明，让地下星空以透视感显影）。
     * 弧路径缓存于 [horizonArcPath]，供 L9 描边与刻度复用。
     */
    private fun DrawScope.drawGroundTwilight(f: SkyViewModel.FrameData) {
        val boreAz = if (vm.sensorMode) vm.boresight.azDeg else vm.orbit.centerAzDeg
        horizonArcCount = 0
        horizonArcMinY = Float.MAX_VALUE
        horizonArcPath.rewind()
        groundPath.rewind()
        var pen = false
        var firstX = 0f
        var lastX = 0f
        val n = HORIZON_ARC_SAMPLES
        for (k in 0 until n) {
            val az = AstrometryEngine.normalizeDeg(boreAz + (k - n / 2.0) * 3.0)
            if (!projector.projectAltAzInto(0.0, az, scr)) { pen = false; continue }
            val x = scr.x; val y = scr.y
            if (y < horizonArcMinY) horizonArcMinY = y
            if (!pen) { horizonArcPath.moveTo(x, y); groundPath.moveTo(x, y); pen = true; firstX = x }
            else { horizonArcPath.lineTo(x, y); groundPath.lineTo(x, y) }
            lastX = x
            horizonArcCount++
        }
        if (horizonArcCount >= 2 && pen) {
            // 弧下方暮光多边形（弧线 → 屏幕底部）
            groundPath.lineTo(lastX, size.height + 4f)
            groundPath.lineTo(firstX, size.height + 4f)
            groundPath.close()
            val bucket = (horizonArcMinY / 24f).toInt()
            if (bucket != horizonTwilightBucket || horizonTwilightBrush == null) {
                horizonTwilightBucket = bucket
                horizonTwilightBrush = Brush.verticalGradient(
                    listOf(Color(0x2E0A1428), Color(0x8A02040A)),
                    startY = (bucket * 24f).coerceIn(0f, size.height * 0.92f),
                    endY = size.height
                )
            }
            horizonTwilightBrush?.let { drawPath(groundPath, it) }
        }
    }

    /** 极细球面地平弧 + 15° 微刻度针 + 45° 方位角文字。 */
    private fun DrawScope.drawHorizon(f: SkyViewModel.FrameData, dp: Float) {
        if (horizonArcCount < 2) return
        val n = nativeCanvas0()
        val q = linePaint
        q.strokeCap = Paint.Cap.ROUND
        q.color = 0x5970D6FF.toInt()
        q.strokeWidth = 0.8f * dp
        n.drawPath(horizonArcPath, q)

        val boreAz = if (vm.sensorMode) vm.boresight.azDeg else vm.orbit.centerAzDeg
        val spanDeg = (HORIZON_ARC_SAMPLES / 2.0) * 3.0
        val tp = hudTextPaint
        tp.isFakeBoldText = false
        tp.setShadowLayer(3f, 0f, 0f, 0xCC000000.toInt())
        var az = kotlin.math.floor((boreAz - spanDeg) / 15.0) * 15.0
        while (az <= boreAz + spanDeg) {
            val azN = AstrometryEngine.normalizeDeg(az)
            if (projector.projectAltAzInto(0.0, azN, scr)) {
                val major = (azN % 45.0).toInt() == 0
                tickPaint.color = 0x9970D6FF.toInt()
                tickPaint.strokeWidth = 0.9f * dp
                n.drawLine(scr.x, scr.y, scr.x, scr.y - 4f * dp, tickPaint)
                if (major) {
                    val label = ARC_AZ_LABELS[((azN / 45.0).toInt()) % 8]
                    tp.color = android.graphics.Color.argb(0xE6, 200, 222, 255)
                    tp.textSize = 9f * dp
                    val tw = tp.measureText(label)
                    n.drawText(label, scr.x - tw / 2, scr.y - 8f * dp, tp)
                }
            }
            az += 15.0
        }
        tp.clearShadowLayer()
    }

    /** 引导目标入镜锁定事件（边沿检测），由 UI 帧循环消费触发触觉/音效。 */
    private var lastGuideInView = false
    fun consumeGuideLockEvent(): Boolean {
        val inView = overlay.guideScreen != null &&
            (overlay.guideSepDeg ?: 99.0) < SkyViewModel.SNAP_THRESHOLD_DEG
        val fired = inView && !lastGuideInView
        lastGuideInView = inView
        return fired
    }

    /** 为 HUD 准星层填充吸附与引导数据。 */
    private fun updateOverlayData(f: SkyViewModel.FrameData, w: Float, h: Float, dp: Float) {
        val snap = vm.snapped
        if (snap != null) {
            val s = projector.projAltAz(snap.altDeg, snap.azDeg)
            overlay.snappedScreen = s
            overlay.snappedLabel = snap.target.displayName
            // 详情串仅在目标或 0.1° 级数值变化时重建，避免逐帧格式化分配
            val altQ = (snap.altDeg * 10).toInt()
            val azQ = (snap.azDeg).toInt()
            val sepQ = (snap.separationDeg * 10).toInt()
            if (snap.target !== lastSnapRef || altQ != lastSnapAltQ ||
                azQ != lastSnapAzQ || sepQ != lastSnapSepQ
            ) {
                overlay.snappedDetail = "星等 %+.1f · 高度 %.1f° · 方位 %.0f° · 偏离 %.1f°"
                    .format(snap.mag, snap.altDeg, snap.azDeg, snap.separationDeg)
                lastSnapRef = snap.target
                lastSnapAltQ = altQ; lastSnapAzQ = azQ; lastSnapSepQ = sepQ
            }
            overlay.snappedSepDeg = snap.separationDeg
        } else {
            overlay.snappedScreen = null
            overlay.snappedLabel = ""
            lastSnapRef = null
        }

        val guide = vm.guideTarget
        if (guide != null) {
            AstrometryEngine.equatorialToHorizontal(
                guide.raDeg, guide.decDeg, f.lstDeg, f.latitudeDeg, hz
            )
            val v = AstrometryEngine.altAzToEnu(hz.altDeg, hz.azDeg)
            val s = projector.proj(v)
            // 入镜判定按“屏幕边距”而非视锥：竖屏水平 FOV 窄，目标常在视锥前部
            // 却远超屏幕边缘，此时必须给出 360° 离轴引导箭头
            val m = 34f * dp
            val onScreen = s != null &&
                s.x >= m && s.x <= w - m && s.y >= m && s.y <= h - m
            overlay.guideScreen = if (onScreen) s else null
            overlay.guideAngleDeg = if (!onScreen) {
                if (s != null) {
                    // 前方但离屏：直接用屏幕坐标方位（y 向下）
                    Math.toDegrees(
                        kotlin.math.atan2(
                            (s.y - h / 2f).toDouble(),
                            (s.x - w / 2f).toDouble()
                        )
                    ).toFloat()
                } else {
                    // 视线后方：相机平面指向角，屏幕系 y 向下取反
                    projector.cameraPlaneAngleDeg(v)?.let { -it.toFloat() }
                }
            } else null
            overlay.guideSepDeg = AstrometryEngine.angularSeparationDeg(
                vm.boresight.azDeg, vm.boresight.altDeg, hz.azDeg, hz.altDeg
            )
            overlay.guideName = guide.displayName
        } else {
            overlay.guideAngleDeg = null
            overlay.guideSepDeg = null
            overlay.guideScreen = null
            overlay.guideName = ""
        }
    }

    // ==================================================================
    // 通用辅助
    // ==================================================================

    /** Color → ARGB int（alpha 通道保留）。 */
    private fun Color.toArgb255(): Int = this.toArgb()

    companion object {
        private const val BG_STAR_COUNT = 1500
        private const val HORIZON_ARC_SAMPLES = 51 // ±75° 方位角，3° 步进
        private val MW_WIDTHS = floatArrayOf(16f, 9.5f, 5f)
        private val MW_ALPHAS = floatArrayOf(0.03f, 0.04f, 0.05f)
        private val MW_COLORS = intArrayOf(
            0xFF4A78D0.toInt(), // 外层宽带 · 星尘蓝
            0xFF6B7FD7.toInt(), // 中层 · 淡紫蓝
            0xFFB8C6E8.toInt()  // 内层 · 暖星光
        )
        private val ARC_AZ_LABELS = arrayOf(
            "N 0°", "NE 45°", "E 90°", "SE 135°",
            "S 180°", "SW 225°", "W 270°", "NW 315°"
        )
        private const val SUN_LABEL = "太阳 Sun"
        private const val SAT_SUNLIT = "被阳光照亮"
        private const val SAT_ECLIPSED = "地影中"

        // 时间轨迹渲染中行星 key → 枚举（保留：供未来轨迹行星精化解算）
        fun planetaryEphemerisIdOf(key: String) = when (key) {
            "MERCURY" -> com.celestiaview.ar.core.math.PlanetaryEphemeris.PlanetId.MERCURY
            "VENUS" -> com.celestiaview.ar.core.math.PlanetaryEphemeris.PlanetId.VENUS
            "MARS" -> com.celestiaview.ar.core.math.PlanetaryEphemeris.PlanetId.MARS
            "JUPITER" -> com.celestiaview.ar.core.math.PlanetaryEphemeris.PlanetId.JUPITER
            "SATURN" -> com.celestiaview.ar.core.math.PlanetaryEphemeris.PlanetId.SATURN
            else -> null
        }
    }
}
