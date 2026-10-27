package com.celestiaview.ar.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.Mat3
import com.celestiaview.ar.core.math.Quaternion
import com.celestiaview.ar.core.math.Vec3

/**
 * 姿态解算器：TYPE_ROTATION_VECTOR → 屏幕旋转重映射 → 真北磁偏角校准 →
 * 四元数半球对齐 + 自适应 SLERP 平滑（慢速防抖 / 快速跟手，且随视场耦合：
 * 窄视场长焦下平滑系数自动进入超稳态区间，模拟赤道仪液压阻尼）。
 *
 * 输出世界(ENU 真北)→相机(屏幕) 四元数，直接喂给 [com.celestiaview.ar.core.math.CelestialProjector.setAttitude]。
 */
class AttitudeTracker(context: Context) : SensorEventListener {

    interface Listener {
        /** @param worldToCamera ENU→屏幕 姿态四元数；boresight 为摄像头视线 (alt, az)。 */
        fun onAttitude(worldToCamera: Quaternion, boresight: AstrometryEngine.Horizontal)
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** 由 LocationProvider 更新的当地磁偏角（度，东偏为正）。 */
    @Volatile
    var declinationDeg: Double = 0.0

    /**
     * 罗盘方位角手动微调（度，-30 ~ +30）。
     * 环境磁场干扰导致 AR 天空整体偏转时，由用户滑块或“对准日月”一键校准写入；
     * 正值使渲染天球绕天顶向方位角减小的方向旋转（把天体“拉回”真实方位）。
     */
    @Volatile
    var azimuthTrimDeg: Double = 0.0

    /** 供自适应平滑的角速度阈值。 */
    var fastAngularSpeedDegPerSec: Double = 40.0

    /** 当前垂直视场（度），由渲染层每帧回写；驱动平滑系数的视场耦合降档。 */
    @Volatile
    var viewFovDeg: Double = 62.0

    var listener: Listener? = null

    /** 屏幕旋转角，由 UI 层每帧提供（Surface.Rotation）。 */
    @Volatile
    var displayRotation: Int = Surface.ROTATION_0

    private var qSmoothed: Quaternion = Quaternion.IDENTITY
    private var lastEventTimeNanos: Long = 0L
    private var lastLogMs: Long = 0L
    private var smoothAlpha = SLOW_ALPHA

    private val rMat = FloatArray(9)
    private val rRemapped = FloatArray(9)

    fun start(): Boolean {
        val sensor = rotationSensor ?: return false
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    /**
     * 是否已真实收到过旋转向量事件。部分虚拟传感器 HAL（如模拟器）的
     * registerListener 会返回 false 却照常派发事件，注册成败以此为准。
     */
    val hasReceivedEvents: Boolean
        get() = lastEventTimeNanos != 0L

    fun stop() {
        sensorManager.unregisterListener(this)
        lastEventTimeNanos = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR || listener == null) return

        val dt = if (lastEventTimeNanos == 0L) 0.016
        else (event.timestamp - lastEventTimeNanos) / 1e9
        lastEventTimeNanos = event.timestamp

        // 1) 旋转矢量 → 设备→世界 旋转矩阵（行主序，v_world = R · v_device）
        SensorManager.getRotationMatrixFromVector(rMat, event.values)

        // 2) 屏幕方向重映射
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(rMat, axisX, axisY, rRemapped)) return

        // 3) 磁偏角校准 + 用户方位角微调：绕世界天顶对齐至真北 ENU
        val declCorrect = Quaternion.fromAxisAngle(Vec3.UNIT_Z,
            -Math.toRadians(declinationDeg) + Math.toRadians(azimuthTrimDeg))

        // R2: 屏幕→世界（行主序，v_world = R2 · v_screen）。列 j = 屏幕轴 j 在世界中的方向。
        val screenToWorld = Mat3(
            rRemapped[0].toDouble(), rRemapped[1].toDouble(), rRemapped[2].toDouble(),
            rRemapped[3].toDouble(), rRemapped[4].toDouble(), rRemapped[5].toDouble(),
            rRemapped[6].toDouble(), rRemapped[7].toDouble(), rRemapped[8].toDouble()
        )
        // 校正后的屏幕→世界 = Rz(-decl) · R2
        val corrected = declCorrect.toRotationMatrix() * screenToWorld

        // 4) 世界→屏幕：转置（行=屏幕轴在世界中的表达）；第三行即屏幕 +Z（出屏/向后），
        //    与投影器的 (right, up, backward) 相机基约定一致
        val worldToScreen = corrected.transpose()
        var qRaw = Quaternion.fromRotationMatrix(worldToScreen)

        // 5) 半球对齐 + 自适应 SLERP（平滑系数随视场耦合：长焦窄视场进入超稳态）
        qRaw = qRaw.alignedHemisphereWith(qSmoothed)
        val angularSpeed = if (dt > 0) qRaw.angleToDeg(qSmoothed) / dt else 0.0
        val stable = stableAlphaFor(viewFovDeg)
        smoothAlpha = when {
            angularSpeed > fastAngularSpeedDegPerSec ->
                if (viewFovDeg < STABLE_FOV_DEG) FAST_ALPHA * 0.6 else FAST_ALPHA
            angularSpeed > fastAngularSpeedDegPerSec * 0.35 ->
                (stable * 2.5).coerceAtMost(0.20)
            else -> stable
        }
        qSmoothed = if (dt <= 0) qRaw else qSmoothed.slerp(qRaw, smoothAlpha)

        // 摄像头视线（屏幕 -Z）在世界中的方向
        val forwardWorld = -(worldToScreen.row(2))
        val boresight = AstrometryEngine.enuToAltAz(forwardWorld)

        // 1Hz 姿态遥测（现场诊断）
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastLogMs > 1000) {
            lastLogMs = nowMs
            android.util.Log.d(TAG,
                "boresight az=%.2f alt=%.2f | decl=%.2f trim=%.2f | rotVec=[%.3f %.3f %.3f %.3f]"
                    .format(
                        boresight.azDeg, boresight.altDeg, declinationDeg, azimuthTrimDeg,
                        event.values[0], event.values[1], event.values[2],
                        if (event.values.size > 3) event.values[3] else 0f
                    ))
        }

        listener?.onAttitude(qSmoothed, boresight)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val SLOW_ALPHA = 0.08
        const val FAST_ALPHA = 0.35
        const val TAG = "AttitudeTracker"

        /** 视场低于该值时平滑系数开始进入超稳态降档（度）。 */
        const val STABLE_FOV_DEG = 25.0

        /** 超稳态下限视场与下限系数：≤5° 深空微距时平滑系数压到 0.02。 */
        const val MIN_STABLE_FOV_DEG = 5.0
        const val MIN_STABLE_ALPHA = 0.02

        /**
         * 稳态平滑系数随视场线性降档：62° 广角 0.08（常规防抖），
         * 25°→5° 区间自 0.08 平滑压至 0.02（望远镜赤道仪级液压阻尼，
         * 消除长焦下手部生理微颤的放大抖动）。
         */
        fun stableAlphaFor(fovDeg: Double): Double {
            if (fovDeg >= STABLE_FOV_DEG) return SLOW_ALPHA
            val t = ((STABLE_FOV_DEG - fovDeg) /
                (STABLE_FOV_DEG - MIN_STABLE_FOV_DEG)).coerceIn(0.0, 1.0)
            return SLOW_ALPHA - t * (SLOW_ALPHA - MIN_STABLE_ALPHA)
        }
    }
}
