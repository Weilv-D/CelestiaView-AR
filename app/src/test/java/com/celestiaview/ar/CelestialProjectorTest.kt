package com.celestiaview.ar

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.CelestialProjector
import com.celestiaview.ar.core.math.Quaternion
import com.celestiaview.ar.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CelestialProjectorTest {

    private fun projector(): CelestialProjector = CelestialProjector().apply {
        width = 1000f
        height = 2000f
        fovYDeg = 90.0
    }

    @Test
    fun straightAheadObjectProjectsToScreenCenter() {
        val p = projector()
        p.setVirtualCamera(centerAltDeg = 45.0, centerAzDeg = 0.0)
        val center = p.project(AstrometryEngine.altAzToEnu(45.0, 0.0))
        assertNotNull(center)
        assertEquals(500f, center!!.x, 0.01f)
        assertEquals(1000f, center.y, 0.01f)
    }

    @Test
    fun objectBehindCameraIsCulled() {
        val p = projector()
        p.setVirtualCamera(45.0, 0.0)
        assertNull(p.project(AstrometryEngine.altAzToEnu(45.0, 180.0)))
        assertNull(p.project(AstrometryEngine.altAzToEnu(-45.0, 180.0)))
    }

    @Test
    fun verticalAngleMapsToFocalLengthPixels() {
        val p = projector()
        p.setVirtualCamera(45.0, 0.0)
        // fovY=90° → f = 1000/tan45° = 1000px。alt=45°-30°=15°（视野中心下方 30°）
        // 屏幕坐标 y 向下增长 → y = 1000 + 1000·tan30° ≈ 1577.35
        val q = p.project(AstrometryEngine.altAzToEnu(15.0, 0.0))
        assertNotNull(q)
        assertEquals(1000f + 577.35f, q!!.y, 1.0f)
        assertEquals(500f, q.x, 1.0f)
        // 视野中心上方 30°（alt=75°）应关于中心对称
        val upper = p.project(AstrometryEngine.altAzToEnu(75.0, 0.0))
        assertEquals(1000f - 577.35f, upper!!.y, 1.0f)
    }

    @Test
    fun horizontalSymmetryAroundCenter() {
        val p = projector()
        p.setVirtualCamera(45.0, 0.0)
        val left = p.project(AstrometryEngine.altAzToEnu(45.0, -20.0))
        val right = p.project(AstrometryEngine.altAzToEnu(45.0, 20.0))
        assertNotNull(left); assertNotNull(right)
        assertEquals(500f - (right!!.x - 500f), left!!.x, 2.0f)
        assertEquals(right.y, left.y, 2.0f)
    }

    @Test
    fun attitudeQuaternionGivesSameResultAsVirtualCamera() {
        val p = projector()
        p.setVirtualCamera(60.0, 120.0)
        val v = AstrometryEngine.altAzToEnu(35.0, 95.0)
        val byOrbit = p.project(v)

        // 用四元数描述同一姿态：世界→相机
        val m = p.cameraRotation
        val q = Quaternion.fromRotationMatrix(m)
        val p2 = projector()
        p2.setAttitude(q)
        val byQuaternion = p2.project(v)

        assertNotNull(byOrbit); assertNotNull(byQuaternion)
        assertEquals(byOrbit!!.x, byQuaternion!!.x, 0.01f)
        assertEquals(byOrbit.y, byQuaternion.y, 0.01f)
    }

    @Test
    fun greatCircleTessellationStaysContinuous() {
        val p = projector()
        p.setVirtualCamera(45.0, 0.0)
        // 沿等高圈 alt=45°，从 az -25° 到 +25° 的大圆弧（水平半视场约 26.6°）
        val a = AstrometryEngine.altAzToEnu(45.0, -25.0)
        val b = AstrometryEngine.altAzToEnu(45.0, 25.0)
        val path = p.projectGreatCircle(a, b, stepDeg = 3.0)
        // 全程可见，无断点
        assertTrue(path.isNotEmpty())
        assertTrue(path.none { CelestialProjector.ScreenPos.isBreakMarker(it) })
        // 相邻投影点的屏幕距离有限（无跨屏跳变）
        var maxJump = 0f
        for (i in 1 until path.size) {
            val dx = path[i].x - path[i - 1].x
            val dy = path[i].y - path[i - 1].y
            maxJump = maxOf(maxJump, kotlin.math.hypot(dx, dy))
        }
        assertTrue("maxJump=$maxJump", maxJump < 120f)
        // 中点在大圆上（非等高圈），会向天极方向隆起 ~3°，落于屏幕中心上方附近
        val mid = path[path.size / 2]
        assertEquals(500f, mid.x, 20f)
        assertTrue("mid.y=${mid.y}", kotlin.math.abs(mid.y - 1000f) < 70f)
    }

    @Test
    fun greatCircleBehindCameraInsertsBreak() {
        val p = projector()
        p.setVirtualCamera(45.0, 0.0)
        // 从正前到正后跨越 180° 的弧线必然产生断点
        val a = AstrometryEngine.altAzToEnu(45.0, 0.0)
        val b = AstrometryEngine.altAzToEnu(45.0, 180.0)
        val path = p.projectGreatCircle(a, b, stepDeg = 3.0)
        assertTrue(path.any { CelestialProjector.ScreenPos.isBreakMarker(it) })
    }

    @Test
    fun circleOnSphereLiesOnConstantAngularRadius() {
        val p = CelestialProjector()
        val ring = p.circleOnSphere(Vec3.UNIT_Z, 30.0, 96)
        assertEquals(96, ring.size)
        for (v in ring) {
            assertEquals(30.0, Vec3.UNIT_Z.angleBetweenDeg(v), 1e-6)
            assertEquals(1.0, v.length(), 1e-9)
        }
    }

    @Test
    fun equatorialProjectionMatchesHorizontal() {
        val p = projector()
        // (ra=0, dec=65, lst=0, lat=35) 恰在上中天：alt=90-|35-65|=60°，az=0°（dec>lat 北侧）
        p.setVirtualCamera(55.0, 355.0)
        val h = AstrometryEngine.equatorialToHorizontal(0.0, 65.0, 0.0, 35.0)
        val a = p.projectAltAz(h.altDeg, h.azDeg)
        val b = p.projectEquatorial(0.0, 65.0, 0.0, 35.0)
        assertNotNull(a); assertNotNull(b)
        assertEquals(a!!.x, b!!.x, 0.02f)
        assertEquals(a.y, b.y, 0.02f)
    }
}
