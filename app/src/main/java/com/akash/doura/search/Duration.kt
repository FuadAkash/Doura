package com.akash.doura.search

/**
 * Durations come off the API as a plain minute count. 116 minutes means
 * nothing at a glance, so render anything from an hour up as "1 h 56 min".
 *
 *    45  -> "45 min"
 *   116  -> "1 h 56 min"
 *   120  -> "2 h"
 *   330  -> "5 h 30 min"
 */
internal fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    if (minutes < 60) return "$minutes min"

    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}
