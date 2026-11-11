package com.freckles.tv

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freckles.tv.data.Repository
import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.db.EpisodeEntity
import com.freckles.tv.data.db.MovieEntity
import com.freckles.tv.data.db.ShowEntity
import com.freckles.tv.data.remote.Credits
import com.freckles.tv.data.remote.FindResponse
import com.freckles.tv.data.remote.GameCatalog
import com.freckles.tv.data.remote.MovieDetails
import com.freckles.tv.data.remote.PersonDetails
import com.freckles.tv.data.remote.SearchResponse
import com.freckles.tv.data.remote.SeasonDetails
import com.freckles.tv.data.remote.SeasonSummary
import com.freckles.tv.data.remote.TmdbApi
import com.freckles.tv.data.remote.TvDetails
import com.freckles.tv.data.remote.TvEpisode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Test sul database vero (in memoria) e sul Repository, con TMDB simulato: niente rete. */
@RunWith(AndroidJUnit4::class)
class DbBugHuntTest {
    private lateinit var db: AppDatabase
    private val past = LocalDate.now().minusDays(10).toString()
    private val future = LocalDate.now().plusDays(10).toString()

    /** TMDB finto: ogni serie ha 1 stagione con 3 episodi già usciti; risponde dopo [delayMs]. */
    private class FakeTmdb(val delayMs: Long = 0, val airDate: String) : TmdbApi {
        override suspend fun tv(id: Int): TvDetails {
            delay(delayMs)
            return TvDetails(id = id, name = "Serie $id", seasons = listOf(SeasonSummary(1, 3)), episodeRunTime = listOf(30))
        }
        override suspend fun season(id: Int, season: Int) =
            SeasonDetails(season, (1..3).map { TvEpisode(it, season, "Ep $it", 30, airDate) })
        override suspend fun movie(id: Int) = MovieDetails(id, "Film $id", runtime = 100)
        override suspend fun search(query: String) = SearchResponse()
        override suspend fun trending() = SearchResponse()
        override suspend fun tvCredits(id: Int) = Credits()
        override suspend fun movieCredits(id: Int) = Credits()
        override suspend fun person(id: Int) = PersonDetails()
        override suspend fun find(externalId: String, source: String) = FindResponse()
    }

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private fun repo(database: AppDatabase = db, delayMs: Long = 0) = Repository(database, FakeTmdb(delayMs, past), GameCatalog())

    @Before fun open() { db = newDb() }
    @After fun close() { db.close() }

    private suspend fun show(notify: Boolean = false) = db.showDao().insert(ShowEntity(tmdbId = 1, title = "S", notifyEnabled = notify))
    private fun ep(showId: Long, season: Int, n: Int, air: String?, watched: Boolean = false, special: Boolean = season == 0, count: Int = if (watched) 1 else 0) =
        EpisodeEntity(showId = showId, season = season, number = n, airDate = air, isSpecial = special, runtime = 40,
            watched = watched, watchedAt = if (watched) System.currentTimeMillis() else null, watchCount = count)

    // ---- conteggi e azioni sugli episodi ----

    @Test fun segnaStagioneNonSegnaGliEpisodiNonAncoraUsciti() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, past), ep(id, 1, 2, past), ep(id, 1, 3, future), ep(id, 1, 4, null)))
        db.episodeDao().setSeasonWatched(id, 1, true, System.currentTimeMillis(), LocalDate.now().toString())
        val byNumber = db.episodeDao().listForShow(id).associateBy { it.number }
        assertTrue(byNumber.getValue(1).watched && byNumber.getValue(2).watched)
        assertFalse("episodio in uscita tra 10 giorni segnato come visto", byNumber.getValue(3).watched)
        assertFalse("episodio senza data segnato come visto", byNumber.getValue(4).watched)
    }

    @Test fun avanzamentoContaSoloEpisodiUscitiOVisti() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, past, watched = true), ep(id, 1, 2, past), ep(id, 1, 3, future), ep(id, 0, 1, past)))
        val p = db.showDao().observeProgress().first().single()
        assertEquals(1, p.watched)
        assertEquals(2, p.total) // il futuro e lo speciale non contano
    }

    @Test fun prossimoEpisodioEIlPrimoUscitoNonVisto() = runBlocking {
        val id = show()
        val other = db.showDao().insert(ShowEntity(tmdbId = 2, title = "Mai iniziata"))
        db.episodeDao().insertNew(
            listOf(
                ep(id, 0, 1, past), ep(id, 1, 1, past, watched = true), ep(id, 1, 2, past), ep(id, 1, 3, past), ep(id, 2, 1, future),
                ep(other, 1, 1, past),
            )
        )
        val next = db.episodeDao().observeNext().first()
        assertEquals(listOf(1 to 2), next.map { it.season to it.number })
    }

    @Test fun segnaFinoAQuiEscludeGliSpeciali() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 0, 1, past), ep(id, 1, 1, past), ep(id, 1, 2, past), ep(id, 1, 3, past)))
        db.episodeDao().markUpTo(id, 1, 2, System.currentTimeMillis())
        val w = db.episodeDao().listForShow(id).filter { it.watched }.map { it.season to it.number }.sortedWith(compareBy({ it.first }, { it.second }))
        assertEquals(listOf(1 to 1, 1 to 2), w)
    }

    @Test fun inUscitaMostraUnSoloEpisodioPerSerie() = runBlocking {
        val id = show(notify = true)
        val soon = LocalDate.now().plusDays(3).toString()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, soon), ep(id, 1, 2, future)))
        assertEquals(listOf(1 to 1), db.episodeDao().observeUpcoming().first().map { it.season to it.number })
    }

    @Test fun eliminareUnaSerieEliminaISuoiEpisodi() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, past)))
        db.showDao().delete(id)
        assertTrue(db.episodeDao().listForShow(id).isEmpty())
    }

    // ---- statistiche ----

    @Test fun tempoTotaleContaLeRivisioniESaltaGliSpeciali() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, past, watched = true, count = 2), ep(id, 0, 1, past, watched = true, special = true)))
        db.movieDao().insert(MovieEntity(tmdbId = 9, title = "F", runtime = 100, watched = true, watchedAt = System.currentTimeMillis(), watchCount = 1))
        assertEquals(80L, db.episodeDao().observeWatchedMinutes().first())
        assertEquals(100L, db.movieDao().observeWatchedMinutes().first())
        assertEquals(1, db.episodeDao().observeWatchedCount().first())
    }

    @Test fun statisticheAnnualiEMensiliDannoLoStessoTotale() = runBlocking {
        val id = show()
        db.episodeDao().insertNew(listOf(ep(id, 1, 1, past, watched = true, count = 3), ep(id, 1, 2, past, watched = true)))
        db.movieDao().insert(MovieEntity(tmdbId = 9, title = "F", runtime = 100, watched = true, watchedAt = System.currentTimeMillis(), watchCount = 2))
        val yearly = repo().yearly.first().sumOf { it.minutes }
        val monthly = db.episodeDao().observeMonthly().first().sumOf { it.minutes }
        val total = db.episodeDao().observeWatchedMinutes().first() + db.movieDao().observeWatchedMinutes().first()
        assertEquals(total, monthly)
        assertEquals("le ore per anno non tornano con il tempo totale", total, yearly)
    }

    // ---- aggiunta titoli e backup ----

    @Test fun aggiungereLaStessaSerieDueVolteInsiemeDaLoStessoId() = runBlocking {
        val r = repo(delayMs = 200) // due import paralleli della stessa serie, es. "Serie (IT)" e "Serie (2010)"
        val a = async { r.addShowNow(42) }
        val b = async { r.addShowNow(42) }
        val ids = listOf(a.await(), b.await())
        assertTrue("id non valido: $ids", ids.all { it > 0 })
        assertEquals(ids[0], ids[1])
    }

    @Test fun reimportareLoStessoBackupNonDuplicaNulla() = runBlocking {
        val r = repo()
        val id = r.addShowNow(7)
        db.episodeDao().setSeasonWatched(id, 1, true, System.currentTimeMillis(), LocalDate.now().toString())
        val backup = r.exportJson()
        r.importer.import(backup)
        r.importer.import(backup)
        assertEquals(1, db.showDao().listAll().size)
        assertEquals(3, db.episodeDao().listForShow(id).count { it.watched })
    }

    @Test fun ripristinoDalBackupMantieneLaCampanella() = runBlocking {
        val r = repo()
        val id = r.addShowNow(5)
        db.showDao().update(db.showDao().listAll().single { it.id == id }.copy(notifyEnabled = true))
        val backup = r.exportJson()

        val fresh = newDb()
        try {
            repo(fresh).importer.import(backup)
            val restored = fresh.showDao().byTmdbId(5)!!
            assertTrue("la campanella si perde con il ripristino", restored.notifyEnabled)
        } finally {
            fresh.close()
        }
    }

    @Test fun ripristinoDalBackupMantieneVisioniEVoti() = runBlocking {
        val r = repo()
        val id = r.addShowNow(5)
        db.showDao().update(db.showDao().listAll().single { it.id == id }.copy(rating = 4, isFavorite = true))
        db.episodeDao().markUpTo(id, 1, 2, System.currentTimeMillis())
        r.addMovieNow(9)
        db.movieDao().update(db.movieDao().byTmdbId(9)!!.copy(watched = true, watchedAt = 1_000, watchCount = 3, rating = 5))
        val backup = r.exportJson()

        val fresh = newDb()
        try {
            repo(fresh).importer.import(backup)
            val s = fresh.showDao().byTmdbId(5)!!
            assertEquals(4, s.rating)
            assertTrue(s.isFavorite)
            assertEquals(2, fresh.episodeDao().listForShow(s.id).count { it.watched })
            val m = fresh.movieDao().byTmdbId(9)!!
            assertEquals(3, m.watchCount)
            assertEquals(5, m.rating)
            assertEquals(1_000L, m.watchedAt)
        } finally {
            fresh.close()
        }
    }
}
