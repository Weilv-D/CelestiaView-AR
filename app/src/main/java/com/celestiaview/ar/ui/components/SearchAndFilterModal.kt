package com.celestiaview.ar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.data.catalog.BrightStarCatalog
import com.celestiaview.ar.data.catalog.ConstellationCatalog
import com.celestiaview.ar.data.catalog.MessierCatalog
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.redLightSafe

/**
 * 分类检索面板：亮星 / 行星 / 星座 / 梅西耶 + 实时模糊搜索 + 一键导航锁定。
 */
@Composable
fun SearchAndFilterModal(
    vm: SkyViewModel,
    onDismiss: () -> Unit,
    onGuide: (CelestialTarget) -> Unit
) {
    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else Color(0xFF94A3B8)
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("亮星", "行星", "星座", "梅西耶")

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 26.dp,
        redLight = red
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "检索天体 SEARCH & NAVIGATE",
                    color = accent.redLightSafe(red),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("输入名称：织女 / Vega / M31 / 木星…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = accent) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = DeepSpacePalette.GlassStroke,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row {
                tabs.forEachIndexed { i, t ->
                    NakedTextButton(
                        text = t,
                        selected = tab == i,
                        redLight = red,
                        onClick = { tab = i }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val q = query.trim()
            LazyColumn(Modifier.height(300.dp)) {
                when (tab) {
                    0 -> {
                        val stars = BrightStarCatalog.stars
                            .filter { q.isEmpty() || it.nameZh.contains(q) || it.nameEn.contains(q, true) }
                            .sortedBy { it.mag }
                        items(stars.take(if (q.isEmpty()) 40 else stars.size)) { s ->
                            RowResult(
                                "${s.nameZh} ${s.nameEn}",
                                "星等 %+.2f · %s · %s".format(s.mag, s.spectral, s.constellation),
                                red
                            ) {
                                onGuide(CelestialTarget.StarTarget(s))
                            }
                        }
                    }
                    1 -> {
                        items(vm.frame.planets) { p ->
                            RowResult(
                                "${p.meta.nameZh} ${p.meta.nameEn}",
                                "高度 %.1f° · 星等 %+.1f · %.3f AU".format(p.altDeg, p.mag, p.eph.distanceAu),
                                red
                            ) {
                                onGuide(CelestialTarget.PlanetTarget(
                                    p.meta.key, "${p.meta.nameZh} ${p.meta.nameEn}", p.eph.raDeg, p.eph.decDeg))
                            }
                        }
                        item {
                            RowResult("月球 Moon", "月相 " + vm.frame.moonEph.phaseNameZh, red) {
                                onGuide(CelestialTarget.MoonTarget(
                                    vm.frame.moonEph.raDeg, vm.frame.moonEph.decDeg))
                            }
                        }
                        item {
                            RowResult("太阳 Sun", "高度 %.1f°".format(vm.frame.sunAltDeg), red) {
                                onGuide(CelestialTarget.SunTarget(
                                    vm.frame.sunEph.raDeg, vm.frame.sunEph.decDeg))
                            }
                        }
                    }
                    2 -> {
                        val cons = ConstellationCatalog.constellations
                            .filter { q.isEmpty() || it.nameZh.contains(q) || it.nameEn.contains(q, true) }
                        items(cons) { c ->
                            val center = ConstellationCatalog.centerRaDec(c)
                            RowResult(
                                "${c.nameZh} ${c.nameEn}",
                                "${c.edges.size} 段骨架 · ${c.memberStarIds.size} 星",
                                red
                            ) {
                                onGuide(CelestialTarget.PlanetTarget(
                                    "CONST:${c.abbr}", "${c.nameZh} ${c.nameEn}",
                                    center.raDeg, center.decDeg))
                            }
                        }
                    }
                    else -> {
                        val dsos = MessierCatalog.objects
                            .filter {
                                q.isEmpty() || it.catalogId.contains(q, true) ||
                                    it.nameZh.contains(q) || it.nameEn.contains(q, true)
                            }
                        items(dsos) { o ->
                            RowResult(
                                "${o.catalogId} ${o.nameZh}",
                                "${o.nameEn} · 星等 %.1f".format(o.mag),
                                red
                            ) {
                                onGuide(CelestialTarget.MessierTarget(o))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowResult(title: String, sub: String, red: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.redLightSafe(red))
            Text(sub, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red))
        }
        Text("引导 ›", fontSize = 11.sp, color = Color(0xFF94A3B8).redLightSafe(red))
    }
}
