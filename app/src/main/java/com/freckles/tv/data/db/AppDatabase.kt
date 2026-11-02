package com.freckles.tv.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShowEntity::class, EpisodeEntity::class, MovieEntity::class, GameEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun showDao(): ShowDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun movieDao(): MovieDao
    abstract fun gameDao(): GameDao

    companion object {
        /**
         * 3 -> 4: i giochi non dipendono più da RAWG. `rawgId` diventa `extId` ("rawg:<id>"),
         * nuova colonna `backdropUrl` (per i vecchi giochi uguale alla copertina).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `extId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `overview` TEXT, `imageUrl` TEXT, `backdropUrl` TEXT, `released` TEXT, " +
                        "`platforms` TEXT, `genres` TEXT, `status` TEXT NOT NULL, `minutesPlayed` INTEGER NOT NULL, " +
                        "`rating` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `finishedAt` INTEGER, `addedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `games_new` (id, extId, title, overview, imageUrl, backdropUrl, released, platforms, genres, " +
                        "status, minutesPlayed, rating, isFavorite, finishedAt, addedAt) " +
                        "SELECT id, 'rawg:' || rawgId, title, overview, imageUrl, imageUrl, released, platforms, genres, " +
                        "status, minutesPlayed, rating, isFavorite, finishedAt, addedAt FROM `games`"
                )
                db.execSQL("DROP TABLE `games`")
                db.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
                db.execSQL("CREATE UNIQUE INDEX `index_games_extId` ON `games` (`extId`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "freckles.db")
                .addMigrations(MIGRATION_3_4)
                .build()
    }
}
