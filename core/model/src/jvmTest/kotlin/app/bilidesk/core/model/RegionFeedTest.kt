package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionFeedTest {
    @Test
    fun fullPageAdvancesCursor() {
        val seen = emptySet<String>()
        val out = afterRegionPage(1, (1..20).map { "BV$it" }, seen)
        assertEquals(2, out.nextDisplayId)
        assertFalse(out.noMore)
        assertEquals(20, out.addedBvids.size)
    }

    @Test
    fun shortPageStops() {
        val out = afterRegionPage(3, listOf("BV1"), emptySet())
        assertEquals(4, out.nextDisplayId)
        assertTrue(out.noMore)
    }

    @Test
    fun allDuplicatesStop() {
        val out = afterRegionPage(5, listOf("BV1", "BV2"), setOf("BV1", "BV2"))
        assertEquals(6, out.nextDisplayId)
        assertTrue(out.noMore)
        assertEquals(emptyList(), out.addedBvids)
    }

    @Test
    fun emptyPageKeepsCursor() {
        val out = afterRegionPage(4, emptyList(), emptySet())
        assertEquals(4, out.nextDisplayId)
        assertTrue(out.noMore)
        assertEquals(emptyList(), out.addedBvids)
    }
}
