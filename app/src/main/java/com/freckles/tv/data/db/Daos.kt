package com.freckles.tv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {
    @Query("SELECT * FROM shows ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE id = :id")
    fun observe(id: Long): Flow<ShowEntity?>

    @Query("SELECT * FROM shows WHERE tmdbId = :tmdbId")
    suspend fun byTmdbId(tmdbId: Int): ShowEntity?

    @Query("SELECT * FROM shows WHERE notifyEnabled = 1")
    suspend fun notifyShows(): List<ShowEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(show: ShowEntity): Long

    @Update
    suspend fun update(show: ShowEntity)

    @Query("DELETE FROM shows WHERE id = :id")
    suspend fun delete(id: Long)

    /** Episodi non special visti / totali per ogni serie. */
    @Query(
        """SELECT showId, SUM(watched) AS watched, COUNT(*) AS total
           FROM episodes WHERE isSpecial = 0 GROUP BY showId"""
    )
    fun observeProgress(): Flow<List<ShowProgress>>
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY season, number")
    fun observeForShow(showId: Long): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE showId = :showId AND season = :season AND number = :number")
    suspend fun find(showId: Long, season: Int, number: Int): EpisodeEntity?

    @Update
    suspend fun update(episode: EpisodeEntity)

    @Query(
        """UPDATE episodes SET watched = :watched,
           watchedAt = CASE WHEN :watched = 1 THEN :now ELSE NULL END,
           watchCount = CASE WHEN :watched = 1 THEN MAX(watchCount, 1) ELSE 0 END
           WHERE showId = :showId AND season = :season AND isSpecial = 0"""
    )
    suspend fun setSeasonWatched(showId: Long, season: Int, watched: Boolean, now: Long)

    /** Minuti totali visti (rewatch inclusi), esclusi gli special. */
    @Query("SELECT COALESCE(SUM(runtime * watchCount), 0) FROM episodes WHERE isSpecial = 0 AND watchCount > 0")
    fun observeWatchedMinutes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM episodes WHERE isSpecial = 0 AND watched = 1")
    fun observeWatchedCount(): Flow<Int>
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    fun observe(id: Long): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId")
    suspend fun byTmdbId(tmdbId: Int): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(movie: MovieEntity): Long

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(SUM(runtime * watchCount), 0) FROM movies WHERE watchCount > 0")
    fun observeWatchedMinutes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM movies WHERE watched = 1")
    fun observeWatchedCount(): Flow<Int>
}
