package com.freckles.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NightVersion(val id: String, val label: String, val sizeBytes: Long)
data class NightMovie(
    val id: String,
    /** null se il server non ha trovato il film su TMDB. */
    val tmdbId: Int?,
    val title: String,
    val year: Int?,
    val image: String?,
    val backdrop: String?,
    val overview: String?,
    val runtimeMin: Int?,
    val genres: List<String>,
    val rating: Double?,
    val versions: List<NightVersion>,
)
data class NightEpisode(val id: String, val num: Int, val title: String, val image: String?, val overview: String?, val runtimeMin: Int?)
data class NightSeason(val n: Int, val episodes: List<NightEpisode>)
data class NightSeries(
    val id: String,
    val tmdbId: Int?,
    val title: String,
    val year: Int?,
    val image: String?,
    val backdrop: String?,
    val overview: String?,
    val genres: List<String>,
    val rating: Double?,
    val seasons: List<NightSeason>,
)
/** Posizione e durata in secondi; [time] = ultimo aggiornamento (ms), serve a trovare l'ultimo episodio guardato. */
data class NightProgress(val pos: Int, val dur: Int, val watched: Boolean, val time: Long = 0)
/** [forced] = solo le battute in lingua straniera, non tutto il dialogo. */
data class NightSub(val key: String, val lang: String, val label: String, val forced: Boolean = false)
data class NightVideo(val codec: String, val bits: Int, val w: Int, val h: Int)
/** Tracce di un file: sottotitoli di testo, formato del video e durata in secondi (0 se il server non la dice). */
data class NightTracks(val subs: List<NightSub> = emptyList(), val video: NightVideo? = null, val dur: Double = 0.0)

// org.json restituisce la stringa "null" per i valori null: qui diventano null veri.
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
private fun JSONObject.list(key: String): List<String> = optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
private fun JSONObject.num(key: String): Double? = if (isNull(key)) null else optDouble(key, 0.0).takeIf { it > 0 }

/** Client del server FrecklesNight (cartella FRECKLES-NIGHT): libreria, streaming, sottotitoli e progresso. */
class NightApi(url: String) {
    val base = url.trim().trimEnd('/').let { if (it.isNotEmpty() && !it.startsWith("http")) "http://$it" else it }

    // Server in LAN: se non risponde entro pochi secondi è meglio mostrare l'errore che restare in caricamento.
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    // Solo per i sottotitoli: la prima richiesta fa estrarre le tracce al server, che legge tutto il file.
    private val slowHttp = http.newBuilder().readTimeout(10, TimeUnit.MINUTES).build()

    private fun call(path: String, body: String? = null): String {
        val req = Request.Builder().url(base + path)
        if (body != null) req.post(body.toRequestBody("application/json".toMediaType()))
        http.newCall(req.build()).execute().use {
            check(it.isSuccessful) { "Server: ${it.code}" }
            return it.body?.string().orEmpty()
        }
    }

    fun abs(path: String?) = path?.let { if (it.startsWith("/")) base + it else it }
    fun mediaUrl(fileId: String) = "$base/media/$fileId"
    /** Video convertito dal server in H.264 a partire da [fromSec]: per i telefoni che non decodificano l'originale. */
    fun transcodeUrl(fileId: String, fromSec: Long) = "$base/transcode/$fileId?t=$fromSec"
    fun subUrl(fileId: String, key: String) = "$base/subs/$fileId/$key.vtt"

    /** Film e serie del server, in ordine alfabetico. */
    suspend fun library(): Pair<List<NightMovie>, List<NightSeries>> = withContext(Dispatchers.IO) {
        val lib = JSONObject(call("/api/library"))
        fun img(o: JSONObject) = abs(o.str("poster") ?: o.str("frame"))
        val ma = lib.getJSONArray("movies")
        val movies = List(ma.length()) { i ->
            val m = ma.getJSONObject(i)
            val v = m.getJSONArray("versions")
            NightMovie(
                id = m.getString("id"),
                tmdbId = m.optInt("tmdbId").takeIf { it > 0 },
                title = m.getString("title"),
                year = m.optInt("year").takeIf { it > 0 },
                image = img(m),
                backdrop = abs(m.str("backdrop") ?: m.str("frame")),
                overview = m.str("overview"),
                runtimeMin = m.optInt("runtime").takeIf { it > 0 },
                genres = m.list("genres"),
                rating = m.num("rating"),
                versions = List(v.length()) { v.getJSONObject(it).let { x -> NightVersion(x.getString("id"), x.getString("label"), x.optLong("size")) } },
            )
        }.filter { it.versions.isNotEmpty() }.sortedBy { it.title.lowercase() }
        val sa = lib.getJSONArray("series")
        val series = List(sa.length()) { i ->
            val s = sa.getJSONObject(i)
            val seasons = s.getJSONArray("seasons")
            NightSeries(
                id = s.getString("id"),
                tmdbId = s.optInt("tmdbId").takeIf { it > 0 },
                title = s.getString("title"),
                year = s.optInt("year").takeIf { it > 0 },
                image = img(s),
                backdrop = abs(s.str("backdrop") ?: s.str("frame")),
                overview = s.str("overview"),
                genres = s.list("genres"),
                rating = s.num("rating"),
                seasons = List(seasons.length()) { j ->
                    val se = seasons.getJSONObject(j)
                    val eps = se.getJSONArray("episodes")
                    NightSeason(se.getInt("n"), List(eps.length()) { k ->
                        val e = eps.getJSONObject(k)
                        NightEpisode(
                            e.getString("id"), e.optInt("e"), e.optString("title").takeIf { it != "null" }.orEmpty(),
                            abs(e.str("still") ?: e.str("frame")), e.str("overview"), e.optInt("runtime").takeIf { it > 0 },
                        )
                    })
                },
            )
        }.sortedBy { it.title.lowercase() }
        movies to series
    }

    private fun JSONObject.toProgress() = NightProgress(optInt("pos"), optInt("dur"), optBoolean("w"), optLong("t"))

    suspend fun progress(): Map<String, NightProgress> = withContext(Dispatchers.IO) {
        val o = JSONObject(call("/api/progress"))
        o.keys().asSequence().associateWith { o.getJSONObject(it).toProgress() }
    }

    /** Il server considera il film visto oltre il 92%. Posizione e durata in secondi. */
    suspend fun saveProgress(fileId: String, posSec: Long, durSec: Long): NightProgress = withContext(Dispatchers.IO) {
        JSONObject(call("/api/progress", JSONObject().put("id", fileId).put("pos", posSec).put("dur", durSec).toString())).toProgress()
    }

    /** Segna un file come visto (o non visto) e restituisce il nuovo stato. */
    suspend fun setWatched(fileId: String, watched: Boolean): NightProgress = withContext(Dispatchers.IO) {
        JSONObject(call("/api/progress", JSONObject().put("id", fileId).put("watched", watched).toString())).toProgress()
    }

    suspend fun tracks(fileId: String): NightTracks = withContext(Dispatchers.IO) {
        val o = JSONObject(call("/api/tracks/$fileId"))
        val arr: JSONArray = o.getJSONArray("subs")
        val subs = List(arr.length()) { i ->
            val s = arr.getJSONObject(i)
            NightSub(s.getString("key"), s.optString("lang"), s.optString("title").ifEmpty { s.optString("lang").ifEmpty { "Sottotitoli" } }, s.optBoolean("forced"))
        }
        val video = o.optJSONObject("video")?.let { NightVideo(it.optString("codec"), it.optInt("bits", 8), it.optInt("w"), it.optInt("h")) }
        NightTracks(subs, video, o.optDouble("dur", 0.0))
    }

    /** Scarica un sottotitolo per farlo estrarre e mettere in cache dal server. */
    suspend fun warm(url: String) = withContext(Dispatchers.IO) {
        slowHttp.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful && it.body?.bytes() != null }
    }
}
