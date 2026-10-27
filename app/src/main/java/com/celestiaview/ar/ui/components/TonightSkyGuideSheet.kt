package com.celestiaview.ar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.render.MoonPhaseRenderer
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.redLightSafe
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 今夜最佳观测向导：天文晨昏蒙影时间轴 + 实时月相盘 + 当晚可见天体榜单。
 */
@Composable
fun TonightSkyGuideSheet(
    vm: SkyViewModel,
    onDismiss: () -> Unit,
    onGuide: (CelestialTarget) -> Unit
) {
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else DeepSpacePalette.AstralGold
    val f = vm.frame
    val tick = vm.frameTick

    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 各晨昏蒙影相位的日落/日出
    val sunRts = AstrometryEngine.riseTransitSet(
        f.sunEph.raDeg, f.sunEph.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg,
        h0Deg = AstrometryEngine.H0_SUN_DEG
    )
    val civil = AstrometryEngine.riseTransitSet(
        f.sunEph.raDeg, f.sunEph.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg,
        h0Deg = -6.0
    )
    val astro = AstrometryEngine.riseTransitSet(
        f.sunEph.raDeg, f.sunEph.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg,
        h0Deg = -18.0
    )
    val moonRts = AstrometryEngine.riseTransitSet(
        f.moonEph.raDeg, f.moonEph.decDeg, f.latitudeDeg, dayStart, f.longitudeEastDeg
    )

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 26.dp,
        redLight = red
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今夜星空简报 TONIGHT'S SKY",
                    color = accent.redLightSafe(red),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
                }
            }

            // ---------- 黄金观测时间窗 ----------
            Spacer(Modifier.height(8.dp))
            Text("黄金观星时间窗", color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TwilightBar(red)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("日落 ${sunRts.setMillis?.let { fmt.format(Date(it)) } ?: "--:--"} · ")
                    append("天文昏影终 ${astro.setMillis?.let { fmt.format(Date(it)) } ?: "极夜/白夜"} · ")
                    append("天文晨光始 ${astro.riseMillis?.let { fmt.format(Date(it)) } ?: "--:--"} · ")
                    append("日出 ${sunRts.riseMillis?.let { fmt.format(Date(it)) } ?: "--:--"}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )

            // ---------- 月相大盘 ----------
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val density = LocalDensity.current
                Canvas(Modifier.size(64.dp)) {
                    with(MoonPhaseRenderer) {
                        drawMoon(
                            center = Offset(size.width / 2f, size.height / 2f),
                            radiusPx = size.minDimension / 2f * 0.92f,
                            phaseAngleDeg = f.moonEph.phaseAngleDeg
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "月相：${f.moonEph.phaseNameZh} ${f.moonEph.phaseNameEn}",
                        color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "亮面 %.1f%% · 月龄 %.1f 天".format(
                            f.moonEph.illuminationFraction * 100,
                            f.moonEph.phaseAngleDeg / 360.0 * 29.53
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red),
                        fontSize = 11.sp
                    )
                    Text(
                        "月升 ${moonRts.riseMillis?.let { fmt.format(Date(it)) } ?: "--:--"} · " +
                            "月落 ${moonRts.setMillis?.let { fmt.format(Date(it)) } ?: "--:--"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red),
                        fontSize = 11.sp
                    )
                }
            }

            // ---------- 今夜可见榜单 ----------
            Spacer(Modifier.height(14.dp))
            Text("今夜可见 · 高度 > 15°（点击启动 360° 寻星引导）",
                color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // 榜单计算包含 110 个梅西耶天体解算与 24h 卫星过境扫描（约数十毫秒），
            // 仅在面板进入组合时计算一次；直接写在组合体内会随 60fps 重组每帧重算
            val events = remember { vm.tonightHighlights() }
            LazyColumn(Modifier.height(240.dp)) {
                items(events) { ev ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onGuide(eventToTarget(vm, ev))
                            }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ev.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.redLightSafe(red))
                            Text(ev.subtitle, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red))
                        }
                        Text("星等 %+.1f".format(ev.mag), fontSize = 11.sp,
                            color = accent.redLightSafe(red))
                    }
                }
            }
        }
    }
}

/** 晨昏蒙影渐变时间条：白昼 → 民用/航海/天文蒙影 → 深夜 → 晨光。
 *  以离散插值色段绘制：Brush.horizontalGradient 缺省 endX=+∞，
 *  无限宽着色器会击穿 qemu 等软件图形栈。 */
@Composable
private fun TwilightBar(red: Boolean) {
    val stops = listOf(
        Color(0xFF2E5FA3),  // 白昼
        Color(0xFF6E4E8C),  // 民用昏影
        Color(0xFF2B2E52),  // 航海昏影
        Color(0xFF070B18),  // 天文黑夜
        Color(0xFF2B2E52),  // 航海晨光
        Color(0xFF6E4E8C),  // 民用晨光
        Color(0xFF2E5FA3)   // 次日白昼
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
    ) {
        val chips = 56
        val seg = (chips - 1).toFloat() / (stops.size - 1).toFloat()
        for (i in 0 until chips) {
            val pos = i / seg
            val k = pos.toInt().coerceAtMost(stops.size - 2)
            val t = (pos - k).coerceIn(0f, 1f)
            val c = lerpColor(stops[k], stops[k + 1], t)
            Box(Modifier.weight(1f).fillMaxHeight().background(c))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

private fun eventToTarget(vm: SkyViewModel, ev: com.celestiaview.ar.data.model.TonightEvent): CelestialTarget {
    return when (ev.kind) {
        com.celestiaview.ar.data.model.TonightEvent.Kind.STAR ->
            vm.starByName(ev.targetKey)?.let { CelestialTarget.StarTarget(it) }
        com.celestiaview.ar.data.model.TonightEvent.Kind.PLANET ->
            CelestialTarget.PlanetTarget(ev.targetKey, ev.title, ev.raDeg, ev.decDeg)
        com.celestiaview.ar.data.model.TonightEvent.Kind.MESSIER ->
            com.celestiaview.ar.data.catalog.MessierCatalog.find(ev.targetKey)
                ?.let { CelestialTarget.MessierTarget(it) }
        else -> null
    } ?: CelestialTarget.PlanetTarget("MOON", "月球", ev.raDeg, ev.decDeg)
}
