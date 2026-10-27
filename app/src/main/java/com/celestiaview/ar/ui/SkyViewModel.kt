package com.celestiaview.ar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.AstrometryEngine.Horizontal
import com.celestiaview.ar.core.math.PlanetaryEphemeris
import com.celestiaview.ar.core.math.Quaternion
import com.celestiaview.ar.core.math.SatelliteTracker
import com.celestiaview.ar.core.media.HapticFeedbackManager
import com.celestiaview.ar.core.media.ProceduralSoundEngine
import com.celestiaview.ar.core.sensor.AttitudeTracker
import com.celestiaview.ar.core.sensor.LocationProvider
import com.celestiaview.ar.core.sensor.OrbitTouchController
import com.celestiaview.ar.data.catalog.BrightStarCatalog
import com.celestiaview.ar.data.catalog.ChineseSkyCatalog
import com.celestiaview.ar.data.catalog.ConstellationCatalog
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.catalog.MessierCatalog
import com.celestiaview.ar.data.catalog.MeteorShower
import com.celestiaview.ar.data.catalog.MeteorShowerCatalog
import com.celestiaview.ar.data.catalog.MilkyWayBandData
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.data.model.ObservingSite

/**
 * 全局状态中枢：60fps 渲染时钟、传感器/手势融合、时间机器、选中与引导、图层开关。
 * Compose 状态在渲染 DrawScope 中直读，实现数据→像素的最短路径。
 */
class SkyViewModel(app: Application) : AndroidViewModel(app) {

    // ------------------------------------------------------------------
    // 引擎
    // ------------------------------------------------------------------
    val locationProvider = LocationProvider(app)
    val attitudeTracker = AttitudeTracker(app)
    val orbit = OrbitTouchController()
    val soundEngine = ProceduralSoundEngine()
    val haptics = HapticFeedbackManager(app)

    // ------------------------------------------------------------------
    // 观测地点
    // ------------------------------------------------------------------
    var site by mutableStateOf(locationProvider.currentSite)
        private set

    // ------------------------------------------------------------------
    // 时间机器
    // ------------------------------------------------------------------
    /** 当前观测时刻（可被时间机器偏移）。 */
    var simTimeMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    /** 时间流速倍率：0=暂停，1=正常。 */
    var timeRate by mutableDoubleStateOf(1.0)
        private set

    /** LIVE：与真实时钟同步。 */
    var isLive by mutableStateOf(true)

    val timeRates: List<Double> = listOf(-3600.0, -300.0, 0.0, 1.0, 60.0, 600.0, 3600.0)

    // ------------------------------------------------------------------
    // 模式
    // ------------------------------------------------------------------
    var arMode by mutableStateOf(false)
    var sensorMode by mutableStateOf(true)
    var redLight by mutableStateOf(false)
    var soundOn by mutableStateOf(false)

    /** 设备是否具备旋转向量传感器；false 时陀螺仪按钮自动回退触摸轨道模式。 */
    var sensorAvailable by mutableStateOf(true)
        private set

    /** AR 相机绑定失败（无相机/被占用/启动异常）→ UI 提示并保持纯星空渲染。 */
    var cameraBindFailed by mutableStateOf(false)

    /** GPS 解算状态镜像（UI 提示“定位中/默认站点”，杜绝静默回退）。 */
    var gpsState by mutableStateOf(LocationProvider.GpsState.OFF)
        private set

    /** AR 实景暗化滤镜浓度 0..1。 */
    var arDarkenAlpha by mutableFloatStateOf(0.60f)

    // ------------------------------------------------------------------
    // 图层开关
    // ------------------------------------------------------------------
    var showGrid by mutableStateOf(true)
    var showEcliptic by mutableStateOf(true)
    var showMilkyWay by mutableStateOf(true)
    var showConstellations by mutableStateOf(true)
    var showMessier by mutableStateOf(true)
    var showTrajectory by mutableStateOf(false) // 临时关闭用于定位 qemu 崩溃源
    var showSatellites by mutableStateOf(true)
    var showMeteorShowers by mutableStateOf(true)

    /** 星图文化体系：现代国际 / 中国古星官 / 双语融合。 */
    var cultureMode by mutableStateOf(CultureMode.IAU)

    // ------------------------------------------------------------------
    // 姿态
    // ------------------------------------------------------------------
    var worldToCamera by mutableStateOf(Quaternion.IDENTITY)
        private set
    var boresight by mutableStateOf(Horizontal(35.0, 180.0))
        private set
    var fovYDeg: Double = 62.0
        get() = if (sensorMode) 62.0 else orbit.fovYDeg

    // ------------------------------------------------------------------
    // 选择 / 引导 / 吸附
    // ------------------------------------------------------------------
    var selected: CelestialTarget? by mutableStateOf(null)
    var guideTarget: CelestialTarget? by mutableStateOf(null)

    data class SnapInfo(
        val target: CelestialTarget,
        val separationDeg: Double,
        val altDeg: Double,
        val azDeg: Double,
        val mag: Double
    )

    var snapped: SnapInfo? by mutableStateOf(null)
        private set

    /** 星座激活权重（准星 25° 内平滑点亮）。 */
    val constellationActivation = mutableStateMapOf<String, Float>()

    // ------------------------------------------------------------------
    // 每帧星历快照
    // ------------------------------------------------------------------
    data class PlanetSnapshot(
        val meta: PlanetaryEphemeris.PlanetMeta,
        val eph: PlanetaryEphemeris.GeoEphemeris,
        val altDeg: Double,
        val azDeg: Double,
        val mag: Double
    )

    data class FrameData(
        val simTimeMillis: Long,
        val jd: Double,
        val lstDeg: Double,
        val latitudeDeg: Double,
        val longitudeEastDeg: Double,
        val obliquityDeg: Double,
        val sunEph: PlanetaryEphemeris.GeoEphemeris,
        val sunAltDeg: Double,
        val sunAzDeg: Double,
        val moonEph: PlanetaryEphemeris.MoonEphemeris,
        val moonAltDeg: Double,
        val moonAzDeg: Double,
        val planets: List<PlanetSnapshot>,
        val satellites: List<SatelliteTracker.SatelliteInfo> = emptyList(),
        val activeShowers: List<Pair<MeteorShower, Double>> = emptyList()
    ) {
        val isNight: Boolean get() = sunAltDeg < -18.0
        val isTwilight: Boolean get() = sunAltDeg >= -18.0 && sunAltDeg <= 0.0
        val isDay: Boolean get() = sunAltDeg > 0.0
    }

    var frame by mutableStateOf(computeFrame(System.currentTimeMillis()))
        private set

    /** 每帧自增，驱动 Canvas 重绘时钟。 */
    var frameTick by mutableLongStateOf(0L)
        private set

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------
    private var lastFrameNanos = 0L
    private var lastSnapCheckMs = 0L
    private var prevSnappedKey: String? = null
    private val constellationCenters: Map<String, AstrometryEngine.RaDec> =
        ConstellationCatalog.constellations.associate {
            it.abbr to ConstellationCatalog.centerRaDec(it)
        }

    /** 供吸附检测的候选（亮星 + 实时行星/月 + 梅西耶）。 */
    private val snapStars = BrightStarCatalog.stars.filter { it.mag <= 2.6 }

    init {
        locationProvider.addCallback(object : LocationProvider.Callback {
            override fun onSiteChanged(site: ObservingSite) {
                this@SkyViewModel.site = site
            }

            override fun onGpsStateChanged(state: LocationProvider.GpsState) {
                this@SkyViewModel.gpsState = state
            }
        })
        attitudeTracker.listener = object : AttitudeTracker.Listener {
            override fun onAttitude(worldToCamera: Quaternion, boresight: Horizontal) {
                if (sensorMode) {
                    this@SkyViewModel.worldToCamera = worldToCamera
                    this@SkyViewModel.boresight = boresight
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /** 传感器注册重试：部分虚拟/融合传感器 HAL 首次 registerListener 会瞬时返回 false。 */
    private val sensorRetryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sensorStartRetries = 0

    private fun tryStartSensors() {
        // 注册返回值不可尽信（部分虚拟传感器 HAL 返回 false 却照常派发事件），
        // 以"是否真的收到事件"为最终判据
        val ok = attitudeTracker.start() || attitudeTracker.hasReceivedEvents
        if (ok) {
            sensorAvailable = true
            sensorStartRetries = 0
            return
        }
        if (sensorStartRetries < 3) {
            sensorStartRetries++
            sensorRetryHandler.postDelayed({ if (sensorMode) tryStartSensors() }, 600L)
            return
        }
        if (attitudeTracker.hasReceivedEvents) {
            // 迟到的首次事件：注册实际成功
            sensorAvailable = true
            sensorStartRetries = 0
            return
        }
        // 确认无陀螺仪/旋转向量传感器 → 无缝降级触摸轨道相机模式
        sensorAvailable = false
        sensorMode = false
        attitudeTracker.stop()
        val b = boresight
        orbit.lookTowards(b.altDeg, b.azDeg)
    }

    fun startEngines() {
        if (sensorMode) {
            tryStartSensors()
        } else if (!sensorAvailable) {
            // 自动降级（非用户手动关闭）后回到前台：重新尝试传感器。
            // 息屏状态下部分传感器 HAL 拒绝注册，亮屏后即可恢复。
            sensorStartRetries = 0
            sensorMode = true
            tryStartSensors()
        }
        if (locationProvider.hasGpsPermission()) locationProvider.startGps()
    }

    fun stopEngines() {
        attitudeTracker.stop()
        locationProvider.stopGps()
    }

    /** 页面不可见时静音（ON_PAUSE 调用），杜绝后台耗电。 */
    fun pauseAudio() {
        if (soundEngine.isRunning) soundEngine.stop()
    }

    /** 回到前台时若用户此前开启了声音则恢复。 */
    fun resumeAudio() {
        if (soundOn && !soundEngine.isRunning) soundEngine.start()
    }

    override fun onCleared() {
        super.onCleared()
        stopEngines()
        soundEngine.stop()
    }

    // ------------------------------------------------------------------
    // 每帧驱动（由 UI withFrameNanos 调用）
    // ------------------------------------------------------------------

    fun onFrame(nanos: Long) {
        val dtSec = if (lastFrameNanos == 0L) 0.016
        else ((nanos - lastFrameNanos) / 1e9).coerceIn(0.001, 0.2)
        lastFrameNanos = nanos

        if (isLive && timeRate == 1.0) {
            simTimeMillis = System.currentTimeMillis()
        } else {
            simTimeMillis += (dtSec * 1000.0 * timeRate).toLong()
        }

        orbit.update(dtSec)
        if (!sensorMode) boresight = Horizontal(orbit.centerAltDeg, orbit.centerAzDeg)

        frame = computeFrame(simTimeMillis)

        val now = System.currentTimeMillis()
        if (now - lastSnapCheckMs > 90) {
            lastSnapCheckMs = now
            updateSnap()
            updateConstellationActivation(dtSec * 6.0)
        }

        // 磁偏角随地点更新（低频）
        if (frameTick % 600 == 0L) {
            attitudeTracker.declinationDeg =
                locationProvider.declinationDeg(simTimeMillis)
        }

        frameTick++
    }

    private fun computeFrame(tMillis: Long): FrameData {
        val s = site
        val jd = AstrometryEngine.julianDate(tMillis)
        val t = AstrometryEngine.julianCenturies(jd)
        val lst = AstrometryEngine.lstDeg(jd, s.longitudeEastDeg)
        val lat = s.latitudeDeg

        val sunEph = PlanetaryEphemeris.sunEphemeris(jd)
        val sunH = AstrometryEngine.equatorialToHorizontal(sunEph.raDeg, sunEph.decDeg, lst, lat)

        val moonEph = PlanetaryEphemeris.moonEphemeris(jd)
        val moonH = AstrometryEngine.equatorialToHorizontal(moonEph.raDeg, moonEph.decDeg, lst, lat)

        val planets = PlanetaryEphemeris.planetMeta
            .filter { it.id != PlanetaryEphemeris.PlanetId.EARTH }
            .map { meta ->
                val eph = PlanetaryEphemeris.planetEphemeris(meta.id, jd)
                val h = AstrometryEngine.equatorialToHorizontal(eph.raDeg, eph.decDeg, lst, lat)
                PlanetSnapshot(meta, eph, h.altDeg, h.azDeg,
                    PlanetaryEphemeris.apparentMagnitude(meta.id, jd))
            }

        return FrameData(
            simTimeMillis = tMillis, jd = jd, lstDeg = lst,
            latitudeDeg = lat, longitudeEastDeg = s.longitudeEastDeg,
            obliquityDeg = AstrometryEngine.obliquityDeg(t),
            sunEph = sunEph, sunAltDeg = sunH.altDeg, sunAzDeg = sunH.azDeg,
            moonEph = moonEph, moonAltDeg = moonH.altDeg, moonAzDeg = moonH.azDeg,
            planets = planets,
            satellites = if (showSatellites) {
                SatelliteTracker.all.map {
                    SatelliteTracker.observe(it, jd, lat, s.longitudeEastDeg)
                }
            } else emptyList(),
            activeShowers = if (showMeteorShowers) {
                MeteorShowerCatalog.activeShowers(tMillis)
            } else emptyList()
        )
    }

    /** 屏幕中心 3.5° 吸附检测 + 掠星触觉/锁定音。 */
    private fun updateSnap() {
        val f = frame
        val b = boresight
        val bEq = AstrometryEngine.equatorialFromHorizontalSafe(
            b.altDeg, b.azDeg, f.lstDeg, f.latitudeDeg
        )
        var best: SnapInfo? = null
        var bestSep = SNAP_THRESHOLD_DEG

        fun consider(target: CelestialTarget, raDeg: Double, decDeg: Double, mag: Double, altDeg: Double, azDeg: Double) {
            // 地平系角距最稳（极区无病态）
            val sep = AstrometryEngine.angularSeparationDeg(b.azDeg, b.altDeg, azDeg, altDeg)
            if (sep < bestSep) {
                bestSep = sep
                best = SnapInfo(target, sep, altDeg, azDeg, mag)
            }
        }

        for (st in snapStars) {
            val h = AstrometryEngine.equatorialToHorizontal(st.raDeg, st.decDeg, f.lstDeg, f.latitudeDeg)
            consider(CelestialTarget.StarTarget(st), st.raDeg, st.decDeg, st.mag, h.altDeg, h.azDeg)
        }
        for (p in f.planets) {
            consider(
                CelestialTarget.PlanetTarget(p.meta.key, p.meta.nameZh, p.eph.raDeg, p.eph.decDeg),
                p.eph.raDeg, p.eph.decDeg, p.mag, p.altDeg, p.azDeg
            )
        }
        consider(
            CelestialTarget.MoonTarget(f.moonEph.raDeg, f.moonEph.decDeg),
            f.moonEph.raDeg, f.moonEph.decDeg, -12.0, f.moonAltDeg, f.moonAzDeg
        )
        if (showMessier) {
            for (o in MessierCatalog.objects) {
                val h = AstrometryEngine.equatorialToHorizontal(o.raDeg, o.decDeg, f.lstDeg, f.latitudeDeg)
                consider(CelestialTarget.MessierTarget(o), o.raDeg, o.decDeg, o.mag, h.altDeg, h.azDeg)
            }
        }

        val key = best?.let { it.target.displayName }
        if (key != prevSnappedKey) {
            if (best != null) {
                haptics.starGlance()
                if (soundOn) soundEngine.playLockChime()
            }
            prevSnappedKey = key
        }
        snapped = best
    }

    private fun updateConstellationActivation(dtScaled: Double) {
        val b = boresight
        val bRa = AstrometryEngine.equatorialFromHorizontalSafe(b.altDeg, b.azDeg, frame.lstDeg, frame.latitudeDeg)
        for (c in ConstellationCatalog.constellations) {
            val center = constellationCenters[c.abbr] ?: continue
            val sep = AstrometryEngine.angularSeparationDeg(
                bRa.raDeg, bRa.decDeg, center.raDeg, center.decDeg
            )
            val targetW = if (sep < 25.0) 1.0f else 0.0f
            val cur = constellationActivation[c.abbr] ?: 0.0f
            val next = (cur + (targetW - cur) * dtScaled.toFloat()).coerceIn(0f, 1f)
            if (next > 0.005f) constellationActivation[c.abbr] = next
            else if (cur > 0f) constellationActivation[c.abbr] = 0f
        }
    }

    // ------------------------------------------------------------------
    // 用户操作
    // ------------------------------------------------------------------

    fun toggleSensorMode() {
        sensorMode = !sensorMode
        if (sensorMode) {
            sensorStartRetries = 0
            tryStartSensors()
        } else {
            attitudeTracker.stop()
            // 从当前指向无缝接管
            val b = boresight
            orbit.lookTowards(b.altDeg, b.azDeg)
        }
    }

    fun toggleArMode() {
        arMode = !arMode
        if (arMode && !sensorMode) toggleSensorMode()
    }

    fun toggleRedLight() { redLight = !redLight }

    fun toggleSound() {
        soundOn = !soundOn
        if (soundOn) soundEngine.start() else soundEngine.stop()
    }

    fun select(target: CelestialTarget?) {
        selected = target
    }

    fun startGuide(target: CelestialTarget) {
        guideTarget = target
        selected = target
    }

    fun clearGuide() { guideTarget = null }

    fun applyTimeRate(rate: Double) {
        timeRate = rate
        isLive = false
    }

    fun backToLive() {
        timeRate = 1.0
        isLive = true
        simTimeMillis = System.currentTimeMillis()
    }

    /** 时间滚轮拨动：粒度毫秒 × 步数。 */
    fun nudgeTime(granularityMs: Long, steps: Int) {
        isLive = false
        if (timeRate == 0.0 || timeRate == 1.0) timeRate = 1.0
        simTimeMillis += granularityMs * steps
    }

    fun selectSite(site: ObservingSite) {
        locationProvider.selectPreset(site)
    }

    fun manualSite(name: String, lat: Double, lon: Double) {
        locationProvider.selectManual(name, lat, lon)
    }

    // ------------------------------------------------------------------
    // 罗盘方位角微调（M8）
    // ------------------------------------------------------------------

    /** 罗盘磁偏角手动微调（度，-30 ~ +30，步长 0.5）。 */
    var azimuthTrimDeg: Double
        get() = attitudeTracker.azimuthTrimDeg
        set(v) {
            attitudeTracker.azimuthTrimDeg = (v * 2.0).toInt() / 2.0 // 0.5° 步进
        }

    /**
     * 一键对准天体校准：以当前准星方位与天体真方位之差修正 trim。
     * 在户外将设备对准真实的太阳/月球后调用，即可让 AR 画面与真实天体严丝合缝。
     * @return 校准后的 trim 值；天体不可见（高度 < 3°）时返回 null 且不修改。
     */
    fun calibrateAzimuthTo(trueAzDeg: Double): Double? {
        val delta = AstrometryEngine.normalizeDeg180(
            trueAzDeg - boresight.azDeg
        )
        val newTrim = (azimuthTrimDeg + delta).coerceIn(-30.0, 30.0)
        azimuthTrimDeg = newTrim
        return newTrim
    }

    /** 今夜可见行星 + 一等亮星 + 梅西耶推荐（高度 > 15°）。 */
    fun tonightHighlights(): List<com.celestiaview.ar.data.model.TonightEvent> {
        val f = frame
        val out = ArrayList<com.celestiaview.ar.data.model.TonightEvent>()
        for (p in f.planets) {
            if (p.altDeg > 15.0) {
                out.add(
                    com.celestiaview.ar.data.model.TonightEvent(
                        "🪐 ${p.meta.nameZh} ${p.meta.nameEn}",
                        "高度 %.1f° · 方位 %.0f° · 星等 %+.1f".format(p.altDeg, p.azDeg, p.mag),
                        p.eph.raDeg, p.eph.decDeg, p.mag,
                        com.celestiaview.ar.data.model.TonightEvent.Kind.PLANET, p.meta.key
                    )
                )
            }
        }
        for (st in BrightStarCatalog.brightest(20)) {
            val h = AstrometryEngine.equatorialToHorizontal(st.raDeg, st.decDeg, f.lstDeg, f.latitudeDeg)
            if (h.altDeg > 15.0) {
                out.add(
                    com.celestiaview.ar.data.model.TonightEvent(
                        "✦ ${st.nameZh} ${st.nameEn}",
                        "高度 %.1f° · 方位 %.0f° · 星等 %+.2f".format(h.altDeg, h.azDeg, st.mag),
                        st.raDeg, st.decDeg, st.mag,
                        com.celestiaview.ar.data.model.TonightEvent.Kind.STAR, st.nameEn
                    )
                )
            }
        }
        for (o in MessierCatalog.objects) {
            val h = AstrometryEngine.equatorialToHorizontal(o.raDeg, o.decDeg, f.lstDeg, f.latitudeDeg)
            if (h.altDeg > 15.0 && o.mag <= 7.0) {
                out.add(
                    com.celestiaview.ar.data.model.TonightEvent(
                        "🌌 ${o.catalogId} ${o.nameZh}",
                        "高度 %.1f° · 方位 %.0f° · 星等 %.1f".format(h.altDeg, h.azDeg, o.mag),
                        o.raDeg, o.decDeg, o.mag,
                        com.celestiaview.ar.data.model.TonightEvent.Kind.MESSIER, o.catalogId
                    )
                )
            }
        }

        // 空间站可目视过境预报（未来 24h）
        if (showSatellites) {
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            for (pass in SatelliteTracker.tonightVisiblePasses(
                f.simTimeMillis, f.latitudeDeg, f.longitudeEastDeg
            )) {
                out.add(
                    com.celestiaview.ar.data.model.TonightEvent(
                        "🛰 ${pass.elements.nameZh}",
                        "%s 过境 · 最大高度 %.0f° · 亮度约 %+.1f 等".format(
                            fmt.format(java.util.Date(pass.maxMillis)),
                            pass.maxAltDeg,
                            SatelliteTracker.magnitudeEstimate(
                                SatelliteTracker.observe(
                                    pass.elements,
                                    AstrometryEngine.julianDate(pass.maxMillis),
                                    f.latitudeDeg, f.longitudeEastDeg
                                )
                            )
                        ),
                        pass.maxAzDeg, pass.maxAltDeg, -3.0,
                        com.celestiaview.ar.data.model.TonightEvent.Kind.SATELLITE,
                        pass.elements.key
                    )
                )
            }
        }

        // 活跃流星雨
        if (showMeteorShowers) {
            for ((shower, weight) in f.activeShowers) {
                val h = AstrometryEngine.equatorialToHorizontal(
                    shower.radiantRaDeg, shower.radiantDecDeg, f.lstDeg, f.latitudeDeg
                )
                out.add(
                    com.celestiaview.ar.data.model.TonightEvent(
                        "☄ ${shower.nameZh}",
                        "辐射点高度 %.0f° · ZHR %d · 母体 %s".format(
                            h.altDeg.coerceAtLeast(0.0), shower.zhr, shower.parentBody
                        ),
                        shower.radiantRaDeg, shower.radiantDecDeg, 6.0 - weight * 3.0,
                        com.celestiaview.ar.data.model.TonightEvent.Kind.METEOR_SHOWER,
                        shower.key
                    )
                )
            }
        }
        return out.sortedBy { it.mag }
    }

    fun snapshotPlaqueLines(centerTarget: String): SkySnapshotPlaqueData {
        val f = frame
        val s = site
        val utc = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(f.simTimeMillis))
        val local = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }.format(java.util.Date(f.simTimeMillis))
        val lstH = f.lstDeg / 15.0
        val lst = "%02dh %02dm %02ds".format(
            lstH.toInt(), ((lstH * 60) % 60).toInt(), ((lstH * 3600) % 60).toInt()
        )
        return SkySnapshotPlaqueData(s.nameZh, s.latitudeDeg, s.longitudeEastDeg, utc, local, lst, centerTarget)
    }

    data class SkySnapshotPlaqueData(
        val siteName: String,
        val latitudeDeg: Double,
        val longitudeEastDeg: Double,
        val utcTime: String,
        val localTime: String,
        val lst: String,
        val centerTarget: String
    )

    /** 星座故事查询辅助。 */
    fun constellationOf(abbr: String) = ConstellationCatalog.byAbbr(abbr)

    /** 按英文名查星（今夜榜单回链）。 */
    fun starByName(nameEn: String) = BrightStarCatalog.find(nameEn)

    val milkyWay get() = MilkyWayBandData

    companion object {
        const val SNAP_THRESHOLD_DEG = 3.5

        /** instrumented 测试置位：暂停 60fps 帧循环，使 Compose 语义树可进入空闲态。 */
        @Volatile
        var framesSuspended: Boolean = false
    }
}

/** 供吸附检测：地平 → 赤道（极点附近做退化处理）。 */
internal fun AstrometryEngine.equatorialFromHorizontalSafe(
    altDeg: Double, azDeg: Double, lstDeg: Double, latDeg: Double
): AstrometryEngine.RaDec {
    val cosLat = kotlin.math.cos(Math.toRadians(latDeg))
    if (cosLat < 1e-6) {
        // 极点：赤纬 = ±(90-|alt|)，赤经取方位角
        val sgn = if (latDeg >= 0) 1 else -1
        return AstrometryEngine.RaDec(
            AstrometryEngine.normalizeDeg(azDeg - 180.0),
            sgn * (90.0 - kotlin.math.abs(altDeg).coerceAtMost(90.0))
        )
    }
    return horizontalToEquatorial(altDeg, azDeg, lstDeg, latDeg)
}
