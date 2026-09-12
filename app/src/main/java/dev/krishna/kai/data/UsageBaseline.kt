package dev.krishna.kai.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Opens is null for days we only have aggregated time for. */
data class DayStat(val day: LocalDate, val opens: Int?, val millis: Long)

data class Baseline(
    val days: List<DayStat>,
    /** Opens AND time over the event window only -- both halves same period. */
    val recentApps: List<AppUsage>,
    /** Time only, over the widest span. Opens are not retained that far back. */
    val allTimeApps: List<AppUsage>,
    /** Widest aggregate coverage actually found on this device. */
    val spanFrom: LocalDate?,
    val spanTo: LocalDate?,
    /** Earliest day raw events exist, i.e. how far precise open counts go. */
    val eventsFrom: LocalDate?,
    val totalMillis: Long,
    /** Which bucket size gave the widest span. */
    val widestInterval: String,
) {
    val activeDays get() = days.count { it.millis > 0 }
    val daysWithOpens get() = days.filter { it.opens != null && it.opens > 0 }
    val avgMillis get() = if (activeDays == 0) 0L else days.sumOf { it.millis } / activeDays
    val avgOpens
        get() = daysWithOpens.takeIf { it.isNotEmpty() }
            ?.let { list -> list.sumOf { it.opens ?: 0 } / list.size }
}

/**
 * Reads everything Android still holds about past usage.
 *
 * Two sources with different retention:
 *  - queryEvents: precise open counts, usually only about a week.
 *  - queryUsageStats: time per app in buckets, retained far longer.
 *
 * Both are reporting APIs -- neither can say "an app just came forward", so
 * neither can drive the Gate. That stays the AccessibilityService's job.
 */
object UsageBaseline {

    private const val LOOKBACK_DAYS = 730L

    private val INTERVALS = listOf(
        "yearly" to UsageStatsManager.INTERVAL_YEARLY,
        "monthly" to UsageStatsManager.INTERVAL_MONTHLY,
        "weekly" to UsageStatsManager.INTERVAL_WEEKLY,
        "daily" to UsageStatsManager.INTERVAL_DAILY,
    )

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun read(context: Context): Baseline {
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return empty()

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val start = now - LOOKBACK_DAYS * 24 * 3_600_000L

        // --- precise open counts, for as far back as events survive ---
        val opensPerDay = mutableMapOf<LocalDate, Int>()
        val opensPerApp = mutableMapOf<String, Int>()
        var eventsFrom: LocalDate? = null

        runCatching {
            val events = usm.queryEvents(start, now)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
                val pkg = event.packageName ?: continue
                if (pkg == lastPkg) continue
                lastPkg = pkg

                val day = Instant.ofEpochMilli(event.timeStamp).atZone(zone).toLocalDate()
                opensPerDay[day] = (opensPerDay[day] ?: 0) + 1
                opensPerApp[pkg] = (opensPerApp[pkg] ?: 0) + 1
                if (eventsFrom == null || day < eventsFrom) eventsFrom = day
            }
        }

        // --- per-day time, from daily buckets ---
        val millisPerDay = mutableMapOf<LocalDate, Long>()
        val millisPerAppRecent = mutableMapOf<String, Long>()
        runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now).forEach { s ->
                if (s.totalTimeInForeground <= 0) return@forEach
                val day = Instant.ofEpochMilli(s.firstTimeStamp).atZone(zone).toLocalDate()
                millisPerDay[day] = (millisPerDay[day] ?: 0L) + s.totalTimeInForeground
                // Only days the event window also covers, so opens and time agree.
                val from = eventsFrom
                if (from != null && !day.isBefore(from)) {
                    millisPerAppRecent[s.packageName] =
                        (millisPerAppRecent[s.packageName] ?: 0L) + s.totalTimeInForeground
                }
            }
        }

        // --- per-app totals, from whichever bucket size reaches furthest back ---
        var widestName = "none"
        var widest: List<UsageStats> = emptyList()
        var widestFrom: Long = Long.MAX_VALUE

        INTERVALS.forEach { (name, interval) ->
            val stats = runCatching { usm.queryUsageStats(interval, start, now) }
                .getOrDefault(emptyList())
                .filter { it.totalTimeInForeground > 0 }
            if (stats.isEmpty()) return@forEach
            val earliest = stats.minOf { it.firstTimeStamp }
            if (earliest < widestFrom) {
                widestFrom = earliest
                widest = stats
                widestName = name
            }
        }

        val millisPerApp = mutableMapOf<String, Long>()
        widest.forEach { s ->
            millisPerApp[s.packageName] = (millisPerApp[s.packageName] ?: 0L) + s.totalTimeInForeground
        }

        val days = (millisPerDay.keys + opensPerDay.keys).sorted().map { day ->
            DayStat(day, opensPerDay[day], millisPerDay[day] ?: 0L)
        }

        val recentApps = (millisPerAppRecent.keys + opensPerApp.keys)
            .map { AppUsage(it, opensPerApp[it] ?: 0, millisPerAppRecent[it] ?: 0L) }
            .sortedByDescending { it.millis }

        val allTimeApps = millisPerApp
            .map { (pkg, ms) -> AppUsage(pkg, 0, ms) }
            .sortedByDescending { it.millis }

        val spanFrom = widest.takeIf { it.isNotEmpty() }
            ?.let { Instant.ofEpochMilli(widestFrom).atZone(zone).toLocalDate() }
        val spanTo = widest.takeIf { it.isNotEmpty() }
            ?.let { Instant.ofEpochMilli(it.maxOf { s -> s.lastTimeStamp }).atZone(zone).toLocalDate() }

        return Baseline(
            days = days,
            recentApps = recentApps,
            allTimeApps = allTimeApps,
            spanFrom = spanFrom,
            spanTo = spanTo,
            eventsFrom = eventsFrom,
            totalMillis = millisPerApp.values.sum(),
            widestInterval = widestName,
        )
    }

    private fun empty() = Baseline(emptyList(), emptyList(), emptyList(), null, null, null, 0L, "none")
}
