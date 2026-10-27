package com.celestiaview.ar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.redLightSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 时间机器控制台（展开态）：无框光学刻度尺（中心 1dp 纯白发光指针 +
 * 疏密相间等宽刻度，横向拨动改写观测时刻，逐格触觉反馈）+
 * 多倍速播放栏 + 一键回今。折叠态的常驻入口是主界面左下角的时间印记。
 */
@Composable
fun TimeMachineScrubber(
    vm: SkyViewModel,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    if (!expanded) return
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else DeepSpacePalette.AstralGold

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        cornerRadius = 24.dp,
        redLight = red
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "时间机器 TIME MACHINE",
                    color = accent.redLightSafe(red),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        Icons.Outlined.ExpandLess,
                        contentDescription = "收起",
                        tint = accent.redLightSafe(red)
                    )
                }
            }
            Text(
                formatDateTime(vm.simTimeMillis) + "   LST " + formatLst(vm.frame.lstDeg),
                color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Spacer(Modifier.height(4.dp))

            // 粒度 + 光学刻度尺
            val granularities = listOf(
                "分钟" to 60_000L, "小时" to 3_600_000L,
                "天" to 86_400_000L, "月" to 2_592_000_000L
            )
            var granularityIdx by remember { mutableIntStateOf(1) }
            Row {
                granularities.forEachIndexed { i, (label, _) ->
                    NakedTextButton(
                        text = label,
                        selected = i == granularityIdx,
                        redLight = red,
                        onClick = { granularityIdx = i }
                    )
                }
            }
            TimeRuler(
                accent = accent.redLightSafe(red),
                red = red,
                onNudge = { steps -> vm.nudgeTime(granularities[granularityIdx].second, steps) }
            )
            Text(
                "横向拨动刻度尺改写观测时刻 · 每格 ±1 ${granularities[granularityIdx].first}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red)
            )

            // 多倍速播放栏
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                vm.timeRates.forEach { rate ->
                    val label = when {
                        rate == 0.0 -> "暂停"
                        rate == 1.0 -> "1×"
                        rate == -3600.0 -> "-1h"
                        rate == 3600.0 -> "+1h"
                        else -> "%+d".format(rate.toInt())
                    }
                    val active = !vm.isLive && vm.timeRate == rate
                    NakedTextButton(
                        text = label,
                        selected = active,
                        redLight = red,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (rate == 1.0 && vm.isLive) return@NakedTextButton
                            vm.applyTimeRate(rate)
                        }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                NakedTextButton(
                    text = if (vm.isLive) "已回到此刻 LIVE" else "回到此刻 LIVE",
                    selected = vm.isLive,
                    redLight = red,
                    onClick = { vm.backToLive() }
                )
            }
        }
    }
}

/**
 * 无框光学刻度尺：中心一根 1dp 纯白发光指针，两侧 ±12 格疏密相间的等宽刻度
 * （每 3 格主刻度），拖动逐格改写时间并伴随触觉微光反馈，无任何按钮方框。
 */
@Composable
private fun TimeRuler(
    accent: Color,
    red: Boolean,
    onNudge: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                val stepPx = size.width / 26f
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f; dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { _, dragAmt ->
                        acc += dragAmt
                        var nudged = 0
                        while (acc >= stepPx) { acc -= stepPx; nudged++ }
                        while (acc <= -stepPx) { acc += stepPx; nudged-- }
                        if (nudged != 0) {
                            onNudge(nudged)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val midY = h * 0.56f
            val step = w / 26f
            // 基线
            drawLine(
                Color(0x22FFFFFF).redLightSafe(red),
                Offset(0f, midY), Offset(w, midY), 0.8f.dp.toPx()
            )
            // ±12 格刻度：主刻度每 3 格；拖动中指针邻域刻度泛起微光
            for (i in -12..12) {
                if (i == 0) continue
                val x = cx + i * step
                val major = i % 3 == 0
                val near = dragging && abs(x - cx) < step * 1.5f
                val tickH = (if (major) 15f else 8f).dp.toPx()
                val alpha = when {
                    near -> 0.95f
                    major -> 0.55f
                    else -> 0.30f
                }
                drawLine(
                    Color(0xFFDBE6F6).copy(alpha = alpha).redLightSafe(red),
                    Offset(x, midY - tickH / 2), Offset(x, midY + tickH / 2),
                    (if (major) 1.2f else 0.8f).dp.toPx()
                )
            }
            // 中心指针：柔光带 + 1dp 纯白发光指针 + 顶端星点
            drawLine(
                Color(0xFFFFFFFF).copy(alpha = if (dragging) 0.22f else 0.14f).redLightSafe(red),
                Offset(cx, h * 0.14f), Offset(cx, h * 0.90f), 5f.dp.toPx()
            )
            drawLine(
                Color(0xFFFFFFFF).redLightSafe(red),
                Offset(cx, h * 0.10f), Offset(cx, h * 0.94f), 1f.dp.toPx()
            )
            drawCircle(accent, 2.4f.dp.toPx(), Offset(cx, h * 0.10f))
        }
    }
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

private fun formatLst(lstDeg: Double): String {
    val h = lstDeg / 15.0
    return "%02d:%02d:%02d".format(h.toInt(), ((h * 60) % 60).toInt(), ((h * 3600) % 60).toInt())
}
