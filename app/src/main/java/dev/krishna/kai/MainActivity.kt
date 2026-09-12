package dev.krishna.kai

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.krishna.kai.data.AppUsage
import dev.krishna.kai.data.Baseline
import dev.krishna.kai.data.DayStat
import dev.krishna.kai.data.KaiDatabase
import dev.krishna.kai.data.UsageBaseline
import dev.krishna.kai.data.formatDuration
import dev.krishna.kai.data.summarise
import dev.krishna.kai.service.KaiAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Stage 1: the numbers, and nothing else. Nothing is blocked yet.
 *
 * Two sources: Kai's own log (precise, starts when you enabled it) and Android's
 * usage history (approximate, already goes back about a week).
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var todayHeadline: TextView
    private lateinit var todayRows: LinearLayout
    private lateinit var baselineHeadline: TextView
    private lateinit var baselineRows: LinearLayout
    private lateinit var usageAccess: Button

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply { textSize = 15f; setPadding(0, 0, 0, 40) }
        todayHeadline = heading()
        todayRows = rowHolder()
        baselineHeadline = heading()
        baselineRows = rowHolder()

        usageAccess = Button(this).apply {
            text = "Grant usage access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }

        val accessibility = Button(this).apply {
            text = "Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(todayHeadline)
            addView(todayRows)
            addView(baselineHeadline)
            addView(baselineRows)
            addView(usageAccess)
            addView(accessibility)
        }

        val root = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(column)
        }

        // Android 15 forces edge-to-edge at targetSdk 35, so content would draw
        // under the status and navigation bars. Pad by whatever they actually occupy.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(48 + bars.left, 48 + bars.top, 48 + bars.right, 48 + bars.bottom)
            insets
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) "Watching. Nothing is blocked." else
            "Not watching yet — turn Kai on under Accessibility → Installed apps."
        status.setTextColor(if (on) GREEN else AMBER)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refresh() = scope.launch {
        val today = LocalDate.now().toString()
        val hasUsage = UsageBaseline.hasPermission(this@MainActivity)

        val (summary, baseline) = withContext(Dispatchers.IO) {
            val dao = KaiDatabase.get(this@MainActivity).appEvents()
            val s = summarise(dao.forDay(today), System.currentTimeMillis())
            val b = if (hasUsage) UsageBaseline.read(this@MainActivity) else null
            s to b
        }

        // --- Kai's own log, today ---
        todayRows.removeAllViews()
        if (summary.opens == 0) {
            todayHeadline.text = "Kai's log · nothing recorded today yet"
        } else {
            todayHeadline.text = "Kai's log · today · ${summary.opens} opens · ${formatDuration(summary.millis)}"
            summary.perApp.take(TOP_N).forEach { todayRows.addView(usageRow(it)) }
        }

        // --- Android's history ---
        usageAccess.visibility = if (hasUsage) View.GONE else View.VISIBLE
        baselineRows.removeAllViews()
        renderBaseline(baseline, hasUsage)
    }

    private fun renderBaseline(baseline: Baseline?, hasUsage: Boolean) {
        if (!hasUsage) {
            baselineHeadline.text = "Baseline · needs usage access"
            baselineRows.addView(note(
                "Android has been recording this all along. Grant usage access " +
                    "below and your history appears immediately."
            ))
            return
        }
        if (baseline == null || baseline.activeDays == 0) {
            baselineHeadline.text = "Baseline · no history available"
            baselineRows.addView(note("Android returned no usage history."))
            return
        }

        val opens = baseline.avgOpens?.let { "$it opens/day · " } ?: ""
        baselineHeadline.text = "Baseline · ${baseline.activeDays} days · " +
            "$opens${formatDuration(baseline.avgMillis)}/day"

        baselineRows.addView(note(coverage(baseline)))

        val recentFirst = baseline.days.sortedByDescending { it.day }
        recentFirst.take(MAX_DAY_ROWS).forEach { baselineRows.addView(dayRow(it)) }
        if (recentFirst.size > MAX_DAY_ROWS) {
            baselineRows.addView(note("… ${recentFirst.size - MAX_DAY_ROWS} earlier days not shown"))
        }

        baselineRows.addView(note("\nBy app · same window as the opens above" +
            (baseline.eventsFrom?.let { " (since $it)" } ?: "")))
        baseline.recentApps.take(TOP_N).forEach { baselineRows.addView(usageRow(it)) }

        baselineRows.addView(note("\nBy app · all history Android still holds" +
            (baseline.spanFrom?.let { " (since $it)" } ?: "") + " · time only"))
        baseline.allTimeApps.take(TOP_N).forEach { baselineRows.addView(timeOnlyRow(it)) }
    }

    /** Be explicit about what Android actually still holds, rather than implying more. */
    private fun coverage(b: Baseline) = buildString {
        append("Time per app: ")
        append(if (b.spanFrom != null) "${b.spanFrom} → ${b.spanTo}  (${b.widestInterval} buckets)"
               else "none")
        append("\nOpen counts: ")
        append(b.eventsFrom?.let { "from $it" } ?: "none retained")
    }

    // ---- small view builders ----

    private fun heading() = TextView(this).apply {
        textSize = 17f
        setTextColor(Color.WHITE)
        setPadding(0, 24, 0, 24)
    }

    private fun rowHolder() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 24)
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#9E9E9E"))
        setPadding(0, 8, 0, 8)
    }

    private fun cell(text: String, color: Int, weight: Float, align: Int) =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(color)
            gravity = align
            layoutParams = LinearLayout.LayoutParams(0, WRAP, weight)
        }

    private fun row(left: String, mid: String, right: String, leftColor: Int) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
            addView(cell(left, leftColor, 5f, Gravity.START))
            addView(cell(mid, Color.parseColor("#9E9E9E"), 2f, Gravity.END))
            addView(cell(right, Color.WHITE, 2f, Gravity.END))
        }

    /** All-time buckets carry no open counts, so don't imply one. */
    private fun timeOnlyRow(usage: AppUsage) =
        row(label(usage.packageName), "", formatDuration(usage.millis), DIM_WHITE)

    private fun usageRow(usage: AppUsage) =
        row(label(usage.packageName), "${usage.opens}", formatDuration(usage.millis), DIM_WHITE)

    private fun dayRow(stat: DayStat) =
        row(stat.day.format(DAY_FORMAT), stat.opens?.toString() ?: "–",
            formatDuration(stat.millis), DIM_WHITE)

    /** Package name -> app name, falling back to the last segment if not installed. */
    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /**
     * Android writes this setting in either fully-qualified or leading-dot short
     * form, and switches between them across reinstalls. unflattenFromString
     * expands the short form, so compare components rather than strings.
     */
    private fun isServiceEnabled(): Boolean {
        val target = ComponentName(this, KaiAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val TOP_N = 12
        const val MAX_DAY_ROWS = 45
        val GREEN = Color.parseColor("#4CAF50")
        val AMBER = Color.parseColor("#FF9800")
        val DIM_WHITE = Color.parseColor("#E0E0E0")
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    }
}
