package com.celestiaview.ar.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 实时 3D 行星球面渲染器：逐像素球面光线投射 + 等距柱状纹理双线性采样 +
 * 兰伯特晨昏线 + 边缘昏暗 + 菲涅尔大气辉光 + 实时自转，土星附 3D 倾斜光环。
 *
 * 软 GPU (angle_indirect) 60fps 保障：着色结果全部落在离屏位图缓存
 * （常规态 128×128 / 聚焦态 256×256，复用 IntArray 缓冲），仅当自转相位
 * 步进 ≥2.5° 或尺寸档变化时重绘；主线程只做一次 drawBitmap 缩放贴回。
 */
object Planet3DRenderer {

    /** 行星视觉参数：自转角速度（度/秒，演示节奏）与自转轴倾角（度）。 */
    internal data class PlanetOptics(
        val spinDegPerSec: Double,
        val tiltDeg: Double,
        val atmoArgb: Int,
        val emissive: Boolean = false
    )

    private val OPTICS = mapOf(
        "SUN" to PlanetOptics(1.2, 7.25, 0x00000000, emissive = true),
        "MOON" to PlanetOptics(1.0, 6.7, 0x00000000),
        "MERCURY" to PlanetOptics(0.8, 0.03, 0x00000000),
        "VENUS" to PlanetOptics(-1.2, 2.6, 0x50E8C97A),
        "EARTH" to PlanetOptics(6.0, 23.4, 0x603FA9F5),
        "MARS" to PlanetOptics(3.0, 25.2, 0x50C1441E),
        "JUPITER" to PlanetOptics(5.0, 3.1, 0x58E8B87A),
        "SATURN" to PlanetOptics(4.5, 26.7, 0x48E0C68C),
        "URANUS" to PlanetOptics(3.5, 97.8, 0x489FE8E8),
        "NEPTUNE" to PlanetOptics(3.2, 28.3, 0x583F7AF5)
    )

    private const val TEX_W = 512
    private const val TEX_H = 256

    /** 纹理采样缓冲（等距柱状投影，ARGB 像素），IO 线程预热。 */
    private val textures = HashMap<String, IntArray>()
    @Volatile private var warm = false

    /** 预热全部纹理（应用启动后 IO 线程调用一次）。 */
    fun warmup(context: Context) {
        if (warm) return
        synchronized(textures) {
            if (warm) return
            val app = context.applicationContext
            for (key in OPTICS.keys) {
                runCatching {
                    app.assets.open("planets/${key.lowercase()}.jpg").use { input ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bmp = BitmapFactory.decodeStream(input, null, opts) ?: return@runCatching
                        val scaled = Bitmap.createScaledBitmap(bmp, TEX_W, TEX_H, true)
                        val px = IntArray(TEX_W * TEX_H)
                        scaled.getPixels(px, 0, TEX_W, 0, 0, TEX_W, TEX_H)
                        textures[key] = px
                        if (scaled !== bmp) { scaled.recycle(); bmp.recycle() }
                    }
                }
            }
            warm = true
            android.util.Log.i("Planet3D", "warmup done textures=" + textures.size + " ring=" + (ringSprite != null))
        }
    }

    fun hasTexture(key: String): Boolean = warm && textures.containsKey(key)

    // ------------------------------------------------------------------
    // 离屏球体缓存
    // ------------------------------------------------------------------

    private class SphereCache {
        var bmp: Bitmap? = null
        var buf = IntArray(0)
        var size = 0
        var rotDeg = Float.NaN
        var litDx = Float.NaN
        var litDy = Float.NaN
    }

    private val caches = HashMap<String, SphereCache>()
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 绘制 3D 自转球体（离屏着色 + 缩放贴回）。
     * @param key 行星键（OPTICS / assets 纹理键）
     * @param radiusPx 屏幕半径
     * @param lightToSunDx/Dy 屏幕面上指向太阳的单位方向（无太阳时给 0,0 → 默认侧光）
     * @param alpha 总透明度 0..1
     * @return true 表示已绘制（纹理就绪）；false 时调用方回落矢量画法
     */
    fun draw(
        n: Canvas,
        key: String,
        cx: Float,
        cy: Float,
        radiusPx: Float,
        timeMillis: Long,
        lightToSunDx: Float,
        lightToSunDy: Float,
        alpha: Float
    ): Boolean {
        val tex = textures[key] ?: return false
        val opt = OPTICS[key] ?: return false
        if (radiusPx < 1f || alpha <= 0.01f) return true

        // 聚焦态（半径大）用 256 档，常规态 128 档；复用像素缓冲
        val size = if (radiusPx >= 56f) 256 else 128
        val rotDeg = ((timeMillis / 1000.0) * opt.spinDegPerSec % 360.0).toFloat()
        val c = caches.getOrPut(key) { SphereCache() }
        val lightLen = sqrt(lightToSunDx * lightToSunDx + lightToSunDy * lightToSunDy)
        val ldx = if (lightLen > 0.01f) lightToSunDx / lightLen else -0.65f
        val ldy = if (lightLen > 0.01f) lightToSunDy / lightLen else 0.25f
        val stale = c.size != size || c.bmp == null ||
            kotlin.math.abs(rotDeg - c.rotDeg) >= 2.5f ||
            kotlin.math.abs(ldx - c.litDx) > 0.08f ||
            kotlin.math.abs(ldy - c.litDy) > 0.08f
        if (stale) {
            if (c.buf.size != size * size) c.buf = IntArray(size * size)
            shadeSphere(c.buf, size, tex, TEX_W, TEX_H, rotDeg, opt, ldx, ldy)
            if (c.bmp == null || c.size != size) {
                c.bmp?.recycle()
                c.bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                c.size = size
            }
            c.bmp!!.setPixels(c.buf, 0, size, 0, 0, size, size)
            c.rotDeg = rotDeg
            c.litDx = ldx
            c.litDy = ldy
        }
        val bmp = c.bmp ?: return false

        val half = size * 0.5f
        drawPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        n.save()
        n.translate(cx, cy)
        n.scale(radiusPx / half, radiusPx / half)
        n.drawBitmap(bmp, -half, -half, drawPaint)
        n.restore()
        return true
    }

    /**
     * 逐像素球面光线投射着色：
     * 归一化坐标 (nx,ny) → nz=√(1−r²) → 自转轴倾角旋转 → 经纬 UV 双线性采样 →
     * 兰伯特晨昏 (smoothstep(-0.15,0.85,N·L)×0.92+0.08) → 边缘昏暗 nz^0.35 →
     * 菲涅尔大气微光（nz<0.25）→ 外缘 [0.96,1] alpha 平滑衰减。
     */
    internal fun shadeSphere(
        out: IntArray,
        size: Int,
        tex: IntArray,
        texW: Int,
        texH: Int,
        rotDeg: Float,
        opt: PlanetOptics,
        ldx: Float,
        ldy: Float
    ) {
        val half = size * 0.5f
        val tiltRad = Math.toRadians(opt.tiltDeg)
        val cosT = cos(tiltRad).toFloat()
        val sinT = sin(tiltRad).toFloat()
        val rotRad = Math.toRadians(rotDeg.toDouble())
        // 光照向量（屏幕空间）：指向太阳方向 + 固定 z 分量
        val lz = 0.62f
        val invLen = 1f / sqrt(ldx * ldx + ldy * ldy + lz * lz)
        val lx = ldx * invLen
        val ly = ldy * invLen
        val lzz = lz * invLen
        val atmoR = (opt.atmoArgb shr 16 and 0xFF) / 255f
        val atmoG = (opt.atmoArgb shr 8 and 0xFF) / 255f
        val atmoB = (opt.atmoArgb and 0xFF) / 255f
        val atmoA = (opt.atmoArgb ushr 24) / 255f

        for (y in 0 until size) {
            val ny = (y + 0.5f - half) / half
            val rowBase = y * size
            for (x in 0 until size) {
                val nx = (x + 0.5f - half) / half
                val r2 = nx * nx + ny * ny
                val idx = rowBase + x
                if (r2 > 1f) { out[idx] = 0; continue }
                val nz = sqrt(1f - r2)
                val r = sqrt(r2)

                // 自转轴倾角：绕屏幕 X 轴旋转法线，两极在盘面上倾斜呈现
                val bx = nx
                val by = ny * cosT - nz * sinT
                val bz = ny * sinT + nz * cosT

                // 经纬度 → UV（自转相位并入经度）
                var u = (atan2(bx, bz) / (2.0 * Math.PI) + 0.5 + rotRad / (2.0 * Math.PI))
                u -= kotlin.math.floor(u)
                var v = asin(by.toDouble().coerceIn(-1.0, 1.0)) / Math.PI + 0.5
                v = v.coerceIn(0.0, 0.9999)
                val c0 = bilinear(tex, texW, texH, u.toFloat(), v.toFloat())

                // 兰伯特晨昏光照（屏幕空间法线）
                val ndotl = nx * lx + ny * ly + nz * lzz
                val ss = smoothstep(-0.15f, 0.85f, ndotl)
                var shade = ss * 0.92f + 0.08f
                if (opt.emissive) shade = 0.94f + 0.06f * ss

                // 边缘昏暗（f_limb = nz^0.35，忌死黑）
                val limb = Math.pow(nz.toDouble(), 0.35).toFloat()
                var fr = ((c0 shr 16 and 0xFF) / 255f) * shade * (0.5f + 0.5f * limb)
                var fg = ((c0 shr 8 and 0xFF) / 255f) * shade * (0.5f + 0.5f * limb)
                var fb = ((c0 and 0xFF) / 255f) * shade * (0.5f + 0.5f * limb)

                // 菲涅尔稀薄大气微光（nz < 0.25 的边缘环带）
                if (atmoA > 0f && nz < 0.25f) {
                    val fres = (0.25f - nz) / 0.25f * atmoA
                    fr += atmoR * fres
                    fg += atmoG * fres
                    fb += atmoB * fres
                }

                // 外缘 [0.96,1] alpha 平滑线性衰减，零锯齿
                var a = 1f
                if (r > 0.96f) a = ((1f - r) / 0.04f).coerceIn(0f, 1f)

                val ir = (fr.coerceIn(0f, 1f) * 255f).toInt()
                val ig = (fg.coerceIn(0f, 1f) * 255f).toInt()
                val ib = (fb.coerceIn(0f, 1f) * 255f).toInt()
                val ia = (a * 255f).toInt()
                out[idx] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    /** 等距柱状纹理双线性采样（尺寸参数化，供 JVM 单测以小纹理覆盖）。 */
    internal fun bilinear(tex: IntArray, tw: Int, th: Int, u: Float, v: Float): Int {
        val fx = u * tw
        val fy = v * th
        val x0 = fx.toInt().coerceIn(0, tw - 1)
        val y0 = fy.toInt().coerceIn(0, th - 1)
        val x1 = (x0 + 1) % tw
        val y1 = (y0 + 1).coerceAtMost(th - 1)
        val ax = fx - x0
        val ay = fy - y0
        val c00 = tex[y0 * tw + x0]
        val c10 = tex[y0 * tw + x1]
        val c01 = tex[y1 * tw + x0]
        val c11 = tex[y1 * tw + x1]
        fun ch(c1: Int, c2: Int, c3: Int, c4: Int, shift: Int): Int {
            val a = (c1 shr shift and 0xFF).toFloat()
            val b = (c2 shr shift and 0xFF).toFloat()
            val c = (c3 shr shift and 0xFF).toFloat()
            val d = (c4 shr shift and 0xFF).toFloat()
            return (a * (1 - ax) * (1 - ay) + b * ax * (1 - ay) + c * (1 - ax) * ay + d * ax * ay)
                .toInt().coerceIn(0, 255)
        }
        val r = ch(c00, c10, c01, c11, 16)
        val g = ch(c00, c10, c01, c11, 8)
        val b = ch(c00, c10, c01, c11, 0)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ------------------------------------------------------------------
    // 土星 3D 倾斜光环（预烘焙环带精灵，卡西尼环缝透明通道）
    // ------------------------------------------------------------------

    private const val RING_SPRITE = 512
    private const val RING_PLANET_R = 106f      // 精灵中行星半径像素
    private const val RING_INNER = 1.24f        // 内环半径（行星半径倍数）
    private const val RING_OUTER = 2.27f        // 外环半径（行星半径倍数）
    private var ringSprite: Bitmap? = null
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ringRectF = RectF()

    /** 预烘焙环带：径向采样 assets/planets/saturn_ring.png 的透明通道（含卡西尼缝）。 */
    fun warmupRing(context: Context) {
        if (ringSprite != null) return
        synchronized(this) {
            if (ringSprite != null) return
            val strip = runCatching {
                context.applicationContext.assets.open("planets/saturn_ring.png").use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
            val bmp = Bitmap.createBitmap(RING_SPRITE, RING_SPRITE, Bitmap.Config.ARGB_8888)
            val px = IntArray(RING_SPRITE * RING_SPRITE)
            val sw = strip?.width ?: 1
            val sh = strip?.height ?: 1
            val half = RING_SPRITE * 0.5f
            for (y in 0 until RING_SPRITE) {
                for (x in 0 until RING_SPRITE) {
                    val dx = x + 0.5f - half
                    val dy = y + 0.5f - half
                    val dist = sqrt(dx * dx + dy * dy) / RING_PLANET_R
                    val idx = y * RING_SPRITE + x
                    if (dist < RING_INNER || dist > RING_OUTER) { px[idx] = 0; continue }
                    // 径向 → 条带 u（条带两端各留 6% 防边缘噪声）
                    val t = ((dist - RING_INNER) / (RING_OUTER - RING_INNER))
                        .coerceIn(0f, 1f)
                    val sx = (0.06 + 0.88 * t) * (sw - 1).coerceAtLeast(1)
                    val sy = (sh * 0.5).toInt().coerceIn(0, sh - 1)
                    px[idx] = strip?.getPixel(sx.toInt(), sy) ?: 0
                }
            }
            bmp.setPixels(px, 0, RING_SPRITE, 0, 0, RING_SPRITE, RING_SPRITE)
            strip?.recycle()
            ringSprite = bmp
        }
    }

    /**
     * 土星光环半圈绘制：[back]=true 画远侧（行星盘后），false 画近侧（行星盘前）。
     * 倾角 18°、纵横比 0.42 的透视椭圆；scale 由行星屏幕半径换算。
     */
    fun drawSaturnRing(
        n: Canvas,
        cx: Float,
        cy: Float,
        planetRadiusPx: Float,
        alpha: Float,
        back: Boolean
    ): Boolean {
        val sprite = ringSprite ?: return false
        val scale = planetRadiusPx / RING_PLANET_R
        ringPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        n.save()
        n.translate(cx, cy)
        n.rotate(-18f)
        n.scale(1f, 0.42f)
        // 只裁绘对应半圈：远侧为旋转坐标 y<0（上方），近侧为 y>0
        val ext = RING_SPRITE * scale
        if (back) n.clipRect(-ext, -ext, ext, 0f)
        else n.clipRect(-ext, 0f, ext, ext)
        ringRectF.set(-half() * scale, -half() * scale, half() * scale, half() * scale)
        n.drawBitmap(sprite, null, ringRectF, ringPaint)
        n.restore()
        return true
    }

    private fun half(): Float = RING_SPRITE * 0.5f

    /** 纹理与环带是否全部就绪（决定行星走 3D 还是矢量回落）。 */
    fun ready(key: String): Boolean = hasTexture(key) && (key != "SATURN" || ringSprite != null)
}
