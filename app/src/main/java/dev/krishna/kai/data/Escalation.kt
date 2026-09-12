package dev.krishna.kai.data

/**
 * How long the gate makes you wait.
 *
 * The first open of the day is usually a real one, so it stays cheap. Each
 * repeat costs more. Reaching for the same app for the tenth time is the
 * behaviour worth taxing, not opening it at all.
 */
object Escalation {

    const val BASE_SECONDS = 8
    const val STEP_SECONDS = 7
    const val MAX_SECONDS = 90

    /** [opensToday] counts this open, so the first one gets BASE_SECONDS. */
    fun waitSeconds(opensToday: Int): Int {
        val repeats = (opensToday - 1).coerceAtLeast(0)
        return (BASE_SECONDS + STEP_SECONDS * repeats).coerceAtMost(MAX_SECONDS)
    }
}

/** 1st, 2nd, 3rd, 4th... */
fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
