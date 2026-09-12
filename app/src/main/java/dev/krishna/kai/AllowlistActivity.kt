package dev.krishna.kai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.krishna.kai.data.KaiSettings
import dev.krishna.kai.data.UsageBaseline
import dev.krishna.kai.data.formatDuration

/**
 * Pick what never sees the gate.
 *
 * Ordered by how much the app is actually used, so the decisions that matter
 * are at the top rather than buried alphabetically.
 */
class AllowlistActivity : Activity() {

    private lateinit var settings: KaiSettings
    private val chosen = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KaiSettings(this)
        chosen += settings.allowlist

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        column.addView(TextView(this).apply {
            text = "Allowed apps"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        })
        column.addView(TextView(this).apply {
            text = "Ticked apps open normally. Everything else meets the gate.\n" +
                "Launchers are always allowed — without one you could never reach home."
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 0, 0, 32)
        })

        launchableApps().forEach { (pkg, label, millis) ->
            column.addView(appRow(pkg, label, millis))
        }

        val root = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(column)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(48 + bars.left, 48 + bars.top, 48 + bars.right, 48 + bars.bottom)
            insets
        }
        setContentView(root)
    }

    /** Save on the way out, so there's no "apply" button to forget. */
    override fun onPause() {
        super.onPause()
        settings.allowlist = chosen.toSet()
    }

    private fun appRow(pkg: String, label: String, millis: Long): LinearLayout {
        val locked = pkg in mandatory
        val box = CheckBox(this).apply {
            text = label
            textSize = 16f
            isChecked = chosen.contains(pkg) || locked
            isEnabled = !locked
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 5f)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) chosen += pkg else chosen -= pkg
            }
        }
        if (locked) chosen += pkg

        val time = TextView(this).apply {
            text = if (millis > 0) formatDuration(millis) else ""
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 2f)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            addView(box)
            addView(time)
        }
    }

    /** Launchers and Kai itself cannot be gated, whatever the user ticks. */
    private val mandatory: Set<String> by lazy {
        val pm = packageManager
        val homes = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toMutableSet()
        homes += packageName
        homes
    }

    private fun launchableApps(): List<Triple<String, String, Long>> {
        val pm = packageManager
        val usage = runCatching {
            if (UsageBaseline.hasPermission(this))
                UsageBaseline.read(this).allTimeApps.associate { it.packageName to it.millis }
            else emptyMap()
        }.getOrDefault(emptyMap())

        return pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                Triple(pkg, label, usage[pkg] ?: 0L)
            }
            .sortedWith(compareByDescending<Triple<String, String, Long>> { it.third }
                .thenBy { it.second.lowercase() })
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
