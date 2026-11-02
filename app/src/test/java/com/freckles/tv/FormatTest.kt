package com.freckles.tv

import com.freckles.tv.util.formatDuration
import com.freckles.tv.util.formatHours
import com.freckles.tv.util.genreTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun duration() {
        assertEquals("0 ore", formatDuration(0))
        assertEquals("1 ora", formatDuration(60))
        assertEquals("2 giorni 14 ore", formatDuration(3745))
        assertEquals("1 giorno 0 ore", formatDuration(60 * 24))
        // 30 giorni = 1 mese
        assertEquals("1 mese 1 giorno 1 ora", formatDuration(60L * 24 * 31 + 60))
        assertEquals("3 mesi 0 ore", formatDuration(60L * 24 * 90))
    }

    @Test
    fun hours() {
        assertEquals("0 min", formatHours(0))
        assertEquals("45 min", formatHours(45))
        assertEquals("2 h", formatHours(120))
        assertEquals("12 h 30 min", formatHours(750))
    }

    @Test
    fun genresSplitAndSum() {
        val totals = genreTotals(
            listOf(
                "Dramma, Crime" to 100L,
                "Dramma" to 50L,
                null to 999L, // senza generi: ignorato
                "" to 10L,
                "Commedia" to 0L, // zero minuti: escluso
            )
        )
        assertEquals(listOf("Dramma" to 150L, "Crime" to 100L), totals)
    }

    @Test
    fun genresLimit() {
        val items = (1..10).map { "G$it" to it.toLong() }
        val totals = genreTotals(items, limit = 3)
        assertEquals(listOf("G10", "G9", "G8"), totals.map { it.first })
    }
}
