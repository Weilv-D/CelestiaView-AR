package com.celestiaview.ar

import com.celestiaview.ar.data.catalog.BrightStarCatalog
import com.celestiaview.ar.data.catalog.ConstellationCatalog
import com.celestiaview.ar.data.catalog.MessierCatalog
import com.celestiaview.ar.data.catalog.MilkyWayBandData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIntegrityTest {

    // ------------------------------------------------------------------
    // 亮星星表
    // ------------------------------------------------------------------

    @Test
    fun starIdsAreSequentialAndUnique() {
        assertEquals(
            (1..BrightStarCatalog.stars.size).toList(),
            BrightStarCatalog.stars.map { it.id }
        )
    }

    @Test
    fun starNamesAreUnique() {
        val names = BrightStarCatalog.stars.map { it.nameEn.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun starCoordinatesAreValid() {
        for (s in BrightStarCatalog.stars) {
            assertTrue("${s.nameEn} ra=${s.raHours}", s.raHours in 0.0..24.0)
            assertTrue("${s.nameEn} dec=${s.decDeg}", s.decDeg in -90.0..90.0)
            assertTrue("${s.nameEn} mag=${s.mag}", s.mag in -2.0..6.0)
            assertTrue("${s.nameEn} bv=${s.bv}", s.bv in -0.5..2.2)
            assertTrue("${s.nameEn} dist=${s.distLy}", s.distLy > 0.0)
            assertTrue("${s.nameEn} zh empty", s.nameZh.isNotBlank())
        }
    }

    @Test
    fun canonicalNavigationStarsPresent() {
        assertNotNull(BrightStarCatalog.find("Sirius"))
        assertNotNull(BrightStarCatalog.find("Polaris"))
        assertNotNull(BrightStarCatalog.find("Vega"))
        assertNotNull(BrightStarCatalog.find("Betelgeuse"))
        assertNotNull(BrightStarCatalog.find("Rigel"))
        assertNotNull(BrightStarCatalog.find("Altair"))
        assertNotNull(BrightStarCatalog.find("Arcturus"))
        assertNotNull(BrightStarCatalog.find("Canopus"))
        assertNotNull(BrightStarCatalog.find("Achernar"))
        assertNotNull(BrightStarCatalog.find("Fomalhaut"))
        assertNotNull(BrightStarCatalog.find("Deneb"))
        // 北斗七星
        listOf("Dubhe", "Merak", "Phecda", "Megrez", "Alioth", "Mizar", "Alkaid").forEach {
            assertNotNull("Big Dipper star $it missing", BrightStarCatalog.find(it))
        }
        // 猎户腰带三星
        listOf("Alnitak", "Alnilam", "Mintaka").forEach {
            assertNotNull("Orion belt star $it missing", BrightStarCatalog.find(it))
        }
    }

    @Test
    fun siriusDataMatchesReference() {
        val s = BrightStarCatalog.sirius
        assertEquals(6.7525, s.raHours, 1e-4)
        assertEquals(-16.7161, s.decDeg, 1e-4)
        assertEquals(-1.46, s.mag, 1e-2)
        assertEquals(0.00, s.bv, 1e-2)
    }

    @Test
    fun polarisDataMatchesReference() {
        val p = BrightStarCatalog.polaris
        assertEquals(2.5303, p.raHours, 1e-4)
        assertEquals(89.2641, p.decDeg, 1e-4)
    }

    @Test
    fun brightestStarsArePlausible() {
        val top = BrightStarCatalog.brightest(3).map { it.nameEn }
        assertTrue(top.contains("Sirius"))
        assertTrue(top.contains("Canopus"))
    }

    // ------------------------------------------------------------------
    // 星座拓扑
    // ------------------------------------------------------------------

    @Test
    fun allConstellationEdgesReferenceExistingStars() {
        val maxId = BrightStarCatalog.stars.size
        for (c in ConstellationCatalog.constellations) {
            assertTrue("${c.abbr} has no edges", c.edges.isNotEmpty())
            for ((a, b) in c.edges) {
                assertTrue("${c.abbr} edge a=$a out of range", a in 1..maxId)
                assertTrue("${c.abbr} edge b=$b out of range", b in 1..maxId)
            }
            // 成员 id 与边端点一致
            assertEquals(
                c.edges.flatMap { listOf(it.first, it.second) }.distinct().sorted(),
                c.memberStarIds.sorted()
            )
        }
    }

    @Test
    fun zodiacTwelveConstellationsPresent() {
        val zodiac = listOf("ARI", "TAU", "GEM", "CNC", "LEO", "VIR",
            "LIB", "SCO", "SGR", "CAP", "AQR", "PSC")
        for (abbr in zodiac) {
            assertNotNull("zodiac $abbr missing", ConstellationCatalog.byAbbr(abbr))
        }
    }

    @Test
    fun constellationStoriesPresent() {
        for (c in ConstellationCatalog.constellations) {
            assertTrue("${c.abbr} western story too short", c.storyWestern.length >= 12)
            assertTrue("${c.abbr} chinese story too short", c.storyChinese.length >= 12)
            assertTrue("${c.abbr} zh name missing", c.nameZh.isNotBlank())
        }
    }

    @Test
    fun constellationCentersAreValid() {
        for (c in ConstellationCatalog.constellations) {
            val center = ConstellationCatalog.centerRaDec(c)
            assertTrue("${c.abbr} center ra=${center.raDeg}", center.raDeg in 0.0..360.0)
            assertTrue("${c.abbr} center dec=${center.decDeg}", center.decDeg in -90.0..90.0)
        }
    }

    // ------------------------------------------------------------------
    // 梅西耶目录
    // ------------------------------------------------------------------

    @Test
    fun messierIdsUniqueAndCoordinatesValid() {
        val ids = MessierCatalog.objects.map { it.catalogId }
        assertEquals(ids.size, ids.toSet().size)
        for (o in MessierCatalog.objects) {
            assertTrue("${o.catalogId} ra=${o.raHours}", o.raHours in 0.0..24.0)
            assertTrue("${o.catalogId} dec=${o.decDeg}", o.decDeg in -90.0..90.0)
            assertTrue("${o.catalogId} mag=${o.mag}", o.mag in 0.0..13.0)
            assertTrue("${o.catalogId} size=${o.majorAxisArcmin}", o.majorAxisArcmin > 0.0)
        }
    }

    @Test
    fun classicMessierObjectsPresent() {
        listOf("M1", "M8", "M13", "M31", "M42", "M45", "M51", "M57", "M81", "M104").forEach {
            assertNotNull("$it missing", MessierCatalog.find(it))
        }
    }

    @Test
    fun messierTypesCoverMainCategories() {
        val types = MessierCatalog.objects.map { it.type }.toSet()
        assertTrue(types.contains(com.celestiaview.ar.data.model.DsoType.GALAXY))
        assertTrue(types.contains(com.celestiaview.ar.data.model.DsoType.NEBULA))
        assertTrue(types.contains(com.celestiaview.ar.data.model.DsoType.GLOBULAR_CLUSTER))
        assertTrue(types.contains(com.celestiaview.ar.data.model.DsoType.OPEN_CLUSTER))
    }

    // ------------------------------------------------------------------
    // 银河数据
    // ------------------------------------------------------------------

    @Test
    fun milkyWayBandPolygonIsValid() {
        assertTrue(MilkyWayBandData.bandPolygon.size >= 100)
        for (p in MilkyWayBandData.bandPolygon) {
            assertTrue("ra=${p.raDeg}", p.raDeg in 0.0..360.0)
            assertTrue("dec=${p.decDeg}", p.decDeg in -90.0..90.0)
        }
    }

    @Test
    fun galacticCenterPointsToSagittarius() {
        val gc = MilkyWayBandData.galacticCenter
        // 银心位于人马座 RA≈17h45m, Dec≈−28.9°
        assertTrue("gc=${gc.raDeg}", gc.raDeg in 260.0..272.0)
        assertTrue("gc=${gc.decDeg}", gc.decDeg in -31.0..-26.0)
    }
}
