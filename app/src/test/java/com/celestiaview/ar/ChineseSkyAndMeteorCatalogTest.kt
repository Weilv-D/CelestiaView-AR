package com.celestiaview.ar

import com.celestiaview.ar.data.catalog.ChineseSkyCatalog
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.catalog.MeteorShowerCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 东方古天文三垣二十八宿目录与流星雨目录完整性。 */
class ChineseSkyAndMeteorCatalogTest {

    @Test
    fun twentyEightMansionsComplete() {
        assertEquals(28, ChineseSkyCatalog.mansions.size)
        val expected = listOf(
            "角", "亢", "氐", "房", "心", "尾", "箕",
            "斗", "牛", "女", "虚", "危", "室", "壁",
            "奎", "娄", "胃", "昴", "毕", "觜", "参",
            "井", "鬼", "柳", "星", "张", "翼", "轸"
        )
        assertEquals(expected, ChineseSkyCatalog.mansions.map { it.abbr })
    }

    @Test
    fun fourSymbolsHaveSevenMansionsEach() {
        for (symbol in ChineseSkyCatalog.FourSymbol.entries) {
            assertEquals(
                "${symbol.nameZh} 应含七宿",
                7, ChineseSkyCatalog.mansions.count { it.symbol == symbol }
            )
        }
    }

    @Test
    fun threeEnclosuresPresent() {
        assertEquals(
            listOf("紫微垣", "太微垣", "天市垣"),
            ChineseSkyCatalog.enclosures.map { it.nameZh }
        )
        for (enc in ChineseSkyCatalog.enclosures) {
            assertTrue(enc.members.size >= 6)
            assertTrue(enc.edges.isNotEmpty())
        }
    }

    @Test
    fun mansionStarsHaveValidCoordinates() {
        for (m in ChineseSkyCatalog.mansions) {
            assertTrue(m.members.isNotEmpty())
            for (s in m.members) {
                assertTrue(s.raDeg in 0.0..360.0)
                assertTrue(s.decDeg in -90.0..90.0)
            }
        }
    }

    // ---------------- 流星雨 ----------------

    @Test
    fun eightMajorShowersPresent() {
        assertEquals(8, MeteorShowerCatalog.showers.size)
        assertEquals(
            listOf("QUA", "LYR", "ETA", "PER", "ORI", "LEO", "GEM", "URS"),
            MeteorShowerCatalog.showers.map { it.key }
        )
    }

    @Test
    fun radiantsHaveValidCoordinates() {
        for (s in MeteorShowerCatalog.showers) {
            assertTrue(s.radiantRaDeg in 0.0..360.0)
            assertTrue(s.radiantDecDeg in -30.0..80.0)
            assertTrue(s.zhr in 5..200)
        }
    }

    @Test
    fun geminidsActiveOnPeakDay() {
        val gem = MeteorShowerCatalog.byKey["GEM"]!!
        assertTrue(gem.isActiveOn(12, 14))
        assertTrue(gem.isActiveOn(12, 10))
        assertTrue(!gem.isActiveOn(6, 15))
        // 象限仪座跨年活跃期
        val qua = MeteorShowerCatalog.byKey["QUA"]!!
        assertTrue(qua.isActiveOn(1, 3))
        assertTrue(qua.isActiveOn(12, 30))
    }

    @Test
    fun intensityPeaksNearMaximum() {
        val per = MeteorShowerCatalog.byKey["PER"]!!
        val peak = Calendar.getInstance().apply { clear(); set(2026, 7, 12) } // 8月12
        val off = Calendar.getInstance().apply { clear(); set(2026, 7, 24) }  // 8月24
        assertTrue(per.intensityWeight(peak) > per.intensityWeight(off))
        assertTrue(per.daysFromPeak(peak) == 0)
    }

    @Test
    fun cultureModeCycles() {
        assertEquals(CultureMode.CHINESE, CultureMode.IAU.next())
        assertEquals(CultureMode.IAU, CultureMode.DUAL.next())
    }
}
