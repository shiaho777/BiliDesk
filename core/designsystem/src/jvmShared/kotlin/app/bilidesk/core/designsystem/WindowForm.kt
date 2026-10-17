package app.bilidesk.core.designsystem

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bilidesk.core.model.ShellLayout

enum class WidthClass { Compact, Medium, Expanded }

data class WindowForm(
    val widthClass: WidthClass,
    val width: Dp,
    val height: Dp,
) {
    /** 手机竖屏、很窄的窗口：底栏。平板和电脑：侧栏。横屏手机高度不够，仍用底栏。 */
    val useRail: Boolean get() = widthClass != WidthClass.Compact && height >= 600.dp

    /** 电脑和横屏大平板：左边视频，右边简介评论。 */
    val splitPlayer: Boolean get() = widthClass == WidthClass.Expanded && height >= 560.dp

    val gridMin: Dp get() = when (widthClass) {
        WidthClass.Compact -> 168.dp
        WidthClass.Medium -> 188.dp
        WidthClass.Expanded -> 220.dp
    }

    companion object {
        fun from(width: Dp, height: Dp): WindowForm {
            val widthClass = when {
                width < 600.dp -> WidthClass.Compact
                width < 840.dp -> WidthClass.Medium
                else -> WidthClass.Expanded
            }
            return WindowForm(widthClass, width, height)
        }
    }
}

val LocalWindowForm = compositionLocalOf { WindowForm.from(411.dp, 891.dp) }

@Composable
fun ProvideWindowForm(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val form = remember(maxWidth, maxHeight) { WindowForm.from(maxWidth, maxHeight) }
        CompositionLocalProvider(LocalWindowForm provides form, content = content)
    }
}

fun ShellLayout.toChrome(form: WindowForm): BiliChrome = when (this) {
    ShellLayout.Bottom -> BiliChrome.Bottom
    ShellLayout.Rail -> BiliChrome.Rail
    ShellLayout.Auto -> if (form.useRail) BiliChrome.Rail else BiliChrome.Bottom
}
