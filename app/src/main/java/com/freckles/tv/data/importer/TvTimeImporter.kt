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
data class ImportId(val tvdb: Int? = null, val imdb: String? = null)

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

data class ImportState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val imported: Int = 0,
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

    /** Importa il file; rieseguirlo non duplica nulla e non toglie mai lo stato "visto". */
    suspend fun import(text: String) {
        if (_state.value.running) return
        val shows = runCatching { json.decodeFromString<List<ImportShow>>(text) }.getOrElse {
            _state.value = ImportState(finished = true, error = "File non valido: ${it.message?.take(120)}")
            return
        }
        _state.value = ImportState(running = true, total = shows.size)
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
        }
        _state.update { it.copy(running = false, finished = true) }
    }

    fun dismiss() {
        if (!_state.value.running) _state.value = ImportState()
    }

    private suspend fun resolveTmdbId(show: ImportShow): Int? {
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
