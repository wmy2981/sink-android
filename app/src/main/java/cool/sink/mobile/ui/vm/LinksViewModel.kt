package cool.sink.mobile.ui.vm

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.R
import cool.sink.mobile.data.Link
import cool.sink.mobile.data.LinkCounters
import cool.sink.mobile.data.SearchItem
import cool.sink.mobile.data.SinkApiFactory
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.data.TagCount
import cool.sink.mobile.util.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption(val apiValue: String, @StringRes val labelRes: Int) {
    NEWEST("newest", R.string.sort_newest),
    OLDEST("oldest", R.string.sort_oldest),
    AZ("az", R.string.sort_az),
    ZA("za", R.string.sort_za),
}

enum class StatusFilter(val apiValue: String, @StringRes val labelRes: Int) {
    ACTIVE("active", R.string.status_active),
    EXPIRED("expired", R.string.status_expired),
    ALL("all", R.string.status_all),
}

class LinksViewModel(private val repository: SinkRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val searching: Boolean = false,
        val links: List<Link> = emptyList(),
        val searchResults: List<SearchItem>? = null,
        val query: String = "",
        val sort: SortOption = SortOption.NEWEST,
        val status: StatusFilter = StatusFilter.ACTIVE,
        val selectedTag: String? = null,
        val tags: List<TagCount> = emptyList(),
        val counters: Map<String, LinkCounters> = emptyMap(),
        val listComplete: Boolean = true,
        val loadMoreFailed: Boolean = false,
        val cursor: String? = null,
        val error: UiText? = null,
        val baseUrl: String = "",
        val host: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = repository.currentSettings()
            _state.update {
                it.copy(
                    baseUrl = settings.baseUrl,
                    host = SinkApiFactory.hostOf(settings.baseUrl),
                )
            }
        }
        refresh()
        loadTags()
    }

    fun refresh(showIndicator: Boolean = true, fromPull: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(snapshot.query) }
            return
        }
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = showIndicator && it.links.isEmpty(),
                    refreshing = fromPull,
                    loadingMore = false,
                    error = null,
                    loadMoreFailed = false,
                )
            }
            try {
                val page = repository.listLinks(
                    sort = snapshot.sort.apiValue,
                    status = snapshot.status.apiValue,
                    tag = snapshot.selectedTag,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        links = page.links,
                        cursor = page.cursor,
                        listComplete = page.listComplete,
                        loadMoreFailed = false,
                        searchResults = null,
                    )
                }
                loadCounters(page.links)
                loadTags()
            } catch (error: SinkException) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = error.text)
                }
                // The empty state already renders the error; keep it visible otherwise.
                if (_state.value.links.isNotEmpty() || _state.value.searchResults != null) {
                    _messages.send(error.text)
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loadingMore || snapshot.listComplete || snapshot.query.isNotBlank()) return
        val cursor = snapshot.cursor ?: return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
            try {
                val page = repository.listLinks(
                    cursor = cursor,
                    sort = snapshot.sort.apiValue,
                    status = snapshot.status.apiValue,
                    tag = snapshot.selectedTag,
                )
                _state.update {
                    it.copy(
                        loadingMore = false,
                        links = it.links + page.links,
                        cursor = page.cursor,
                        listComplete = page.listComplete,
                    )
                }
                loadCounters(page.links)
            } catch (error: SinkException) {
                _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                _messages.send(error.text)
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(searchResults = null, searching = false) }
            refresh(showIndicator = false)
            return
        }
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        // A cancelled refresh must not leave the list stuck behind its spinner.
        _state.update { it.copy(loading = false, refreshing = false, loadingMore = false) }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(value)
        }
    }

    fun onSortChange(sort: SortOption) {
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun onStatusChange(status: StatusFilter) {
        _state.update { it.copy(status = status) }
        refresh()
    }

    fun onTagChange(tag: String?) {
        _state.update { it.copy(selectedTag = tag) }
        refresh()
    }

    fun delete(link: Link) {
        viewModelScope.launch {
            try {
                repository.deleteLink(link.slug)
                _state.update { state ->
                    state.copy(
                        links = state.links.filterNot { it.slug == link.slug },
                        searchResults = state.searchResults?.filterNot { it.slug == link.slug },
                        counters = link.id?.let { id -> state.counters - id } ?: state.counters,
                    )
                }
                _messages.send(UiText(R.string.snackbar_deleted, listOf(link.slug)))
                loadTags()
            } catch (error: SinkException) {
                _messages.send(error.text)
            }
        }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true, error = null) }
        try {
            val results = repository.searchLinks(query)
            if (_state.value.query.trim() == query.trim()) {
                _state.update { it.copy(searching = false, searchResults = results, error = null) }
            }
        } catch (error: SinkException) {
            _state.update { it.copy(searching = false, error = error.text) }
            if (_state.value.links.isNotEmpty() || _state.value.searchResults != null) {
                _messages.send(error.text)
            }
        }
    }

    private fun loadCounters(links: List<Link>) {
        val ids = links.mapNotNull { it.id }.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.linkCounters(ids) }
                .onSuccess { list ->
                    val fetched = list.associateBy { it.id }
                    val values = ids.associateWith { id -> fetched[id] ?: LinkCounters(id = id) }
                    _state.update { state -> state.copy(counters = state.counters + values) }
                }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.tags() }
                .onSuccess { tags -> _state.update { it.copy(tags = tags) } }
        }
    }

    companion object {
        fun factory(repository: SinkRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LinksViewModel(repository) }
        }
    }
}
