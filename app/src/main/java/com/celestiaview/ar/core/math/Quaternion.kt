package com.celestiaview.ar.core.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 单位四元数 (w + xi + yj + zk)，双精度。
 * 约定：rotate(v) 将向量从源坐标系旋转到目标坐标系，即 v_dst = q ⊗ v_src ⊗ q*。
 */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    fun norm(): Double = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm()
        require(n > 0.0) { "Cannot normalize zero quaternion" }
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    fun negated(): Quaternion = Quaternion(-w, -x, -y, -z)

    operator fun times(o: Quaternion): Quaternion = Quaternion(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * z + x * o.y - y * o.x + z * o.w
    )

    fun dot(o: Quaternion): Double = w * o.w + x * o.x + y * o.y + z * o.z

    /** 用四元数旋转向量（等价于 q ⊗ v ⊗ q*，但只用叉积实现，无四元数乘法）。 */
    fun rotate(v: Vec3): Vec3 {
        val qv = Vec3(x, y, z)
        val t = qv.cross(v) * 2.0
        return v + t * w + qv.cross(t)
    }

    /** 与目标四元数的夹角（度），用于评估角速度、自适应平滑系数。 */
    fun angleToDeg(o: Quaternion): Double {
        val c = kotlin.math.min(1.0, kotlin.math.max(-1.0, abs(dot(o))))
        return 2.0 * acos(c) * Vec3.RAD2DEG
    }

    /**
     * 半球对齐：若与参考四元数点积为负则将自身取反，
     * 保证后续插值始终走最短球面路径。
     */
    fun alignedHemisphereWith(reference: Quaternion): Quaternion =
        if (dot(reference) < 0.0) negated() else this

    /**
     * 球面线性插值（SLERP）。调用方须保证两四元数已半球对齐。
     */
    fun slerp(to: Quaternion, t: Double): Quaternion {
        var b = to
        var d = kotlin.math.min(1.0, kotlin.math.max(-1.0, dot(b)))
        if (d < 0.0) { // 双保险：走最短路径
            b = b.negated()
            d = -d
        }
        if (d > 0.9995) return nlerp(b, t)
        val theta0 = acos(d)
        val theta = theta0 * t
        val sinTheta0 = kotlin.math.sin(theta0)
        val s0 = kotlin.math.cos(theta) - d * kotlin.math.sin(theta) / sinTheta0
        val s1 = kotlin.math.sin(theta) / sinTheta0
        return Quaternion(
            w * s0 + b.w * s1,
            x * s0 + b.x * s1,
            y * s0 + b.y * s1,
            z * s0 + b.z * s1
        ).normalized()
    }

    /** 归一化线性插值（NLERP），角速度恒定的小角度快速插值。 */
    fun nlerp(to: Quaternion, t: Double): Quaternion {
        val b = if (dot(to) < 0.0) to.negated() else to
        return Quaternion(
            w + (b.w - w) * t,
            x + (b.x - x) * t,
            y + (b.y - y) * t,
            z + (b.z - z) * t
        ).normalized()
    }

    fun toRotationMatrix(): Mat3 {
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val yy = y * y2; val zz = z * z2
        val xy = x * y2; val xz = x * z2; val yz = y * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        return Mat3(
            1.0 - (yy + zz), xy - wz, xz + wy,
            xy + wz, 1.0 - (xx + zz), yz - wx,
            xz - wy, yz + wx, 1.0 - (xx + yy)
        )
    }

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        /** 绕单位轴 [axis] 旋转 [angleRad] 弧度。 */
        fun fromAxisAngle(axis: Vec3, angleRad: Double): Quaternion {
            val a = axis.normalized()
            val h = angleRad / 2.0
            val s = kotlin.math.sin(h)
            return Quaternion(kotlin.math.cos(h), a.x * s, a.y * s, a.z * s)
        }

        /**
         * 从旋转矩阵提取四元数（Shepperd 方法，数值稳定分支选择）。
         * 矩阵约定与 [Mat3] 一致：v' = M · v。
         */
        fun fromRotationMatrix(m: Mat3): Quaternion {
            val tr = m.m00 + m.m11 + m.m22
            val q: Quaternion
            if (tr > 0.0) {
                val s = sqrt(tr + 1.0) * 2.0
                q = Quaternion(
                    0.25 * s,
                    (m.m21 - m.m12) / s,
                    (m.m02 - m.m20) / s,
                    (m.m10 - m.m01) / s
                )
            } else if (m.m00 > m.m11 && m.m00 > m.m22) {
                val s = sqrt(1.0 + m.m00 - m.m11 - m.m22) * 2.0
                q = Quaternion(
                    (m.m21 - m.m12) / s,
                    0.25 * s,
                    (m.m01 + m.m10) / s,
                    (m.m02 + m.m20) / s
                )
            } else if (m.m11 > m.m22) {
                val s = sqrt(1.0 + m.m11 - m.m00 - m.m22) * 2.0
                q = Quaternion(
                    (m.m02 - m.m20) / s,
                    (m.m01 + m.m10) / s,
                    0.25 * s,
                    (m.m12 + m.m21) / s
                )
            } else {
                val s = sqrt(1.0 + m.m22 - m.m00 - m.m11) * 2.0
                q = Quaternion(
                    (m.m10 - m.m01) / s,
                    (m.m02 + m.m20) / s,
                    (m.m12 + m.m21) / s,
                    0.25 * s
                )
            }
            return q.normalized()
        }
    }
}
