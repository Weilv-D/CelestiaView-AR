package com.celestiaview.ar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.equatorialFromHorizontalSafe
import com.celestiaview.ar.ui.render.SkyCanvasRenderer
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * 屏幕中心光学准星（极简微光几何）：
 * - 52dp 同心微光细环（0.5dp）+ 四方位刻度针 + 中心 1.5dp 微光白点；
 * - 3.5° 吸附四角括号 + HUD 抬头标签 + 360° 离轴寻星引导箭头。
 *
 * 零分配约定：Path / RectF / Paint 均为 remember 预分配，逐帧 rewind + 改参复用。
 */
/** 准星专用预分配绘图对象（UI 渲染线程单线程访问）。 */
private class ReticlePaints {
    val arcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }
    val roundArcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val bracketPath = android.graphics.Path()
    val arrowPath = android.graphics.Path()
    val rectF = android.graphics.RectF()
}

@Composable
fun TargetReticleOverlay(
    vm: SkyViewModel,
    renderer: SkyCanvasRenderer,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.SANS_SERIF
        }
    }
    val paints = remember { ReticlePaints() }

    Canvas(modifier.fillMaxSize()) {
        // 读取 frameTick：与主渲染同帧重绘
        vm.frameTick
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dp = density.density
        val accent = if (vm.redLight) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentCyan
        val accentArgb = accent.toArgbInt()
        val n = drawContext.canvas.nativeCanvas
        val p = paints

        // ---------- 中心准星：52dp 微光同心环 + 方位刻度针 + 中心微光点 ----------
        val ringR = 26f * dp
        val ring = p.arcPaint
        ring.color = accentArgb
        ring.alpha = (0.22f * 255f).toInt()
        ring.strokeWidth = 0.5f * dp
        n.drawCircle(cx, cy, ringR, ring)

        // 四方位刻度针（环外 1.5dp → 4.5dp）
        val tickLine = p.linePaint
        tickLine.color = accentArgb
        tickLine.alpha = (0.5f * 255f).toInt()
        tickLine.strokeWidth = 0.8f * dp
        for (q in 0 until 4) {
            val a = q * (Math.PI / 2.0)
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            n.drawLine(
                cx + (ringR + 1.5f * dp) * ca, cy + (ringR + 1.5f * dp) * sa,
                cx + (ringR + 4.5f * dp) * ca, cy + (ringR + 4.5f * dp) * sa,
                tickLine
            )
        }

        // 中心微光白点：柔晕 + 1.5dp 白点
        val dot = p.fillPaint
        dot.color = 0x33FFFFFF.toInt()
        n.drawCircle(cx, cy, 3.5f * dp, dot)
        dot.color = 0xE6FFFFFF.toInt()
        n.drawCircle(cx, cy, 1.5f * dp, dot)

        val ov = renderer.overlay

        // 淡淡的 N/E/S/W 悬浮方位标：按真实基点方位投影方向悬浮于环外
        if (!vm.focusMode) {
            val lr = ringR + 12f * dp
            val letters = CARDINAL_LETTERS
            textPaint.isFakeBoldText = false
            textPaint.setShadowLayer(3f, 0f, 0f, 0xB4000000.toInt())
            for (q in 0 until 4) {
                val v = ov.cardinalScreen[q] ?: continue
                val dx = v.x - cx
                val dy = v.y - cy
                val len = kotlin.math.hypot(dx, dy)
                if (len < 1f) continue
                val lx = cx + dx / len * lr
                val ly = cy + dy / len * lr
                textPaint.color = accentArgb
                textPaint.alpha = (0.42f * 255f).toInt()
                textPaint.textSize = 9f * dp
                val tw = textPaint.measureText(letters[q])
                n.drawText(letters[q], lx - tw / 2, ly + 3f * dp, textPaint)
            }
            textPaint.clearShadowLayer()
        }

        // ---------- 吸附包裹 ----------
        val snapScreen = ov.snappedScreen
        if (snapScreen != null && vm.snapped != null) {
            val sep = vm.snapped!!.separationDeg
            // 收缩量随角距减小而收紧
            val bracket = (34f + (sep / SkyViewModel.SNAP_THRESHOLD_DEG).toFloat() * 16f) * dp
            val half = bracket / 2
            val cornerLen = 8f * dp
            val bp = p.bracketPath
            bp.rewind()
            // 四角 L 形括号（零分配展开）
            for (i in 0 until 4) {
                val bx = if (i == 0 || i == 2) snapScreen.x - half else snapScreen.x + half
                val by = if (i < 2) snapScreen.y - half else snapScreen.y + half
                val dx = if (i == 0 || i == 2) 1f else -1f
                val dy = if (i < 2) 1f else -1f
                bp.moveTo(bx + dx * cornerLen, by)
                bp.lineTo(bx, by)
                bp.lineTo(bx, by + dy * cornerLen)
            }
            val bracketPaint = p.roundArcPaint
            bracketPaint.color = accentArgb
            bracketPaint.alpha = 255
            bracketPaint.strokeWidth = 1.6f * dp
            n.drawPath(bp, bracketPaint)
        }

        // ---------- 准星两侧对称等宽小字：左 RA / 右 DEC（半透明纯白，11sp） ----------
        // 聚焦态由 3D 特写的双星旁注接管坐标显示，准星小字整段让位防重叠
        if (!vm.focusMode) {
        val coordTarget = vm.snapped?.target
        val raDeg: Double
        val decDeg: Double
        if (coordTarget != null) {
            raDeg = coordTarget.raDeg; decDeg = coordTarget.decDeg
        } else {
            val eq = com.celestiaview.ar.core.math.AstrometryEngine.equatorialFromHorizontalSafe(
                vm.boresight.altDeg, vm.boresight.azDeg,
                vm.frame.lstDeg, vm.frame.latitudeDeg
            )
            raDeg = eq.raDeg; decDeg = eq.decDeg
        }
        textPaint.typeface = android.graphics.Typeface.MONOSPACE
        textPaint.isFakeBoldText = false
        textPaint.textSize = 11f * dp
        val raCol = if (vm.redLight) android.graphics.Color.argb(170, 255, 138, 128)
        else android.graphics.Color.argb(170, 241, 245, 249)
        textPaint.color = raCol
        val rh = raDeg / 15.0
        val raText = "RA %02dh %02dm".format(rh.toInt(), ((rh * 60) % 60).toInt())
        val sign = if (decDeg < 0) "-" else "+"
        val ad = kotlin.math.abs(decDeg)
        val decText = "DEC %s%02d° %02d′".format(sign, ad.toInt(), ((ad * 60) % 60).toInt())
        val raW = textPaint.measureText(raText)
        n.drawText(raText, cx - 52f * dp - raW, cy + 4f * dp, textPaint)
        n.drawText(decText, cx + 52f * dp, cy + 4f * dp, textPaint)
        textPaint.typeface = android.graphics.Typeface.SANS_SERIF
        }

        // ---------- 360° 离轴引导箭头 ----------
        val angle = ov.guideAngleDeg
        if (angle != null && ov.guideSepDeg != null) {
            val screenAngle = Math.toRadians(angle.toDouble())
            val r = 62f * dp
            val ax = cx + r * cos(screenAngle).toFloat()
            val ay = cy + r * sin(screenAngle).toFloat()
            val arrowLen = 12f * dp
            val arrowW = 5.5f * dp

            // 箭头本体（指向角度方向）
            val tipX = ax + arrowLen * cos(screenAngle).toFloat()
            val tipY = ay + arrowLen * sin(screenAngle).toFloat()
            val perp = screenAngle + Math.PI / 2
            val ap = p.arrowPath
            ap.rewind()
            ap.moveTo(tipX, tipY)
            ap.lineTo(
                ax + arrowW * cos(perp).toFloat(),
                ay + arrowW * sin(perp).toFloat()
            )
            ap.lineTo(
                ax - arrowW * cos(perp).toFloat(),
                ay - arrowW * sin(perp).toFloat()
            )
            ap.close()
            val arrowPaint = p.fillPaint
            arrowPaint.color = accentArgb
            n.drawPath(ap, arrowPaint)

            // 角距倒计时文本（半透明白等宽小字）
            textPaint.typeface = android.graphics.Typeface.MONOSPACE
            textPaint.isFakeBoldText = false
            textPaint.color = if (vm.redLight) android.graphics.Color.argb(200, 255, 138, 128)
            else android.graphics.Color.argb(200, 241, 245, 249)
            textPaint.textSize = 11f * dp
            val txt = "${ov.guideName} · %.1f°".format(ov.guideSepDeg!!)
            val tw = textPaint.measureText(txt)
            n.drawText(txt, cx - tw / 2, cy + 70f * dp, textPaint)
            textPaint.typeface = android.graphics.Typeface.SANS_SERIF
        } else if (ov.guideScreen != null && ov.guideSepDeg != null) {
            // 目标已入镜：锁定脉冲
            val t = (System.nanoTime() / 1e9f) % 1.2f
            val pulseR = (14f + 26f * t) * dp
            val alpha = ((1f - t / 1.2f).coerceIn(0f, 1f) * 0.7f * 255f).toInt()
            val pulse = p.arcPaint
            pulse.color = accentArgb
            pulse.alpha = alpha
            pulse.strokeWidth = 2f * dp
            n.drawCircle(ov.guideScreen!!.x, ov.guideScreen!!.y, pulseR, pulse)
        }
    }
}

/** Compose Color → ARGB int（零分配内联）。 */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = this.toArgb()

/** 罗盘方位基点字母（方位 0/90/180/270 → N/E/S/W）。 */
private val CARDINAL_LETTERS = arrayOf("N", "E", "S", "W")
