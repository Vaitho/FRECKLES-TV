package com.freckles.tv

import com.freckles.tv.data.remote.NightEpisode
import com.freckles.tv.data.remote.NightSeason
import com.freckles.tv.data.remote.NightSeries
import com.freckles.tv.ui.watch.episodeNeighbors
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeNeighborsTest {
    private fun ep(id: String) = NightEpisode(id, 1, id, null, null, null)
    private val base = listOf(NightSeries("s", null, "S", null, null, null, null, emptyList(), null, listOf(
        NightSeason(0, listOf(ep("sp"))), NightSeason(1, listOf(ep("a"), ep("b"), ep("c"))), NightSeason(2, listOf(ep("d"))),
    )))

    @Test
    fun precedenteESuccessivoAttraversanoLeStagioni() {
        assertEquals(null to "b", episodeNeighbors(base, "a"))
        assertEquals("b" to "d", episodeNeighbors(base, "c")) // c = ultimo della stagione 1, d = primo della 2
        assertEquals("c" to null, episodeNeighbors(base, "d"))
    }

    @Test
    fun filmESpecialiNonHannoVicini() {
        assertEquals(null to null, episodeNeighbors(base, "film"))
        assertEquals(null to null, episodeNeighbors(base, "sp"))
    }
}
