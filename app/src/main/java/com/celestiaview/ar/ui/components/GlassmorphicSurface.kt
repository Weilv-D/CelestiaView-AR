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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * 2. 精密遥测数据单元格：等宽 tnum 大数字 + 字距展开的小标签。
 */
@Composable
fun TelemetryMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    redLight: Boolean = false
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        redLight = redLight,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = CelestialPalette.TextMuted.redLightSafe(redLight)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    fontFamily = FontFamily.Monospace,
                    color = CelestialPalette.TextPrimary.redLightSafe(redLight)
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = CelestialPalette.AccentCyan.redLightSafe(redLight)
                    )
                }
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
