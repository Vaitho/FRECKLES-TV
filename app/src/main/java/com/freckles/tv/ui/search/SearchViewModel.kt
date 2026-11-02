package com.freckles.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.RawgGame
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
    val games: List<RawgGame> = emptyList(),
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

    fun onMode(m: SearchMode) {
        _mode.value = m
    }

    val state: StateFlow<SearchUi> = combine(_query.debounce(400), _mode) { q, m -> q.trim() to m }
        .distinctUntilChanged()
        .flatMapLatest { (q, m) ->
            flow {
                if (q.length < 2) {
                    emit(SearchUi())
                } else {
                    emit(SearchUi(loading = true))
                    emit(
                        runCatching {
                            if (m == SearchMode.Media) SearchUi(results = repo.search(q))
                            else SearchUi(games = repo.searchGames(q))
                        }.getOrElse { SearchUi(error = "Ricerca non riuscita: ${it.message ?: "controlla la connessione"}") }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUi())
}
