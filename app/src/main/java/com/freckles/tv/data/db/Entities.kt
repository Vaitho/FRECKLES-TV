package com.freckles.tv.data.db

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
)
