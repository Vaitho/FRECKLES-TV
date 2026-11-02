package com.freckles.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.SearchItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class SearchUi(
    val loading: Boolean = false,
    val results: List<SearchItem> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(repo: Repository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun onQuery(q: String) {
        _query.value = q
    }

    val state: StateFlow<SearchUi> = _query
        .debounce(400)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.trim().length < 2) {
                    emit(SearchUi())
                } else {
                    emit(SearchUi(loading = true))
                    emit(
                        runCatching { repo.search(q.trim()) }.fold(
                            { SearchUi(results = it) },
                            { SearchUi(error = "Ricerca non riuscita: ${it.message ?: "controlla la connessione"}") },
                        )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUi())
}
