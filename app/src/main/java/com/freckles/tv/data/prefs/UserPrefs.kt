package com.freckles.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("user_prefs")

/** Preferenze locali: l'app è standalone, l'unico "account" è un nome utente. */
class UserPrefs(private val context: Context) {
    private val usernameKey = stringPreferencesKey("username")

    /** null = primo avvio, nome non ancora impostato. */
    val username: Flow<String?> = context.store.data.map { it[usernameKey] }

    suspend fun setUsername(name: String) {
        context.store.edit { it[usernameKey] = name.trim() }
    }
}
