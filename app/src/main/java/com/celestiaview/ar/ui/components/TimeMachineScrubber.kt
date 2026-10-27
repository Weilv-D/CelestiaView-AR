package com.celestiaview.ar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import java.util.TimeZone

/**
 * 时间机器控制台：
 * 折叠态 = 顶/底状态栏（观测时间 + 地方恒星时 + LIVE 徽章）；
 * 展开态 = 阻尼刻度滚轮（分/时/天/月粒度，拨动触觉反馈）+ 多倍速播放栏 + 一键回今。
 */
@Composable
fun TimeMachineScrubber(
    vm: SkyViewModel,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else DeepSpacePalette.AstralGold

    Column(Modifier.fillMaxWidth()) {
        if (!expanded) {
            // ---------------- 折叠状态栏 ----------------
            GlassPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 18.dp,
                redLight = red
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = formatDateTime(vm.simTimeMillis),
                            color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                        )
                        Text(
                            text = "LST " + formatLst(vm.frame.lstDeg) +
                                if (!vm.isLive) "  ⏩ %s×".format(formatRate(vm.timeRate)) else "",
                            color = accent.redLightSafe(red),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                    Text(
                        text = if (vm.isLive) "● LIVE" else "⟲ 回此刻",
                        color = (if (vm.isLive) DeepSpacePalette.RiseGreen else accent).redLightSafe(red),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .glassChip(red)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onToggle) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "展开时间机器",
                            tint = accent.redLightSafe(red)
                        )
                    }
                }
            }
        } else {
            // ---------------- 展开控制台 ----------------
            GlassPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                cornerRadius = 24.dp,
                redLight = red
            ) {
                Column(Modifier.padding(16.dp)) {
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
                                Icons.Default.ExpandLess,
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
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                    )

                    Spacer(Modifier.height(10.dp))

                    // 粒度 + 阻尼滚轮
                    val granularities = listOf(
                        "分钟" to 60_000L, "小时" to 3_600_000L,
                        "天" to 86_400_000L, "月" to 2_592_000_000L
                    )
                    var granularityIdx by remember { mutableIntStateOf(1) }
                    var sliderPos by remember { mutableFloatStateOf(0f) }
                    var lastTick by remember { mutableIntStateOf(0) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        granularities.forEachIndexed { i, (label, _) ->
                            GlassCapsuleLabelButton(
                                icon = if (i == granularityIdx) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.ArrowForward,
                                label = label,
                                selected = i == granularityIdx,
                                redLight = red,
                                onClick = { granularityIdx = i; sliderPos = 0f; lastTick = 0 }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Slider(
                        value = sliderPos,
                        onValueChange = { v ->
                            sliderPos = v
                            val tick = (v * 12f).toInt()
                            if (tick != lastTick) {
                                val steps = tick - lastTick
                                lastTick = tick
                                vm.nudgeTime(granularities[granularityIdx].second, steps)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onValueChangeFinished = {
                            sliderPos = 0f
                            lastTick = 0
                        },
                        valueRange = -12f..12f,
                        steps = 23,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    // 多倍速播放栏
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        vm.timeRates.forEach { rate ->
                            val label = when {
                                rate == 0.0 -> "⏸"
                                rate == 1.0 -> "1×"
                                rate == -300.0 -> "-300×"
                                else -> "%+d×".format(rate.toInt())
                            }
                            val active = !vm.isLive && vm.timeRate == rate
                            GlassCapsuleLabelButton(
                                icon = if (rate == 0.0) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = label,
                                selected = active,
                                redLight = red,
                                onClick = {
                                    if (rate == 1.0 && vm.isLive) return@GlassCapsuleLabelButton
                                    vm.applyTimeRate(rate)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // LIVE 复位
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GlassCapsuleLabelButton(
                            icon = Icons.Default.ExpandLess,
                            label = if (vm.isLive) "已回到此刻 LIVE" else "回到此刻 LIVE",
                            selected = vm.isLive,
                            redLight = red,
                            onClick = { vm.backToLive() }
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.glassChip(red: Boolean): Modifier = this

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

private fun formatLst(lstDeg: Double): String {
    val h = lstDeg / 15.0
    return "%02d:%02d:%02d".format(h.toInt(), ((h * 60) % 60).toInt(), ((h * 3600) % 60).toInt())
}

private fun formatRate(rate: Double): String =
    if (rate % 1.0 == 0.0) "%d".format(rate.toInt()) else "%.1f".format(rate)
