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

@Serializable
data class RawgSearch(val results: List<RawgGame> = emptyList())

@Serializable
data class RawgNamed(val id: Int = 0, val name: String = "")

@Serializable
data class RawgPlatformWrap(val platform: RawgNamed = RawgNamed())

@Serializable
data class RawgGame(
    val id: Int,
    val name: String,
    val released: String? = null,
    @SerialName("background_image") val backgroundImage: String? = null,
    val metacritic: Int? = null,
    val playtime: Int = 0,
    val platforms: List<RawgPlatformWrap>? = null,
) {
    val year get() = released?.take(4)?.takeIf { it.isNotBlank() }
    val platformNames get() = platforms.orEmpty().map { it.platform.name }
}

@Serializable
data class RawgDetails(
    val id: Int,
    val name: String,
    val released: String? = null,
    @SerialName("background_image") val backgroundImage: String? = null,
    @SerialName("description_raw") val description: String? = null,
    val metacritic: Int? = null,
    val playtime: Int = 0,
    val platforms: List<RawgPlatformWrap>? = null,
    val genres: List<RawgNamed>? = null,
)

/** Le immagini RAWG si possono ridimensionare: molto più leggere per le liste. */
fun rawgThumb(url: String?): String? =
    url?.takeIf { "media.rawg.io/media/" in it && "/resize/" !in it }
        ?.replace("media.rawg.io/media/", "media.rawg.io/media/resize/420/-/") ?: url

interface RawgApi {
    @GET("games?page_size=20&ordering=-added")
    suspend fun search(@Query("search") query: String): RawgSearch

    @GET("games/{id}")
    suspend fun game(@Path("id") id: Int): RawgDetails

    companion object {
        fun create(): RawgApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.newBuilder().addQueryParameter("key", BuildConfig.RAWG_API_KEY).build()
                    chain.proceed(req.newBuilder().url(url).build())
                }
                .build()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            return Retrofit.Builder()
                .baseUrl("https://api.rawg.io/api/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RawgApi::class.java)
        }
    }
}
