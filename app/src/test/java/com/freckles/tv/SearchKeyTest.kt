package com.freckles.tv

import com.freckles.tv.ui.watch.searchKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKeyTest {
    @Test fun ignoraMaiuscoleAccentiEPunteggiatura() {
        assertEquals("il fantasma dell opera", searchKey("Il fantasma dell'Opera"))
        assertEquals("perche cosi", searchKey("  Perché, così!  "))
        assertEquals("cyberpunk edgerunners", searchKey("Cyberpunk: Edgerunners"))
    }
}
