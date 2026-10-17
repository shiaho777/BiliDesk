package app.bilidesk.core.network

import app.bilidesk.core.model.ContentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchDecodeTest {
    @Test
    fun nullDataIsEmptySuccess() {
        val page = decodeSearchSuccess(ContentKind.Video, 1, null)
        assertFalse(page.blocked)
        assertEquals(0, page.items.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun parsesBangumiAndStripsHtml() {
        val e = JSONObject("""{"season_id":123,"title":"<em class=\"keyword\">咒术</em>回战","cover":"//i0.hdslb.com/c.jpg","badge":"番剧","index_show":"更新至第2话","ep_size":24}""")
        val item = parseBangumiItem(e)!!
        assertEquals(123L, item.seasonId)
        assertEquals("咒术回战", item.title)
        assertTrue(item.cover.startsWith("https://"))
        assertEquals("更新至第2话", item.indexShow)
    }

    @Test
    fun mergeDedupesBySeasonId() {
        val a = parseBangumiItem(JSONObject("""{"season_id":1,"title":"A","cover":"x"}"""))!!
        val b = parseBangumiItem(JSONObject("""{"season_id":1,"title":"A2","cover":"y"}"""))!!
        val c = parseBangumiItem(JSONObject("""{"season_id":2,"title":"纪录片","cover":"z"}"""))!!
        val merged = mergeBangumi(listOf(a), listOf(b, c))
        assertEquals(listOf(1L, 2L), merged.map { it.seasonId })
    }

    @Test
    fun documentaryOnlyLivesInFt() {
        val ft = parseBangumiItem(JSONObject("""{"season_id":9,"title":"人生一串","cover":"d","badge":"纪录片"}"""))!!
        val page = decodeSearchSuccess(ContentKind.Bangumi, 1, null, extraFt = listOf(ft))
        assertEquals(1, page.bangumiItems.size)
        assertEquals("人生一串", page.bangumiItems.single().title)
        assertFalse(page.hasMore)
    }
}
