package com.freckles.tv.data.importer

import androidx.room.withTransaction
import com.freckles.tv.data.Repository
import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.remote.TmdbApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

// ---- formato del file (export TV Time) ----

@Serializable
data class ImportId(val tvdb: Int? = null, val imdb: String? = null, val tmdb: Int? = null, val rawg: Int? = null)

@Serializable
data class ImportEpisode(
    val number: Int = 0,
    val special: Boolean = false,
    @kotlinx.serialization.SerialName("is_watched") val isWatched: Boolean = false,
    @kotlinx.serialization.SerialName("watched_at") val watchedAt: String? = null,
    @kotlinx.serialization.SerialName("watched_count") val watchedCount: Int = 0,
)

@Serializable
data class ImportSeason(
    val number: Int = 0,
    @kotlinx.serialization.SerialName("is_specials") val isSpecials: Boolean = false,
    val episodes: List<ImportEpisode> = emptyList(),
)

@Serializable
data class ImportShow(
    val id: ImportId = ImportId(),
    val title: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("is_favorite") val isFavorite: Boolean = false,
    val seasons: List<ImportSeason> = emptyList(),
)

@Serializable
data class ImportMovie(
    val id: ImportId = ImportId(),
    val title: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("is_favorite") val isFavorite: Boolean = false,
    @kotlinx.serialization.SerialName("is_watched") val isWatched: Boolean = false,
    @kotlinx.serialization.SerialName("watched_at") val watchedAt: String? = null,
    @kotlinx.serialization.SerialName("watched_count") val watchedCount: Int = 0,
)

@Serializable
data class ImportGame(
    val id: ImportId = ImportId(),
    val title: String,
    val status: String = "backlog",
    @kotlinx.serialization.SerialName("minutes_played") val minutesPlayed: Int = 0,
    val rating: Int = 0,
    @kotlinx.serialization.SerialName("is_favorite") val isFavorite: Boolean = false,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("finished_at") val finishedAt: String? = null,
)

/** Formato di export dell'app; l'import accetta anche l'array di sole serie di TV Time. */
@Serializable
data class Backup(val version: Int = 1, val shows: List<ImportShow> = emptyList(), val movies: List<ImportMovie> = emptyList(), val games: List<ImportGame> = emptyList())

data class ImportState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val imported: Int = 0,
    val moviesImported: Int = 0,
    val gamesImported: Int = 0,
    val episodesMarked: Int = 0,
    val episodesUnmatched: Int = 0,
    val failed: List<String> = emptyList(),
    val error: String? = null,
)

class TvTimeImporter(
    private val db: AppDatabase,
    private val api: TmdbApi,
    private val repo: Repository,
) {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val exportJson = Json { encodeDefaults = true }

    /** Importa il file; rieseguirlo non duplica nulla e non toglie mai lo stato "visto". */
    suspend fun import(text: String) {
        if (_state.value.running) return
        val backup = runCatching {
            if (text.trimStart().startsWith("[")) Backup(shows = json.decodeFromString<List<ImportShow>>(text))
            else json.decodeFromString<Backup>(text)
        }.getOrElse {
            _state.value = ImportState(finished = true, error = "File non valido: ${it.message?.take(120)}")
            return
        }
        val shows = backup.shows
        _state.value = ImportState(running = true, total = shows.size + backup.movies.size + backup.games.size)
        val gate = Semaphore(4)
        coroutineScope {
            shows.map { show ->
                async {
                    gate.withPermit {
                        runCatching { importShow(show) }.onFailure { e ->
                            _state.update { it.copy(failed = it.failed + "${show.title}: ${e.message ?: "errore"}") }
                        }
                        _state.update { it.copy(done = it.done + 1) }
                    }
                }
            }.awaitAll()
            backup.movies.map { movie ->
                async {
                    gate.withPermit {
                        runCatching { importMovie(movie) }.onFailure { e ->
                            _state.update { it.copy(failed = it.failed + "${movie.title}: ${e.message ?: "errore"}") }
                        }
                        _state.update { it.copy(done = it.done + 1) }
                    }
                }
            }.awaitAll()
            backup.games.map { game ->
                async {
                    gate.withPermit {
                        runCatching { importGame(game) }.onFailure { e ->
                            _state.update { it.copy(failed = it.failed + "${game.title}: ${e.message ?: "errore"}") }
                        }
                        _state.update { it.copy(done = it.done + 1) }
                    }
                }
            }.awaitAll()
        }
        _state.update { it.copy(running = false, finished = true) }
    }

    fun dismiss() {
        if (!_state.value.running) _state.value = ImportState()
    }

    private fun parseTime(s: String?) = s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private suspend fun importMovie(m: ImportMovie) {
        val tmdbId = m.id.tmdb
            ?: m.id.imdb?.let { api.find(it, "imdb_id").movieResults.firstOrNull()?.id }
            ?: api.search(m.title).results.firstOrNull { it.mediaType == "movie" }?.id
            ?: error("non trovato su TMDB")
        repo.addMovieNow(tmdbId)
        val row = db.movieDao().byTmdbId(tmdbId) ?: return
        db.movieDao().update(
            row.copy(
                isFavorite = row.isFavorite || m.isFavorite,
                watched = row.watched || m.isWatched,
                watchedAt = row.watchedAt ?: if (m.isWatched) parseTime(m.watchedAt) ?: System.currentTimeMillis() else null,
                watchCount = maxOf(row.watchCount, if (m.isWatched) maxOf(1, m.watchedCount) else 0),
                addedAt = parseTime(m.createdAt) ?: row.addedAt,
            )
        )
        _state.update { it.copy(moviesImported = it.moviesImported + 1) }
    }

    private suspend fun importGame(g: ImportGame) {
        val rawgId = g.id.rawg ?: repo.searchGames(g.title).firstOrNull()?.id ?: error("non trovato su RAWG")
        repo.addGameNow(rawgId)
        val row = db.gameDao().byRawgId(rawgId) ?: return
        db.gameDao().update(
            row.copy(
                status = g.status, minutesPlayed = maxOf(row.minutesPlayed, g.minutesPlayed),
                rating = if (g.rating > 0) g.rating else row.rating, isFavorite = row.isFavorite || g.isFavorite,
                finishedAt = parseTime(g.finishedAt) ?: row.finishedAt, addedAt = parseTime(g.createdAt) ?: row.addedAt,
            )
        )
        _state.update { it.copy(gamesImported = it.gamesImported + 1) }
    }

    suspend fun export(): String {
        val shows = db.showDao().listAll().map { sh ->
            val eps = db.episodeDao().listForShow(sh.id).filter { it.watched && !it.isSpecial }
            ImportShow(
                id = ImportId(tvdb = sh.tvdbId, imdb = sh.imdbId, tmdb = sh.tmdbId),
                title = sh.title,
                createdAt = Instant.ofEpochMilli(sh.addedAt).toString(),
                isFavorite = sh.isFavorite,
                seasons = eps.groupBy { it.season }.toSortedMap().map { (n, list) ->
                    ImportSeason(n, false, list.sortedBy { it.number }.map {
                        ImportEpisode(it.number, false, true, it.watchedAt?.let { t -> Instant.ofEpochMilli(t).toString() }, it.watchCount)
                    })
                },
            )
        }
        val movies = db.movieDao().listAll().map { m ->
            ImportMovie(
                id = ImportId(imdb = m.imdbId, tmdb = m.tmdbId), title = m.title,
                createdAt = Instant.ofEpochMilli(m.addedAt).toString(), isFavorite = m.isFavorite,
                isWatched = m.watched, watchedAt = m.watchedAt?.let { Instant.ofEpochMilli(it).toString() },
                watchedCount = m.watchCount,
            )
        }
        val games = db.gameDao().listAll().map { g ->
            ImportGame(
                id = ImportId(rawg = g.rawgId), title = g.title, status = g.status, minutesPlayed = g.minutesPlayed,
                rating = g.rating, isFavorite = g.isFavorite, createdAt = Instant.ofEpochMilli(g.addedAt).toString(),
                finishedAt = g.finishedAt?.let { Instant.ofEpochMilli(it).toString() },
            )
        }
        return exportJson.encodeToString(Backup.serializer(), Backup(1, shows, movies, games))
    }

    private suspend fun resolveTmdbId(show: ImportShow): Int? {
        show.id.tmdb?.let { return it }
        show.id.tvdb?.let { tvdb ->
            api.find(tvdb.toString(), "tvdb_id").tvResults.firstOrNull()?.let { return it.id }
        }
        // Es. "Parenthood (IT)" -> "Parenthood"
        val clean = show.title.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
        return api.search(clean).results.firstOrNull { it.mediaType == "tv" }?.id
    }

    private suspend fun importShow(show: ImportShow) {
        val tmdbId = resolveTmdbId(show) ?: error("non trovata su TMDB")
        val showId = repo.addShowNow(tmdbId)

        val existing = db.episodeDao().listForShow(showId).associateBy { it.season to it.number }
        var marked = 0
        var unmatched = 0
        val updated = buildList {
            for (season in show.seasons) {
                if (season.isSpecials || season.number == 0) continue
                for (ep in season.episodes) {
                    // Gli "special" dentro le stagioni normali hanno numerazione che collide con gli episodi veri.
                    if (ep.special || !ep.isWatched) continue
                    val target = existing[season.number to ep.number]
                    if (target == null) { unmatched++; continue }
                    if (target.watched) continue
                    val at = ep.watchedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    add(target.copy(watched = true, watchedAt = at ?: System.currentTimeMillis(), watchCount = maxOf(1, ep.watchedCount)))
                    marked++
                }
            }
        }
        db.withTransaction {
            if (updated.isNotEmpty()) db.episodeDao().updateAll(updated)
            val row = db.showDao().byTmdbId(tmdbId)
            if (row != null) {
                val added = show.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                db.showDao().update(row.copy(isFavorite = row.isFavorite || show.isFavorite, addedAt = added ?: row.addedAt))
            }
        }
        _state.update { it.copy(imported = it.imported + 1, episodesMarked = it.episodesMarked + marked, episodesUnmatched = it.episodesUnmatched + unmatched) }
    }
}
