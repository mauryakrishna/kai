package dev.krishna.kai

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * Phase 0: proves the toolchain, the cable and the device all work.
 * No gate, no service, no logging yet -- just something that runs.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device = Build.MODEL
            .takeIf { it.startsWith(Build.MANUFACTURER, ignoreCase = true) }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"

        val info = """
            Kai is alive.

            $device
            Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

            Stage 0 of 6 complete.
        """.trimIndent()

        setContentView(TextView(this).apply {
            text = info
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        })
    }
}
