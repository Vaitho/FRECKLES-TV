package com.freckles.tv.data

import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.importer.TvTimeImporter
import com.freckles.tv.data.db.EpisodeEntity
import com.freckles.tv.data.db.MovieEntity
import com.freckles.tv.data.db.ShowEntity
import com.freckles.tv.data.db.GameEntity
import com.freckles.tv.data.db.GameStatus
import com.freckles.tv.data.remote.GameCatalog
import com.freckles.tv.data.remote.GameInfo
import com.freckles.tv.data.remote.GameSearch
import com.freckles.tv.data.remote.SearchItem
import com.freckles.tv.data.remote.TmdbApi
import com.freckles.tv.data.remote.TvDetails
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Dati per la pagina di anteprima di un titolo non ancora in libreria. */
data class PreviewInfo(
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val meta: String,
    val imdbId: String?,
)

class Repository(private val db: AppDatabase, private val api: TmdbApi, private val catalog: GameCatalog) {
    val importer = TvTimeImporter(db, api, this)

    /** Scope di app: le operazioni di rete non si interrompono se l'utente cambia schermata. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val shows = db.showDao()
    private val episodes = db.episodeDao()
    private val movies = db.movieDao()
    private val games = db.gameDao()

    val allShows = shows.observeAll()
    val allMovies = movies.observeAll()
    val allGames = games.observeAll()
    fun observeGame(id: Long) = games.observe(id)
    val finishedGames = games.observeFinishedCount()
    val progress = shows.observeProgress()
    fun observeShow(id: Long) = shows.observe(id)
    fun observeMovie(id: Long) = movies.observe(id)
    fun observeEpisodes(showId: Long) = episodes.observeForShow(showId)
    fun observeNext() = episodes.observeNext()
    val monthly = episodes.observeMonthly()
    val minutesByShow = episodes.observeMinutesByShow()
    val yearly = combine(episodes.observeYearly(), movies.observeYearly()) { a, b ->
        (a + b).groupBy { it.year }.map { (y, l) -> com.freckles.tv.data.db.YearStat(y, l.sumOf { it.minutes }) }.sortedBy { it.year }
    }
    val watchedEpisodes = episodes.observeWatchedCount()
    val watchedMovies = movies.observeWatchedCount()
    val totalMinutes = combine(
        episodes.observeWatchedMinutes(), movies.observeWatchedMinutes(), games.observeMinutes(),
    ) { a, b, c -> a + b + c }

    // ---- rete ----

    suspend fun search(query: String): List<SearchItem> = api.search(query).results.filterTmdb()
    private var trendingCache: Pair<Long, List<SearchItem>>? = null

    suspend fun trending(): List<SearchItem> {
        val cached = trendingCache
        if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60_000) return cached.second
        return api.trending().results.filterTmdb().also { trendingCache = System.currentTimeMillis() to it }
    }
    private fun List<SearchItem>.filterTmdb() = filter { it.mediaType == "tv" || it.mediaType == "movie" }

    suspend fun preview(type: String, id: Int): PreviewInfo =
        if (type == "tv") {
            val d = api.tv(id)
            val seasons = d.seasons.count { it.seasonNumber > 0 }
            PreviewInfo(
                d.name, d.overview, d.posterPath, d.backdropPath,
                listOfNotNull(
                    "Serie TV", d.firstAirDate?.take(4)?.takeIf { it.isNotBlank() },
                    if (seasons > 0) "$seasons ${if (seasons == 1) "stagione" else "stagioni"}" else null,
                ).joinToString(" · "),
                d.externalIds?.imdbId,
            )
        } else {
            val d = api.movie(id)
            PreviewInfo(
                d.title, d.overview, d.posterPath, d.backdropPath,
                listOfNotNull(
                    "Film", d.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                    d.runtime?.takeIf { it > 0 }?.let { "$it min" },
                ).joinToString(" · "),
                d.imdbId,
            )
        }

    // ---- videogiochi (IGDB, con Steam come riserva) ----

    suspend fun searchGames(query: String): GameSearch = catalog.search(query)

    fun gamePendingKey(extId: String) = "game:$extId"

    suspend fun gamePreview(extId: String): GameInfo = catalog.details(extId)

    fun addGame(extId: String, status: String) {
        val key = gamePendingKey(extId)
        if (key in _pending.value) return
        _pending.update { it + key }
        launchReporting("Impossibile aggiungere il gioco") {
            if (games.byExtId(extId) == null) {
                addGameNow(extId, status)
                _messages.tryEmit("Gioco aggiunto")
            }
        }.invokeOnCompletion { _pending.update { it - key } }
    }

    suspend fun addGameNow(extId: String, status: String = GameStatus.BACKLOG): Long {
        games.byExtId(extId)?.let { return it.id }
        val d = catalog.details(extId)
        return games.insert(
            GameEntity(
                extId = extId, title = d.title, overview = d.overview?.takeIf { it.isNotBlank() },
                imageUrl = d.imageUrl, backdropUrl = d.backdropUrl, released = d.released,
                platforms = d.platforms, genres = d.genres, status = status,
                finishedAt = if (status == GameStatus.FINISHED) System.currentTimeMillis() else null,
            )
        )
    }

    fun setGameStatus(g: GameEntity, status: String) = launchReporting("Errore") {
        games.update(
            g.copy(
                status = status,
                finishedAt = if (status == GameStatus.FINISHED) g.finishedAt ?: System.currentTimeMillis() else null,
            )
        )
    }
    fun setGameMinutes(g: GameEntity, minutes: Int) = launchReporting("Errore") { games.update(g.copy(minutesPlayed = minutes.coerceAtLeast(0))) }
    fun setGameRating(g: GameEntity, rating: Int) = launchReporting("Errore") { games.update(g.copy(rating = rating.coerceIn(0, 5))) }
    fun setGameFavorite(g: GameEntity) = launchReporting("Errore") { games.update(g.copy(isFavorite = !g.isFavorite)) }
    fun deleteGame(id: Long) = launchReporting("Errore") { games.delete(id) }

    // ---- aggiunta ----

    /** `watched` = lo aggiungo come già visto (film visto / tutti gli episodi usciti visti). */
    fun add(item: SearchItem, watched: Boolean = false) {
        val key = pendingKey(item)
        if (key in _pending.value) return
        _pending.update { it + key }
        val job = if (item.mediaType == "tv") addShow(item.id, watched) else addMovie(item.id, watched)
        job.invokeOnCompletion { _pending.update { it - key } }
    }

    /** Titoli la cui aggiunta è ancora in corso (la rete può metterci qualche secondo). */
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending

    fun pendingKey(item: SearchItem) = "${item.mediaType}:${item.id}"

    fun addShow(tmdbId: Int, watched: Boolean = false) = launchReporting("Impossibile aggiungere la serie") {
        if (shows.byTmdbId(tmdbId) == null) {
            val id = addShowNow(tmdbId)
            if (watched) episodes.markAiredWatched(id, LocalDate.now().toString(), System.currentTimeMillis())
            _messages.tryEmit(if (watched) "Serie aggiunta come vista" else "Serie aggiunta")
        }
    }

    fun addMovie(tmdbId: Int, watched: Boolean = false) = launchReporting("Impossibile aggiungere il film") {
        if (movies.byTmdbId(tmdbId) == null) {
            val id = addMovieNow(tmdbId)
            if (watched) movies.byTmdbId(tmdbId)?.let {
                movies.update(it.copy(watched = true, watchedAt = System.currentTimeMillis(), watchCount = 1))
            }
            _messages.tryEmit(if (watched) "Film aggiunto come visto" else "Film aggiunto")
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
                genres = d.genres.joinToString(", ") { it.name }.ifBlank { null },
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
                genres = d.genres.joinToString(", ") { it.name }.ifBlank { null },
                knownSeasonCount = d.seasons.count { it.seasonNumber > 0 },
                knownNextAirDate = d.nextEpisodeToAir?.airDate,
            )
        )
        syncEpisodes(id, d, rt)
        return id
    }

    /**
     * Scarica le stagioni e inserisce gli episodi mancanti. Con `onlyChanged` scarica solo le stagioni
     * nuove o con un numero di episodi diverso, più l'ultima (per date di uscita appena annunciate).
     * Gli episodi già presenti ricevono nome, durata e data aggiornati, lo stato di visione resta.
     */
    suspend fun syncEpisodes(showId: Long, d: TvDetails, defaultRuntime: Int, onlyChanged: Boolean = false) {
        val local = if (onlyChanged) episodes.seasonCounts(showId).associate { it.season to it.n } else emptyMap()
        val lastSeason = d.seasons.maxOfOrNull { it.seasonNumber }
        for (s in d.seasons) {
            if (onlyChanged && s.seasonNumber != lastSeason && local[s.seasonNumber] == s.episodeCount) continue
            val sd = runCatching { api.season(d.id, s.seasonNumber) }.getOrNull() ?: continue
            val list = sd.episodes.map {
                EpisodeEntity(
                    showId = showId, season = s.seasonNumber, number = it.episodeNumber,
                    name = it.name, runtime = it.runtime?.takeIf { r -> r > 0 } ?: defaultRuntime,
                    airDate = it.airDate, isSpecial = s.seasonNumber == 0,
                )
            }
            db.withTransaction {
                episodes.insertNew(list)
                if (onlyChanged) list.forEach { episodes.updateMeta(showId, it.season, it.number, it.name, it.runtime, it.airDate) }
            }
        }
    }

    /** Riempie i generi mancanti (titoli aggiunti prima che li salvassimo). Silenzioso, una volta per avvio. */
    fun backfillGenres() {
        scope.launch {
            for (sh in shows.listAll().filter { it.genres == null }) {
                val d = runCatching { api.tv(sh.tmdbId) }.getOrNull() ?: continue
                shows.update(sh.copy(genres = d.genres.joinToString(", ") { it.name }.ifBlank { "" }))
            }
            for (m in movies.listAll().filter { it.genres == null }) {
                val d = runCatching { api.movie(m.tmdbId) }.getOrNull() ?: continue
                movies.update(m.copy(genres = d.genres.joinToString(", ") { it.name }.ifBlank { "" }))
            }
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
            syncEpisodes(show.id, d, show.defaultRuntime, onlyChanged = true)
            shows.update(show.copy(knownSeasonCount = seasons, knownNextAirDate = next?.airDate, status = d.status))
        }
        return found
    }

    suspend fun exportJson(): String = importer.export()

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

    fun setShowRating(s: ShowEntity, rating: Int) = launchReporting("Errore") { shows.update(s.copy(rating = rating.coerceIn(0, 5))) }
    fun setMovieRating(m: MovieEntity, rating: Int) = launchReporting("Errore") { movies.update(m.copy(rating = rating.coerceIn(0, 5))) }

    /** Messaggio breve mostrato nella snackbar. */
    fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun setShowFavorite(s: ShowEntity) = launchReporting("Errore") { shows.update(s.copy(isFavorite = !s.isFavorite)) }
    fun setShowNotify(s: ShowEntity) = launchReporting("Errore") { shows.update(s.copy(notifyEnabled = !s.notifyEnabled)) }
    fun setMovieFavorite(m: MovieEntity) = launchReporting("Errore") { movies.update(m.copy(isFavorite = !m.isFavorite)) }
    fun deleteShow(id: Long) = launchReporting("Errore") { shows.delete(id) }
    fun deleteMovie(id: Long) = launchReporting("Errore") { movies.delete(id) }

    private fun launchReporting(errorPrefix: String, block: suspend () -> Unit): Job {
        return scope.launch {
            runCatching { block() }.onFailure { _messages.tryEmit("$errorPrefix: ${it.message ?: "errore di rete"}") }
        }
    }
}
