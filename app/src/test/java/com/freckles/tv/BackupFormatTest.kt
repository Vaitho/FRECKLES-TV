package com.freckles.tv

import com.freckles.tv.data.importer.Backup
import com.freckles.tv.data.importer.ImportEpisode
import com.freckles.tv.data.importer.ImportGame
import com.freckles.tv.data.importer.ImportId
import com.freckles.tv.data.importer.ImportMovie
import com.freckles.tv.data.importer.ImportSeason
import com.freckles.tv.data.importer.ImportShow
import com.freckles.tv.data.importer.parseTvTimeArray
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Estratto nel formato reale di TV Time: campi in più, imdb null, special dentro stagioni normali. */
    private val tvTime = """
        [{
          "uuid": "x", "id": {"tvdb": 253463, "imdb": null}, "created_at": "2025-07-12T10:31:43.08205Z",
          "title": "Black Mirror", "status": "up_to_date", "is_favorite": true, "_noEpisodeData": false,
          "seasons": [
            {"number": 0, "is_specials": true, "episodes": [
              {"id": {"tvdb": 1, "imdb": null}, "number": 1, "name": "Extra", "special": true, "is_watched": false,
               "watched_at": null, "rewatch_count": 0, "watched_count": 0}]},
            {"number": 2, "is_specials": false, "episodes": [
              {"id": {"tvdb": 2, "imdb": null}, "number": 1, "name": "Be Right Back", "special": false, "is_watched": true,
               "watched_at": "2025-07-14T11:25:47Z", "rewatch_count": 0, "watched_count": 1},
              {"id": {"tvdb": 3, "imdb": null}, "number": 1, "name": "White Christmas", "special": true, "is_watched": true,
               "watched_at": "2025-07-18T11:17:42Z", "rewatch_count": 0, "watched_count": 1}]},
            {"number": 7, "is_specials": false, "episodes": []}
          ]
        }]
    """.trimIndent()

    @Test
    fun parsesTvTimeArray() {
        val shows = json.decodeFromString<List<ImportShow>>(tvTime)
        val s = shows.single()
        assertEquals(253463, s.id.tvdb)
        assertTrue(s.isFavorite)
        assertEquals(3, s.seasons.size)
        assertTrue(s.seasons[0].isSpecials)
        val s2 = s.seasons[1].episodes
        assertTrue(s2[0].isWatched && !s2[0].special)
        assertTrue(s2[1].special) // va ignorato dall'import: collide con S2E1
        assertTrue(s.seasons[2].episodes.isEmpty())
        assertEquals(0, s.rating) // TV Time non ha voti: resta "nessun voto"
    }

    @Test
    fun backupRoundTrip() {
        val original = Backup(
            version = 1,
            shows = listOf(
                ImportShow(
                    id = ImportId(tvdb = 1, tmdb = 2), title = "Serie", isFavorite = true, rating = 4,
                    seasons = listOf(ImportSeason(1, false, listOf(ImportEpisode(3, false, true, "2024-01-01T00:00:00Z", 2)))),
                )
            ),
            movies = listOf(ImportMovie(id = ImportId(tmdb = 9, imdb = "tt1"), title = "Film", isWatched = true, watchedCount = 1, rating = 5)),
            games = listOf(ImportGame(id = ImportId(ext = "igdb:1942"), title = "Gioco", status = "finished", minutesPlayed = 600, rating = 5)),
        )
        val text = Json { encodeDefaults = true }.encodeToString(Backup.serializer(), original)
        val back = json.decodeFromString<Backup>(text)
        assertEquals(original, back)
    }

    @Test
    fun tvTimeMoviesArrayIsReadAsMovies() {
        val text = """
            [{"id": {"tvdb": 344830, "imdb": "tt21909764"}, "uuid": "a", "created_at": "2023-11-26T16:22:38Z",
              "title": "My Fault", "year": 2023, "watched_at": "2023-11-26T16:22:38Z",
              "is_watched": true, "is_favorite": false, "rewatch_count": 2},
             {"id": {"tvdb": 1, "imdb": "tt2"}, "title": "Da vedere", "is_watched": false, "is_favorite": true, "rewatch_count": 0}]
        """.trimIndent()
        val back = parseTvTimeArray(text, json)
        assertTrue(back.shows.isEmpty())
        assertEquals(2, back.movies.size)
        val m = back.movies[0]
        assertEquals("tt21909764", m.id.imdb)
        assertTrue(m.isWatched)
        assertEquals(2, m.rewatchCount)
        assertTrue(back.movies[1].isFavorite && !back.movies[1].isWatched)
    }

    @Test
    fun tvTimeSeriesArrayStillReadAsShows() {
        val back = parseTvTimeArray(tvTime, json)
        assertEquals(1, back.shows.size)
        assertTrue(back.movies.isEmpty())
    }

    @Test
    fun oldRawgBackupStillParses() {
        val back = json.decodeFromString<Backup>("""{"version":1,"games":[{"id":{"rawg":3328},"title":"The Witcher 3","status":"finished","minutes_played":600}]}""")
        val g = back.games.single()
        assertEquals(3328, g.id.rawg)
        assertEquals(null, g.id.ext)
        assertEquals(600, g.minutesPlayed)
    }

    @Test
    fun oldBackupWithoutGamesStillParses() {
        val back = json.decodeFromString<Backup>("""{"version":1,"shows":[],"movies":[{"title":"Film","is_watched":false}]}""")
        assertTrue(back.games.isEmpty())
        assertFalse(back.movies.single().isWatched)
    }
}
