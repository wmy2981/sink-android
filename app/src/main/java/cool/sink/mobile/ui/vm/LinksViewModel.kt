package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.data.Link
import cool.sink.mobile.data.SearchItem
import cool.sink.mobile.data.SinkApiFactory
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.data.TagCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption(val apiValue: String, val label: String) {
    NEWEST("newest", "最新优先"),
    OLDEST("oldest", "最早优先"),
    AZ("az", "短码 A→Z"),
    ZA("za", "短码 Z→A"),
}

enum class StatusFilter(val apiValue: String, val label: String) {
    ACTIVE("active", "有效"),
    EXPIRED("expired", "已过期"),
    ALL("all", "全部"),
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
        val listComplete: Boolean = true,
        val cursor: String? = null,
        val error: String? = null,
        val baseUrl: String = "",
        val host: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var searchJob: Job? = null

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

    fun refresh(showIndicator: Boolean = true) {
        val snapshot = _state.value
        if (snapshot.query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(snapshot.query) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = showIndicator && it.links.isEmpty(),
                    refreshing = !showIndicator,
                    error = null,
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
                        links = page.links,
                        cursor = page.cursor,
                        listComplete = page.listComplete,
                        searchResults = null,
                    )
                }
            } catch (error: SinkException) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = error.message)
                }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loadingMore || snapshot.listComplete || snapshot.query.isNotBlank()) return
        val cursor = snapshot.cursor ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
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
            } catch (error: SinkException) {
                _state.update { it.copy(loadingMore = false) }
                _messages.send(error.message ?: "加载失败")
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
                    )
                }
                _messages.send("已删除 ${link.slug}")
            } catch (error: SinkException) {
                _messages.send(error.message ?: "删除失败")
            }
        }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true, error = null) }
        try {
            val results = repository.searchLinks(query)
            if (_state.value.query.trim() == query.trim()) {
                _state.update { it.copy(searching = false, searchResults = results) }
            }
        } catch (error: SinkException) {
            _state.update { it.copy(searching = false, error = error.message) }
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
