package com.celestiaview.ar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import com.celestiaview.ar.MainActivity
import com.celestiaview.ar.R
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.data.catalog.BrightStarCatalog
import kotlin.math.abs
import kotlin.math.cos

/**
 * 今夜苍穹桌面小组件：无需打开 App 即时呈现当前月相盘、月龄、
 * 地方恒星时（LST）与今夜推荐观测天体；点击直接唤起 AR 寻星。
 */
class TonightSkyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildViews(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 系统时间显著跳变或手动刷新时重绘
        if (intent.action == android.content.Intent.ACTION_TIME_CHANGED ||
            intent.action == ACTION_REFRESH
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
            onUpdate(context, manager, ids)
        }
    }

    internal fun buildViews(context: Context): RemoteViews {
        val now = System.currentTimeMillis()
        val jd = AstrometryEngine.julianDate(now)
        val moon = PlanetaryEphemeris.moonEphemeris(jd)

        val lstH = AstrometryEngine.lstDeg(jd, siteLon(context)) / 15.0
        val lst = "%02d:%02d".format(lstH.toInt(), ((lstH % 1.0) * 60.0).toInt())
        val moonAgeDays = moon.phaseAngleDeg / 360.0 * 29.530588

        val recommends = recommendText(jd, siteLat(context), siteLon(context))

        val views = RemoteViews(context.packageName, R.layout.widget_tonight_sky).apply {
            setImageViewBitmap(R.id.widget_moon_icon, moonBitmap(moon.phaseAngleDeg, 96))
            setTextViewText(
                R.id.widget_moon_info,
                "月龄 %.1f 天 · %s · 亮度 %d%%".format(
                    moonAgeDays, moon.phaseNameZh, (moon.illuminationFraction * 100).toInt()
                )
            )
            setTextViewText(R.id.widget_lst, "LST $lst · ${moon.phaseNameEn}")
            setTextViewText(R.id.widget_recommend, recommends)
            setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
        }
        return views
    }

    /** 无 App 打开时以北京古观象台为默认展示地点（主界面仍可换址）。 */
    private fun siteLat(context: Context): Double =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_LAT, 39.9372f).toDouble()

    private fun siteLon(context: Context): Double =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_LON, 116.4387f).toDouble()

    /** 今夜推荐：实时行星 + 顶尖亮星（高度 > 15°），取前三。 */
    private fun recommendText(jd: Double, latDeg: Double, lonDeg: Double): String {
        val lst = AstrometryEngine.lstDeg(jd, lonDeg)
        val items = ArrayList<String>(6)
        for (meta in PlanetaryEphemeris.planetMeta) {
            if (meta.id == PlanetaryEphemeris.PlanetId.EARTH) continue
            val e = PlanetaryEphemeris.planetEphemeris(meta.id, jd)
            val h = AstrometryEngine.equatorialToHorizontal(e.raDeg, e.decDeg, lst, latDeg)
            if (h.altDeg > 15.0) items.add("行星 · ${meta.nameZh}")
        }
        for (st in BrightStarCatalog.brightest(8)) {
            val h = AstrometryEngine.equatorialToHorizontal(st.raDeg, st.decDeg, lst, latDeg)
            if (h.altDeg > 15.0) items.add("恒星 · ${st.nameZh}")
        }
        return if (items.isEmpty()) "今夜地平线上暂无亮目标，静待星升"
        else "今夜推荐：${items.take(3).joinToString(" · ")}"
    }

    private fun launchIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_GUIDE, true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_REFRESH = "com.celestiaview.ar.widget.REFRESH"
        const val EXTRA_OPEN_GUIDE = "open_guide"
        const val PREFS = "aetheris_widget"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"

        /**
         * 月相盘位图：晨昏线 = 半圆 + 椭圆（rx = r·|cosΦ|），
         * 与应用内 MoonPhaseRenderer 同一套几何。
         */
        fun moonBitmap(phaseDeg: Double, sizePx: Int): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val r = sizePx / 2f - 3f
            val cx = sizePx / 2f
            val rect = RectF(cx - r, cx - r, cx + r, cx + r)

            val dark = Paint().apply {
                color = 0xFF1B2440.toInt(); isAntiAlias = true
            }
            val lit = Paint().apply {
                color = 0xFFE6ECFA.toInt(); isAntiAlias = true
            }
            canvas.drawCircle(cx, cx, r, dark)

            val phi = Math.toRadians(phaseDeg)
            val rx = (r * abs(cos(phi))).toFloat().coerceAtLeast(0.5f)
            val litOnRight = phaseDeg < 180.0
            val path = Path()
            val term = RectF(cx - rx, cx - r, cx + rx, cx + r)
            if (litOnRight) {
                path.addArc(rect, -90f, 180f)
                // 盈蛾眉(Φ<90)界线凸右；盈凸(Φ>90)界线凸左
                path.arcTo(term, 90f, if (phaseDeg < 90.0) -180f else 180f)
            } else {
                path.addArc(rect, 90f, 180f)
                // 亏凸(Φ<270)界线凸右；亏蛾眉(Φ>270)界线凸左
                path.arcTo(term, 270f, if (phaseDeg > 270.0) -180f else 180f)
            }
            path.close()
            canvas.drawPath(path, lit)

            canvas.drawCircle(
                cx, cx, r - 1f,
                Paint().apply {
                    color = 0x66FFD54F.toInt(); style = Paint.Style.STROKE
                    strokeWidth = 2f; isAntiAlias = true
                }
            )
            return bmp
        }
    }
}
