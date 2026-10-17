package app.bilidesk.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionFeedDecodeTest {
    @Test
    fun code62013IsEmptySuccess() {
        val page = decodeRegionFeed(62013, "暂时没有更多内容了", null)
        assertTrue(page.ok)
        assertTrue(page.ended)
        assertEquals(0, page.items.size)
    }

    @Test
    fun readsCoverAndNestedAuthor() {
        val json = """
            {"archives":[{"bvid":"BV1xx","aid":1,"title":"面","cover":"http://i0.hdslb.com/a.jpg",
              "duration":60,"pubdate":1,"stat":{"view":10,"like":2,"danmaku":3},
              "author":{"mid":9,"name":"厨子"}}]}
        """.trimIndent()
        val page = decodeRegionFeed(0, "ok", JSONObject(json))
        assertTrue(page.ok)
        assertEquals("BV1xx", page.items.single().bvid)
        assertEquals("厨子", page.items.single().author)
        assertEquals(9L, page.items.single().ownerMid)
        assertTrue(page.items.single().cover.contains("a.jpg"))
        assertTrue(page.items.single().cover.startsWith("https://"))
    }

    @Test
    fun otherCodesFail() {
        val page = decodeRegionFeed(-400, "请求错误", null)
        assertTrue(!page.ok)
        assertEquals("请求错误", page.error)
    }
}
