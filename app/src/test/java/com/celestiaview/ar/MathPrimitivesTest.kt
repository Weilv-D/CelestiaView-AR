package com.celestiaview.ar

import com.celestiaview.ar.core.math.Mat3
import com.celestiaview.ar.core.math.Quaternion
import com.celestiaview.ar.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MathPrimitivesTest {

    private fun assertVecEquals(expected: Vec3, actual: Vec3, eps: Double = 1e-9, msg: String = "") {
        assertTrue(
            "$msg expected=$expected actual=$actual",
            abs(expected.x - actual.x) < eps &&
                abs(expected.y - actual.y) < eps &&
                abs(expected.z - actual.z) < eps
        )
    }

    // ---------------- Vec3 ----------------

    @Test
    fun vec3DotAndCross() {
        assertEquals(0.0, Vec3.UNIT_X.dot(Vec3.UNIT_Y), 1e-12)
        assertEquals(1.0, Vec3.UNIT_X.dot(Vec3.UNIT_X), 1e-12)
        assertVecEquals(Vec3.UNIT_Z, Vec3.UNIT_X.cross(Vec3.UNIT_Y))
        assertVecEquals(Vec3(3.0, 6.0, 9.0), Vec3(1.0, 2.0, 3.0) * 3.0)
    }

    @Test
    fun vec3Normalize() {
        val v = Vec3(3.0, 4.0, 0.0)
        assertEquals(5.0, v.length(), 1e-12)
        assertEquals(1.0, v.normalized().length(), 1e-12)
    }

    @Test
    fun vec3SlerpMidpointAt45Degrees() {
        val a = Vec3.UNIT_X
        val b = Vec3.UNIT_Y
        val mid = a.slerp(b, 0.5)
        assertEquals(45.0, a.angleBetweenDeg(mid), 1e-6)
        assertEquals(45.0, b.angleBetweenDeg(mid), 1e-6)
        assertEquals(1.0, mid.length(), 1e-9)
    }

    @Test
    fun vec3AngleBetween() {
        assertEquals(90.0, Vec3.UNIT_X.angleBetweenDeg(Vec3.UNIT_Z), 1e-9)
        assertEquals(0.0, Vec3.UNIT_X.angleBetweenDeg(Vec3(2.0, 0.0, 0.0)), 1e-9)
        assertEquals(180.0, Vec3.UNIT_X.angleBetweenDeg(-Vec3.UNIT_X), 1e-9)
    }

    // ---------------- Mat3 ----------------

    @Test
    fun mat3IdentityTransform() {
        val v = Vec3(1.5, -2.0, 3.25)
        assertVecEquals(v, Mat3.IDENTITY * v)
    }

    @Test
    fun mat3RotationZ() {
        val r = Mat3.rotationZ(Math.PI / 2.0)
        // ENU 中绕天顶 +90°（右手）：东→北
        assertVecEquals(Vec3.UNIT_Y, r * Vec3.UNIT_X, 1e-12)
        assertVecEquals(-Vec3.UNIT_X, r * Vec3.UNIT_Y, 1e-12)
    }

    @Test
    fun mat3OrthonormalInverseIsTranspose() {
        val m = Mat3.rotationZ(0.7) * Mat3.rotationX(-0.4)
        val prod = m * m.transpose()
        val eps = 1e-12
        for (i in 0..2) {
            val row = prod.row(i)
            val expected = if (i == 0) Vec3(1.0, 0.0, 0.0) else if (i == 1) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
            assertVecEquals(expected, row, eps, "row $i of M·Mᵗ")
        }
    }

    @Test
    fun mat3OrthonormalizeRecoversPureRotation() {
        val pure = Mat3.rotationZ(0.3) * Mat3.rotationX(0.9)
        // 注入剪切扰动
        val perturbed = Mat3(
            pure.m00 + 0.02, pure.m01 - 0.01, pure.m02,
            pure.m10 + 0.015, pure.m11, pure.m12 + 0.005,
            pure.m20, pure.m21 + 0.02, pure.m22
        )
        val fixed = perturbed.orthonormalized()
        for (i in 0..2) {
            assertEquals(1.0, fixed.row(i).length(), 1e-9)
        }
        assertEquals(1.0, fixed.determinant(), 1e-9)
    }

    // ---------------- Quaternion ----------------

    @Test
    fun quaternionRotatesVector() {
        val q = Quaternion.fromAxisAngle(Vec3.UNIT_Z, Math.PI / 2.0)
        assertVecEquals(Vec3.UNIT_Y, q.rotate(Vec3.UNIT_X), 1e-12)
        val q2 = Quaternion.fromAxisAngle(Vec3.UNIT_X, Math.PI / 2.0)
        assertVecEquals(Vec3.UNIT_Z, q2.rotate(Vec3.UNIT_Y), 1e-12)
    }

    @Test
    fun quaternionMatrixRoundTrip() {
        val q = Quaternion.fromAxisAngle(Vec3(1.0, 2.0, -0.5).normalized(), 1.234)
        val m = q.toRotationMatrix()
        val back = Quaternion.fromRotationMatrix(m)
        val aligned = back.alignedHemisphereWith(q)
        assertEquals(q.w, aligned.w, 1e-9)
        assertEquals(q.x, aligned.x, 1e-9)
        assertEquals(q.y, aligned.y, 1e-9)
        assertEquals(q.z, aligned.z, 1e-9)
    }

    @Test
    fun quaternionSlerpHalfwayIs45Degrees() {
        val a = Quaternion.IDENTITY
        val b = Quaternion.fromAxisAngle(Vec3.UNIT_Z, Math.PI / 2.0)
        val mid = a.slerp(b, 0.5)
        val expected = Quaternion.fromAxisAngle(Vec3.UNIT_Z, Math.PI / 4.0)
        val aligned = mid.alignedHemisphereWith(expected)
        assertEquals(expected.w, aligned.w, 1e-9)
        assertEquals(expected.x, aligned.x, 1e-9)
        assertEquals(expected.y, aligned.y, 1e-9)
        assertEquals(expected.z, aligned.z, 1e-9)
    }

    @Test
    fun quaternionHemisphereAlignment() {
        val q = Quaternion.fromAxisAngle(Vec3.UNIT_Y, 0.8)
        val negated = q.negated()
        assertTrue(negated.dot(q) < 0.0)
        assertTrue(negated.alignedHemisphereWith(q).dot(q) > 0.0)
    }

    @Test
    fun quaternionAngleTo() {
        val a = Quaternion.IDENTITY
        val b = Quaternion.fromAxisAngle(Vec3.UNIT_X, Math.PI / 6.0) // 30°
        assertEquals(30.0, a.angleToDeg(b), 1e-6)
    }
}
