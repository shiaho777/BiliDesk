package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiTitleTest {
    @Test
    fun displayTitleJoinsLongTitleWhenPresent() {
        assertEquals("1 某集名", bangumiEpisodeDisplayTitle("1", "某集名"))
        assertEquals("1", bangumiEpisodeDisplayTitle("1", ""))
        assertEquals("1", bangumiEpisodeDisplayTitle("1", "  "))
    }

    @Test
    fun playTitleIncludesSeasonAndDisplay() {
        assertEquals("剧名 - 1 某集名", bangumiPlayTitle("剧名", "1", "某集名"))
        assertEquals("剧名 - 1", bangumiPlayTitle("剧名", "1", ""))
    }
}
