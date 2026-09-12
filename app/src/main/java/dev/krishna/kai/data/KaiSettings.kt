package dev.krishna.kai.data

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony

/**
 * Kai's configuration.
 *
 * Deliberately SharedPreferences rather than DataStore: the allowlist is read
 * on every single window change, on the main thread, in the hot path of the
 * accessibility service. SharedPreferences is synchronous and memory-cached,
 * which is exactly what that needs; DataStore's Flow API is not.
 */
class KaiSettings(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("kai", Context.MODE_PRIVATE)

    /** Master switch. The gate does nothing at all until this is on. */
    var armed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_ARMED, value).apply()

    /** Packages that never see the gate. */
    var allowlist: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWLIST, null) ?: defaultAllowlist()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWLIST, value).apply()

    /**
     * Escape hatch: while this timestamp is in the future, Kai stands down
     * entirely. Gating the whole phone means a bug can make the device
     * unusable, so there must always be a way out that isn't uninstalling.
     */
    var pausedUntil: Long
        get() = prefs.getLong(KEY_PAUSED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_PAUSED_UNTIL, value).apply()

    val isPaused: Boolean get() = System.currentTimeMillis() < pausedUntil

    fun pauseFor(millis: Long) {
        pausedUntil = System.currentTimeMillis() + millis
    }

    fun resume() {
        pausedUntil = 0L
    }

    /**
     * After passing the gate an app is open for a short while, then gated again.
     * Stored per package with an expiry, so a grant cannot outlive a reboot in
     * any surprising way -- it is just a timestamp comparison.
     */
    fun grantAccess(pkg: String, millis: Long = SESSION_MILLIS) {
        prefs.edit().putLong("grant_$pkg", System.currentTimeMillis() + millis).apply()
    }

    fun hasGrant(pkg: String): Boolean =
        System.currentTimeMillis() < prefs.getLong("grant_$pkg", 0L)

    fun grantRemaining(pkg: String): Long =
        (prefs.getLong("grant_$pkg", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** True when this package should be let straight through. */
    fun isAllowed(pkg: String): Boolean = pkg in allowlist || pkg == app.packageName

    /**
     * Resolved from the system rather than hardcoded, so it stays correct if
     * the default dialer or launcher changes. The launcher especially: without
     * it you could never reach the home screen.
     */
    fun defaultAllowlist(): Set<String> {
        val pm = app.packageManager
        val found = mutableSetOf(app.packageName)

        fun add(intent: Intent) {
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName
                ?.takeIf { it != "android" }
                ?.let { found += it }
        }

        add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        add(Intent(Intent.ACTION_DIAL))
        add(Intent(Settings.ACTION_SETTINGS))
        add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
        add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS))

        Telephony.Sms.getDefaultSmsPackage(app)?.let { found += it }

        // Every launcher, not just the current one -- the fallback launcher
        // takes over if the default crashes, and it must not be gated.
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .forEach { found += it.activityInfo.packageName }

        return found
    }

    companion object {
        /** How long a pass through the gate is good for. */
        const val SESSION_MILLIS = 5 * 60_000L
        /** How long the escape hatch stands Kai down for. */
        const val ESCAPE_MILLIS = 60 * 60_000L

        private const val KEY_ARMED = "armed"
        private const val KEY_ALLOWLIST = "allowlist"
        private const val KEY_PAUSED_UNTIL = "paused_until"
    }
}
