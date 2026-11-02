package com.freckles.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("user_prefs")

/** Profilo locale + aspetto. L'app è standalone: l'unico "account" è un nome utente. */
data class Profile(
    /** null = primo avvio, nome non ancora impostato. */
    val name: String? = null,
    val emoji: String = "🍿",
    val colorIdx: Int = 0,
    val photoPath: String? = null,
    /** "system" | "light" | "dark" */
    val themeMode: String = "system",
    /** id di un [com.freckles.tv.ui.theme.Accent] oppure "dynamic". */
    val accent: String = "violet",
    /** Fino a 5 preferiti, nel formato "s:<id serie>" o "m:<id film>". */
    val favorites: List<String> = emptyList(),
    /** Fino a 5 videogiochi preferiti, nel formato "g:<id gioco>". */
    val gameFavorites: List<String> = emptyList(),
    /** Cartella (SAF tree URI) del backup automatico giornaliero; null = disattivato. */
    val autoBackupUri: String? = null,
    val lastAutoBackup: Long? = null,
)

class UserPrefs(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("username")
        val emoji = stringPreferencesKey("avatar_emoji")
        val color = intPreferencesKey("avatar_color")
        val photo = stringPreferencesKey("avatar_photo")
        val mode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("theme_accent")
        val favs = stringPreferencesKey("top_favorites")
        val gameFavs = stringPreferencesKey("top_game_favorites")
        val backupUri = stringPreferencesKey("auto_backup_uri")
        val lastBackup = androidx.datastore.preferences.core.longPreferencesKey("last_auto_backup")
    }

    val profile: Flow<Profile> = context.store.data.map { p ->
        Profile(
            name = p[K.name],
            emoji = p[K.emoji] ?: "🍿",
            colorIdx = p[K.color] ?: 0,
            photoPath = p[K.photo],
            themeMode = p[K.mode] ?: "system",
            accent = p[K.accent] ?: "violet",
            favorites = p[K.favs]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            gameFavorites = p[K.gameFavs]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            autoBackupUri = p[K.backupUri],
            lastAutoBackup = p[K.lastBackup],
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    suspend fun setName(name: String) = edit { it[K.name] = name.trim() }
    suspend fun setAvatar(emoji: String, colorIdx: Int) = edit { it[K.emoji] = emoji; it[K.color] = colorIdx }
    suspend fun setPhoto(path: String?) = edit { if (path == null) it.remove(K.photo) else it[K.photo] = path }
    suspend fun setThemeMode(mode: String) = edit { it[K.mode] = mode }
    suspend fun setAccent(id: String) = edit { it[K.accent] = id }
    suspend fun setFavorites(list: List<String>) = edit { it[K.favs] = list.take(5).joinToString(",") }
    suspend fun setAutoBackupUri(uri: String?) = edit { if (uri == null) it.remove(K.backupUri) else it[K.backupUri] = uri }
    suspend fun setLastAutoBackup(time: Long) = edit { it[K.lastBackup] = time }
    suspend fun setGameFavorites(list: List<String>) = edit { it[K.gameFavs] = list.take(5).joinToString(",") }
}

