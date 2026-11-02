package com.freckles.tv

import android.app.Application
import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.data.remote.TmdbApi

class FreckleApp : Application() {
    // DI manuale: niente Hilt, l'app è piccola.
    val db by lazy { AppDatabase.create(this) }
    val api by lazy { TmdbApi.create() }
    val repository by lazy { com.freckles.tv.data.Repository(db, api) }
    val prefs by lazy { UserPrefs(this) }
}
