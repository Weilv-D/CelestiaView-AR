package com.celestiaview.ar.ui.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.sin

/**
 * 恒星光学着色器：
 * - B-V 色指数 → 普朗克黑体辐射 RGB；
 * - 视星等 → 基础半径与 4-Layer PSF 多层大气光晕；
 * - 一等星十字衍射星芒（Diffraction Spikes）；
 * - 地平高度调制的大气闪烁（Scintillation）。
 *
 * 零分配约定：全部 android.graphics 绘图对象（Paint / Shader / Xfermode / PathEffect）
 * 在进程内惰性预分配一次，逐帧只做 setColor / setAlpha / save / scale 原地变换，
 * 严禁在绘制路径中出现任何 new / Brush 工厂调用（详见《零分配渲染循环审计》）。
 */
object StarOpticsShader {

    /** B-V 到黑体色的分段插值锚点。 */
    private data class BvColor(val bv: Float, val color: Color)

    private val BV_TABLE = listOf(
        BvColor(-0.33f, Color(0xFF9BB0FF)), // 蓝巨星 O/B
        BvColor(0.00f, Color(0xFFCAD7FF)), // 蓝白 A
        BvColor(0.40f, Color(0xFFF8F7FF)), // 纯白 F
        BvColor(0.80f, Color(0xFFFFE4B5)), // 暖黄 G
        BvColor(1.40f, Color(0xFFFFD2A0)), // 橙 K
        BvColor(1.85f, Color(0xFFFF9E64)), // 橙红 M 巨星
        BvColor(2.20f, Color(0xFFFF7B4F))  // 深红
    )

    fun bvToColor(bv: Double): Color {
        val v = bv.toFloat().coerceIn(-0.33f, 2.20f)
        for (i in 0 until BV_TABLE.size - 1) {
            val a = BV_TABLE[i]
            val b = BV_TABLE[i + 1]
            if (v in a.bv..b.bv) {
                val t = (v - a.bv) / (b.bv - a.bv)
                return lerpColor(a.color, b.color, t)
            }
        }
        return BV_TABLE.last().color
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )

    /** 视星等 → 基础核心半径（dp）。r = max(1.2, 6.5 - 1.15·m)。 */
    fun coreRadiusDp(mag: Double): Float =
        max(1.2f, (6.5 - 1.15 * mag).toFloat())

    // ------------------------------------------------------------------
    // 预分配原生绘图对象（单渲染线程访问，无并发）
    // ------------------------------------------------------------------

    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * 8 阶指数衰减高斯光晕剖面：中心 1.0 → 0.1 处 0.6 → 0.25 处 0.2 →
     * 0.5 处 0.04 → 1.0 处 0。肉眼看不到任何圆圈边界（杜绝"白心灰圈"）。
     *
     * 软 GPU (angle_indirect / swiftshader / guest) 安全铁律：多阶径向渐变着色器
     * 走逐帧 GPU 管线会击穿模拟器渲染进程（宿主段错误），因此剖面在类加载时
     * 以软件画布一次性预烘焙为位图精灵，逐帧只做 drawBitmap 纹理块传输。
     */
    private val GAUSS_POS = floatArrayOf(
        0f, 0.10f, 0.25f, 0.40f, 0.50f, 0.65f, 0.80f, 1f
    )
    private val GAUSS_ALPHA = floatArrayOf(
        1f, 0.60f, 0.20f, 0.08f, 0.04f, 0.015f, 0.005f, 0f
    )
    private val GAUSS_COLORS = intArrayOf(
        WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE
    )
    private const val SPRITE_SIZE = 128
    private const val SPRITE_HALF = SPRITE_SIZE * 0.5f

    /** 8 阶高斯光晕位图精灵（软件画布烘焙， alpha 即径向衰减剖面）。 */
    private val gaussSprite: android.graphics.Bitmap by lazy {
        val bmp = android.graphics.Bitmap.createBitmap(
            SPRITE_SIZE, SPRITE_SIZE, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bmp) // 软件画布：烘焙零 GPU 参与
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                SPRITE_HALF, SPRITE_HALF, SPRITE_HALF,
                GAUSS_COLORS, GAUSS_POS, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(SPRITE_HALF, SPRITE_HALF, SPRITE_HALF, paint)
        bmp
    }

    /**
     * 光晕精灵绘制 Paint：直接 SRC_OVER 叠加。
     * 软 GPU (angle_indirect) 上 drawBitmap 的 xfermode ADD 会被忽略而回退
     * SRC_OVER，白色精灵随即呈灰盘荷包蛋——故剖面全部内建于位图 alpha，
     * 不依赖任何混合模式。
     */
    private val haloPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    /** 通用柔光精灵（日落辉光 / 银心增强 / 行星光球 / 月晕）：同一 8 阶精灵。 */
    private val glowPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    /**
     * 高斯光晕精灵原生绘制（零分配）：半径 [radiusPx] 的 8 阶指数衰减柔光，
     * alpha 通道即剖面；[additive] 为真时加色混合（星光），否则标准覆盖。
     */
    private fun drawGaussSpriteN(
        n: Canvas,
        cx: Float,
        cy: Float,
        radiusPx: Float,
        colorArgb: Int,
        alpha01: Float,
        @Suppress("UNUSED_PARAMETER") additive: Boolean
    ) {
        if (radiusPx < 0.5f) return
        val p = haloPaint
        p.color = colorArgb
        p.alpha = (alpha01.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        n.save()
        n.translate(cx, cy)
        n.scale(radiusPx / SPRITE_HALF, radiusPx / SPRITE_HALF)
        n.drawBitmap(gaussSprite, -SPRITE_HALF, -SPRITE_HALF, p)
        n.restore()
    }

    /** 衍射星芒：单位长度水平渐变（配合画布 translate+scale 复用）。 */
    private val spikePaintH: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                -1f, 0f, 1f, 0f,
                intArrayOf(WHITE, WHITE, WHITE),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    /** 衍射星芒：单位长度垂直渐变。 */
    private val spikePaintV: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -1f, 0f, 1f,
                intArrayOf(WHITE, WHITE, WHITE),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    private val coreFillPaint: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val ringStrokePaint: Paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    private val dashRingPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 6f), 0f)
        }
    }

    // ------------------------------------------------------------------
    // 4-Layer PSF 恒星渲染（原生画布，零分配）
    // ------------------------------------------------------------------

    /**
     * 4-Layer PSF 恒星光学渲染（加色混合，星光自然辉映）：
     * L1 广域大气散射柔光（5.5r，仅 mag < 2.5）→
     * L2 内层高亮冕环（2.2r）→
     * L3 望远镜十字衍射星芒（9r 十字 + 4r 对角，仅 mag < 1.2）→
     * L4 炙热白炽核心（光子过曝纯白）+ 光谱色薄环。
     *
     * @param colorArgb 黑体色 ARGB（不含透明度信息）
     */
    fun drawStarN(
        n: Canvas,
        cx: Float,
        cy: Float,
        coreRadiusPx: Float,
        colorArgb: Int,
        twinkleAlpha: Float = 1f,
        haloScale: Float = 1f,
        mag: Double = 3.0
    ) {
        val alpha = twinkleAlpha.coerceIn(0.25f, 1f)
        val r = coreRadiusPx

        // L1 广域大气散射柔光（仅亮星）：8 阶高斯精灵加色混合
        if (mag < 2.5) {
            val haloR = r * 5.5f * haloScale
            drawGaussSpriteN(n, cx, cy, haloR, colorArgb, 0.22f * alpha, additive = true)
        }

        // L2 内层高亮冕环
        val coronaR = r * 2.2f
        drawGaussSpriteN(n, cx, cy, coronaR, colorArgb, 0.75f * alpha, additive = true)

        // L3 望远镜十字衍射星芒（仅一等亮星）
        if (mag < 1.2) {
            drawSpikesN(n, cx, cy, r * 9.0f, r * 4.0f, 0.65f * alpha)
        }

        // L4 炙热恒星白炽核心：中心光子过曝呈纯白
        coreFillPaint.color = WHITE
        coreFillPaint.alpha = (0.95f * alpha * 255f).toInt().coerceIn(0, 255)
        n.drawCircle(cx, cy, max(r * 0.65f, 1.5f), coreFillPaint)

        // 光谱色薄环（保留黑体色感）
        ringStrokePaint.color = colorArgb
        ringStrokePaint.alpha = (0.55f * alpha * 255f).toInt().coerceIn(0, 255)
        ringStrokePaint.strokeWidth = max(r * 0.5f, 0.8f)
        n.drawCircle(cx, cy, r, ringStrokePaint)
    }

    /** 衍射星芒原生绘制：主十字 + 45° 对角短芒，全部复用预分配渐变 Paint。 */
    private fun drawSpikesN(n: Canvas, cx: Float, cy: Float, lengthPx: Float, diagPx: Float, alpha: Float) {        val a255 = (alpha * 255f).toInt().coerceIn(0, 255)
        val d = diagPx * 0.7071f
        // 主十字（水平 / 垂直，线宽 1.2px）
        n.save()
        n.translate(cx, cy)
        spikePaintH.alpha = a255
        n.scale(lengthPx, 1f)
        n.drawRect(-1f, -0.6f, 1f, 0.6f, spikePaintH)
        n.scale(1f / lengthPx, 1f)
        spikePaintV.alpha = a255
        n.scale(1f, lengthPx)
        n.drawRect(-0.6f, -1f, 0.6f, 1f, spikePaintV)
        n.restore()
        // 45° 对角短芒（线宽 1.0px，强度 0.7×）
        val da = (alpha * 0.7f * 255f).toInt().coerceIn(0, 255)
        n.save()
        n.translate(cx, cy)
        n.rotate(45f)
        spikePaintH.alpha = da
        n.scale(d, 1f)
        n.drawRect(-1f, -0.5f, 1f, 0.5f, spikePaintH)
        n.restore()
        n.save()
        n.translate(cx, cy)
        n.rotate(-45f)
        n.scale(d, 1f)
        n.drawRect(-1f, -0.5f, 1f, 0.5f, spikePaintH)
        n.restore()
    }

    /** 通用径向柔光精灵（零分配）：用于日落辉光 / 银心 / 行星光球 / 月晕等。 */
    fun glowSpriteN(
        n: Canvas,
        cx: Float,
        cy: Float,
        radiusPx: Float,
        colorArgb: Int,
        alpha01: Float
    ) {
        drawGaussSpriteN(n, cx, cy, radiusPx, colorArgb, alpha01, additive = false)
    }

    /**
     * 八芒璀璨耀星（星座骨架主星 / 北斗七星）：
     * 冰蓝径向发光晕圈 + 纯白圆核 + 4 道长主星芒 + 4 道 45° 交错短星芒，
     * 芒线自中心纯白线性衰减。零分配（复用 glow / spike / core Paint 池）。
     */
    fun drawStarburst8(
        n: Canvas,
        cx: Float,
        cy: Float,
        coreRadiusPx: Float,
        longSpikePx: Float,
        shortSpikePx: Float,
        alpha: Float
    ) {
        glowSpriteN(n, cx, cy, longSpikePx * 0.55f, 0xFF7DD3FC.toInt(), 0.22f * alpha)
        drawSpikesN(n, cx, cy, longSpikePx, shortSpikePx, 0.9f * alpha)
        coreFillPaint.color = WHITE
        coreFillPaint.alpha = (0.95f * alpha * 255f).toInt().coerceIn(0, 255)
        n.drawCircle(cx, cy, max(coreRadiusPx * 0.8f, 1.6f), coreFillPaint)
    }

    /**
     * 大气闪烁调制：越靠近地平线闪烁越剧烈。
     * @return [0,1] 的乘性透明度
     */
    fun scintillation(altDeg: Double, timeSec: Float, seed: Float): Float {
        if (altDeg > 45.0) return 1f
        val horizonFactor =
            (((45.0 - altDeg) / 45.0).coerceIn(0.0, 1.0)).toFloat()
        val amplitude = horizonFactor * horizonFactor * 0.45f
        val wobble = sin(timeSec * (7.0f + seed * 3.7f) + seed * 31.7f) *
            sin(timeSec * (3.1f + seed * 1.3f) + seed * 11.3f)
        return (1f - amplitude * (0.5f + 0.5f * wobble)).coerceIn(0.35f, 1f)
    }

    /** 虚线圆环（星团标识 / 行星脉冲环）：复用预分配虚线 Paint。 */
    fun DrawScope.drawDashedRing(
        centerX: Float,
        centerY: Float,
        radiusPx: Float,
        color: Color,
        strokeWidthPx: Float = 1.4f
    ) {
        val p = dashRingPaint
        p.color = color.copy(alpha = 1f).toArgb()
        p.alpha = (color.alpha * 255f).toInt().coerceIn(0, 255)
        p.strokeWidth = strokeWidthPx
        drawContext.canvas.nativeCanvas.drawCircle(centerX, centerY, radiusPx, p)
    }

    /** 稳定伪随机 [0,1)。 */
    fun hash01(seed: Int): Float {
        val h = seed * 2654435761.toInt()
        return ((h ushr 8) and 0xFFFF).toFloat() / 65535f
    }
}
