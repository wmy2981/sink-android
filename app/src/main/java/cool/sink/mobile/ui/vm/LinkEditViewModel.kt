package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.data.CreateLinkPayload
import cool.sink.mobile.data.EditLinkPayload
import cool.sink.mobile.data.Link
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.util.isValidHttpUrl
import cool.sink.mobile.util.normalizeTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val slugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", RegexOption.IGNORE_CASE)

class LinkEditViewModel(
    private val repository: SinkRepository,
    private val linkSlug: String?,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val saving: Boolean = false,
        val isEdit: Boolean = false,
        val slug: String = "",
        val url: String = "",
        val comment: String = "",
        val expiration: Long? = null,
        val tags: List<String> = emptyList(),
        val knownTags: List<String> = emptyList(),
        val urlError: String? = null,
        val slugError: String? = null,
        val error: String? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            loading = linkSlug != null,
            isEdit = linkSlug != null,
            slug = linkSlug.orEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var original: Link? = null

    init {
        loadTags()
        if (linkSlug != null) loadLink(linkSlug)
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, urlError = null) }

    fun onSlugChange(value: String) = _state.update {
        it.copy(slug = value.trim().lowercase(), slugError = null)
    }

    fun onCommentChange(value: String) = _state.update { it.copy(comment = value) }

    fun onExpirationChange(value: Long?) = _state.update { it.copy(expiration = value) }

    fun addTag(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isEmpty()) return
        _state.update { state ->
            when {
                state.tags.size >= 10 -> state
                tag.length > 32 -> state
                state.tags.contains(tag) -> state
                else -> state.copy(tags = state.tags + tag)
            }
        }
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun toggleTag(tag: String) {
        val normalized = normalizeTag(tag)
        _state.update {
            if (it.tags.contains(normalized)) it.copy(tags = it.tags - normalized)
            else if (it.tags.size < 10) it.copy(tags = it.tags + normalized)
            else it
        }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving) return
        if (!isValidHttpUrl(snapshot.url)) {
            _state.update { it.copy(urlError = "请输入以 http:// 或 https:// 开头的完整链接") }
            return
        }
        val slug = snapshot.slug.trim()
        if (!snapshot.isEdit && slug.isNotEmpty() && (!slugPattern.matches(slug) || slug.equals("dashboard", true))) {
            _state.update { it.copy(slugError = "短码只能包含字母、数字和连字符，且不能占用 dashboard") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = original
                val comment = snapshot.comment.trim().ifEmpty { null }
                if (existing == null) {
                    repository.createLink(
                        CreateLinkPayload(
                            url = snapshot.url.trim(),
                            slug = slug.ifEmpty { null },
                            comment = comment,
                            expiration = snapshot.expiration,
                            tags = snapshot.tags,
                        ),
                    )
                } else {
                    repository.editLink(
                        EditLinkPayload.from(
                            original = existing,
                            url = snapshot.url.trim(),
                            comment = comment,
                            expiration = snapshot.expiration,
                            tags = snapshot.tags,
                        ),
                    )
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (error: SinkException) {
                _state.update { it.copy(saving = false, error = error.message ?: "保存失败") }
            }
        }
    }

    private fun loadLink(slug: String) {
        viewModelScope.launch {
            try {
                val link = repository.link(slug)
                original = link
                _state.update {
                    it.copy(
                        loading = false,
                        slug = link.slug,
                        url = link.url,
                        comment = link.comment.orEmpty(),
                        expiration = link.expiration,
                        tags = link.tags,
                    )
                }
            } catch (error: SinkException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.tags() }
                .onSuccess { tags -> _state.update { it.copy(knownTags = tags.map { tag -> tag.name }) } }
        }
    }

    companion object {
        fun factory(repository: SinkRepository, slug: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { LinkEditViewModel(repository, slug) }
        }
    }
}
