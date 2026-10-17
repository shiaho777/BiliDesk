package app.bilidesk.core.model

import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

actual fun localDateTimeParts(epochSec: Long): LocalDateTimeParts {
    val local = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
    return LocalDateTimeParts(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute)
}

actual fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun epochMs(): Long = System.currentTimeMillis()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
