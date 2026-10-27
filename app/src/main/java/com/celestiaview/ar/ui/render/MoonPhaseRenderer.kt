package com.celestiaview.ar.ui.render

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 高拟真月相渲染器：
 * 大圆暗底 + 半圆/椭圆复合路径明暗界线（Terminator）+ 地球照微光 + 月海暗斑。
 *
 * 零分配约定：Path / RectF / Paint 全部为对象字段，逐帧 rewind + set 复用。
 */
object MoonPhaseRenderer {

    /** 月海暗斑（归一化坐标 cx,cy,r：相对月面半径的比例）。 */
    private val MARIA = listOf(
        Maria(0.30f, -0.28f, 0.26f, 0.50f),   // 危海
        Maria(-0.22f, 0.12f, 0.30f, 0.40f),   // 雨海
        Maria(-0.38f, -0.18f, 0.20f, 0.40f),  // 风暴洋
        Maria(0.10f, 0.05f, 0.22f, 0.36f),    // 静海
        Maria(0.22f, 0.28f, 0.16f, 0.42f),    // 丰富海
        Maria(-0.10f, 0.34f, 0.18f, 0.38f)    // 湿海/云海
    )

    private class Maria(val cx: Float, val cy: Float, val r: Float, val dark: Float)

    private val litPath = Path()
    private val discRect = RectF()
    private val termRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * @param phaseAngleDeg 月相角 Φ：0=新月，90=上弦，180=满月，270=下弦。
     * 北半球视角：Φ∈(0,180) 亮面在西（画面右侧），Φ∈(180,360) 亮面在东（左侧）。
     */
    fun DrawScope.drawMoon(
        center: Offset,
        radiusPx: Float,
        phaseAngleDeg: Double,
        rimGlow: Float = 0.6f
    ) {
        val phi = ((phaseAngleDeg % 360.0) + 360.0) % 360.0
        val r = radiusPx
        val n = drawContext.canvas.nativeCanvas
        val cx = center.x
        val cy = center.y
        discRect.set(cx - r, cy - r, cx + r, cy + r)

        // —— 暗面底色（月面灰 + 地球照微光）
        fillPaint.color = 0xFF2A2E38.toInt()
        n.drawCircle(cx, cy, r, fillPaint)
        fillPaint.color = 0xFF46507A.toInt()
        fillPaint.alpha = (0.35f * 255f).toInt() // Earthshine 地球照
        n.drawCircle(cx, cy, r, fillPaint)

        // —— 亮面路径：半圆 + 椭圆明暗界线
        // 椭圆半宽 rx = R·|cosΦ|；Φ<90 亮面凸（>半圆），90-180 凹（<半圆）
        val litOnRight = phi < 180.0
        val c = kotlin.math.cos(Math.toRadians(phi)).toFloat() // +: 亮面宽于半圆
        val rx = kotlin.math.abs(c) * r

        litPath.rewind()
        termRect.set(cx - rx, cy - r, cx + rx, cy + r)
        if (litOnRight) {
            // 右半圆：从顶端到底端（顺时针经过右侧）
            litPath.arcTo(discRect, -90f, 180f, false)
            // 明暗界线椭圆弧：从 (cx, cy+r) 回到 (cx, cy-r)
            // c>0（盈凸/上弦前）界线凸向左 → 沿椭圆左半；c<0（蛾眉）凸向右 → 沿椭圆右半
            if (c >= 0) {
                litPath.arcTo(termRect, 90f, -180f, false) // 左半弧，逆时针
            } else {
                litPath.arcTo(termRect, 90f, 180f, false)  // 右半弧，顺时针
            }
        } else {
            // 左半圆
            litPath.arcTo(discRect, 90f, 180f, false)
            val cc = kotlin.math.cos(Math.toRadians(360.0 - phi)).toFloat() // 对称处理
            // 亏凸月（Φ∈180..270, cc<0）：界线凸向右（暗侧）→ 顺时针经右半弧；
            // 亏蛾眉（Φ∈270..360, cc>=0）：界线凸向左（与亮缘同侧）→ 逆时针经左半弧
            if (cc < 0) {
                litPath.arcTo(termRect, -90f, 180f, false)
            } else {
                litPath.arcTo(termRect, -90f, -180f, false)
            }
        }
        litPath.close()
        fillPaint.color = 0xFFE8E4D8.toInt()
        n.drawPath(litPath, fillPaint)

        // —— 月海暗斑（相对月盘固定布局，亮面侧更醒目）
        for (m in MARIA) {
            val mx = cx + m.cx * r * (if (litOnRight) 1f else -1f)
            val my = cy + m.cy * r
            fillPaint.color = 0xFF6B6F76.toInt()
            fillPaint.alpha = (0.35f * m.dark * 255f).toInt()
            n.drawCircle(mx, my, m.r * r, fillPaint)
        }

        // —— 边缘轮廓与辉光
        ringPaint.color = 0xFFB0B6C4.toInt()
        ringPaint.alpha = (0.55f * 255f).toInt()
        ringPaint.strokeWidth = r * 0.03f
        n.drawCircle(cx, cy, r, ringPaint)
        if (rimGlow > 0f) {
            StarOpticsShader.glowSpriteN(
                n, cx, cy, r * 1.9f, 0xFFE8E4D8.toInt(), 0.28f * rimGlow
            )
        }
    }
}
