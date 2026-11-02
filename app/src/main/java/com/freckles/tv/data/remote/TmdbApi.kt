package com.freckles.tv.data.remote

import com.freckles.tv.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val TMDB_IMG = "https://image.tmdb.org/t/p/"

fun posterUrl(path: String?) = path?.let { "${TMDB_IMG}w342$it" }
fun backdropUrl(path: String?) = path?.let { "${TMDB_IMG}w780$it" }

@Serializable
data class SearchResponse(val results: List<SearchItem> = emptyList())

@Serializable
data class SearchItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
) {
    val displayTitle get() = title ?: name ?: ""
    val year get() = (releaseDate ?: firstAirDate)?.take(4)
}

@Serializable
data class ExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
)

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")

@Serializable
data class TvDetails(
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val status: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TvEpisode? = null,
    val seasons: List<SeasonSummary> = emptyList(),
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
    val genres: List<TmdbGenre> = emptyList(),
)

@Serializable
data class SeasonSummary(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
data class SeasonDetails(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<TvEpisode> = emptyList(),
)

@Serializable
data class TvEpisode(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class MovieDetails(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val runtime: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
)

@Serializable
data class FindResponse(
    @SerialName("tv_results") val tvResults: List<SearchItem> = emptyList(),
    @SerialName("movie_results") val movieResults: List<SearchItem> = emptyList(),
)

interface TmdbApi {
    @GET("search/multi")
    suspend fun search(@Query("query") query: String): SearchResponse

    @GET("trending/all/week")
    suspend fun trending(): SearchResponse

    @GET("tv/{id}?append_to_response=external_ids")
    suspend fun tv(@Path("id") id: Int): TvDetails

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int): SeasonDetails

    @GET("movie/{id}")
    suspend fun movie(@Path("id") id: Int): MovieDetails

    @GET("find/{externalId}")
    suspend fun find(
        @Path("externalId") externalId: String,
        @Query("external_source") source: String,
    ): FindResponse

    companion object {
        fun create(): TmdbApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.newBuilder()
                        .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                        .addQueryParameter("language", "it-IT")
                        .build()
                    chain.proceed(req.newBuilder().url(url).build())
                }
                .build()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }
    }
}
