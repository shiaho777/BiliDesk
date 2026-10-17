package app.bilidesk.core.data

/**
 * 观看进度上报：起播过 3 秒就报一次，播放中每 15 秒，暂停和离开时补报。
 * 未登录或失败都忽略，不能挡住播放。
 */
object HistorySync {
    private const val INTERVAL_MS = 15_000L
    private const val MIN_SEC = 3L

    private var lastAid = 0L
    private var lastCid = 0L
    private var lastProgress = 0L
    private var lastAt = 0L

    fun reset() {
        lastAid = 0
        lastCid = 0
        lastProgress = 0
        lastAt = 0
    }

    suspend fun report(aid: Long, cid: Long, progressSec: Long, bvid: String?) {
        if (!shouldPeriodic(aid, cid, progressSec)) return
        mark(aid, cid, progressSec)
        send(aid, cid, progressSec, bvid)
    }

    suspend fun reportNow(aid: Long, cid: Long, progressSec: Long, bvid: String?) {
        if (!ready(aid, cid, progressSec)) return
        mark(aid, cid, progressSec)
        send(aid, cid, progressSec, bvid)
    }

    private fun ready(aid: Long, cid: Long, progressSec: Long): Boolean {
        if (aid <= 0 || cid <= 0 || progressSec < MIN_SEC) return false
        return AppGraph.repo.session.value.loggedIn
    }

    private fun shouldPeriodic(aid: Long, cid: Long, progressSec: Long): Boolean {
        if (!ready(aid, cid, progressSec)) return false
        if (aid != lastAid || cid != lastCid) return true
        if (System.currentTimeMillis() - lastAt < INTERVAL_MS) return false
        if (kotlin.math.abs(progressSec - lastProgress) < 2) return false
        return true
    }

    private fun mark(aid: Long, cid: Long, progressSec: Long) {
        lastAid = aid
        lastCid = cid
        lastProgress = progressSec
        lastAt = System.currentTimeMillis()
    }

    private suspend fun send(aid: Long, cid: Long, progressSec: Long, bvid: String?) {
        AppGraph.repo.reportHistory(aid, cid, progressSec)
        if (!bvid.isNullOrBlank()) AppGraph.repo.heartbeat(bvid, cid, progressSec)
    }
}
