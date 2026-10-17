package app.bilidesk.core.model

data class LocalDateTimeParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
)

expect fun localDateTimeParts(epochSec: Long): LocalDateTimeParts

expect fun currentHourOfDay(): Int

expect fun epochMs(): Long

expect val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher

internal fun pad2(n: Int): String = n.toString().padStart(2, '0')
