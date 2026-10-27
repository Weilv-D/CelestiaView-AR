package com.celestiaview.ar.ui.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 高分辨率程序化底片生成器（512×512）：
 * 网络实拍图加载瞬间或离线环境下，按天体类型实时合成分形星云 / 旋涡星系 /
 * 木星云带大红斑 / 土星卡西尼环缝 / 月面环形山等高清底片，经径向羽化后
 * 与真实摄影同规格呈现。
 */
object AstrophotoPlateGenerator {

    const val PLATE_SIZE = 512

    /** 共享绘图对象池的串行入口（可从任意后台线程调用）。 */
    @Synchronized
    fun generate(style: PlateStyle, seed: Int, sizePx: Int = PLATE_SIZE): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        when (style) {
            PlateStyle.GALAXY -> drawGalaxy(c, sizePx.toFloat(), rnd)
            PlateStyle.NEBULA -> drawNebula(c, sizePx.toFloat(), rnd)
            PlateStyle.OPEN_CLUSTER -> drawCluster(c, sizePx.toFloat(), rnd, globular = false)
            PlateStyle.GLOBULAR_CLUSTER -> drawCluster(c, sizePx.toFloat(), rnd, globular = true)
            PlateStyle.PLANETARY_NEBULA -> drawPlanetaryNebula(c, sizePx.toFloat(), rnd)
            PlateStyle.SUPERNOVA_REMNANT -> drawRemnant(c, sizePx.toFloat(), rnd)
            PlateStyle.JUPITER -> drawJupiter(c, sizePx.toFloat())
            PlateStyle.SATURN -> drawSaturn(c, sizePx.toFloat())
            PlateStyle.MOON -> drawMoon(c, sizePx.toFloat(), rnd)
            PlateStyle.SUN -> drawSun(c, sizePx.toFloat(), rnd)
            PlateStyle.STAR_FIELD -> drawStarField(c, sizePx.toFloat(), rnd)
        }
        return AstrophotoRepository.applyRadialFeatherMask(bmp)
    }

    // ------------------------------------------------------------------
    // 绘图对象池（生成属一次性路径，不做逐帧复用约束）
    // ------------------------------------------------------------------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun radialPaint(colorArgb: Int): Paint = paint.apply {
        shader = null
        color = colorArgb
        xfermode = null
    }

    /** 柔光团（普通混合半透明色，多层叠加出云气翻涌层次）。 */
    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, colorArgb: Int) {
        radialPaint(colorArgb)
        c.drawCircle(cx, cy, r, paint)
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, colorArgb: Int, alpha: Int) {
        paint.shader = null
        paint.color = colorArgb
        paint.alpha = alpha
        c.drawCircle(x, y, r, paint)
    }

    private fun backgroundStars(c: Canvas, s: Float, rnd: Random, count: Int) {
        val colors = intArrayOf(0xFF93C5FD.toInt(), 0xFFF8FAFC.toInt(), 0xFFFDE68A.toInt())
        repeat(count) {
            val rr = 0.6f + rnd.nextFloat() * 1.4f
            star(c, rnd.nextFloat() * s, rnd.nextFloat() * s, rr,
                colors[rnd.nextInt(3)], (70 + rnd.nextInt(130)))
        }
    }

    // ------------------------------------------------------------------
    // 深空天体
    // ------------------------------------------------------------------

    private fun drawGalaxy(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        val tilt = -0.5f + rnd.nextFloat() // 盘面倾角
        blob(c, cx, cy, s * 0.40f, 0x14101830) // 外晕
        blob(c, cx, cy, s * 0.22f, 0x2C30466E) // 盘面蓝
        blob(c, cx, cy, s * 0.10f, 0x66FFF3E0) // 暖金核球
        blob(c, cx, cy, s * 0.035f, 0xE6FFF8E8.toInt())
        c.save()
        c.rotate(tilt * 57.3f, cx, cy)
        // 两条旋臂：对数螺线上的星团 + 尘埃辉光
        for (arm in 0 until 2) {
            val phase = arm * PI
            var t = 0.12f
            while (t < 1.0f) {
                val ang = phase + t * 3.1f
                val r = s * 0.05f + t * s * 0.36f
                val x = cx + r * cos(ang).toFloat()
                val y = cy + r * 0.42f * sin(ang).toFloat()
                blob(c, x, y, s * (0.02f + 0.035f * (1f - t)), 0x2E6E8CFF)
                if (rnd.nextFloat() < 0.6f) {
                    blob(c, x + rnd.nextFloat() * 6f - 3f, y + rnd.nextFloat() * 6f - 3f,
                        s * 0.012f, 0x38BDD5F5)
                }
                t += 0.045f
            }
        }
        c.restore()
        backgroundStars(c, s, rnd, 140)
    }

    private fun drawNebula(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        // 三色发射云气：洋红 Hα + 青 OIII + 紫
        val palette = intArrayOf(
            0x38E879F9.toInt(), 0x30C084FC.toInt(), 0x2822D3EE.toInt(), 0x26FF8FD0.toInt()
        )
        repeat(46) {
            val a = rnd.nextFloat() * 2f * PI
            val d = rnd.nextFloat().pow(1.6f) * s * 0.36f
            val x = cx + d * cos(a).toFloat()
            val y = cy + d * 0.9f * sin(a).toFloat()
            blob(c, x, y, s * (0.03f + rnd.nextFloat() * 0.09f), palette[rnd.nextInt(palette.size)])
        }
        // 亮核 + 新生星团
        blob(c, cx, cy, s * 0.16f, 0x3CFFE4E1)
        blob(c, cx, cy, s * 0.06f, 0x73FFF6EF)
        repeat(26) {
            val a = rnd.nextFloat() * 2f * PI
            val d = rnd.nextFloat() * s * 0.2f
            star(c, cx + d * cos(a).toFloat(), cy + d * sin(a).toFloat(),
                0.8f + rnd.nextFloat() * 1.6f, 0xFFF8FAFC.toInt(), 200)
        }
        backgroundStars(c, s, rnd, 120)
    }

    private fun drawCluster(c: Canvas, s: Float, rnd: Random, globular: Boolean) {
        val cx = s / 2f
        val cy = s / 2f
        val n = if (globular) 340 else 90
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI
            val d = if (globular) rnd.nextFloat().pow(2.2f) * s * 0.42f
            else sqrt(rnd.nextFloat()) * s * 0.34f
            val warm = rnd.nextFloat() < 0.4f
            star(c, cx + d * cos(a).toFloat(), cy + d * sin(a).toFloat(),
                0.6f + rnd.nextFloat() * (if (globular) 1.5f else 2.1f),
                if (warm) 0xFFFDE68A.toInt() else 0xFFD6E8FF.toInt(),
                120 + rnd.nextInt(120))
        }
        if (globular) blob(c, cx, cy, s * 0.20f, 0x2ED6E4FF)
        backgroundStars(c, s, rnd, 90)
    }

    private fun drawPlanetaryNebula(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        val rx = s * 0.30f
        val ry = s * 0.30f * (0.75f + rnd.nextFloat() * 0.2f)
        blob(c, cx, cy, s * 0.10f, 0x5CFFFFFF)
        strokePaint.color = 0x8226C6DA.toInt()
        strokePaint.strokeWidth = s * 0.075f
        c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
        strokePaint.color = 0x46E879F9.toInt()
        strokePaint.strokeWidth = s * 0.13f
        c.drawOval(cx - rx * 1.18f, cy - ry * 1.18f, cx + rx * 1.18f, cy + ry * 1.18f, strokePaint)
        star(c, cx, cy, s * 0.016f, 0xFFFFFFFF.toInt(), 255)
        backgroundStars(c, s, rnd, 110)
    }

    private fun drawRemnant(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        // 丝状纤维：多圈随机椭圆弧
        repeat(16) { k ->
            val rx = s * (0.12f + rnd.nextFloat() * 0.26f)
            val ry = rx * (0.5f + rnd.nextFloat() * 0.5f)
            strokePaint.color = if (k % 3 == 0) 0x5CFF8A65.toInt() else 0x4C64FFDA.toInt()
            strokePaint.strokeWidth = 1.4f + rnd.nextFloat() * 2.6f
            c.save()
            c.rotate(rnd.nextFloat() * 360f, cx, cy)
            c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
            c.restore()
        }
        blob(c, cx, cy, s * 0.045f, 0x8ADFEFFF.toInt())
        star(c, cx, cy, s * 0.008f, 0xFFFFFFFF.toInt(), 255)
        backgroundStars(c, s, rnd, 120)
    }

    // ------------------------------------------------------------------
    // 太阳系天体
    // ------------------------------------------------------------------

    private fun clipDisc(c: Canvas, cx: Float, cy: Float, r: Float, block: () -> Unit) {
        c.save()
        c.clipPath(android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) })
        block()
        c.restore()
    }

    private fun drawJupiter(c: Canvas, s: Float) {
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.40f
        clipDisc(c, cx, cy, r) {
            // 赤道云带：奶油 / 赭褐交替
            val bands = arrayOf(
                0xFFDBC9A6.toInt(), 0xFFB08D62.toInt(), 0xFFEBDDC0.toInt(),
                0xFFA17C55.toInt(), 0xFFD9C8A4.toInt(), 0xFFB58F67.toInt(),
                0xFFEBDDC0.toInt(), 0xFFC4A87E.toInt()
            )
            val bandH = r * 2f / bands.size
            bands.forEachIndexed { i, col ->
                paint.shader = null
                paint.color = col
                c.drawRect(cx - r, cy - r + i * bandH, cx + r, cy - r + (i + 1) * bandH, paint)
            }
            // 大红斑
            paint.color = 0xFFC26B4A.toInt()
            c.drawOval(cx + r * 0.12f, cy + r * 0.28f, cx + r * 0.66f, cy + r * 0.60f, paint)
            paint.color = 0x66E29A78.toInt()
            c.drawOval(cx + r * 0.20f, cy + r * 0.35f, cx + r * 0.58f, cy + r * 0.53f, paint)
            // 临边昏暗 + 极区晕
            paint.shader = RadialGradient(
                cx, cy, r, 0x00000000.toInt(), 0x59040A18.toInt(), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r, paint)
        }
    }

    private fun drawSaturn(c: Canvas, s: Float) {
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.26f
        c.save()
        c.rotate(-14f, cx, cy)
        // 环系（含卡西尼环缝）：A 环 + B 环
        strokePaint.color = 0x99D9C078.toInt()
        strokePaint.strokeWidth = s * 0.045f
        c.drawOval(cx - r * 2.05f, cy - r * 0.62f, cx + r * 2.05f, cy + r * 0.62f, strokePaint)
        strokePaint.color = 0xCCDEC98C.toInt()
        strokePaint.strokeWidth = s * 0.075f
        c.drawOval(cx - r * 1.62f, cy - r * 0.50f, cx + r * 1.62f, cy + r * 0.50f, strokePaint)
        // 球体：淡金云带
        clipDisc(c, cx, cy, r) {
            paint.color = 0xFFE3D3A8.toInt()
            c.drawCircle(cx, cy, r, paint)
            paint.color = 0x66C9AE72.toInt()
            for (k in -2..2) {
                c.drawRect(cx - r, cy + k * r * 0.36f - r * 0.09f, cx + r, cy + k * r * 0.36f + r * 0.09f, paint)
            }
            paint.color = 0x26FFFFFF.toInt()
            c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.7f, paint)
        }
        c.restore()
    }

    private fun drawMoon(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.44f
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.25f, r * 1.5f,
            0xFFF2F0EA.toInt(), 0xFF8E8C85.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, paint)
        clipDisc(c, cx, cy, r) {
            // 月海
            repeat(5) {
                paint.shader = null
                paint.color = 0x30555A63.toInt()
                val mx = cx + (rnd.nextFloat() - 0.5f) * r * 1.2f
                val my = cy + (rnd.nextFloat() - 0.3f) * r * 0.9f
                c.drawOval(mx - r * 0.3f, my - r * 0.22f, mx + r * 0.3f, my + r * 0.22f, paint)
            }
            // 环形山：暗缘 + 亮心
            repeat(46) {
                val cr = 1.6f + rnd.nextFloat().pow(2f) * r * 0.14f
                val a = rnd.nextFloat() * 2f * PI
                val d = rnd.nextFloat() * (r - cr * 2)
                val x = cx + d * cos(a).toFloat()
                val y = cy + d * sin(a).toFloat()
                paint.color = 0x4C6B6A66.toInt()
                c.drawCircle(x, y, cr, paint)
                paint.color = 0x59E8E4DA.toInt()
                c.drawCircle(x - cr * 0.2f, y - cr * 0.2f, cr * 0.55f, paint)
            }
        }
    }

    private fun drawSun(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.42f
        blob(c, cx, cy, r * 1.25f, 0x33FFB300)
        paint.shader = RadialGradient(
            cx, cy, r,
            0xFFFFF3B0.toInt(), 0xFFFF9E2C.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, paint)
        clipDisc(c, cx, cy, r) {
            // 米粒组织 + 黑子
            repeat(60) {
                paint.shader = null
                paint.color = if (rnd.nextFloat() < 0.12) 0x59804A1E.toInt() else 0x40FFDF9A
                val a = rnd.nextFloat() * 2f * PI
                val d = rnd.nextFloat() * r
                c.drawCircle(cx + d * cos(a).toFloat(), cy + d * sin(a).toFloat(),
                    1.2f + rnd.nextFloat() * 2.8f, paint)
            }
        }
    }

    private fun drawStarField(c: Canvas, s: Float, rnd: Random) {
        val cx = s / 2f
        val cy = s / 2f
        backgroundStars(c, s, rnd, 260)
        blob(c, cx, cy, s * 0.09f, 0x40BFD8FF)
        blob(c, cx, cy, s * 0.035f, 0x99FFFFFF.toInt())
        star(c, cx, cy, s * 0.012f, 0xFFFFFFFF.toInt(), 255)
        // 中央主星十字星芒
        strokePaint.color = 0xB4EAF6FF.toInt()
        strokePaint.strokeWidth = 2.2f
        c.drawLine(cx - s * 0.16f, cy, cx + s * 0.16f, cy, strokePaint)
        c.drawLine(cx, cy - s * 0.16f, cx, cy + s * 0.16f, strokePaint)
    }

    private fun Float.pow(e: Float): Float = Math.pow(this.toDouble(), e.toDouble()).toFloat()
}
