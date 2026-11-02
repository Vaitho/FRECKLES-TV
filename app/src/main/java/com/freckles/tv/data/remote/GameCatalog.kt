package com.freckles.tv.data.remote

import com.freckles.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** Un risultato di ricerca. `extId` è "igdb:<id>" oppure "steam:<appid>". */
data class GameResult(
    val extId: String,
    val title: String,
    val year: String?,
    val platforms: String?,
    val imageUrl: String?,
)

data class GameSearch(val items: List<GameResult>, val source: String)

data class GameInfo(
    val extId: String,
    val title: String,
    val overview: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val released: String?,
    val platforms: String?,
    val genres: String?,
    /** Durata media per finirlo, in ore (se nota). */
    val hours: Int?,
)

/**
 * Catalogo dei videogiochi: IGDB (Twitch) come fonte principale, Steam come riserva
 * quando IGDB non risponde. Entrambi sono interrogati con OkHttp.
 */
class GameCatalog {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedToken: Pair<String, Long>? = null

    suspend fun search(query: String): GameSearch = withContext(Dispatchers.IO) {
        val q = query.replace("\"", "").replace("\\", "").trim()
        runCatching { igdbSearch(q) }.fold(
            { GameSearch(it, "igdb") },
            { igdbError ->
                val steam = runCatching { steamSearch(q) }.getOrNull() ?: throw igdbError
                GameSearch(steam, "steam")
            },
        )
    }

    suspend fun details(extId: String): GameInfo = withContext(Dispatchers.IO) {
        val id = extId.substringAfter(':')
        when (extId.substringBefore(':')) {
            "igdb" -> igdbDetails(id.toLong())
            "steam" -> steamDetails(id.toLong())
            else -> error("fonte non supportata: $extId")
        }
    }

    // ---------------- IGDB ----------------

    private fun token(force: Boolean): String {
        val cached = cachedToken
        if (!force && cached != null && System.currentTimeMillis() < cached.second) return cached.first
        val url = "https://id.twitch.tv/oauth2/token?client_id=${BuildConfig.IGDB_CLIENT_ID}" +
            "&client_secret=${BuildConfig.IGDB_CLIENT_SECRET}&grant_type=client_credentials"
        client.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute().use { r ->
            if (!r.isSuccessful) error("Twitch ${r.code}")
            val o = json.parseToJsonElement(r.body!!.string()).jsonObject
            val tok = o["access_token"]!!.jsonPrimitive.content
            val expires = o["expires_in"]!!.jsonPrimitive.long
            cachedToken = tok to System.currentTimeMillis() + (expires - 3600) * 1000
            return tok
        }
    }

    private fun igdb(endpoint: String, body: String): JsonArray {
        fun call(tok: String) = client.newCall(
            Request.Builder().url("https://api.igdb.com/v4/$endpoint")
                .header("Client-ID", BuildConfig.IGDB_CLIENT_ID)
                .header("Authorization", "Bearer $tok")
                .header("Accept", "application/json")
                .post(body.toRequestBody("text/plain".toMediaType()))
                .build()
        ).execute()

        var resp = call(token(false))
        if (resp.code == 401) { // token scaduto o revocato
            resp.close()
            resp = call(token(true))
        }
        resp.use {
            if (!it.isSuccessful) error("IGDB ${it.code}")
            return json.parseToJsonElement(it.body!!.string()).jsonArray
        }
    }

    private fun igdbSearch(q: String): List<GameResult> {
        // "contiene il testo", niente edizioni/DLC, i più popolari per primi
        val body = """where name ~ *"$q"* & version_parent = null & parent_game = null;
            fields name,first_release_date,platforms.name,cover.image_id;
            sort total_rating_count desc; limit 20;"""
        return igdb("games", body).map { g ->
            val o = g.jsonObject
            GameResult(
                extId = "igdb:${o["id"]!!.jsonPrimitive.long}",
                title = o.str("name") ?: "?",
                year = o.date("first_release_date")?.take(4),
                platforms = o.names("platforms", 3),
                imageUrl = o.imageId("cover")?.let { igdbImage(it, "t_cover_big") },
            )
        }
    }

    private fun igdbDetails(id: Long): GameInfo {
        val o = igdb(
            "games",
            "where id = $id; fields name,summary,first_release_date,platforms.name,genres.name," +
                "cover.image_id,artworks.image_id,screenshots.image_id; limit 1;",
        ).firstOrNull()?.jsonObject ?: error("gioco non trovato")
        val hours = runCatching {
            igdb("game_time_to_beats", "where game_id = $id; fields normally,hastily; limit 1;")
                .firstOrNull()?.jsonObject?.let { t -> (t["normally"] ?: t["hastily"])?.jsonPrimitive?.doubleOrNull }
                ?.let { (it / 3600).toInt() }?.takeIf { it > 0 }
        }.getOrNull()
        val backdropId = o.firstImageId("artworks") ?: o.firstImageId("screenshots")
        return GameInfo(
            extId = "igdb:$id",
            title = o.str("name") ?: "?",
            overview = o.str("summary"),
            imageUrl = o.imageId("cover")?.let { igdbImage(it, "t_cover_big") },
            backdropUrl = backdropId?.let { igdbImage(it, "t_1080p") } ?: o.imageId("cover")?.let { igdbImage(it, "t_1080p") },
            released = o.date("first_release_date"),
            platforms = o.names("platforms", 99),
            genres = o.names("genres", 99),
            hours = hours,
        )
    }

    private fun igdbImage(imageId: String, size: String) = "https://images.igdb.com/igdb/image/upload/$size/$imageId.jpg"

    // ---------------- Steam (riserva) ----------------

    private fun steamGet(url: String): JsonElement =
        client.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) error("Steam ${it.code}")
            json.parseToJsonElement(it.body!!.string())
        }

    private fun steamCover(id: Long) = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg"

    private fun steamSearch(q: String): List<GameResult> {
        val url = "https://store.steampowered.com/api/storesearch/?term=${URLEncoder.encode(q, "UTF-8")}&l=italian&cc=IT"
        val items = steamGet(url).jsonObject["items"]?.jsonArray ?: return emptyList()
        return items.map { it.jsonObject }.filter { it.str("type") == "app" }.map { o ->
            val id = o["id"]!!.jsonPrimitive.long
            val p = o["platforms"] as? JsonObject
            val plat = buildList {
                if (p?.get("windows")?.jsonPrimitive?.contentOrNull == "true") add("PC")
                if (p?.get("mac")?.jsonPrimitive?.contentOrNull == "true") add("macOS")
                if (p?.get("linux")?.jsonPrimitive?.contentOrNull == "true") add("Linux")
            }.joinToString(", ").ifBlank { null }
            GameResult("steam:$id", o.str("name") ?: "?", null, plat, steamCover(id))
        }
    }

    private fun steamDetails(id: Long): GameInfo {
        val root = steamGet("https://store.steampowered.com/api/appdetails?appids=$id&l=italian&cc=it").jsonObject[id.toString()]!!.jsonObject
        val d = root["data"]?.jsonObject ?: error("gioco non trovato")
        val year = Regex("(19|20)\\d{2}").findAll(d["release_date"]?.jsonObject?.str("date").orEmpty()).lastOrNull()?.value
        return GameInfo(
            extId = "steam:$id",
            title = d.str("name") ?: "?",
            overview = d.str("short_description")?.let { html(it) },
            imageUrl = steamCover(id),
            backdropUrl = d.str("header_image"),
            released = year,
            platforms = (d["platforms"] as? JsonObject)?.let { p ->
                listOfNotNull(
                    "PC".takeIf { p.str("windows") == "true" }, "macOS".takeIf { p.str("mac") == "true" }, "Linux".takeIf { p.str("linux") == "true" },
                ).joinToString(", ").ifBlank { null }
            },
            genres = d["genres"]?.jsonArray?.mapNotNull { it.jsonObject.str("description") }?.joinToString(", ")?.ifBlank { null },
            hours = null,
        )
    }

    private fun html(s: String) = s.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace(Regex("<[^>]*>"), "")

    // ---------------- helper JSON ----------------

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.date(key: String): String? =
        (this[key] as? JsonPrimitive)?.doubleOrNull?.let { Instant.ofEpochSecond(it.toLong()).atZone(ZoneOffset.UTC).toLocalDate().toString() }
    private fun JsonObject.names(key: String, limit: Int): String? =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonObject.str("name") }?.take(limit)?.joinToString(", ")?.ifBlank { null }
    private fun JsonObject.imageId(key: String): String? = (this[key] as? JsonObject)?.str("image_id")
    private fun JsonObject.firstImageId(key: String): String? =
        (this[key] as? JsonArray)?.firstOrNull()?.jsonObject?.str("image_id")
}
