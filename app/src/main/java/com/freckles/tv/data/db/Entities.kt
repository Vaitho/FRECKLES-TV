package com.freckles.tv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shows", indices = [Index(value = ["tmdbId"], unique = true)])
data class ShowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val tvdbId: Int? = null,
    val imdbId: String? = null,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val status: String? = null,
    val genres: String? = null,
    /** Voto personale 0-5 (0 = nessuno). */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    val isFavorite: Boolean = false,
    /** Campanella: notifica per nuove stagioni/episodi. */
    val notifyEnabled: Boolean = false,
    val defaultRuntime: Int = 40,
    /** Stato noto all'ultimo controllo, per rilevare le novità. */
    val knownSeasonCount: Int = 0,
    val knownNextAirDate: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(ShowEntity::class, ["id"], ["showId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("showId"), Index(value = ["showId", "season", "number"], unique = true)],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val showId: Long,
    val season: Int,
    val number: Int,
    val name: String? = null,
    val runtime: Int = 40,
    val airDate: String? = null,
    val isSpecial: Boolean = false,
    val watched: Boolean = false,
    val watchedAt: Long? = null,
    /** Numero di visioni (rewatch inclusi). */
    val watchCount: Int = 0,
)

@Entity(tableName = "movies", indices = [Index(value = ["tmdbId"], unique = true)])
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val imdbId: String? = null,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val runtime: Int = 100,
    val releaseDate: String? = null,
    val genres: String? = null,
    /** Voto personale 0-5 (0 = nessuno). */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    val isFavorite: Boolean = false,
    val watched: Boolean = false,
    val watchedAt: Long? = null,
    val watchCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)

data class ShowProgress(
    val showId: Long,
    val watched: Int,
    val total: Int,
    /** Data del primo episodio: serve a ordinare per uscita (le serie non hanno una data propria). */
    val firstAir: String? = null,
    /** Ultima visione di un episodio (ms), per ordinare per ultima visione. */
    val lastWatched: Long? = null,
)

data class MonthStat(val month: String, val minutes: Long)

object GameStatus {
    const val BACKLOG = "backlog"
    const val PLAYING = "playing"
    const val FINISHED = "finished"
    const val DROPPED = "dropped"

    val all = listOf(BACKLOG, PLAYING, FINISHED, DROPPED)
    fun label(s: String) = when (s) {
        BACKLOG -> "Da giocare"
        PLAYING -> "In corso"
        FINISHED -> "Finito"
        DROPPED -> "Abbandonato"
        else -> s
    }
}

@Entity(tableName = "games", indices = [Index(value = ["extId"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificatore esterno con la fonte: "igdb:123", "steam:620" o "rawg:3328" (giochi aggiunti in passato). */
    val extId: String,
    val title: String,
    val overview: String? = null,
    val imageUrl: String? = null,
    val backdropUrl: String? = null,
    val released: String? = null,
    val platforms: String? = null,
    val genres: String? = null,
    val status: String = GameStatus.BACKLOG,
    /** Tempo di gioco inserito a mano, in minuti. */
    val minutesPlayed: Int = 0,
    /** Voto personale 0-5 (0 = nessuno). */
    val rating: Int = 0,
    val isFavorite: Boolean = false,
    val finishedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

data class YearStat(val year: String, val minutes: Long)
data class ShowMinutes(val showId: Long, val minutes: Long)
data class SeasonCount(val season: Int, val n: Int)
