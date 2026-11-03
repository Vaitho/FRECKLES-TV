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

    @Query("SELECT * FROM shows")
    suspend fun listAll(): List<ShowEntity>

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
        """SELECT showId, SUM(watched) AS watched, COUNT(*) AS total, MIN(airDate) AS firstAir, MAX(watchedAt) AS lastWatched
           FROM episodes WHERE isSpecial = 0 AND (watched = 1 OR (airDate IS NOT NULL AND airDate <= date('now', 'localtime')))
           GROUP BY showId"""
    )
    fun observeProgress(): Flow<List<ShowProgress>>
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY season, number")
    fun observeForShow(showId: Long): Flow<List<EpisodeEntity>>

    /** Inserisce solo gli episodi nuovi: non tocca quelli già presenti (e il loro stato di visione). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(episodes: List<EpisodeEntity>)

    /** Prossimo episodio da vedere per ogni serie già iniziata. */
    @Query(
        """SELECT * FROM episodes e
           WHERE e.isSpecial = 0 AND e.watched = 0 AND e.airDate IS NOT NULL AND e.airDate <= date('now', 'localtime')
           AND EXISTS (SELECT 1 FROM episodes w WHERE w.showId = e.showId AND w.watched = 1 AND w.isSpecial = 0)
           AND NOT EXISTS (SELECT 1 FROM episodes p WHERE p.showId = e.showId AND p.isSpecial = 0
               AND p.watched = 0 AND p.airDate IS NOT NULL AND p.airDate <= date('now', 'localtime')
               AND (p.season < e.season OR (p.season = e.season AND p.number < e.number)))"""
    )
    fun observeNext(): Flow<List<EpisodeEntity>>

    /** Ore per mese (ora locale) di episodi e film visti, rivisioni incluse. */
    @Query(
        """SELECT month, SUM(minutes) AS minutes FROM (
               SELECT strftime('%Y-%m', watchedAt / 1000, 'unixepoch', 'localtime') AS month, runtime * watchCount AS minutes
               FROM episodes WHERE watchCount > 0 AND isSpecial = 0 AND watchedAt IS NOT NULL
               UNION ALL
               SELECT strftime('%Y-%m', watchedAt / 1000, 'unixepoch', 'localtime'), runtime * watchCount
               FROM movies WHERE watchCount > 0 AND watchedAt IS NOT NULL
           ) GROUP BY month ORDER BY month DESC LIMIT 12"""
    )
    fun observeMonthly(): Flow<List<MonthStat>>

    @Query("SELECT season, COUNT(*) AS n FROM episodes WHERE showId = :showId GROUP BY season")
    suspend fun seasonCounts(showId: Long): List<SeasonCount>

    /** Aggiorna i metadati (es. data di uscita annunciata) senza toccare lo stato di visione. */
    @Query("UPDATE episodes SET name = :name, runtime = :runtime, airDate = :airDate WHERE showId = :showId AND season = :season AND number = :number")
    suspend fun updateMeta(showId: Long, season: Int, number: Int, name: String?, runtime: Int, airDate: String?)

    @Query(
        """SELECT showId, SUM(runtime * watchCount) AS minutes FROM episodes
           WHERE isSpecial = 0 AND watchCount > 0 GROUP BY showId ORDER BY minutes DESC"""
    )
    fun observeMinutesByShow(): Flow<List<ShowMinutes>>

    @Query(
        """SELECT strftime('%Y', watchedAt / 1000, 'unixepoch', 'localtime') AS year, SUM(runtime * MAX(watchCount, 1)) AS minutes
           FROM episodes WHERE watched = 1 AND isSpecial = 0 AND watchedAt IS NOT NULL GROUP BY year ORDER BY year"""
    )
    fun observeYearly(): Flow<List<YearStat>>

    @Query("SELECT * FROM episodes WHERE showId = :showId")
    suspend fun listForShow(showId: Long): List<EpisodeEntity>

    @Update
    suspend fun updateAll(episodes: List<EpisodeEntity>)

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

    /** Segna come visti tutti gli episodi già usciti (esclusi gli special). */
    @Query(
        """UPDATE episodes SET watched = 1, watchedAt = :now, watchCount = MAX(watchCount, 1)
           WHERE showId = :showId AND isSpecial = 0 AND watched = 0 AND airDate IS NOT NULL AND airDate <= :today"""
    )
    suspend fun markAiredWatched(showId: Long, today: String, now: Long)

    /** "Segna fino a qui": tutti gli episodi non visti fino a (season, number) compreso, special esclusi. */
    @Query(
        """UPDATE episodes SET watched = 1, watchedAt = :now, watchCount = MAX(watchCount, 1)
           WHERE showId = :showId AND isSpecial = 0 AND watched = 0 AND season > 0
           AND (season < :season OR (season = :season AND number <= :number))"""
    )
    suspend fun markUpTo(showId: Long, season: Int, number: Int, now: Long)

    /** Prossimo episodio in uscita per ogni serie seguita (iniziata o con campanella). */
    @Query(
        """SELECT * FROM episodes e
           WHERE e.isSpecial = 0 AND e.airDate > date('now', 'localtime')
           AND ((SELECT notifyEnabled FROM shows s WHERE s.id = e.showId) = 1
                OR EXISTS (SELECT 1 FROM episodes w WHERE w.showId = e.showId AND w.watched = 1 AND w.isSpecial = 0))
           AND NOT EXISTS (SELECT 1 FROM episodes p WHERE p.showId = e.showId AND p.isSpecial = 0 AND p.airDate > date('now', 'localtime')
               AND (p.airDate < e.airDate OR (p.airDate = e.airDate AND p.season * 1000 + p.number < e.season * 1000 + e.number)))
           ORDER BY e.airDate LIMIT 10"""
    )
    fun observeUpcoming(): Flow<List<EpisodeEntity>>

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

    @Query("SELECT * FROM movies")
    suspend fun listAll(): List<MovieEntity>

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

    @Query(
        """SELECT strftime('%Y', watchedAt / 1000, 'unixepoch', 'localtime') AS year, SUM(runtime * MAX(watchCount, 1)) AS minutes
           FROM movies WHERE watched = 1 AND watchedAt IS NOT NULL GROUP BY year ORDER BY year"""
    )
    fun observeYearly(): Flow<List<YearStat>>

    @Query("SELECT COALESCE(SUM(runtime * watchCount), 0) FROM movies WHERE watchCount > 0")
    fun observeWatchedMinutes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM movies WHERE watched = 1")
    fun observeWatchedCount(): Flow<Int>
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games")
    suspend fun listAll(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE extId = :extId")
    suspend fun byExtId(extId: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: GameEntity): Long

    @Update
    suspend fun update(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(SUM(minutesPlayed), 0) FROM games")
    fun observeMinutes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM games WHERE status = 'finished'")
    fun observeFinishedCount(): Flow<Int>
}
