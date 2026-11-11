package com.freckles.tv.ui.watch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freckles.tv.data.remote.NightApi
import com.freckles.tv.data.remote.NightEpisode
import com.freckles.tv.data.remote.NightMovie
import com.freckles.tv.data.remote.NightProgress
import com.freckles.tv.data.remote.NightSeries
import java.util.Locale

/** Libreria e progresso del server, condivisi tra la griglia Guarda e le schede di dettaglio. */
class NightData(val api: NightApi) {
    var movies by mutableStateOf<List<NightMovie>?>(null); private set
    var series by mutableStateOf<List<NightSeries>>(emptyList()); private set
    var progress by mutableStateOf<Map<String, NightProgress>>(emptyMap()); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set

    /** Ricarica libreria e progresso; se fallisce tiene i dati già caricati. */
    suspend fun refresh() {
        if (api.base.isEmpty() || loading) return
        loading = true
        runCatching {
            val (m, s) = api.library()
            val p = api.progress()
            movies = m; series = s; progress = p; error = null
        }.onFailure { error = "Il server non risponde (${api.base}). Controlla che sia acceso e che il telefono sia sulla stessa rete." }
        loading = false
    }

    suspend fun setWatched(fileIds: List<String>, watched: Boolean) {
        runCatching { fileIds.forEach { progress = progress + (it to api.setWatched(it, watched)) } }
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

val NightProgress?.resumable get() = this != null && pos > 0 && dur > 0

fun fmtMin(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "${min.coerceAtLeast(1)} min"
fun fmtTime(sec: Int) = (sec / 3600).let { h ->
    if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, sec / 60 % 60, sec % 60) else String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
}
