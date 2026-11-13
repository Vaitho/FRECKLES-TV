package com.freckles.tv

import com.freckles.tv.data.remote.NightSub
import com.freckles.tv.ui.watch.defaultSub
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSubTest {
    private val itaForced = NightSub("i0", "ita", "Italiano Forced", forced = true)
    private val engForced = NightSub("i1", "eng", "English Forced", forced = true)
    private val ita = NightSub("i3", "ita", "Italiano")
    private val eng = NightSub("i4", "eng", "English")
    private val all = listOf(engForced, itaForced, eng, ita)

    @Test
    fun ogniModalitaSceglieIlSottotitoloGiusto() {
        assertEquals(null, defaultSub(all, "off"))
        assertEquals(itaForced, defaultSub(all, "forced"))
        assertEquals(ita, defaultSub(all, "ita"))
        assertEquals(eng, defaultSub(all, "eng"))
    }

    @Test
    fun seMancaNonNeAccendeUnAltro() {
        assertEquals(null, defaultSub(listOf(engForced, eng), "forced")) // niente forzati inglesi su audio italiano
        assertEquals(null, defaultSub(listOf(itaForced, eng), "ita"))    // il forzato non sostituisce il completo
        assertEquals(null, defaultSub(emptyList(), "ita"))
    }

    @Test
    fun laSceltaNelPlayerDiventaLaModalita() {
        val pick = { cur: String, sel: NightSub? -> com.freckles.tv.ui.watch.pickedSubMode(all, cur, sel != null, sel?.key, sel?.lang) }
        assertEquals("ita", pick("forced", ita))
        assertEquals("eng", pick("ita", eng))
        assertEquals("forced", pick("off", itaForced))
        assertEquals("off", pick("ita", null))
    }

    @Test
    fun laSelezioneAutomaticaNonCambiaNulla() {
        val pick = { subs: List<NightSub>, cur: String, sel: NightSub? -> com.freckles.tv.ui.watch.pickedSubMode(subs, cur, sel != null, sel?.key, sel?.lang) }
        assertEquals(null, pick(all, "ita", ita))             // quello acceso da solo
        assertEquals(null, pick(listOf(eng), "ita", null))    // il file non ha l'italiano: resta "ita"
        assertEquals(null, pick(all, "off", null))
    }
}
