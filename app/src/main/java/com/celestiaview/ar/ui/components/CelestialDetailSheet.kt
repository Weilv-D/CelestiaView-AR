package com.celestiaview.ar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.data.model.DsoType
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.render.StarOpticsShader
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.CelestialType
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.redLightSafe
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 抽屉深邃底色：实拍横幅底缘溶入的目标色。 */
private val SheetDeepColor = Color(0xF2050914)

/**
 * 天体百科详情抽屉：画册级实拍横幅 + 实时遥测仪表盘 + 24h 高度角曲线 +
 * 今日升降时刻 + 物理属性网格 + 光谱色温条 + 中西文化典故。
 */
@Composable
fun CelestialDetailSheet(
    vm: SkyViewModel,
    onDismiss: () -> Unit
) {
    val target = vm.selected ?: return
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else Color(0xFF94A3B8)
    val f = vm.frame

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 660.dp) // 浮层抽屉：内容超高时内部滚动，底部让出船坞
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
    ) {
        // ---------- 沉浸式实拍大图横幅（底部溶入抽屉深邃背景） ----------
        AstrophotoHeader(vm, target, red)

        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
            // ---------- 操作行 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                NakedTextButton(
                    text = "寻星引导",
                    selected = vm.guideTarget == target,
                    redLight = red
                ) { vm.startGuide(target) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 今日可见性：24h 高度角曲线（紧随横幅，处于抽屉首屏） ----------
            val hz = AstrometryEngine.equatorialToHorizontal(
                target.raDeg, target.decDeg, f.lstDeg, f.latitudeDeg
            )
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val rts = AstrometryEngine.riseTransitSet(
                target.raDeg, target.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg,
                h0Deg = if (target is CelestialTarget.SunTarget) AstrometryEngine.H0_SUN_DEG
                else AstrometryEngine.H0_STAR_DEG
            )
            AltitudeCurve24hChart(vm, target, rts, dayStart, hz.altDeg, red)

            Spacer(Modifier.height(14.dp))

            // ---------- 实时遥测 ----------
            val hourAngle = AstrometryEngine.normalizeDeg180(f.lstDeg - target.raDeg)
            SectionTitle("实时遥测 TELEMETRY", accent.redLightSafe(red))
            TelemetryGrid(
                listOf(
                    "赤经 RA" to raString(target.raDeg),
                    "赤纬 Dec" to decString(target.decDeg),
                    "方位角 Az" to "%.2f°".format(hz.azDeg),
                    "高度角 Alt" to "%.2f°".format(hz.altDeg),
                    "视高度" to "%.2f°".format(AstrometryEngine.apparentAltitudeDeg(hz.altDeg)),
                    "时角 H" to "%+.2f°".format(hourAngle)
                ),
                red
            )
            val badge = when {
                hz.altDeg <= -0.5 -> "已落下 / 地平线下"
                hourAngle > 2.0 -> "西沉中 / 已过中天"
                hourAngle < -2.0 -> "正在升起 / 接近东方"
                else -> "接近中天 · 最高仰角"
            }
            Text(
                "● $badge",
                color = (if (hz.altDeg > -0.5) DeepSpacePalette.RiseGreen else DeepSpacePalette.SetOrange)
                    .redLightSafe(red),
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            // ---------- 今日可见性：升降中天时刻（曲线见抽屉首屏） ----------
            SectionTitle("今日可见性 VISIBILITY", accent.redLightSafe(red))
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            when {
                rts.circumpolar -> Text("拱极星 · 今夜永不落下（最高 %.1f°）".format(rts.transitMaxAltDeg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red), fontSize = 13.sp)
                rts.neverRises -> Text("今夜位于地平线之下，不可见",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red), fontSize = 13.sp)
                else -> TelemetryGrid(
                    listOf(
                        "升起" to (rts.riseMillis?.let { fmt.format(Date(it)) } ?: "--:--"),
                        "中天" to (rts.transitMillis?.let { fmt.format(Date(it)) } ?: "--:--"),
                        "最高仰角" to "%.1f°".format(rts.transitMaxAltDeg),
                        "落下" to (rts.setMillis?.let { fmt.format(Date(it)) } ?: "--:--")
                    ),
                    red
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 物理属性 ----------
            SectionTitle("物理属性 PHYSICS", accent.redLightSafe(red))
            when (target) {
                is CelestialTarget.StarTarget -> {
                    val s = target.star
                    TelemetryGrid(
                        listOf(
                            "视星等" to "%+.2f".format(s.mag),
                            "绝对星等" to "%+.2f".format(PlanetaryEphemeris.absoluteMagnitude(s.mag, s.distLy)),
                            "距离" to if (s.distLy >= 1000) "%.0f 光年".format(s.distLy) else "%.1f 光年".format(s.distLy),
                            "光谱型" to s.spectral,
                            "色指数 B-V" to "%+.2f".format(s.bv),
                            "所属星座" to s.constellation
                        ),
                        red
                    )
                    SpectrumBar(s.bv, red)
                }
                is CelestialTarget.PlanetTarget -> {
                    val p = f.planets.firstOrNull { it.meta.key == target.key }
                    if (p != null) {
                        TelemetryGrid(
                            listOf(
                                "视星等" to "%+.1f".format(p.mag),
                                "距地球" to "%.3f AU".format(p.eph.distanceAu),
                                "赤经" to raString(target.raDeg),
                                "赤纬" to decString(target.decDeg)
                            ),
                            red
                        )
                    }
                }
                is CelestialTarget.MoonTarget -> {
                    val m = f.moonEph
                    val moonAge = (m.phaseAngleDeg / 360.0) * 29.53
                    TelemetryGrid(
                        listOf(
                            "月相" to m.phaseNameZh,
                            "亮面比例" to "%.1f%%".format(m.illuminationFraction * 100),
                            "月龄" to "%.1f 天".format(moonAge),
                            "距离" to "%.0f km".format(m.distanceKm),
                            "赤经" to raString(target.raDeg),
                            "赤纬" to decString(target.decDeg)
                        ),
                        red
                    )
                }
                is CelestialTarget.SunTarget -> {
                    TelemetryGrid(
                        listOf(
                            "距地球" to "%.4f AU".format(f.sunEph.distanceAu),
                            "黄经" to "%.2f°".format(f.sunEph.eclipticLonDeg),
                            "地平高度" to "%.1f°".format(f.sunAltDeg),
                            "赤纬" to decString(target.decDeg)
                        ),
                        red
                    )
                }
                is CelestialTarget.MessierTarget -> {
                    val o = target.dso
                    TelemetryGrid(
                        listOf(
                            "类型" to dsoTypeName(o.type),
                            "视星等" to "%.1f".format(o.mag),
                            "视直径" to "%.0f′".format(o.majorAxisArcmin),
                            "距离" to if (o.distanceKly >= 1000) "%.1f 百万光年".format(o.distanceKly / 1000)
                            else "%.0f 千光年".format(o.distanceKly),
                            "赤经" to raString(target.raDeg),
                            "赤纬" to decString(target.decDeg)
                        ),
                        red
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 科普与文化 ----------
            SectionTitle("天文科普与文化典故 LORE", accent.redLightSafe(red))
            val story = storyOf(vm, target)
            Text(
                story.first,
                style = CelestialType.LoreBody.copy(
                    color = CelestialPalette.TextSecondary.redLightSafe(red)
                )
            )
            if (story.second.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    story.second,
                    style = CelestialType.LoreBody.copy(
                        color = CelestialPalette.TextSecondary.redLightSafe(red)
                    )
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// 画册级横幅与 24h 高度曲线
// ----------------------------------------------------------------------

/**
 * 顶部沉浸式实拍横幅：220dp 高清实拍（未就绪时程序化底片）铺满抽屉首屏，
 * 底缘以垂直渐变溶入抽屉深邃底色；左下角叠加无框双语天体名、分类徽章
 * 与摄影信源角标（NASA / HST / DSS2 Optical Survey）。
 */
@Composable
private fun AstrophotoHeader(vm: SkyViewModel, target: CelestialTarget, red: Boolean) {
    val plate = vm.focusPlate
    val photo = vm.focusPhoto
    val sourceLabel = vm.focusRequest?.let { req ->
        if (photo != null) req.sourceLabel.uppercase(Locale.US) else "程序化底片 · 实拍加载中"
    } ?: "OPTICAL SURVEY · DSS2 / NASA HST"

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        if (plate != null) {
            Image(
                bitmap = plate,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (photo != null) {
                // 聚焦交叉淡化由帧循环以固定速率推进（1.4/s），直读即得丝滑淡入，
                // 不再叠加弹簧动画的双层迟滞
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(vm.focusCrossfade)
                )
            }
        } else {
            Box(Modifier.matchParentSize().background(SheetDeepColor))
        }

        // 底缘溶接渐变：照片边界消失于抽屉底色（尺寸驱动的有限区间渐变）
        val bannerBg = SheetDeepColor.redLightSafe(red)
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bannerBg.copy(alpha = 0.55f),
                                bannerBg
                            ),
                            startY = size.height * 0.42f,
                            endY = size.height * 0.98f
                        )
                    )
                }
        )

        // 左下角：双语名 + 分类徽章 + 摄影信源角标
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
        ) {
            Text(
                target.displayName,
                style = CelestialType.HeroTitle.copy(
                    color = CelestialPalette.TextPrimary.redLightSafe(red)
                )
            )
            subtitleOf(target)?.let {
                Text(
                    it.uppercase(),
                    style = CelestialType.LatinSubtitle.copy(
                        color = CelestialPalette.AccentCyan.redLightSafe(red)
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    classificationOf(target),
                    modifier = Modifier
                        .background(
                            Color(0x337DD3FC).redLightSafe(red),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = Color(0xFFDBEAFE).redLightSafe(red)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    sourceLabel,
                    style = CelestialType.Tnum.copy(
                        color = Color(0xCCE8C96A).redLightSafe(red)
                    ),
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 天体分类徽章文案。 */
private fun classificationOf(target: CelestialTarget): String = when (target) {
    is CelestialTarget.StarTarget -> "${target.star.spectral} 型恒星"
    is CelestialTarget.PlanetTarget -> "行星"
    is CelestialTarget.MoonTarget -> "天然卫星"
    is CelestialTarget.SunTarget -> "G2V 主序星"
    is CelestialTarget.MessierTarget -> dsoTypeName(target.dso.type)
}

private fun dsoTypeName(type: DsoType): String = when (type) {
    DsoType.GALAXY -> "星系"
    DsoType.NEBULA -> "星云"
    DsoType.GLOBULAR_CLUSTER -> "球状星团"
    DsoType.OPEN_CLUSTER -> "疏散星团"
    DsoType.PLANETARY_NEBULA -> "行星状星云"
    DsoType.SUPERNOVA_REMNANT -> "超新星遗迹"
}

/**
 * 今日 24 小时地平高度角曲线：0° 地平虚线、升起（绿）/ 中天（金）/ 落下（橙）
 * 时刻节点，以及代表“此刻位置”的发光星点。
 * 采样在目标 / 站点 / 日期变化时重算一次，绘制零分配。
 */
@Composable
private fun AltitudeCurve24hChart(
    vm: SkyViewModel,
    target: CelestialTarget,
    rts: AstrometryEngine.RiseTransitSet,
    dayStartMillis: Long,
    nowAltDeg: Double,
    red: Boolean
) {
    val f = vm.frame
    val samples = remember(
        target.raDeg, target.decDeg, f.latitudeDeg, f.longitudeEastDeg, dayStartMillis
    ) {
        AstrometryEngine.altitudeCurve24h(
            target.raDeg, target.decDeg, f.latitudeDeg, f.longitudeEastDeg, dayStartMillis
        )
    }
    val curveColor = (if (red) DeepSpacePalette.RedSafePrimary else Color(0xFF7DD3FC))
        .redLightSafe(red)
    val riseColor = DeepSpacePalette.RiseGreen.redLightSafe(red)
    val setColor = DeepSpacePalette.SetOrange.redLightSafe(red)
    val transitColor = if (red) DeepSpacePalette.RedSafePrimary else Color(0xFF94A3B8)
    val gridColor = Color(0x3DFFFFFF).redLightSafe(red)
    val nowColor = Color(0xFFF8FAFC).redLightSafe(red)

    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
        val w = size.width
        val h = size.height
        val altMin = -25f
        val altMax = 85f
        fun yOf(altDeg: Double): Float = h * (
            1f - ((altDeg.toFloat() - altMin) / (altMax - altMin)).coerceIn(0f, 1f)
        )
        fun xOf(millis: Long): Float =
            w * ((millis - dayStartMillis).toFloat() / 86_400_000f)

        // 30° 仰角参考细线 + 0° 地平虚线
        drawLine(
            gridColor, Offset(0f, yOf(30.0)), Offset(w, yOf(30.0)), 0.6f.dp.toPx()
        )
        drawLine(
            gridColor,
            Offset(0f, yOf(0.0)), Offset(w, yOf(0.0)), 0.8f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
        )

        // 高度角曲线
        val path = Path()
        val last = samples.size - 1
        for (i in samples.indices) {
            val x = w * i / last.toFloat()
            val y = yOf(samples[i])
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, curveColor.copy(alpha = 0.92f), style = Stroke(1.7f.dp.toPx()))

        // 升起 / 中天 / 落下节点（时间细节由下方遥测网格给出）
        fun markAt(millis: Long?, color: Color) {
            if (millis == null) return
            val x = xOf(millis)
            if (x < 0f || x > w) return
            val y = yOf(samples[((x / w) * last).toInt().coerceIn(0, last)])
            drawLine(color, Offset(x, h * 0.82f), Offset(x, y - 6f.dp.toPx()), 1f.dp.toPx())
            drawCircle(color, 2.6f.dp.toPx(), Offset(x, y))
        }
        markAt(rts.riseMillis, riseColor)
        markAt(rts.setMillis, setColor)
        rts.transitMillis?.let { tx ->
            val x = xOf(tx)
            if (x in 0f..w) {
                val y = yOf(rts.transitMaxAltDeg)
                drawLine(transitColor, Offset(x, h * 0.82f), Offset(x, y - 6f.dp.toPx()), 1f.dp.toPx())
                drawCircle(transitColor, 2.6f.dp.toPx(), Offset(x, y))
            }
        }

        // 此刻：发光星点（径向渐变显式有限 center/radius，兼容软件图形栈）
        val nowX = xOf(f.simTimeMillis).coerceIn(0f, w)
        val nowY = yOf(nowAltDeg)
        val glowR = (13f.dp.toPx()).coerceIn(2f, 600f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nowColor.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(nowX, nowY),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(nowX, nowY)
        )
        drawCircle(nowColor, 3.1f.dp.toPx(), Offset(nowX, nowY))
        drawCircle(Color(0xFF0A1220), 1.4f.dp.toPx(), Offset(nowX, nowY))
    }
    Text(
        "高度角 24H ALTITUDE",
        fontSize = 9.sp, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red),
        modifier = Modifier.padding(top = 2.dp)
    )
}

// ----------------------------------------------------------------------
// 辅助
// ----------------------------------------------------------------------

@Composable
private fun SectionTitle(t: String, color: androidx.compose.ui.graphics.Color) {
    Text(t, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TelemetryGrid(items: List<Pair<String, String>>, red: Boolean) {
    Column {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                rowItems.forEach { (k, v) ->
                    TelemetryMetricRow(
                        label = k,
                        value = v,
                        modifier = Modifier.weight(1f),
                        redLight = red
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Hairline(redLight = red)
        }
    }
}

@Composable
private fun SpectrumBar(bv: Double, red: Boolean) {
    Column {
        Spacer(Modifier.height(6.dp))
        // 离散色阶芯片：无限宽渐变着色器会击穿 qemu 等软件图形栈
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val chips = 36
            val currentIdx = (((bv + 0.5) / 2.3) * chips).toInt().coerceIn(0, chips - 1)
            for (i in 0 until chips) {
                val bvAt = -0.5 + 2.3 * i / (chips - 1.0)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            StarOpticsShader.bvToColor(bvAt).let {
                                if (i == currentIdx) it.copy(alpha = 1f).redLightSafe(red)
                                else it.copy(alpha = 0.82f).redLightSafe(red)
                            }
                        )
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("蓝巨星", fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red))
            Text("当前 B-V %+.2f".format(bv), fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red))
            Text("红巨星", fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red))
        }
    }
}

@Composable
private fun subtitleOf(target: CelestialTarget): String? = when (target) {
    is CelestialTarget.StarTarget ->
        "${target.star.nameEn} · ${target.star.spectral} · B-V %+.2f".format(target.star.bv)
    is CelestialTarget.PlanetTarget -> target.key
    is CelestialTarget.MoonTarget -> "MOON"
    is CelestialTarget.SunTarget -> "SUN"
    is CelestialTarget.MessierTarget -> "${target.dso.nameEn} · ${target.dso.constellation}"
}

@Composable
private fun storyOf(vm: SkyViewModel, target: CelestialTarget): Pair<String, String> = when (target) {
    is CelestialTarget.StarTarget -> {
        val s = target.star
        val con = vm.constellationOf(s.constellation)
        Pair(
            s.description.ifBlank { "${s.nameZh}（${s.nameEn}），${s.spectral} 型恒星，距我们 ${if (s.distLy >= 1000) "%.0f" .format(s.distLy) else "%.1f".format(s.distLy)} 光年。" },
            con?.let { "【西方神话】${it.storyWestern}\n\n【中国星官】${it.storyChinese}" } ?: ""
        )
    }
    is CelestialTarget.PlanetTarget -> {
        val meta = PlanetaryEphemeris.planetMeta.firstOrNull { it.key == target.key }
        Pair(meta?.description ?: "", "行星在黄道带上巡游，古代占星家称其为“流浪者”（Planetes）。")
    }
    is CelestialTarget.MoonTarget -> Pair(
        "月球是地球唯一的天然卫星，平均距离 38.4 万公里。同步自转使它永远以同一面朝向我们；" +
            "明暗界线的推移勾勒出阴晴圆缺，潮汐锁定与月海盆地记录了太阳系 46 亿年的沧桑。",
        "中国古代以月相定历法——朔望月 29.53 天即是农历一个月的由来。" +
            "中秋赏月、“月有阴晴圆缺”的咏叹皆源于这颗离我们最近的邻居。"
    )
    is CelestialTarget.SunTarget -> Pair(
        "太阳是距我们最近的恒星，G2V 型黄矮星，核心每秒将 6 亿吨氢聚变为氦。" +
            "它的光球温度 5772K，一生的燃料还够燃烧约 50 亿年。",
        "圭表测影、二十四节气皆以太阳周年视运动为标尺。" +
            "古人以“日为阳精之宗”，夏至“立竿无影”的观测可追溯至商周。"
    )
    is CelestialTarget.MessierTarget -> Pair(target.dso.description, "")
}

private fun raString(raDeg: Double): String {
    val h = raDeg / 15.0
    return "%02dh %02dm %02ds".format(h.toInt(), ((h * 60) % 60).toInt(), ((h * 3600) % 60).toInt())
}

private fun decString(decDeg: Double): String {
    val sign = if (decDeg >= 0) "+" else "-"
    val a = kotlin.math.abs(decDeg)
    return "%s%02d° %02d′".format(sign, a.toInt(), ((a * 60) % 60).toInt())
}
