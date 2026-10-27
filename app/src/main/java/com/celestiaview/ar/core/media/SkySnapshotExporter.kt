package com.celestiaview.ar.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 星空海报快门：将当前星空 Canvas 位图与观测元数据铭牌合成高清 PNG，
 * 写入系统相册 MediaStore（Pictures/CelestiaView）。
 */
object SkySnapshotExporter {

    data class Plaque(
        val siteName: String,
        val latitudeDeg: Double,
        val longitudeEastDeg: Double,
        val utcTime: String,
        val localTime: String,
        val lst: String,
        val centerTarget: String
    )

    /** 合成海报并保存，返回相册 Uri（失败返回 null）。 */
    fun export(context: Context, skyBitmap: Bitmap, plaque: Plaque): Uri? {
        val poster = compose(skyBitmap, plaque)
        return try {
            save(context, poster)
        } finally {
            poster.recycle()
        }
    }

    // ------------------------------------------------------------------
    // 海报合成
    // ------------------------------------------------------------------

    private fun compose(sky: Bitmap, plaque: Plaque): Bitmap {
        val targetW = maxOf(sky.width, 1080)
        val scale = targetW.toFloat() / sky.width
        val skyH = (sky.height * scale).toInt().coerceAtLeast(600)

        val bg = Bitmap.createScaledBitmap(sky, targetW, skyH, true)
        val plaqueH = (targetW * 0.26f).toInt().coerceAtLeast(220)
        val out = Bitmap.createBitmap(targetW, skyH + plaqueH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 深空铭牌底
        val platePaint = Paint().apply { color = Color.rgb(6, 10, 22) }
        canvas.drawRect(0f, skyH.toFloat(), targetW.toFloat(), (skyH + plaqueH).toFloat(), platePaint)

        // 金色渐变描边线
        val linePaint = Paint().apply {
            color = Color.rgb(255, 213, 79)
            strokeWidth = 4f
        }
        canvas.drawLine(0f, skyH.toFloat(), targetW.toFloat(), skyH.toFloat(), linePaint)
        canvas.drawRect(
            16f, skyH + 20f, targetW - 16f, (skyH + plaqueH - 20f).toFloat(),
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.rgb(255, 213, 79, )
                alpha = 140
            }
        )

        canvas.drawBitmap(bg, 0f, 0f, null)
        bg.recycle()

        val unit = targetW / 1080f
        val titlePaint = Paint().apply {
            color = Color.rgb(255, 224, 130)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 34f * unit
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        val textPaint = Paint().apply {
            color = Color.rgb(196, 210, 240)
            typeface = Typeface.MONOSPACE
            textSize = 22f * unit
            isAntiAlias = true
        }

        var y = skyH + 64f * unit
        canvas.drawText("CELESTIAVIEW AR · 星空铭牌", 48f * unit, y, titlePaint)
        y += 44f * unit

        val lines = listOf(
            "观测地点  ${plaque.siteName}  (${fmt(plaque.latitudeDeg)}°, ${fmt(plaque.longitudeEastDeg)}°)",
            "UTC  ${plaque.utcTime}    本地  ${plaque.localTime}",
            "地方恒星时 LST  ${plaque.lst}",
            "视场中心  ${plaque.centerTarget}"
        )
        for (l in lines) {
            canvas.drawText(l, 48f * unit, y, textPaint)
            y += 32f * unit
        }
        return out
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    // ------------------------------------------------------------------
    // MediaStore 写入
    // ------------------------------------------------------------------

    private fun save(context: Context, bitmap: Bitmap): Uri? {
        val name = "CelestiaView_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/CelestiaView"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            try {
                resolver.openOutputStream(uri)?.use { os ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) return null
                } ?: return null
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            // API 26-28：legacy 插入
            try {
                @Suppress("DEPRECATION")
                val url = MediaStore.Images.Media.insertImage(
                    context.contentResolver, bitmap, name,
                    "CelestiaView AR sky poster"
                )
                url?.let(Uri::parse)
            } catch (e: Exception) {
                null
            }
        }
    }
}
