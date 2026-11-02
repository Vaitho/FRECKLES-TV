package com.freckles.tv.data

import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.importer.TvTimeImporter
import com.freckles.tv.data.db.EpisodeEntity
import com.freckles.tv.data.db.MovieEntity
import com.freckles.tv.data.db.ShowEntity
import com.freckles.tv.data.remote.SearchItem
import com.freckles.tv.data.remote.TmdbApi
import com.freckles.tv.data.remote.TvDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

class Repository(private val db: AppDatabase, private val api: TmdbApi) {
    val importer = TvTimeImporter(db, api, this)

    /** Scope di app: le operazioni di rete non si interrompono se l'utente cambia schermata. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val shows = db.showDao()
    private val episodes = db.episodeDao()
    private val movies = db.movieDao()

    val allShows = shows.observeAll()
    val allMovies = movies.observeAll()
    val progress = shows.observeProgress()
    fun observeShow(id: Long) = shows.observe(id)
    fun observeMovie(id: Long) = movies.observe(id)
    fun observeEpisodes(showId: Long) = episodes.observeForShow(showId)
    fun observeNext() = episodes.observeNext(LocalDate.now().toString())
    val monthly = episodes.observeMonthly()
    val watchedEpisodes = episodes.observeWatchedCount()
    val watchedMovies = movies.observeWatchedCount()
    val totalMinutes = combine(episodes.observeWatchedMinutes(), movies.observeWatchedMinutes()) { a, b -> a + b }

    // ---- rete ----

    suspend fun search(query: String): List<SearchItem> = api.search(query).results.filterTmdb()
    suspend fun trending(): List<SearchItem> = api.trending().results.filterTmdb()
    private fun List<SearchItem>.filterTmdb() = filter { it.mediaType == "tv" || it.mediaType == "movie" }

    // ---- aggiunta ----

    fun add(item: SearchItem) {
        if (item.mediaType == "tv") addShow(item.id) else addMovie(item.id)
    }

    fun addShow(tmdbId: Int) = launchReporting("Impossibile aggiungere la serie") {
        if (shows.byTmdbId(tmdbId) == null) {
            addShowNow(tmdbId)
            _messages.tryEmit("Serie aggiunta")
        }
    }

    fun addMovie(tmdbId: Int) = launchReporting("Impossibile aggiungere il film") {
        if (movies.byTmdbId(tmdbId) == null) {
            addMovieNow(tmdbId)
            _messages.tryEmit("Film aggiunto")
        }
    }

    suspend fun addMovieNow(tmdbId: Int): Long {
        movies.byTmdbId(tmdbId)?.let { return it.id }
        val d = api.movie(tmdbId)
        return movies.insert(
            MovieEntity(
                tmdbId = d.id, imdbId = d.imdbId, title = d.title, overview = d.overview,
                posterPath = d.posterPath, backdropPath = d.backdropPath,
                runtime = d.runtime?.takeIf { it > 0 } ?: 100, releaseDate = d.releaseDate,
            )
        )
    }

    suspend fun addShowNow(tmdbId: Int): Long {
        shows.byTmdbId(tmdbId)?.let { return it.id }
        val d = api.tv(tmdbId)
        val rt = d.episodeRunTime.firstOrNull { it > 0 } ?: 40
        val id = shows.insert(
            ShowEntity(
                tmdbId = d.id, tvdbId = d.externalIds?.tvdbId, imdbId = d.externalIds?.imdbId,
                title = d.name, overview = d.overview, posterPath = d.posterPath,
                backdropPath = d.backdropPath, status = d.status, defaultRuntime = rt,
                knownSeasonCount = d.seasons.count { it.seasonNumber > 0 },
                knownNextAirDate = d.nextEpisodeToAir?.airDate,
            )
        )
        syncEpisodes(id, d, rt)
        return id
    }

    /** Scarica tutte le stagioni e inserisce gli episodi mancanti. */
    suspend fun syncEpisodes(showId: Long, d: TvDetails, defaultRuntime: Int) {
        for (s in d.seasons) {
            val sd = runCatching { api.season(d.id, s.seasonNumber) }.getOrNull() ?: continue
            episodes.insertNew(
                sd.episodes.map {
                    EpisodeEntity(
                        showId = showId, season = s.seasonNumber, number = it.episodeNumber,
                        name = it.name, runtime = it.runtime?.takeIf { r -> r > 0 } ?: defaultRuntime,
                        airDate = it.airDate, isSpecial = s.seasonNumber == 0,
                    )
                }
            )
        }
    }

    /** Avvia l'import in background; `read` viene eseguito su un thread IO. */
    fun startImport(read: () -> String) {
        scope.launch {
            runCatching { read() }
                .onSuccess { importer.import(it) }
                .onFailure { _messages.tryEmit("Impossibile leggere il file: ${it.message}") }
        }
    }

    /**
     * Controlla le serie con la campanella: ritorna (titolo, testo) per ogni nuova stagione
     * o premiere annunciata. Aggiorna anche gli episodi e lo stato noto.
     */
    suspend fun checkForUpdates(): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        for (show in shows.notifyShows()) {
            val d = runCatching { api.tv(show.tmdbId) }.getOrNull() ?: continue
            val seasons = d.seasons.count { it.seasonNumber > 0 }
            val next = d.nextEpisodeToAir
            val newSeason = seasons > show.knownSeasonCount
            val premiere = next != null && next.episodeNumber == 1 && next.airDate != show.knownNextAirDate
            if (newSeason || premiere) {
                val text = when {
                    next?.airDate != null && premiere -> "Nuova stagione dal ${next.airDate}"
                    else -> "È disponibile una nuova stagione"
                }
                found += show.title to text
            }
            syncEpisodes(show.id, d, show.defaultRuntime)
            shows.update(show.copy(knownSeasonCount = seasons, knownNextAirDate = next?.airDate, status = d.status))
        }
        return found
    }

    /** Genera il backup e lo consegna a `write` (che lo scrive sul file scelto). */
    fun startExport(write: (String) -> Unit) = launchReporting("Esportazione non riuscita") {
        write(importer.export())
        _messages.tryEmit("Backup esportato")
    }

    // ---- azioni ----

    fun toggleEpisode(e: EpisodeEntity) = launchReporting("Errore") {
        episodes.update(
            if (e.watched) e.copy(watched = false, watchedAt = null, watchCount = 0)
            else e.copy(watched = true, watchedAt = System.currentTimeMillis(), watchCount = maxOf(1, e.watchCount))
        )
    }

    fun setSeasonWatched(showId: Long, season: Int, watched: Boolean) = launchReporting("Errore") {
        episodes.setSeasonWatched(showId, season, watched, System.currentTimeMillis())
    }

    fun toggleMovieWatched(m: MovieEntity) = launchReporting("Errore") {
        movies.update(
            if (m.watched) m.copy(watched = false, watchedAt = null, watchCount = 0)
            else m.copy(watched = true, watchedAt = System.currentTimeMillis(), watchCount = maxOf(1, m.watchCount))
        )
    }

    fun setShowFavorite(s: ShowEntity) = launchReporting("Errore") { shows.update(s.copy(isFavorite = !s.isFavorite)) }
    fun setShowNotify(s: ShowEntity) = launchReporting("Errore") { shows.update(s.copy(notifyEnabled = !s.notifyEnabled)) }
    fun setMovieFavorite(m: MovieEntity) = launchReporting("Errore") { movies.update(m.copy(isFavorite = !m.isFavorite)) }
    fun deleteShow(id: Long) = launchReporting("Errore") { shows.delete(id) }
    fun deleteMovie(id: Long) = launchReporting("Errore") { movies.delete(id) }

    private fun launchReporting(errorPrefix: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { _messages.tryEmit("$errorPrefix: ${it.message ?: "errore di rete"}") }
        }
    }
}
