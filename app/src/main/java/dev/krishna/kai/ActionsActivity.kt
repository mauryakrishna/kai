package dev.krishna.kai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.krishna.kai.data.Actions
import dev.krishna.kai.data.KaiDatabase
import dev.krishna.kai.data.OffPhoneAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 4.1: the things you'd rather be doing.
 *
 * Each carries the minutes the phone stays shut once you commit to it.
 */
class ActionsActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dao by lazy { KaiDatabase.get(this).actions() }
    private lateinit var list: LinearLayout
    private lateinit var input: EditText
    private lateinit var minutes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Things to do instead"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        val help = TextView(this).apply {
            text = "Offered when you turn an app down. Saying you'll do one shuts " +
                "the gated apps for its minutes.\n\n" +
                "Keep them short and physical. Anything needing planning will lose " +
                "to the urge you're standing in."
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 0, 0, 32)
        }

        input = EditText(this).apply {
            hint = "Something you'd actually do"
            textSize = 16f
        }

        minutes = EditText(this).apply {
            hint = "Minutes"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val add = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val text = input.text.toString().trim()
                val mins = minutes.text.toString().toIntOrNull() ?: 5
                if (text.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.insert(OffPhoneAction(text = text, minutes = mins.coerceIn(1, 180)))
                        }
                        input.setText("")
                        minutes.setText("")
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
            addView(minutes)
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
            withContext(Dispatchers.IO) { Actions.seedIfEmpty(this@ActionsActivity) }
            reload()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun reload() {
        val actions = withContext(Dispatchers.IO) { dao.all() }
        list.removeAllViews()
        if (actions.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nothing here yet. Turning an app down will just send you home."
                textSize = 14f
                setTextColor(Color.parseColor("#9E9E9E"))
            })
            return
        }
        actions.forEach { list.addView(row(it)) }
    }

    private fun row(action: OffPhoneAction): LinearLayout {
        val body = TextView(this).apply {
            text = action.text
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 6f)
        }
        val meta = TextView(this).apply {
            text = "${action.minutes}m" +
                if (action.doneCount > 0) " · ${action.doneCount}×" else ""
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 2f)
        }
        val remove = Button(this).apply {
            this.text = "×"
            setOnClickListener {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(action) }
                    reload()
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
            addView(body)
            addView(meta)
            addView(remove)
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
