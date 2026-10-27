package com.celestiaview.ar.core.math

/**
 * 3x3 旋转矩阵，行主序存储。约定 v' = M · v。
 * 在姿态管线中：行向量为相机基（右/上/前）在世界 ENU 系中的表达，实现世界→相机变换。
 */
data class Mat3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double
) {

    operator fun times(o: Mat3): Mat3 = Mat3(
        m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
        m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
        m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
        m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
        m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
        m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22
    )

    operator fun times(v: Vec3): Vec3 = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z
    )

    fun transpose(): Mat3 = Mat3(
        m00, m10, m20,
        m01, m11, m21,
        m02, m12, m22
    )

    /** 转置即旋转矩阵的逆。 */
    fun inverse(): Mat3 = transpose()

    fun determinant(): Double =
        m00 * (m11 * m22 - m12 * m21) -
        m01 * (m10 * m22 - m12 * m20) +
        m02 * (m10 * m21 - m11 * m20)

    /**
     * Gram-Schmidt 正交化（以第一行为主导轴），消除传感器数值漂移累积的非正交误差。
     */
    fun orthonormalized(): Mat3 {
        val r0 = Vec3(m00, m01, m02).normalized()
        val r1raw = Vec3(m10, m11, m12)
        val r1 = (r1raw - r0 * r1raw.dot(r0)).normalized()
        val r2 = r0.cross(r1)
        return Mat3(
            r0.x, r0.y, r0.z,
            r1.x, r1.y, r1.z,
            r2.x, r2.y, r2.z
        )
    }

    fun row(i: Int): Vec3 = when (i) {
        0 -> Vec3(m00, m01, m02)
        1 -> Vec3(m10, m11, m12)
        2 -> Vec3(m20, m21, m22)
        else -> throw IndexOutOfBoundsException("row $i")
    }

    companion object {
        val IDENTITY = Mat3(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )

        /** 绕 Z 轴（天顶轴）旋转 [angleRad] 弧度，正角为 ENU 下从北向东。 */
        fun rotationZ(angleRad: Double): Mat3 {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return Mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0)
        }

        /** 绕 X 轴旋转 [angleRad] 弧度（用于黄道→赤道的黄赤交角旋转）。 */
        fun rotationX(angleRad: Double): Mat3 {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return Mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c)
        }

        /** 由三个基向量按行构造矩阵。 */
        fun fromRows(r0: Vec3, r1: Vec3, r2: Vec3): Mat3 =
            Mat3(r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, r2.x, r2.y, r2.z)
    }
}
