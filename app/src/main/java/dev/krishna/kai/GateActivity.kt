package dev.krishna.kai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.krishna.kai.data.Escalation
import dev.krishna.kai.data.KaiDatabase
import dev.krishna.kai.data.KaiSettings
import dev.krishna.kai.data.ordinal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.ceil

/**
 * The gate. Stands between you and an app that isn't on the allowlist.
 *
 * Deliberately cheap to obey and expensive to ignore: waiting is free, getting
 * through costs a deliberate wait. It is never impossible -- friction, not a lock.
 */
class GateActivity : Activity() {

    private val settings by lazy { KaiSettings(this) }
    private lateinit var target: String
    private lateinit var continueButton: Button
    private lateinit var countdown: TextView
    private lateinit var prompt: TextView
    private var timer: CountDownTimer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val escapeHandler = Handler(Looper.getMainLooper())
    private var escapeArmed: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (target.isEmpty()) { finish(); return }

        val appName = label(target)

        val heading = TextView(this).apply {
            text = appName
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        // Stage 3 replaces this with your own written truths.
        prompt = TextView(this).apply {
            text = "Is this what you want to be doing?"
            textSize = 18f
            setTextColor(Color.parseColor("#BDBDBD"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 64)
        }

        countdown = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        continueButton = Button(this).apply {
            text = "Wait…"
            isEnabled = false
            setOnClickListener {
                settings.grantAccess(target)
                finish()
            }
        }

        val notNow = Button(this).apply {
            text = "Not now"
            setOnClickListener { goHome() }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(heading)
            addView(prompt)
            addView(countdown)
            addView(continueButton)
            addView(notNow)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1621"))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(column, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(escapeCorner())
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(64 + bars.left, 64 + bars.top, 64 + bars.right, 24 + bars.bottom)
            insets
        }
        setContentView(root)

        startWhenCountKnown()
    }

    /**
     * The wait depends on how often this app has already been opened today, so
     * the count has to be read first. The gate is already on screen by then and
     * Continue starts disabled, so there is no window where it can be rushed.
     */
    private fun startWhenCountKnown() = scope.launch {
        val opens = withContext(Dispatchers.IO) {
            runCatching {
                KaiDatabase.get(this@GateActivity).appEvents()
                    .countForApp(LocalDate.now().toString(), target)
            }.getOrDefault(1).coerceAtLeast(1)
        }
        prompt.text = "${ordinal(opens)} time today"
        startCountdown(Escalation.waitSeconds(opens))
    }

    private fun startCountdown(seconds: Int) {
        countdown.text = seconds.toString()
        timer = object : CountDownTimer(seconds * 1000L, TICK_MILLIS) {
            override fun onTick(remaining: Long) {
                // Whole seconds only -- a flickering decimal is something to watch.
                countdown.text = ceil(remaining / 1000.0).toInt().toString()
            }
            override fun onFinish() {
                countdown.text = ""
                continueButton.text = "Continue for 5 minutes"
                continueButton.isEnabled = true
            }
        }.start()
    }

    /**
     * The way out that isn't uninstalling. Press and hold the dot for five
     * seconds and Kai stands down for an hour.
     *
     * A faint dot rather than an invisible corner: in a moment where the phone
     * genuinely must be used, an escape you cannot find is no escape at all.
     * Faint and slow enough that it never becomes the reflexive way through.
     */
    private fun escapeCorner(): View = TextView(this).apply {
        text = "•"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(ESCAPE_IDLE)
        layoutParams = LinearLayout.LayoutParams(ESCAPE_SIZE, ESCAPE_SIZE)

        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setTextColor(ESCAPE_HELD)
                    text = "hold"
                    val armed = Runnable {
                        settings.pauseFor(KaiSettings.ESCAPE_MILLIS)
                        Toast.makeText(
                            this@GateActivity, "Kai paused for 1 hour", Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    escapeArmed = armed
                    escapeHandler.postDelayed(armed, ESCAPE_HOLD_MILLIS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    escapeArmed?.let { escapeHandler.removeCallbacks(it) }
                    setTextColor(ESCAPE_IDLE)
                    text = "•"
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    /** Back must not simply reveal the app behind the gate. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        scope.cancel()
        escapeArmed?.let { escapeHandler.removeCallbacks(it) }
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        const val EXTRA_PACKAGE = "package"
        private const val TICK_MILLIS = 200L
        private const val ESCAPE_HOLD_MILLIS = 5_000L
        private const val ESCAPE_SIZE = 220
        private val ESCAPE_IDLE = Color.parseColor("#2E3B4A")
        private val ESCAPE_HELD = Color.parseColor("#FF9800")
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
