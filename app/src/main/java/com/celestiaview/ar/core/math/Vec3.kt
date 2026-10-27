package com.celestiaview.ar.core.math

/**
 * 三维双精度向量。天体测量中所有方向均为单位向量（ENU 或赤道坐标系）。
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun length(): Double = kotlin.math.sqrt(dot(this))

    fun lengthSquared(): Double = dot(this)

    fun normalized(): Vec3 {
        val len = length()
        require(len > 0.0) { "Cannot normalize zero-length vector" }
        return Vec3(x / len, y / len, z / len)
    }

    /** 球面线性插值：沿单位球面从本向量向 [b] 插值 t∈[0,1]，用于大圆弧细分。 */
    fun slerp(b: Vec3, t: Double): Vec3 {
        val cosOmega = kotlin.math.min(1.0, kotlin.math.max(-1.0, dot(b)))
        val omega = kotlin.math.acos(cosOmega)
        if (omega < 1e-10) return this
        val sinOmega = kotlin.math.sin(omega)
        val ta = kotlin.math.sin((1.0 - t) * omega) / sinOmega
        val tb = kotlin.math.sin(t * omega) / sinOmega
        return (this * ta + b * tb).normalized()
    }

    /** 与另一向量的夹角（度）。 */
    fun angleBetweenDeg(o: Vec3): Double {
        val c = kotlin.math.min(1.0, kotlin.math.max(-1.0, dot(o) / (length() * o.length())))
        return kotlin.math.acos(c) * RAD2DEG
    }

    companion object {
        const val RAD2DEG = 180.0 / Math.PI
        const val DEG2RAD = Math.PI / 180.0

        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}
