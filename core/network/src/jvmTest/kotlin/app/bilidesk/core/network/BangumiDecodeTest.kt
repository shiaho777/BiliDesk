package app.bilidesk.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BangumiDecodeTest {
    @Test
    fun dropsPreviewWithoutBvid() {
        val preview = JSONObject("""{"id":1,"title":"预告","long_title":"","bvid":"","cid":0,"duration":60000}""")
        assertNull(parseBangumiEpisode(preview))
    }

    @Test
    fun durationMsBecomesSeconds() {
        val ep = JSONObject("""{"id":2,"title":"1","long_title":"起始","bvid":"BV1","cid":9,"duration":1500}""")
        val parsed = parseBangumiEpisode(ep)!!
        assertEquals(2, parsed.durationSec)
        assertEquals("BV1", parsed.bvid)
        assertEquals(9L, parsed.cid)
    }

    @Test
    fun detailKeepsOnlyPlayableEpisodes() {
        val result = JSONObject("""
            {"title":"剧","cover":"//i0.hdslb.com/c.jpg","evaluate":"简介",
             "episodes":[
               {"id":1,"title":"0","bvid":"","cid":1,"duration":1000},
               {"id":2,"title":"1","long_title":"第一集","bvid":"BV2","cid":2,"duration":1000}
             ]}
        """.trimIndent())
        val detail = parseBangumiDetail(88, result)
        assertEquals(88L, detail.seasonId)
        assertEquals("剧", detail.title)
        assertEquals(1, detail.episodes.size)
        assertEquals("BV2", detail.episodes.single().bvid)
        assertTrue(detail.cover.startsWith("https://"))
    }
}
