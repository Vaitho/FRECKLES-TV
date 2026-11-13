package com.freckles.tv.ui.watch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.NightApi
import com.freckles.tv.data.remote.NightEpisode
import com.freckles.tv.data.remote.NightMovie
import com.freckles.tv.data.remote.NightProgress
import com.freckles.tv.data.remote.NightSeries
import com.freckles.tv.data.remote.NightSub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Libreria e progresso del server, condivisi tra la griglia Guarda e le schede di dettaglio. */
class NightData(val api: NightApi) {
    var movies by mutableStateOf<List<NightMovie>?>(null); private set
    var series by mutableStateOf<List<NightSeries>>(emptyList()); private set
    var progress by mutableStateOf<Map<String, NightProgress>>(emptyMap()); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set

    // Per i salvataggi del player: devono finire anche dopo che il player è stato chiuso.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Aggiorna il progresso (leggero). La libreria intera (tutti i titoli con trame ed episodi) si scarica
     * solo la prima volta o con [full], cioè col "trascina per aggiornare". Se fallisce tiene i dati già caricati.
     */
    suspend fun refresh(full: Boolean = false) {
        if (api.base.isEmpty() || loading) return
        loading = true
        runCatching {
            if (full || movies == null) {
                val (m, s) = api.library()
                movies = m; series = s
            }
            progress = api.progress()
            error = null
        }.onFailure { error = "Il server non risponde (${api.base}). Controlla che sia acceso e che il telefono sia sulla stessa rete." }
        loading = false
    }

    /** Salva la posizione sul server e tiene aggiornata la copia in memoria (secondi). */
    fun saveProgress(fileId: String, posSec: Long, durSec: Long) {
        scope.launch { runCatching { progress = progress + (fileId to api.saveProgress(fileId, posSec, durSec)) } }
    }

    suspend fun setWatched(fileIds: List<String>, watched: Boolean) {
        runCatching { fileIds.forEach { progress = progress + (it to api.setWatched(it, watched)) } }
    }

    /** Porta il "visto" di un file del server nella libreria di Freckles TV (film o episodio abbinato via TMDB). */
    fun syncToLibrary(repo: Repository, fileId: String) {
        movies?.firstOrNull { m -> m.versions.any { it.id == fileId } }?.let { m ->
            m.tmdbId?.let(repo::markMovieWatchedFromServer)
            return
        }
        for (s in series) for (se in s.seasons) {
            val e = se.episodes.firstOrNull { it.id == fileId } ?: continue
            s.tmdbId?.let { repo.markEpisodeWatchedFromServer(it, se.n, e.num) }
            return
        }
    }

    /** Film iniziati e serie da continuare, dal più recente. */
    fun continueItems(): List<ContinueItem> {
        val fromMovies = movies.orEmpty().flatMap { m ->
            m.versions.mapNotNull { v ->
                progress[v.id]?.takeIf { it.resumable && !it.watched }?.let { p ->
                    ContinueItem(v.id, m.title, "Mancano ${fmtMin((p.dur - p.pos) / 60)}", m.backdrop ?: m.image, p.pos / p.dur.toFloat(), p.time)
                }
            }
        }
        val fromSeries = series.mapNotNull { s ->
            val t = seriesTarget(s, progress)?.takeIf { it.kind == Target.RESUME || it.kind == Target.NEXT } ?: return@mapNotNull null
            val p = progress[t.episode.id]
            val label = "S${t.season}E${t.episode.num}" + t.episode.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            ContinueItem(
                t.episode.id, s.title, if (t.kind == Target.NEXT) "Prossimo: $label" else label,
                t.episode.image ?: s.backdrop, if (p.resumable) p!!.pos / p.dur.toFloat() else 0f, t.time,
            )
        }
        return (fromMovies + fromSeries).sortedByDescending { it.time }.take(15)
    }
}

data class ContinueItem(val fileId: String, val title: String, val subtitle: String, val image: String?, val progress: Float, val time: Long)

enum class Target { START, RESUME, NEXT, AGAIN }

/** Episodio da proporre col tasto principale; [time] = ultima visione della serie. */
data class SeriesTarget(val season: Int, val episode: NightEpisode, val kind: Target, val time: Long) {
    val label get() = when (kind) {
        Target.START -> "Riproduci S${season}E${episode.num}"
        Target.RESUME -> "Riprendi S${season}E${episode.num}"
        Target.NEXT -> "Prossimo: S${season}E${episode.num}"
        Target.AGAIN -> "Riguarda da capo"
    }
}

/** Come la web app: riprende l'ultimo episodio aperto, altrimenti propone il successivo. Gli speciali (stagione 0) restano fuori. */
fun seriesTarget(s: NightSeries, progress: Map<String, NightProgress>): SeriesTarget? {
    val flat = s.seasons.filter { it.n > 0 }.ifEmpty { s.seasons }.flatMap { se -> se.episodes.map { se.n to it } }
    if (flat.isEmpty()) return null
    val lastIdx = flat.indices.filter { (progress[flat[it].second.id]?.time ?: 0) > 0 }.maxByOrNull { progress[flat[it].second.id]!!.time } ?: -1
    if (lastIdx < 0) return SeriesTarget(flat[0].first, flat[0].second, Target.START, 0)
    val p = progress.getValue(flat[lastIdx].second.id)
    fun at(i: Int, kind: Target) = SeriesTarget(flat[i].first, flat[i].second, kind, p.time)
    return when {
        !p.watched -> at(lastIdx, if (p.resumable) Target.RESUME else Target.START)
        lastIdx + 1 < flat.size -> at(lastIdx + 1, Target.NEXT)
        else -> at(0, Target.AGAIN)
    }
}

/** Episodio precedente e successivo a [fileId] nella sua serie; null per i film, gli speciali e ai due estremi. */
fun episodeNeighbors(series: List<NightSeries>, fileId: String): Pair<String?, String?> {
    for (s in series) {
        val ids = s.seasons.filter { it.n > 0 }.flatMap { se -> se.episodes.map { it.id } }
        val i = ids.indexOf(fileId)
        if (i >= 0) return ids.getOrNull(i - 1) to ids.getOrNull(i + 1)
    }
    return null to null
}

/**
 * Sottotitolo da accendere all'avvio secondo l'impostazione [mode] ("off" | "forced" | "ita" | "eng"); null = nessuno.
 * "forced" sceglie i forzati in italiano (o senza lingua dichiarata); le lingue scelgono i sottotitoli completi.
 */
fun defaultSub(subs: List<NightSub>, mode: String): NightSub? = when (mode) {
    "forced" -> subs.firstOrNull { it.forced && (it.lang.isEmpty() || it.lang.startsWith("it", ignoreCase = true)) }
    "ita", "eng" -> subs.firstOrNull { !it.forced && it.lang.startsWith(mode.take(2), ignoreCase = true) }
    else -> null
}

/**
 * Modalità da ricordare dopo una scelta fatta a mano nel player, o null se non c'è niente da cambiare
 * (la selezione è quella automatica, oppure è una traccia in una lingua che non gestiamo).
 * [selectedKey] = chiave del sottotitolo acceso, null se è una traccia non nostra; [anySelected] = c'è un sottotitolo acceso.
 */
fun pickedSubMode(subs: List<NightSub>, current: String, anySelected: Boolean, selectedKey: String?, selectedLang: String?): String? {
    val chosen = subs.firstOrNull { it.key == selectedKey }
    if (chosen == defaultSub(subs, current) && (chosen != null || !anySelected)) return null
    val lang = chosen?.lang ?: selectedLang.orEmpty()
    return when {
        !anySelected -> "off"
        chosen?.forced == true -> "forced"
        lang.startsWith("it", ignoreCase = true) -> "ita"
        lang.startsWith("en", ignoreCase = true) -> "eng"
        else -> null
    }?.takeIf { it != current }
}

val NightProgress?.resumable get() = this != null && pos > 0 && dur > 0

fun fmtMin(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "${min.coerceAtLeast(1)} min"
fun fmtTime(sec: Int) = (sec / 3600).let { h ->
    if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, sec / 60 % 60, sec % 60) else String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
}
