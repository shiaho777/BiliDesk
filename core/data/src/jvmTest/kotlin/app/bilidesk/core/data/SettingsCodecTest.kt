package app.bilidesk.core.data

import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.network.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsCodecTest {
    @Test
    fun roundTripsVolumeAndSearchKind() {
        val src = decodeAppSettings(JSONObject()).copy(playerVolume = 0f, lastSearchKind = ContentKind.Bangumi)
        val json = encodeAppSettings(src)
        val out = decodeAppSettings(json)
        assertEquals(0f, out.playerVolume)
        assertEquals(ContentKind.Bangumi, out.lastSearchKind)
    }

    @Test
    fun missingFieldsDefault() {
        val out = decodeAppSettings(JSONObject())
        assertEquals(0.8f, out.playerVolume)
        assertEquals(ContentKind.Video, out.lastSearchKind)
    }

    @Test
    fun clampsVolume() {
        val json = encodeAppSettings(decodeAppSettings(JSONObject()).copy(playerVolume = 3f))
        assertEquals(1f, json.optDouble("playerVolume").toFloat())
    }
}
