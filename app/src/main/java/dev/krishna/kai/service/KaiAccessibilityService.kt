package dev.krishna.kai.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.krishna.kai.data.AppEvent
import dev.krishna.kai.data.KaiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Watches which app is in the foreground and records every switch.
 *
 * Stage 1: measurement only. Nothing is blocked. The baseline this collects is
 * what the later escalation stages get tuned against.
 */
class KaiAccessibilityService : AccessibilityService() {

    /** Window state changes fire constantly, including for dialogs inside the
     *  same app. We only care when the foreground *package* actually changes. */
    private var lastPackage: String? = null

    /** onAccessibilityEvent runs on the main thread; DB writes must not. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dao by lazy { KaiDatabase.get(this).appEvents() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED || pkg == lastPackage) return

        lastPackage = pkg
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        scope.launch {
            runCatching { dao.insert(AppEvent(packageName = pkg, startedAt = now, day = today)) }
                .onFailure { Log.w(TAG, "could not record $pkg", it) }
        }
        Log.i(TAG, "foreground -> $pkg")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val TAG = "KaiService"

        /** Chrome that isn't really "an app you opened". Refined from real data. */
        private val IGNORED = setOf(
            "com.android.systemui",
        )
    }
}
