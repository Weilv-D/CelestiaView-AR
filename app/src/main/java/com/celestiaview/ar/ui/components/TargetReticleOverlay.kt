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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.render.SkyCanvasRenderer
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * 屏幕中心动态光学准星（徕卡取景器 / JWST 精密几何）：
 * - 38dp 刻度环：30° 分段 / 6° 呼吸缺口 / 24 秒顺时针一周；
 * - 内圈四段 45° 锁定弧：吸附时 spring 收缩至 14dp 包裹目标；
 * - 中心微距十字：4dp 净空 + 5dp 极细准线；
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

    // 内圈锁定弧半径：吸附时 spring 收缩至 14dp 包裹目标（composable 作用域动画）
    val snappedNow = vm.snapped != null
    val lockArcRadius by animateDpAsState(
        targetValue = if (snappedNow) 14.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "lockArcR"
    )

    Canvas(modifier.fillMaxSize()) {
        // 读取 frameTick：与主渲染同帧重绘
        vm.frameTick
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val dp = density.density
        val accent = if (vm.redLight) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentCyan
        val accentArgb = accent.toArgbInt()
        val timeSec = System.nanoTime() / 1e9f
        val n = drawContext.canvas.nativeCanvas
        val p = paints

        // ---------- 中心准星：徕卡取景器 / JWST 精密几何 ----------
        val lockR = lockArcRadius.toPx()
        // 外圈刻度环：38dp / 0.8dp / 每 30° 留 6° 呼吸缺口 / 24s 顺时针
        val tickR = 38f * dp
        val segDeg = 30f
        val gapDeg = 6f
        val rotDeg = (timeSec * 360f / 24f) % 360f // 24 秒一整圈
        val arc = p.arcPaint
        arc.color = accentArgb
        arc.alpha = (0.55f * 255f).toInt()
        arc.strokeWidth = 0.8f * dp
        val tickLine = p.linePaint
        tickLine.color = accentArgb
        tickLine.alpha = (0.8f * 255f).toInt()
        tickLine.strokeWidth = 1.1f * dp
        var k = 0
        while (k * segDeg < 360f) {
            val start = k * segDeg + rotDeg
            val sweep = segDeg - gapDeg
            p.rectF.set(cx - tickR, cy - tickR, cx + tickR, cy + tickR)
            n.drawArc(p.rectF, start, sweep, false, arc)
            // 每 30° 一枚主刻度短线
            val a = Math.toRadians((start + sweep / 2).toDouble())
            val r1 = tickR - 3.5f * dp
            n.drawLine(
                cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                cx + tickR * cos(a).toFloat(), cy + tickR * sin(a).toFloat(),
                tickLine
            )
            k++
        }

        // 内圈锁定弧：四段 45° 圆弧（锁定时收缩包裹目标）
        val snapCenter = renderer.overlay.snappedScreen
        val arcCx = snapCenter?.x ?: cx
        val arcCy = snapCenter?.y ?: cy
        val lock = p.roundArcPaint
        lock.color = accentArgb
        lock.alpha = (0.8f * 255f).toInt()
        lock.strokeWidth = 1.5f * dp
        p.rectF.set(arcCx - lockR, arcCy - lockR, arcCx + lockR, arcCy + lockR)
        for (q in 0 until 4) {
            n.drawArc(p.rectF, q * 90f + 6f, 45f, false, lock)
        }

        // 中心微距十字：中心 4dp 净空（不遮挡被瞄准的星），四周 5dp 极细准线
        val clearR = 4f * dp
        val tickLen = 5f * dp
        val cross = p.linePaint
        cross.color = accentArgb
        cross.alpha = (0.85f * 255f).toInt()
        cross.strokeWidth = 1f * dp
        n.drawLine(cx - clearR - tickLen, cy, cx - clearR, cy, cross)
        n.drawLine(cx + clearR, cy, cx + clearR + tickLen, cy, cross)
        n.drawLine(cx, cy - clearR - tickLen, cx, cy - clearR, cross)
        n.drawLine(cx, cy + clearR, cx, cy + clearR + tickLen, cross)

        val ov = renderer.overlay

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

            // HUD 抬头标签
            textPaint.color = android.graphics.Color.argb(255, 255, 224, 130)
            if (vm.redLight) textPaint.color = android.graphics.Color.rgb(255, 138, 128)
            textPaint.textSize = 12f * dp
            textPaint.isFakeBoldText = true
            n.drawText(ov.snappedLabel, snapScreen.x + half + 8f * dp, snapScreen.y - 4f * dp, textPaint)
            textPaint.color = android.graphics.Color.argb(200, 180, 197, 230)
            if (vm.redLight) textPaint.color = android.graphics.Color.argb(200, 255, 138, 128)
            textPaint.textSize = 10f * dp
            textPaint.isFakeBoldText = false
            n.drawText(ov.snappedDetail, snapScreen.x + half + 8f * dp, snapScreen.y + 10f * dp, textPaint)
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

            // 角距倒计时文本
            textPaint.color = android.graphics.Color.argb(235, 255, 224, 130)
            if (vm.redLight) textPaint.color = android.graphics.Color.rgb(255, 138, 128)
            textPaint.textSize = 11f * dp
            val txt = "目标：${ov.guideName} · 偏转 %.1f°".format(ov.guideSepDeg!!)
            val tw = textPaint.measureText(txt)
            n.drawText(txt, cx - tw / 2, cy + 70f * dp, textPaint)
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
