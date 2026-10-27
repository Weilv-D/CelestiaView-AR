package com.celestiaview.ar.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.celestiaview.ar.data.model.ObservingSite
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 观测地点提供者：GPS/网络实时定位 + 全球顶级暗夜保护区预设 + 地磁偏角解算。
 * 定位权限由 UI 层请求；无权限或无信号时回落到预设地点。
 */
class LocationProvider(private val context: Context) {

    /** GPS 解算状态：OFF=未启用/已回退默认站点，SEARCHING=监听中尚无定位，LIVE=已定位。 */
    enum class GpsState { OFF, SEARCHING, LIVE }

    interface Callback {
        fun onSiteChanged(site: ObservingSite)
        /** 默认空实现：仅关心地点变化的旧监听方无需修改。 */
        fun onGpsStateChanged(state: GpsState) {}
    }

    /** 全球观星胜地预设。 */
    val presets: List<ObservingSite> = listOf(
        ObservingSite("青海冷湖天文观测基地", "Lenghu Observatory",
            38.60, 93.45, "海拔 4000 米的柴达木荒漠，全年晴夜超过 300 天，中国新一代大望远镜基地。"),
        ObservingSite("夏威夷莫纳克亚山", "Mauna Kea, Hawaii",
            19.82, -155.47, "4207 米死火山之巅，北半球最佳天文台址，十三座世界级望远镜矗立云端。"),
        ObservingSite("智利阿塔卡马沙漠", "Atacama Desert, Chile",
            -23.02, -67.75, "世界最干燥的沙漠，南天窥镜圣地，ALMA 与 VLT 的家园。"),
        ObservingSite("西藏阿里暗夜公园", "Ali Dark Sky Park, Tibet",
            32.31, 80.02, "喜马拉雅之北的亚洲首席暗夜保护区，星空旅游的圣地。"),
        ObservingSite("北京古观象台", "Ancient Observatory, Beijing",
            39.91, 116.43, "明清皇家天文台，赤道经纬仪与天体仪仍指向四百年前的星空。"),
        ObservingSite("南极点站", "South Pole Station",
            -90.00, 0.00, "地球自转轴南端，星辰永转不落，六个月极夜连续观测。")
    )

    @Volatile
    var currentSite: ObservingSite = presets[4]
        private set

    @Volatile
    var altitudeMeters: Double = 50.0

    /** 当前 GPS 解算状态（UI 据此提示“定位中/默认站点”，杜绝静默回退）。 */
    @Volatile
    var gpsState: GpsState = GpsState.OFF
        private set

    /** 缓存定位最大可信时长：超过则视为陈旧（可能来自其他城市），按无定位处理。 */
    private val MAX_FIX_AGE_MS: Long = 2 * 60 * 60 * 1000L

    private val callbacks = CopyOnWriteArrayList<Callback>()

    private fun notifyGpsState(state: GpsState) {
        gpsState = state
        callbacks.forEach { it.onGpsStateChanged(state) }
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 是否已授予精细/粗略定位权限。 */
    fun hasGpsPermission(): Boolean {
        val fine = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine || coarse
    }

    /** 最近一次 GPS 源定位时刻：新鲜 GPS 定位存在时忽略网络源，杜绝双源站点乒乓。 */
    @Volatile
    private var lastGpsFixMillis = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // GPS 源优先：10 分钟内有 GPS 定位时，网络源（运营商/缓存，可能来自异地）不覆盖
            val fromGps = location.provider == LocationManager.GPS_PROVIDER
            val now = System.currentTimeMillis()
            if (fromGps) {
                lastGpsFixMillis = now
            } else if (now - lastGpsFixMillis < 10 * 60_000L) {
                return
            }
            val name = if (location.latitude < -60) "南极观测点"
            else "GPS 实时定位"
            altitudeMeters = location.altitude.coerceAtLeast(-100.0)
            val site = ObservingSite(
                name, "Live GPS",
                location.latitude, location.longitude,
                "由设备传感器实时解算的观测位置。"
            )
            currentSite = site
            notifyGpsState(GpsState.LIVE)
            callbacks.forEach { it.onSiteChanged(site) }
        }

        override fun onProviderDisabled(provider: String) {
            // 全部提供器被关闭时回到默认站点状态并告知 UI
            val anyEnabled = listOf(
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER
            ).any { locationManager.isProviderEnabled(it) }
            if (!anyEnabled && gpsState != GpsState.OFF) notifyGpsState(GpsState.OFF)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    /** 权限已授予时开始监听（调用方负责检查 ACCESS_FINE_LOCATION）。 */
    @SuppressLint("MissingPermission")
    fun startGps() {
        val providers = listOf(
            LocationManager.GPS_PROVIDER to 2000L,
            LocationManager.NETWORK_PROVIDER to 5000L
        )
        var anyRegistered = false
        for ((provider, minTime) in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                try {
                    locationManager.requestLocationUpdates(
                        provider, minTime, 5f, locationListener, Looper.getMainLooper()
                    )
                    anyRegistered = true
                } catch (_: SecurityException) {
                    // 权限被动态回收时静默忽略
                }
            }
        }
        // 立即取缓存位置：仅采信 2 小时内的新鲜定位，陈旧缓存（可能来自异地）不采用
        try {
            var best: Location? = null
            for (p in providers.map { it.first }) {
                val last = locationManager.getLastKnownLocation(p) ?: continue
                if (best == null || last.time > best!!.time) best = last
            }
            val fix = best
            if (fix != null && System.currentTimeMillis() - fix.time < MAX_FIX_AGE_MS) {
                locationListener.onLocationChanged(fix)
                return
            }
        } catch (_: SecurityException) {
        }
        // 无可用定位：明确进入 SEARCHING（UI 显示“定位中”），天穹继续按默认站点计算
        if (anyRegistered) notifyGpsState(GpsState.SEARCHING) else notifyGpsState(GpsState.OFF)
    }

    fun stopGps() {
        locationManager.removeUpdates(locationListener)
    }

    fun addCallback(cb: Callback) {
        callbacks.add(cb)
        cb.onSiteChanged(currentSite)
    }

    fun removeCallback(cb: Callback) = callbacks.remove(cb)

    /** 手动切换预设地点（同时停止 GPS 覆盖）。 */
    fun selectPreset(site: ObservingSite) {
        stopGps()
        currentSite = site
        altitudeMeters = 50.0
        notifyGpsState(GpsState.OFF)
        callbacks.forEach { it.onSiteChanged(site) }
    }

    fun selectManual(nameZh: String, lat: Double, lonEast: Double) {
        stopGps()
        val site = ObservingSite(nameZh.ifBlank { "手动坐标" }, "Manual",
            lat, lonEast, "手动输入的观测坐标。")
        currentSite = site
        notifyGpsState(GpsState.OFF)
        callbacks.forEach { it.onSiteChanged(site) }
    }

    /** 解算当地地磁偏角（度，东偏为正），供 AttitudeTracker 真北校准。 */
    fun declinationDeg(timeMillis: Long): Double {
        val s = currentSite
        val field = GeomagneticField(
            s.latitudeDeg.toFloat(),
            s.longitudeEastDeg.toFloat(),
            altitudeMeters.toFloat(),
            timeMillis
        )
        return field.declination.toDouble()
    }
}
