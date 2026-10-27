package com.celestiaview.ar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.data.model.CelestialTarget
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

/**
 * 天体百科详情抽屉：实时遥测仪表盘 + 今日升降时刻 + 物理属性网格 + 光谱色温条 + 中西文化典故。
 */
@Composable
fun CelestialDetailSheet(
    vm: SkyViewModel,
    onDismiss: () -> Unit
) {
    val target = vm.selected ?: return
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else DeepSpacePalette.AstralGold
    val f = vm.frame
    // 遥测数值降频刷新（~5Hz 足够），避免抽屉以 60fps 全量重组拖垮低端图形栈
    val tick = vm.frameTick / 12

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 26.dp,
        redLight = red
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .heightIn(max = 640.dp) // 内容超高时内部滚动，防止整抽屉把底部控件顶出屏幕
                .verticalScroll(rememberScrollState())
        ) {
            // ---------- 高清实拍画幅（程序化底片 → 真实摄影交叉淡化） ----------
            AstrophotoHeader(vm, red)

            // ---------- 标题行 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        target.displayName,
                        style = CelestialType.HeroTitle.copy(
                            color = CelestialPalette.TextPrimary.redLightSafe(red)
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    subtitleOf(target)?.let {
                        Text(
                            it.uppercase(),
                            style = CelestialType.LatinSubtitle.copy(
                                color = CelestialPalette.AccentCyan.redLightSafe(red)
                            )
                        )
                    }
                }
                NakedTextButton(
                    text = "寻星引导",
                    selected = vm.guideTarget == target,
                    redLight = red
                ) { vm.startGuide(target) }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 实时遥测 ----------
            val hz = AstrometryEngine.equatorialToHorizontal(
                target.raDeg, target.decDeg, f.lstDeg, f.latitudeDeg
            )
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

            // ---------- 今日可见性 ----------
            SectionTitle("今日可见性 VISIBILITY", accent.redLightSafe(red))
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val rts = AstrometryEngine.riseTransitSet(
                target.raDeg, target.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg,
                h0Deg = if (target is CelestialTarget.SunTarget) AstrometryEngine.H0_SUN_DEG
                else AstrometryEngine.H0_STAR_DEG
            )
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
                    val typeName = when (o.type) {
                        com.celestiaview.ar.data.model.DsoType.GALAXY -> "星系"
                        com.celestiaview.ar.data.model.DsoType.NEBULA -> "星云"
                        com.celestiaview.ar.data.model.DsoType.GLOBULAR_CLUSTER -> "球状星团"
                        com.celestiaview.ar.data.model.DsoType.OPEN_CLUSTER -> "疏散星团"
                        com.celestiaview.ar.data.model.DsoType.PLANETARY_NEBULA -> "行星状星云"
                        com.celestiaview.ar.data.model.DsoType.SUPERNOVA_REMNANT -> "超新星遗迹"
                    }
                    TelemetryGrid(
                        listOf(
                            "类型" to typeName,
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
// 辅助
// ----------------------------------------------------------------------

/**
 * 详情抽屉顶部高清实拍画幅：径向羽化后的真实天体摄影（未就绪时程序化底片），
 * 圆形画幅 + 右下角拍摄源标注（NASA / HST / DSS2 Optical Survey）。
 */
@Composable
private fun AstrophotoHeader(vm: SkyViewModel, red: Boolean) {
    val plate = vm.focusPlate ?: return
    val photo = vm.focusPhoto
    val label = vm.focusRequest?.let { req ->
        if (photo != null) req.sourceLabel else "程序化底片 · 实拍加载中"
    } ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CelestialPalette.ZenithNight.redLightSafe(red)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = plate,
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            modifier = Modifier.fillMaxHeight()
        )
        if (photo != null) {
            val fade by animateFloatAsState(targetValue = vm.focusCrossfade, label = "photoFade")
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.fillMaxHeight().alpha(fade)
            )
        }
        Text(
            label,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 8.dp),
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            style = CelestialType.Tnum.copy(
                color = Color(0xCCE8C96A).redLightSafe(red)
            )
        )
    }
    Spacer(Modifier.height(12.dp))
}

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
