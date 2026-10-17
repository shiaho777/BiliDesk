package app.bilidesk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerLayersTest {
    @Test
    fun tabStaysOnBase() {
        val (base, overlay) = playerLayers(listOf(Dest.Home))
        assertEquals(Dest.Home, base)
        assertNull(overlay)
    }

    @Test
    fun playSitsOnHome() {
        val play = Dest.Play(bvid = "BV1")
        val (base, overlay) = playerLayers(listOf(Dest.Home, play))
        assertEquals(Dest.Home, base)
        assertEquals(play, overlay)
    }

    @Test
    fun playKeepsSpaceUnderneath() {
        val play = Dest.Play(bvid = "BV2")
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Space(7), play))
        assertEquals(Dest.Space(7), base)
        assertEquals(play, overlay)
    }

    @Test
    fun stackedPlaysKeepNonPlayerBase() {
        val first = Dest.Play(bvid = "BV1")
        val second = Dest.Play(bvid = "BV2")
        val (base, overlay) = playerLayers(listOf(Dest.Home, first, second))
        assertEquals(Dest.Home, base)
        assertEquals(second, overlay)
    }

    @Test
    fun liveAndLocalAreOverlays() {
        val live = Dest.Live(99, "直播")
        val local = Dest.Local("BV9")
        assertEquals(Dest.Home to live, playerLayers(listOf(Dest.Home, live)))
        assertEquals(Dest.Cache to local, playerLayers(listOf(Dest.Cache, local)))
    }

    @Test
    fun tabKeepsChromeUnderPlayerOverlay() {
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Home, base)
        assertEquals(Dest.Play(bvid = "BV1"), overlay)
        assertTrue(base.showsShellChrome())
    }

    @Test
    fun spaceHidesChromeUnderPlayerOverlay() {
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Space(1), Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Space(1), base)
        assertNotNull(overlay)
        assertFalse(base.showsShellChrome())
    }

    @Test
    fun railFollowsBaseNotPlayer() {
        val (base, overlay) = playerLayers(listOf(Dest.Dynamic, Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Dynamic, base)
        assertNotNull(overlay)
        assertEquals(1, railSelection(base))
        assertEquals(0, railSelection(Dest.Home))
        assertEquals(2, railSelection(Dest.Mine))
        assertEquals(3, railSelection(Dest.Settings))
    }

    @Test
    fun overlayLayerGoneWhenIdle() {
        assertFalse(composeOverlayLayer(overlay = null, held = null))
    }

    @Test
    fun overlayLayerPresentForPlayerDest() {
        val play = Dest.Play(bvid = "BV1")
        assertTrue(composeOverlayLayer(overlay = play, held = null))
    }

    @Test
    fun overlayLayerPresentDuringExitHold() {
        val play = Dest.Play(bvid = "BV1")
        assertTrue(composeOverlayLayer(overlay = null, held = play))
    }

    @Test
    fun bangumiSeasonIsNotPlayerOverlay() {
        val season = Dest.Bangumi(8, "剧")
        assertFalse(season.isPlayerLayer())
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Search("咒术"), season))
        assertEquals(season, base)
        assertNull(overlay)
    }
}
