package dev.krishna.kai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.krishna.kai.data.KaiDatabase
import dev.krishna.kai.data.Truth
import dev.krishna.kai.data.Truths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 3.1: the statements themselves.
 *
 * Nothing here is clever. The value is entirely in what you write, so the
 * screen gets out of the way.
 */
class TruthsActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dao by lazy { KaiDatabase.get(this).truths() }
    private lateinit var list: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "What you told yourself"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        val help = TextView(this).apply {
            text = "These appear on the gate, least recently seen first.\n\n" +
                "Vague ones stop working within a week. The ones that keep " +
                "working are specific — a number, a name, a date. Write the " +
                "sentence you would rather not read."
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 0, 0, 32)
        }

        input = EditText(this).apply {
            hint = "Write one, then Add"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val add = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.insert(Truth(text = text)) }
                        input.setText("")
                        reload()
                    }
                }
            }
        }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(help)
            addView(input)
            addView(add)
            addView(list)
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

        scope.launch {
            withContext(Dispatchers.IO) { Truths.seedIfEmpty(this@TruthsActivity) }
            reload()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun reload() {
        val truths = withContext(Dispatchers.IO) { dao.all() }
        list.removeAllViews()
        if (truths.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nothing written yet. The gate will fall back to a plain question."
                textSize = 14f
                setTextColor(Color.parseColor("#9E9E9E"))
            })
            return
        }
        truths.forEach { list.addView(row(it)) }
    }

    private fun row(truth: Truth): LinearLayout {
        val body = TextView(this).apply {
            text = truth.text
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 6f)
        }
        val seen = TextView(this).apply {
            this.text = if (truth.shownCount > 0) "seen ${truth.shownCount}×" else "unseen"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 2f)
        }
        val remove = Button(this).apply {
            this.text = "×"
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
            setOnClickListener {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(truth) }
                    reload()
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
            addView(body)
            addView(seen)
            addView(remove)
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
