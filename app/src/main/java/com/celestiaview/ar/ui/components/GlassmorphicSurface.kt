package com.celestiaview.ar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.redLightSafe

/**
 * 1. Apple VisionOS 风格斜向高光磨砂玻璃容器。
 * 左上迎光高光描边 + 右下消隐描边 + 内部微弱顶部辉光；
 * [isElevated] 抬升模态抽屉，[isActive] 激活青色描边，[redLight] 暗适应保护。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isElevated: Boolean = false,
    isActive: Boolean = false,
    redLight: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bgColor = if (redLight) DeepSpacePalette.RedSafeSurface
    else if (isElevated) CelestialPalette.GlassSurfaceElevated
    else CelestialPalette.GlassSurface
    val borderBrush = if (redLight) {
        Brush.linearGradient(
            colors = listOf(Color(0x66FF5252), Color.Transparent, Color(0x1AFF5252))
        )
    } else if (isActive) {
        Brush.linearGradient(
            colors = listOf(CelestialPalette.BorderActiveCyan, CelestialPalette.BorderHighlightBottomRight)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                CelestialPalette.BorderHighlightTopLeft,
                Color(0x1AFFFFFF),
                CelestialPalette.BorderHighlightBottomRight
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            // 顶部微弱内发光质感
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x14FFFFFF), Color.Transparent),
                    endY = 120f
                )
            )
            .border(width = 0.8.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * 2. 遥测数据行：等宽 tnum 数值 + 小标签，以 0.4dp 极细分割线组织层级。
 */
@Composable
fun Hairline(modifier: Modifier = Modifier, redLight: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.4.dp)
            .background(Color(0x18FFFFFF).redLightSafe(redLight))
    )
}

@Composable
fun TelemetryMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    redLight: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp,
            color = CelestialPalette.TextMuted.redLightSafe(redLight)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(fontFeatureSettings = "tnum"),
            fontFamily = FontFamily.Monospace,
            color = CelestialPalette.TextPrimary.redLightSafe(redLight)
        )
    }
}

/**
 * 无框文字按钮：激活态亮起星轨青白 + 底部 2dp 光点。
 */
@Composable
fun NakedTextButton(
    text: String,
    selected: Boolean,
    redLight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = (if (selected) Color(0xFFE6F4FF) else Color(0x739AB2D6)).redLightSafe(redLight)
    Column(
        modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(2.dp)) {
            if (selected) {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(Color(0xFF64D2FF).redLightSafe(redLight), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * 无框船坞操作项：20dp 图标 + 10sp 双字标签；激活态图标背后 32dp 微光圆晕
 * 与正下方 2dp 发光星点，未激活态为静谧暗夜灰蓝，全程无背景无描边。
 */
@Composable
fun DockItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    redLight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = (if (active) Color(0xFFE6F4FF) else Color(0x739AB2D6)).redLightSafe(redLight)
    Column(
        modifier
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                // 图标中心微光圆晕（显式有限 center/radius 的径向渐变）
                androidx.compose.foundation.Canvas(Modifier.size(32.dp)) {
                    val r = 16.dp.toPx().coerceIn(2f, 500f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2864D2FF).redLightSafe(redLight), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(r, r),
                            radius = r
                        )
                    )
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = tint)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.height(2.dp)) {
            if (active) {
                Box(
                    Modifier
                        .size(2.dp)
                        .background(Color(0xFF64D2FF).redLightSafe(redLight), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * 简洁玻璃面板（兼容既有调用）：GlassCard 轻量包装。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    redLight: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        redLight = redLight,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) { content() }
    }
}

/**
 * 玻璃胶囊图标按钮：底部 Dock 与顶部快捷按钮共用。
 * 激活态 = 冰川青底 + 青色描边 + 顶部微光点。
 */
@Composable
fun GlassCapsuleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    redLight: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit
) {
    val accent = if (redLight) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentCyan
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .background(
                if (selected) CelestialPalette.GlassPillActive.redLightSafe(redLight)
                else CelestialPalette.GlassPillInactive,
                shape
            )
            .border(
                BorderStroke(
                    if (selected) 1.dp else 0.6.dp,
                    if (selected) accent.copy(alpha = 0.6f)
                    else CelestialPalette.BorderHighlightTopLeft.copy(alpha = 0.35f)
                ),
                shape
            )
            .clickable { onClick() }
            .height(size)
            .width(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accent.redLightSafe(redLight)
            else CelestialPalette.TextSecondary.redLightSafe(redLight),
            modifier = Modifier.padding(iconSize / 5)
        )
        if (selected) {
            // 顶部微光点
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .width(14.dp)
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.9f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

/**
 * 带文字的玻璃胶囊标签按钮（面板行内开关用）。
 */
@Composable
fun GlassCapsuleLabelButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    redLight: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (redLight) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentCyan
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .background(
                if (selected) CelestialPalette.GlassPillActive.redLightSafe(redLight)
                else CelestialPalette.GlassPillInactive,
                shape
            )
            .border(
                BorderStroke(
                    if (selected) 1.dp else 0.6.dp,
                    if (selected) accent.copy(alpha = 0.6f)
                    else CelestialPalette.BorderHighlightTopLeft.copy(alpha = 0.35f)
                ),
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent.redLightSafe(redLight)
            else CelestialPalette.TextSecondary.redLightSafe(redLight),
            modifier = Modifier.width(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent.redLightSafe(redLight)
            else CelestialPalette.TextSecondary.redLightSafe(redLight)
        )
    }
}
