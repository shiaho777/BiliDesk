package app.bilidesk.feature.player

import app.bilidesk.core.data.AppFile
import app.bilidesk.core.data.appStorageDir
import app.bilidesk.core.network.JSONObject

/** 记住每个分 P 看到的位置。关掉再打开，从上次停下的地方继续。 */
internal object PlayMemory {
    private const val FileName = "play-memory.json"
    private const val Max = 40

    fun read(bvid: String, cid: Long): Long {
        if (bvid.isBlank() || cid <= 0) return 0
        return load().optLong(key(bvid, cid), 0L)
    }

    fun write(bvid: String, cid: Long, positionMs: Long) {
        if (bvid.isBlank() || cid <= 0 || positionMs < 5_000) return
        val root = load()
        root.put(key(bvid, cid), positionMs)
        val keys = root.keys().asSequence().toList()
        if (keys.size > Max) keys.take(keys.size - Max).forEach { root.remove(it) }
        runCatching { file().writeText(root.toString()) }
    }

    private fun key(bvid: String, cid: Long) = "$bvid:$cid"

    private fun file(): AppFile = AppFile(appStorageDir(), FileName)

    private fun load(): JSONObject {
        val stored = file()
        if (!stored.exists()) return JSONObject()
        return runCatching { JSONObject(stored.readText()) }.getOrDefault(JSONObject())
    }
}
