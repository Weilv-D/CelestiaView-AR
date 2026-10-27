package com.celestiaview.ar.ui.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.PI

/** 程序化底片风格（网络实拍图未就绪时的兜底底片）。 */
enum class PlateStyle { GALAXY, NEBULA, OPEN_CLUSTER, GLOBULAR_CLUSTER, PLANETARY_NEBULA, SUPERNOVA_REMNANT, JUPITER, SATURN, MOON, SUN, STAR_FIELD }

/** 一次天体摄影请求：缓存键 + 巡天坐标 + 兜底底片风格 + 实拍源标注 + 真实角直径（度）。 */
data class AstroRequest(
    val key: String,
    val raDeg: Double,
    val decDeg: Double,
    val fovDeg: Double,
    val plateStyle: PlateStyle,
    val sourceLabel: String,
    /** 天体真实角直径（度），驱动贴图随视场的物理缩放；巡天切片即切片视场。 */
    val angularDiameterDeg: Double = 0.0
)

/**
 * 天体摄影加载引擎：
 * - 数据源 A：CDS Aladin hips2fits —— DSS2 帕洛玛彩色巡天底片，全天任意坐标实时切片；
 * - 数据源 B：太阳系行星与经典深空天体的 NASA / Wikimedia 高清摄影精选表；
 * - 先读内置 assets 零延迟首发，再走内存 LruCache → 磁盘缓存 → 联网三级流水线；
 * - 所有实拍 Bitmap 送渲染前经 [applyRadialFeatherMask] 径向羽化 + 死黑转透明，
 *   贴入星空后无方形边框，宛如目镜视野。
 *
 * 纯函数（URL 构造 / 羽化曲线）在 companion，可被 JVM 单元测试直接覆盖。
 */
class AstrophotoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val memoryCache = LruCache<String, Bitmap>(MEMORY_CACHE_BYTES)
    private val diskDir: File = File(appContext.cacheDir, "astrophotos")
    private val inFlight = ConcurrentHashMap<String, Unit>()

    /** 磁盘缓存条目名（键中的非法字符折叠为下划线）。 */
    private fun cacheFileOf(key: String): File =
        File(diskDir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".jpg")

    /**
     * 加载实拍图（内存 → 内置 assets → 磁盘 → 联网），全部失败返回 null 由调用方兜底。
     * 位图羽化在 IO 线程完成后入内存缓存；原始字节仅落磁盘。
     */
    suspend fun load(request: AstroRequest): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(request.key)?.let { return@withContext it }
        // 相同键请求在途：等待其落地后直接复用内存缓存
        if (inFlight.putIfAbsent(request.key, Unit) != null) {
            repeat(15) {
                kotlinx.coroutines.delay(200)
                memoryCache.get(request.key)?.let { return@withContext it }
            }
            return@withContext null
        }
        try {
            val bitmap = decodeFeathered(assetBytes(request.key))
                ?: decodeFeathered(diskBytes(request.key))
                ?: fetchNetwork(request)?.let { raw ->
                    writeDisk(request.key, raw)
                    decodeFeathered(raw)
                }
            bitmap?.also { memoryCache.put(request.key, it) }
        } finally {
            inFlight.remove(request.key)
        }
    }

    private fun assetBytes(key: String): ByteArray? = runCatching {
        appContext.assets.open("astrophotos/$key.jpg").use { it.readBytes() }
    }.getOrNull()

    /**
     * 缩略图快速通道（今夜简报等列表场景）：仅内存 / 内置 assets / 磁盘缓存，
     * 绝不发起网络请求；未命中返回 null 由调用方以矢量符号兜底。
     */
    suspend fun loadCachedThumbnail(key: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(key)?.let { return@withContext it }
        decodeFeathered(assetBytes(key))
            ?: decodeFeathered(diskBytes(key))?.also { memoryCache.put(key, it) }
    }

    private fun diskBytes(key: String): ByteArray? {
        val f = cacheFileOf(key)
        return if (f.exists() && f.length() > 0) runCatching { f.readBytes() }.getOrNull() else null
    }

    private fun writeDisk(key: String, raw: ByteArray) {
        runCatching {
            diskDir.mkdirs()
            cacheFileOf(key).writeBytes(raw)
        }
    }

    private fun fetchNetwork(request: AstroRequest): ByteArray? {
        val url = curatedUrlFor(request.key) ?: hips2fitsUrl(request.raDeg, request.decDeg, request.fovDeg)
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val raw = if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
            conn.disconnect()
            raw?.takeIf { it.size > 512 } // 低于 512 字节的响应按失败处理（错误页/空切片）
        } catch (_: Exception) {
            null
        }
    }

    /** 解码（最长边压到 768px 控制内存）+ 径向羽化；解码失败返回 null。 */
    private fun decodeFeathered(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxSample(bounds.outWidth, bounds.outHeight)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return applyRadialFeatherMask(bmp)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val USER_AGENT = "AetherisSkyApp/2.0"
        private const val MEMORY_CACHE_BYTES = 24 * 1024 * 1024
        const val MAX_DECODE_EDGE = 768
        const val LABEL_DSS2 = "DSS2 Optical Survey · CDS Aladin"

        /** CDS Aladin hips2fits：DSS2 彩色巡天天球切片（全天任意恒星/星团/深空天体）。 */
        fun hips2fitsUrl(raDeg: Double, decDeg: Double, fovDeg: Double): String =
            "https://alasky.cds.unistra.fr/hips-image-services/hips2fits" +
                "?hips=CDS%2FP%2FDSS2%2Fcolor&width=512&height=512&fov=$fovDeg" +
                "&projection=TAN&coordsys=icrs&ra=$raDeg&dec=$decDeg&format=jpg"

        /** 解码采样率：最长边压到 [MAX_DECODE_EDGE] 以内（2 的幂）。 */
        fun maxSample(w: Int, h: Int): Int {
            var sample = 1
            val maxEdge = maxOf(w, h)
            while (maxEdge / (sample * 2) >= MAX_DECODE_EDGE) sample *= 2
            return sample
        }

        /**
         * 无痕星云脱底管线（纯函数可测）：
         * @param dist01 归一化半径 t = dist / maxR ∈ [0,1]（maxR = 内切圆 × 0.82）
         * @param luma01 脱底后的相对亮度 luma / 217
         * @return 0..1 的像素 alpha：亮度律 luma^0.75 × 余弦窗口 × 三次衰减，上限增益 1.5
         */
        fun seamlessAlpha01(dist01: Float, luma01: Float): Float {
            val t = dist01.coerceIn(0f, 1f)
            val window = (0.5f * (1f + cos(PI * t).toFloat()))
            val radialFade = (window * (1f - t * t)).coerceAtLeast(0f)
            val fade15 = radialFade * kotlin.math.sqrt(radialFade)
            val a = Math.pow(luma01.coerceAtLeast(0f).toDouble(), 0.75).toFloat() * fade15 * 1.5f
            return a.coerceIn(0f, 1f)
        }

        /** 环境底色抬黑位：四角采样均值与保底黑位 38 取大。 */
        fun pedestalChannel(sampledAvg: Int): Int = maxOf(sampledAvg, 38)

        /**
         * 数据源 B：行星与经典深空天体高清摄影精选表（NASA / ESA 公共领域，经 Wikimedia）。
         * 键 → (图源 URL, 实拍源标注)。
         */
        private val WM = "https://upload.wikimedia.org/wikipedia/commons"
        val CURATED: Map<String, Pair<String, String>> = mapOf(
            "SUN" to ("$WM/b/b4/The_Sun_by_the_Atmospheric_Imaging_Assembly_of_NASA%27s_Solar_Dynamics_Observatory_-_20100819.jpg" to "NASA · SDO AIA"),
            "MOON" to ("$WM/e/e1/FullMoon2010.jpg" to "Wikimedia Commons · CC BY-SA"),
            "MERCURY" to ("$WM/d/d9/Mercury_in_color_-_Prockter07-edit1.jpg" to "NASA · MESSENGER"),
            "VENUS" to ("$WM/e/e5/Venus-real_color.jpg" to "NASA · Cassini"),
            "MARS" to ("$WM/0/02/OSIRIS_Mars_true_color.jpg" to "ESA · Rosetta OSIRIS"),
            "JUPITER" to ("$WM/2/2b/Jupiter_and_its_shrunken_Great_Red_Spot.jpg" to "NASA / ESA · Hubble WFC3"),
            "SATURN" to ("$WM/c/c7/Saturn_during_Equinox.jpg" to "NASA · Cassini"),
            "M1" to ("$WM/0/00/Crab_Nebula.jpg" to "NASA / ESA · Hubble"),
            "M8" to ("$WM/c/c4/Lagoon_Nebula.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M13" to ("$WM/6/60/Heart_of_M13_Hercules_Globular_Cluster.jpg" to "NASA / ESA · Hubble"),
            "M16" to ("$WM/b/b2/Eagle_nebula_pillars.jpg" to "NASA / ESA · Hubble"),
            "M17" to ("$WM/7/72/Omega_Nebula.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M20" to ("$WM/2/2b/Trifid_Nebula_Messier_20.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M27" to ("$WM/8/85/Dumbbell_Nebula.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M31" to ("$WM/9/98/Andromeda_Galaxy_%28with_h-alpha%29.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M33" to ("$WM/b/b2/Triangulum_Galaxy.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M42" to ("$WM/f/f3/Orion_Nebula_-_Hubble_2006_mosaic_18000.jpg" to "NASA / ESA · Hubble"),
            "M45" to ("$WM/4/4e/Pleiades_large.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M51" to ("$WM/8/87/Whirlpool_Galaxy.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M57" to ("$WM/1/13/M57_The_Ring_Nebula.JPG" to "NASA / ESA · Hubble"),
            "M81" to ("$WM/d/da/M81_Galaxy.jpg" to "Wikimedia Commons · CC BY-SA"),
            "M104" to ("$WM/5/54/Sombrero_Galaxy.jpg" to "NASA / ESA · Hubble")
        )

        fun curatedUrlFor(key: String): String? = CURATED[key]?.first
        fun curatedLabelFor(key: String): String? = CURATED[key]?.second

        /** 内置离线首发贴图键（assets 下 astrophotos 目录，14 张核心天体）。 */
        val BUNDLED_KEYS = setOf(
            "M31", "M42", "M45", "M1", "M13", "M51", "M57",
            "JUPITER", "SATURN", "MARS", "MOON", "SUN", "SIRIUS", "UMA"
        )

        /**
         * 无痕实拍脱底：四角环境底色采样（消除 DSS2 灰蓝底雾）→ 保底黑位 38
         * 压制残余底噪 → maxR 收缩至内切圆 82%（四角必在半径外）→ 余弦窗口 +
         * 三次方衰减 → 预乘 Alpha 输出。星云像长在宇宙深处一样自然浮现，
         * 无任何方形边框或灰底痕迹。
         */
        fun processSeamlessAstrophoto(src: Bitmap): Bitmap {
            val width = src.width
            val height = src.height
            val pixels = IntArray(width * height)
            src.getPixels(pixels, 0, width, 0, 0, width, height)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val maxR = minOf(cx, cy) * 0.82f

            // 步骤 1：四角各 5×5 采样环境底色均值，与保底黑位 38 取大
            var bgR = 0L; var bgG = 0L; var bgB = 0L; var n = 0
            for (cy0 in listOf(0, height - 5)) for (cx0 in listOf(0, width - 5))
                for (dy in 0 until 5) for (dx in 0 until 5) {
                    val c = pixels[(cy0 + dy) * width + (cx0 + dx)]
                    bgR += (c shr 16 and 0xFF); bgG += (c shr 8 and 0xFF); bgB += (c and 0xFF); n++
                }
            val pedR = pedestalChannel((bgR / n).toInt())
            val pedG = pedestalChannel((bgG / n).toInt())
            val pedB = pedestalChannel((bgB / n).toInt())

            // 步骤 2：逐像素脱黑底 + 余弦径向消隐 + 预乘 Alpha
            for (y in 0 until height) for (x in 0 until width) {
                val idx = y * width + x
                val dx = x - cx; val dy = y - cy
                val dist = hypot(dx, dy)
                if (dist >= maxR) { pixels[idx] = 0x00000000; continue }
                val c = pixels[idx]
                val r = ((c shr 16 and 0xFF) - pedR).coerceAtLeast(0)
                val g = ((c shr 8 and 0xFF) - pedG).coerceAtLeast(0)
                val b = ((c and 0xFF) - pedB).coerceAtLeast(0)
                val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 217f
                val t = (dist / maxR).coerceIn(0f, 1f)
                val alpha01 = seamlessAlpha01(t, luma)
                val alpha = (alpha01 * 255f).toInt().coerceIn(0, 255)
                val pr = (r * alpha / 255).coerceIn(0, 255)
                val pg = (g * alpha / 255).coerceIn(0, 255)
                val pb = (b * alpha / 255).coerceIn(0, 255)
                pixels[idx] = (alpha shl 24) or (pr shl 16) or (pg shl 8) or pb
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        /** 兼容入口：程序化底片与实拍统一走无痕脱底管线。 */
        fun applyRadialFeatherMask(src: Bitmap): Bitmap = processSeamlessAstrophoto(src)
    }
}
