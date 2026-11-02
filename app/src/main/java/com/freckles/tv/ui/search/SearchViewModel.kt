package com.freckles.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.GameResult
import com.freckles.tv.data.remote.SearchItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

enum class SearchMode { Media, Games }

data class SearchUi(
    val loading: Boolean = false,
    val results: List<SearchItem> = emptyList(),
    val games: List<GameResult> = emptyList(),
    /** "igdb" o "steam" (riserva, quando IGDB non risponde). */
    val gameSource: String? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(repo: Repository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _mode = MutableStateFlow(SearchMode.Media)
    val mode: StateFlow<SearchMode> = _mode

    fun onQuery(q: String) {
        _query.value = q
    }

    private val _retry = MutableStateFlow(0)
    fun retry() {
        _retry.value++
    }

    fun onMode(m: SearchMode) {
        _mode.value = m
    }

    val state: StateFlow<SearchUi> = combine(_query.debounce(400), _mode, _retry) { q, m, r -> Triple(q.trim(), m, r) }
        .distinctUntilChanged()
        .flatMapLatest { (q, m, _) ->
            flow {
                if (q.length < 2) {
                    emit(SearchUi())
                } else {
                    emit(SearchUi(loading = true))
                    emit(
                        runCatching {
                            if (m == SearchMode.Media) SearchUi(results = repo.search(q))
                            else repo.searchGames(q).let { SearchUi(games = it.items, gameSource = it.source) }
                        }.getOrElse { SearchUi(error = errorText(it, m)) }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUi())
}

private fun errorText(e: Throwable, mode: SearchMode): String {
    val service = if (mode == SearchMode.Games) "Il servizio dei giochi" else "Il servizio di film e serie (TMDB)"
    return when (e) {
        is java.net.SocketTimeoutException -> "$service non risponde in questo momento. Riprova tra poco."
        is java.net.UnknownHostException -> "Nessuna connessione a internet."
        else -> "Ricerca non riuscita: ${e.message ?: "errore sconosciuto"}"
    }
}
