package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartitionCatalogTest {
    @Test
    fun hasThirtyV2RegionsAndFoodIs1020() {
        assertEquals(30, PartitionCatalog.all.size)
        assertEquals(1020, PartitionCatalog.all.first { it.name == "美食" }.tid)
        assertNull(PartitionCatalog.all.find { it.tid == 211 })
        assertEquals(1005, PartitionCatalog.all.first { it.name == "动画" }.tid)
    }
}
