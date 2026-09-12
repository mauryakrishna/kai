package dev.krishna.kai.data

import android.content.Context

/**
 * Starter statements, so the gate is never blank.
 *
 * These are deliberately generic and deliberately weak. The ones that work are
 * specific -- they carry a number, a name, a date. The editor says so, and
 * these exist to be replaced.
 */
object Truths {

    val STARTERS = listOf(
        "This hour is not coming back.",
        "Nothing on the other side of this is going to change your life.",
        "You already know what you are avoiding.",
    )

    suspend fun seedIfEmpty(context: Context) {
        val dao = KaiDatabase.get(context).truths()
        if (dao.count() == 0) {
            STARTERS.forEach { dao.insert(Truth(text = it)) }
        }
    }
}
