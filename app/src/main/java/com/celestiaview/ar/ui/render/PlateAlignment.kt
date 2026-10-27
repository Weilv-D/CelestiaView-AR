package com.celestiaview.ar.ui.render

/**
 * 天体摄影贴图的天球几何对齐纯函数：
 * 视差旋转角 / 跨尺度溶解曲线 / 角直径物理缩放。
 * 全部为无状态的 JVM 可测函数，渲染循环逐帧调用零分配。
 */
object PlateAlignment {

    /**
     * 屏幕空间天球北向旋转角（度，顺时针为正，Canvas rotate 语义）。
     * 输入为目标中心投影 (u0, v0) 与天球北极参考点投影 (uN, vN)，屏幕 y 轴向下：
     * 北极参考点在正上方时贴图无需旋转，在正右方时旋转 +90°。
     */
    fun screenRotationDeg(u0: Float, v0: Float, uN: Float, vN: Float): Float =
        Math.toDegrees(
            kotlin.math.atan2(
                (uN - u0).toDouble(),
                (-(vN - v0)).toDouble()
            )
        ).toFloat()

    /** 平滑 S 曲线（Smoothstep）：两端导数为零的自然缓入缓出。 */
    fun smoothstep01(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * 贴图屏幕半径（px）：角直径 ÷ 视场角 × 屏幕短边，严格随变焦物理缩放；
     * 两端做视觉托底钳制——小角径天体（行星/恒星切片）不缩成针尖，
     * 大角径天体（仙女座星系）不撑破画幅。
     */
    fun plateRadiusPx(
        angularDiameterDeg: Double,
        fovDeg: Double,
        minSidePx: Float,
        minPx: Float,
        maxPx: Float
    ): Float {
        if (fovDeg <= 0.0 || angularDiameterDeg <= 0.0) return minPx
        val physical = (angularDiameterDeg / fovDeg * minSidePx).toFloat()
        return physical.coerceIn(minPx, maxPx)
    }
}
