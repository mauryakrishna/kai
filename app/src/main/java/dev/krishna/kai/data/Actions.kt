package dev.krishna.kai.data

import android.content.Context

/**
 * Starter actions, so the redirect is never empty.
 *
 * Short and physical on purpose. The point is to be standing up and away from
 * the phone before the urge returns; anything that needs planning will lose.
 */
object Actions {

    val STARTERS = listOf(
        "Stand up and walk to the balcony" to 5,
        "20 pushups" to 5,
        "Open the book on your desk, read one page" to 10,
        "Write three lines in the journal" to 10,
        "Fill a glass of water and drink it, sitting down" to 5,
    )

    suspend fun seedIfEmpty(context: Context) {
        val dao = KaiDatabase.get(context).actions()
        if (dao.count() == 0) {
            STARTERS.forEach { (text, minutes) ->
                dao.insert(OffPhoneAction(text = text, minutes = minutes))
            }
        }
    }
}
