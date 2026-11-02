package com.freckles.tv

import com.freckles.tv.data.importer.ImportShow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ImportParseTest {
    /** Usa il file reale se presente (variabile TVTIME_JSON), altrimenti salta. */
    @Test
    fun parsesRealExport() {
        val path = System.getenv("TVTIME_JSON")
        assumeTrue(path != null && File(path).exists())
        val shows = Json { ignoreUnknownKeys = true }.decodeFromString<List<ImportShow>>(File(path!!).readText())
        val watched = shows.sumOf { s -> s.seasons.filter { !it.isSpecials }.sumOf { se -> se.episodes.count { it.isWatched && !it.special } } }
        println("IMPORT shows=${shows.size} watchedEpisodes=$watched withTvdb=${shows.count { it.id.tvdb != null }}")
        assertTrue(shows.isNotEmpty())
    }

    @Test
    fun parsesMinimal() {
        val j = """[{"title":"X","id":{"tvdb":1,"imdb":null},"seasons":[{"number":1,"is_specials":false,"episodes":[{"number":1,"special":false,"is_watched":true,"watched_at":"2023-01-16T14:39:23Z","watched_count":1}]}]}]"""
        val s = Json { ignoreUnknownKeys = true }.decodeFromString<List<ImportShow>>(j)
        assertTrue(s[0].seasons[0].episodes[0].isWatched)
    }
}
