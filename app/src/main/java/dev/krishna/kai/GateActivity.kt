package dev.krishna.kai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import dev.krishna.kai.data.Truths
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
 * Cheap to obey, expensive to ignore. Never impossible -- friction, not a lock.
 */
class GateActivity : Activity() {

    private val settings by lazy { KaiSettings(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var target: String
    private lateinit var truthView: TextView
    private lateinit var countLine: TextView
    private lateinit var countdown: TextView
    private lateinit var typeField: NoPasteEditText
    private lateinit var typeHint: TextView
    private lateinit var continueButton: Button

    private var timer: CountDownTimer? = null
    private var requiredText: String? = null

    private val escapeHandler = Handler(Looper.getMainLooper())
    private var escapeArmed: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (target.isEmpty()) { finish(); return }

        val heading = TextView(this).apply {
            text = label(target)
            textSize = 22f
            setTextColor(Color.parseColor("#8A99AB"))
            gravity = Gravity.CENTER
        }

        // Your words, not mine. This is the part that is supposed to land.
        truthView = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
            setPadding(0, 56, 0, 40)
        }

        countLine = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        countdown = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        typeHint = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FF9800"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, 16)
        }

        typeField = NoPasteEditText(this).apply {
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val need = requiredText ?: return
                    continueButton.isEnabled = normalise(s?.toString().orEmpty()) == need
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
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
            addView(truthView)
            addView(countLine)
            addView(countdown)
            addView(typeHint)
            addView(typeField)
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

        begin()
    }

    /**
     * The wait and the statement both need the database, so the gate paints
     * first and fills in after. Continue starts disabled, so nothing can be
     * rushed in the gap.
     */
    private fun begin() = scope.launch {
        val db = KaiDatabase.get(this@GateActivity)

        val opens = withContext(Dispatchers.IO) {
            runCatching {
                db.appEvents().countForApp(LocalDate.now().toString(), target)
            }.getOrDefault(1).coerceAtLeast(1)
        }

        val truth = withContext(Dispatchers.IO) {
            runCatching {
                Truths.seedIfEmpty(this@GateActivity)
                db.truths().leastRecentlyShown()?.also {
                    db.truths().markShown(it.id, System.currentTimeMillis())
                }
            }.getOrNull()
        }

        truthView.text = truth?.text ?: "Is this what you want to be doing?"
        countLine.text = "${ordinal(opens)} time today"

        // Past a handful of opens, waiting is no longer enough.
        if (opens >= TYPE_AFTER && truth != null) {
            requiredText = normalise(truth.text)
        }
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
                if (requiredText != null) askForTyping() else allowContinue()
            }
        }.start()
    }

    private fun allowContinue() {
        continueButton.text = "Continue for 5 minutes"
        continueButton.isEnabled = true
    }

    private fun askForTyping() {
        typeHint.text = "Type it out to continue"
        typeHint.visibility = View.VISIBLE
        typeField.visibility = View.VISIBLE
        continueButton.text = "Continue for 5 minutes"
        continueButton.isEnabled = false
    }

    /** Forgiving about spacing and case, strict about the words. */
    private fun normalise(s: String) = s.trim().replace(WHITESPACE, " ").lowercase()

    /**
     * The way out that isn't uninstalling. Hold the dot for five seconds and
     * Kai stands down for an hour. Faint, because an escape you cannot find
     * when you genuinely need the phone is no escape -- but never reflexive.
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

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "gate paused (target=$target)")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "gate stopped (target=$target)")
    }

    private fun goHome() {
        Log.i(TAG, "goHome (target=$target)")
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
        Log.i(TAG, "gate destroyed (target=$target)")
        timer?.cancel()
        scope.cancel()
        escapeArmed?.let { escapeHandler.removeCallbacks(it) }
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        const val TAG = "KaiGate"
        const val EXTRA_PACKAGE = "package"
        /** Opens in a day after which waiting is replaced by typing. */
        const val TYPE_AFTER = 5
        private const val TICK_MILLIS = 200L
        private const val ESCAPE_HOLD_MILLIS = 5_000L
        private const val ESCAPE_SIZE = 220
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private val WHITESPACE = Regex("\\s+")
        private val ESCAPE_IDLE = Color.parseColor("#2E3B4A")
        private val ESCAPE_HELD = Color.parseColor("#FF9800")
    }
}
