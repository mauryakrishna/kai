package dev.krishna.kai.data

data class AppUsage(
    val packageName: String,
    val opens: Int,
    val millis: Long,
)

data class DaySummary(
    val opens: Int,
    val millis: Long,
    val perApp: List<AppUsage>,
)

/**
 * Turns a day's raw switch events into per-app totals.
 *
 * An event's duration is the gap to the next one; the final event of the day
 * runs until [now], which is what makes the currently-open app count properly.
 */
fun summarise(events: List<AppEvent>, now: Long): DaySummary {
    if (events.isEmpty()) return DaySummary(0, 0L, emptyList())

    val opens = mutableMapOf<String, Int>()
    val millis = mutableMapOf<String, Long>()

    events.forEachIndexed { i, event ->
        val end = events.getOrNull(i + 1)?.startedAt ?: now
        val duration = (end - event.startedAt).coerceAtLeast(0L)
        opens[event.packageName] = (opens[event.packageName] ?: 0) + 1
        millis[event.packageName] = (millis[event.packageName] ?: 0L) + duration
    }

    val perApp = opens.map { (pkg, count) -> AppUsage(pkg, count, millis[pkg] ?: 0L) }
        .sortedByDescending { it.millis }

    return DaySummary(opens = events.size, millis = millis.values.sum(), perApp = perApp)
}

fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
