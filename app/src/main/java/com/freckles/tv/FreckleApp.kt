package com.freckles.tv

import android.app.Application
import com.freckles.tv.data.db.AppDatabase
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.data.remote.TmdbApi

class FreckleApp : Application() {
    // DI manuale: niente Hilt, l'app è piccola.
    val db by lazy { AppDatabase.create(this) }
    val api by lazy { TmdbApi.create() }
    val rawg by lazy { com.freckles.tv.data.remote.RawgApi.create() }
    val repository by lazy { com.freckles.tv.data.Repository(db, api, rawg) }
    val prefs by lazy { UserPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        com.freckles.tv.notify.NewSeasonWorker.schedule(this)
    }
}
