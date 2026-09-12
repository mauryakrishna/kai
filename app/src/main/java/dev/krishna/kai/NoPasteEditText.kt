package dev.krishna.kai

import android.content.Context
import android.widget.EditText

/**
 * An EditText you cannot paste into.
 *
 * The point of typing the sentence is the typing. Pasting it would satisfy the
 * check while skipping the entire mechanism, so paste and autofill are refused.
 */
class NoPasteEditText(context: Context) : EditText(context) {

    init {
        isLongClickable = false
        setOnLongClickListener { true }
    }

    override fun onTextContextMenuItem(id: Int): Boolean = when (id) {
        android.R.id.paste, android.R.id.pasteAsPlainText, android.R.id.autofill -> false
        else -> super.onTextContextMenuItem(id)
    }

    override fun isSuggestionsEnabled(): Boolean = false
}
