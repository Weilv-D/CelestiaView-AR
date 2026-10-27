package com.celestiaview.ar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * CelestialPalette — 精密光学仪器级色彩体系。
 * 通过极低透明度叠加营造深邃通透感；所有发光色保持克制。
 */
object CelestialPalette {
    // 1. 天穹背景三阶径向渐变（天顶 → 地平）
    val ZenithNight = Color(0xFF02040A)       // 天顶深渊黑
    val MidSkyNight = Color(0xFF070E22)       // 中天深海蓝
    val HorizonNight = Color(0xFF102042)      // 地平线微光蓝
    val HorizonAirglow = Color(0x3338669E)    // 地平线大气散射辉光（20% Alpha）

    // 2. 磨砂玻璃 HUD 容器（Fluid Glassmorphism）
    val GlassSurface = Color(0xB8091021)      // 主面板背景（72% 深空蓝黑）
    val GlassSurfaceElevated = Color(0xD9101B33) // 模态抽屉/高亮卡片（85%）
    val GlassPillActive = Color(0x3D56CCF2)   // 激活胶囊背景（24% 冰川青）
    val GlassPillInactive = Color(0x660B1326) // 未激活胶囊背景（40%）

    // 3. 镜面高光渐变描边（1dp Specular Border）
    val BorderHighlightTopLeft = Color(0x59FFFFFF)     // 左上迎光面（35% 白）
    val BorderHighlightBottomRight = Color(0x0DFFFFFF) // 右下背光面（5% 白）
    val BorderActiveCyan = Color(0x9964D2FF)           // 激活青色微光描边（60%）

    // 4. 品牌主题色与天球参考线
    val AccentCyan = Color(0xFF64D2FF)        // 星轨青：核心交互高亮
    val AccentGold = Color(0xFFFFD166)        // 星盘金：行星与黄道高亮
    val GridLineAzAlt = Color(0x1464D2FF)     // 经纬网格（8%）
    val EclipticLine = Color(0x4DFFD166)      // 黄道虚线（30% 金）
    val ConstellationLineIdle = Color(0x248AB4F8)   // 静默星座连线（14%）
    val ConstellationLineActive = Color(0xCC64D2FF) // 激活星座内芯（80%）
    val ConstellationGlowActive = Color(0x3364D2FF) // 激活星座外发光（20%）

    // 5. 文字排版三级灰度
    val TextPrimary = Color(0xFFF2F6FF)       // 主标题（微蓝星光白）
    val TextSecondary = Color(0xB3C5D4F0)     // 副标题/坐标（70% 银蓝灰）
    val TextMuted = Color(0x668DA2C8)         // 辅助标签（40% 暗夜灰蓝）
}

/**
 * 字体排版黄金比例：等宽数字（tnum）保证实时跳动的坐标宽度恒定。
 */
object CelestialType {
    /** 等宽表格数字基础样式（实时遥测专用）。 */
    val Tnum = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Monospace)

    /** 天体主标题 Hero Title：24sp SemiBold。 */
    val HeroTitle = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp, color = CelestialPalette.TextPrimary
    )

    /** 拉丁学名副标：12sp Medium，全大写宽松字距。 */
    val LatinSubtitle = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 2.0.sp, color = CelestialPalette.AccentCyan
    )

    /** 遥测仪表大数字：18sp Light + tnum。 */
    val TelemetryValue = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.Light,
        fontFeatureSettings = "tnum",
        fontFamily = FontFamily.Monospace,
        color = CelestialPalette.TextPrimary
    )

    /** 遥测仪表小标签：10sp SemiBold 1.2sp 字距。 */
    val TelemetryLabel = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, color = CelestialPalette.TextMuted
    )

    /** 科普长文正文：14sp Normal，1.57 倍行高。 */
    val LoreBody = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 22.sp, color = CelestialPalette.TextSecondary
    )
}

/**
 * 深空色彩体系（兼容层：既有渲染/HUD 引用）+ 夜间暗适应红光模式。
 */
object DeepSpacePalette {
    // 基础深空色
    val CosmosBlack = CelestialPalette.ZenithNight
    val HorizonNavy = CelestialPalette.HorizonNight
    val DaylightBlue = Color(0xFF112240)
    val AstralGold = CelestialPalette.AccentGold
    val NebulaCyan = CelestialPalette.AccentCyan
    val AuroraTeal = Color(0xFF26A69A)
    val PlasmaMagenta = Color(0xFFFF4081)
    val StarlightWhite = CelestialPalette.TextPrimary

    // 玻璃拟态
    val GlassSurface = CelestialPalette.GlassSurface
    val GlassStroke = CelestialPalette.BorderHighlightTopLeft
    val GlassHighlight = Color(0x14FFFFFF)

    // 状态
    val RiseGreen = Color(0xFF69F0AE)
    val SetOrange = Color(0xFFFFB74D)

    // 红光模式（暗适应保护）
    val RedSafePrimary = Color(0xFFFF5252)
    val RedSafeDim = Color(0xFFB71C1C)
    val RedSafeSurface = Color(0xCC1A0000)
    val RedSafeText = Color(0xFFFF8A80)
}

private val DeepSpaceScheme = darkColorScheme(
    primary = CelestialPalette.AccentGold,
    onPrimary = CelestialPalette.ZenithNight,
    secondary = CelestialPalette.AccentCyan,
    onSecondary = CelestialPalette.ZenithNight,
    tertiary = Color(0xFF26A69A),
    background = CelestialPalette.ZenithNight,
    onBackground = CelestialPalette.TextPrimary,
    surface = CelestialPalette.GlassSurface,
    onSurface = CelestialPalette.TextPrimary,
    surfaceVariant = CelestialPalette.HorizonNight,
    onSurfaceVariant = CelestialPalette.TextSecondary,
    outline = Color(0xFF5470A8)
)

private val RedLightScheme = darkColorScheme(
    primary = DeepSpacePalette.RedSafePrimary,
    onPrimary = DeepSpacePalette.CosmosBlack,
    secondary = DeepSpacePalette.RedSafeDim,
    onSecondary = DeepSpacePalette.RedSafeText,
    tertiary = DeepSpacePalette.RedSafePrimary,
    background = Color(0xFF000000),
    onBackground = DeepSpacePalette.RedSafeText,
    surface = DeepSpacePalette.RedSafeSurface,
    onSurface = DeepSpacePalette.RedSafeText,
    surfaceVariant = Color(0xFF220505),
    onSurfaceVariant = DeepSpacePalette.RedSafeText,
    outline = DeepSpacePalette.RedSafeDim
)

@Composable
fun DeepSpaceTheme(
    redLight: Boolean = false,
    content: @Composable () -> Unit
) {
    isSystemInDarkTheme() // 星图应用恒定深色；调用以声明意图
    MaterialTheme(
        colorScheme = if (redLight) RedLightScheme else DeepSpaceScheme,
        content = content
    )
}

/** 红光模式下将任意颜色替换为等亮度红光，保护暗适应。 */
fun Color.redLightSafe(redLight: Boolean): Color {
    if (!redLight) return this
    // 只保留红色通道能量
    val lum = (red * 0.30f + green * 0.59f + blue * 0.11f)
    return Color(lum.coerceAtLeast(0.35f), 0f, 0f, alpha)
}
