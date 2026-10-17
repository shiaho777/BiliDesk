package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchKindTest {
    @Test
    fun mapsEndpoints() {
        assertEquals("video", ContentKind.Video.searchType())
        assertEquals("live_room", ContentKind.Live.searchType())
        assertEquals("media_bangumi", ContentKind.Bangumi.searchType())
        assertTrue(ContentKind.Video.signed())
        assertTrue(ContentKind.Live.signed())
        assertFalse(ContentKind.Bangumi.signed())
        assertNull(ContentKind.Video.searchReferer())
        assertEquals("https://search.bilibili.com/", ContentKind.Bangumi.searchReferer())
        assertEquals(20, ContentKind.Video.pageSize())
        assertEquals(40, ContentKind.Live.pageSize())
        assertEquals(20, ContentKind.Bangumi.pageSize())
    }

    @Test
    fun hasMoreUsesPageSizeNotThisPageCountWhenTotalKnown() {
        assertTrue(searchHasMore(got = 20, page = 1, pageSize = 20, numResults = 50))
        assertFalse(searchHasMore(got = 20, page = 3, pageSize = 20, numResults = 50))
        assertFalse(searchHasMore(got = 0, page = 1, pageSize = 20, numResults = 0))
        assertTrue(searchHasMore(got = 20, page = 1, pageSize = 20, numResults = 0))
        assertFalse(searchHasMore(got = 5, page = 1, pageSize = 20, numResults = 0))
    }

    @Test
    fun emptyCopy() {
        assertEquals("没有找到相关视频", searchEmptyMessage(ContentKind.Video))
        assertEquals("没有找到相关直播间", searchEmptyMessage(ContentKind.Live))
        assertEquals("没有找到相关番剧", searchEmptyMessage(ContentKind.Bangumi))
    }

    @Test
    fun emptySuccessIsNotAnError() {
        assertNull(searchPageError(blocked = false))
        assertEquals("搜索被风控拦截，过一会儿再试", searchPageError(blocked = true))
    }

    @Test
    fun kindSwitchShouldLoadOnlyWhenKeywordPresent() {
        assertFalse(searchKindSwitchShouldLoad(""))
        assertFalse(searchKindSwitchShouldLoad("   "))
        assertTrue(searchKindSwitchShouldLoad("foo"))
    }

    @Test
    fun kindSwitchBlankKeywordAfterSearchReturnsIdleAndBumpsGeneration() {
        val out = searchKindSwitch(keyword = "", loadGen = 4, searched = true)
        assertEquals(5, out.loadGen)
        assertFalse(out.loading)
        assertFalse(out.searched)
    }

    @Test
    fun kindSwitchWithQueryReloadsAndBumpsGeneration() {
        val out = searchKindSwitch(keyword = "foo", loadGen = 2, searched = true)
        assertEquals(3, out.loadGen)
        assertTrue(out.loading)
        assertTrue(out.searched)
    }

    @Test
    fun kindSwitchWithoutPriorSearchStaysIdle() {
        val out = searchKindSwitch(keyword = "foo", loadGen = 0, searched = false)
        assertEquals(1, out.loadGen)
        assertFalse(out.loading)
        assertFalse(out.searched)
    }
}
