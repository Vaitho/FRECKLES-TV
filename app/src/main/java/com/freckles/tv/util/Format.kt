package com.freckles.tv.util

/** Es. 3745 min -> "2 giorni 14 ore". Mese = 30 giorni. */
fun formatDuration(totalMinutes: Long): String {
    val months = totalMinutes / (60 * 24 * 30)
    val days = totalMinutes / (60 * 24) % 30
    val hours = totalMinutes / 60 % 24
    val parts = buildList {
        if (months > 0) add("$months ${if (months == 1L) "mese" else "mesi"}")
        if (days > 0) add("$days ${if (days == 1L) "giorno" else "giorni"}")
        add("$hours ${if (hours == 1L) "ora" else "ore"}")
    }
    return parts.joinToString(" ")
}

/** "12 h 30 min" */
fun formatHours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/**
 * Somma i minuti per genere. Ogni elemento è (generi separati da virgola, minuti):
 * il tempo di un titolo conta per intero in ciascuno dei suoi generi.
 */
fun genreTotals(items: List<Pair<String?, Long>>, limit: Int = 6): List<Pair<String, Long>> =
    items.flatMap { (genres, minutes) ->
        genres.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it to minutes }
    }
        .groupBy({ it.first }, { it.second })
        .map { (g, list) -> g to list.sum() }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit)
