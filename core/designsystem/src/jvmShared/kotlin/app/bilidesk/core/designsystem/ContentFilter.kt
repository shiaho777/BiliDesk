package app.bilidesk.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bilidesk.core.model.VideoItem

/**
 * 首页、收藏、历史共用的屏蔽。命中标题或 UP 名的条目不再显示。
 * 多个词用空格、逗号或顿号分开，命中任意一个就隐藏。
 */
object ContentFilter {
    var title by mutableStateOf("")
    var up by mutableStateOf("")

    val active: Boolean get() = titleTerms.isNotEmpty() || upTerms.isNotEmpty()

    private val titleTerms: List<String> get() = split(title)
    private val upTerms: List<String> get() = split(up)

    fun allows(item: VideoItem): Boolean {
        if (!active) return true
        if (titleTerms.any { item.title.contains(it, ignoreCase = true) }) return false
        if (upTerms.any { item.author.contains(it, ignoreCase = true) }) return false
        return true
    }

    fun summary(): String {
        if (!active) return "未屏蔽任何内容"
        val parts = buildList {
            if (titleTerms.isNotEmpty()) add("标题含「${titleTerms.joinToString(" / ")}」")
            if (upTerms.isNotEmpty()) add("UP 含「${upTerms.joinToString(" / ")}」")
        }
        return "已屏蔽 " + parts.joinToString(" · ")
    }

    fun clear() {
        title = ""
        up = ""
    }

    private fun split(raw: String): List<String> =
        raw.split(' ', ',', '，', '、', ';', '；', '\t', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
