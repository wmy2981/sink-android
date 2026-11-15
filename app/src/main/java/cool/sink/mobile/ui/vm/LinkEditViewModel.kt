package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.R
import cool.sink.mobile.data.CreateLinkPayload
import cool.sink.mobile.data.EditLinkPayload
import cool.sink.mobile.data.Link
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.util.DEFAULT_SLUG_LENGTH
import cool.sink.mobile.util.IMAGE_MIME_TYPES
import cool.sink.mobile.util.MAX_COMMENT_LENGTH
import cool.sink.mobile.util.MAX_DESCRIPTION_LENGTH
import cool.sink.mobile.util.MAX_IMAGE_BYTES
import cool.sink.mobile.util.MAX_PASSWORD_LENGTH
import cool.sink.mobile.util.MAX_TAGS
import cool.sink.mobile.util.MAX_TAG_LENGTH
import cool.sink.mobile.util.MAX_TITLE_LENGTH
import cool.sink.mobile.util.UiText
import cool.sink.mobile.util.decodeUrlForDisplay
import cool.sink.mobile.util.isValidHttpUrl
import cool.sink.mobile.util.normalizeTag
import cool.sink.mobile.util.randomSlug
import cool.sink.mobile.util.resolveAssetUrl
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val slugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", RegexOption.IGNORE_CASE)

/** One geo-routing row; the web editor edits the same list of country/URL pairs. */
data class GeoRule(
    val country: String = "",
    val url: String = "",
)

class LinkEditViewModel(
    private val repository: SinkRepository,
    private val linkSlug: String?,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val saving: Boolean = false,
        val isEdit: Boolean = false,
        val baseUrl: String = "",
        val slug: String = "",
        val url: String = "",
        val comment: String = "",
        val expiration: Long? = null,
        val tags: List<String> = emptyList(),
        val knownTags: List<String> = emptyList(),
        val title: String = "",
        val description: String = "",
        val image: String = "",
        val apple: String = "",
        val google: String = "",
        val cloaking: Boolean = false,
        val redirectWithQuery: Boolean = false,
        val proxy: Boolean = false,
        val unsafe: Boolean = false,
        val passwordInput: String? = null,
        val hasStoredPassword: Boolean = false,
        val geo: List<GeoRule> = emptyList(),
        val generatingSlug: Boolean = false,
        val generatingOg: Boolean = false,
        val uploadingImage: Boolean = false,
        val slugLength: Int = DEFAULT_SLUG_LENGTH,
        val slugLengthLoaded: Boolean = false,
        val urlError: UiText? = null,
        val slugError: UiText? = null,
        val appleError: UiText? = null,
        val googleError: UiText? = null,
        val geoError: UiText? = null,
        val error: UiText? = null,
        val transientError: UiText? = null,
        val saved: Boolean = false,
    ) {
        val imagePreviewUrl: String?
            get() = image.takeIf { it.isNotBlank() }?.let { resolveAssetUrl(baseUrl, it) }
    }

    private val _state = MutableStateFlow(
        UiState(
            loading = linkSlug != null,
            isEdit = linkSlug != null,
            slug = linkSlug.orEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var original: Link? = null
    private var originalUrl: String? = null

    init {
        loadTags()
        loadBaseUrl()
        if (linkSlug == null) loadSlugLength()
        if (linkSlug != null) loadLink(linkSlug)
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, urlError = null) }

    fun onSlugChange(value: String) = _state.update {
        it.copy(slug = value.trim().lowercase(), slugError = null)
    }

    fun onCommentChange(value: String) = _state.update { it.copy(comment = value.take(MAX_COMMENT_LENGTH)) }

    fun onExpirationChange(value: Long?) = _state.update { it.copy(expiration = value) }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value.take(MAX_TITLE_LENGTH)) }

    fun onDescriptionChange(value: String) = _state.update {
        it.copy(description = value.take(MAX_DESCRIPTION_LENGTH))
    }

    fun onAppleChange(value: String) = _state.update { it.copy(apple = value, appleError = null) }

    fun onGoogleChange(value: String) = _state.update { it.copy(google = value, googleError = null) }

    fun onCloakingChange(enabled: Boolean) = _state.update {
        it.copy(cloaking = enabled, proxy = if (enabled) false else it.proxy)
    }

    fun onProxyChange(enabled: Boolean) = _state.update {
        it.copy(proxy = enabled, cloaking = if (enabled) false else it.cloaking)
    }

    fun onRedirectWithQueryChange(enabled: Boolean) = _state.update { it.copy(redirectWithQuery = enabled) }

    fun onUnsafeChange(enabled: Boolean) = _state.update { it.copy(unsafe = enabled) }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(passwordInput = value.take(MAX_PASSWORD_LENGTH))
    }

    fun clearPassword() = _state.update { it.copy(passwordInput = "") }

    fun addGeoRule() = _state.update { it.copy(geo = it.geo + GeoRule(), geoError = null) }

    fun updateGeoCountry(index: Int, country: String) = _state.update {
        it.copy(
            geo = it.geo.mapIndexed { i, rule -> if (i == index) rule.copy(country = country) else rule },
            geoError = null,
        )
    }

    fun updateGeoUrl(index: Int, url: String) = _state.update {
        it.copy(
            geo = it.geo.mapIndexed { i, rule -> if (i == index) rule.copy(url = url) else rule },
            geoError = null,
        )
    }

    fun removeGeoRule(index: Int) = _state.update {
        it.copy(geo = it.geo.filterIndexed { i, _ -> i != index }, geoError = null)
    }

    fun clearImage() = _state.update { it.copy(image = "") }

    fun reportImageReadFailed() = _state.update {
        it.copy(transientError = UiText(R.string.error_image_read))
    }

    fun consumeTransientError() = _state.update { it.copy(transientError = null) }

    fun addTag(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isEmpty()) return
        _state.update { state ->
            when {
                state.tags.size >= MAX_TAGS -> state
                tag.length > MAX_TAG_LENGTH -> state
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
            else if (it.tags.size < MAX_TAGS) it.copy(tags = it.tags + normalized)
            else it
        }
    }

    /** Random slug in the same alphabet and configured length the instance uses. */
    fun generateRandomSlug() {
        if (_state.value.isEdit) return
        viewModelScope.launch {
            val snapshot = _state.value
            val length = if (snapshot.slugLengthLoaded) {
                snapshot.slugLength
            } else {
                val fetched = runCatching { repository.slugDefaultLength() }.getOrNull()
                    ?: DEFAULT_SLUG_LENGTH
                _state.update { it.copy(slugLength = fetched, slugLengthLoaded = true) }
                fetched
            }
            _state.update { it.copy(slug = randomSlug(length), slugError = null) }
        }
    }

    fun generateSlugWithAi() {
        val snapshot = _state.value
        if (snapshot.generatingSlug) return
        val resolved = resolvedUrl(snapshot)
        if (resolved == null) {
            _state.update { it.copy(urlError = UiText(R.string.error_url_invalid)) }
            return
        }
        _state.update { it.copy(generatingSlug = true) }
        viewModelScope.launch {
            try {
                val generated = repository.generateSlug(resolved)
                if (generated.isBlank()) {
                    _state.update {
                        it.copy(generatingSlug = false, transientError = UiText(R.string.error_ai_failed))
                    }
                } else {
                    _state.update { it.copy(generatingSlug = false, slug = generated, slugError = null) }
                }
            } catch (error: SinkException) {
                _state.update { it.copy(generatingSlug = false, transientError = error.text) }
            }
        }
    }

    fun generateSocialPreviewWithAi() {
        val snapshot = _state.value
        if (snapshot.generatingOg) return
        val resolved = resolvedUrl(snapshot)
        if (resolved == null) {
            _state.update { it.copy(urlError = UiText(R.string.error_url_invalid)) }
            return
        }
        _state.update { it.copy(generatingOg = true) }
        viewModelScope.launch {
            try {
                val generated = repository.generateOg(resolved, Locale.getDefault().toLanguageTag())
                val title = generated.title?.trim().orEmpty().take(MAX_TITLE_LENGTH)
                val description = generated.description?.trim().orEmpty().take(MAX_DESCRIPTION_LENGTH)
                if (title.isEmpty() && description.isEmpty()) {
                    _state.update {
                        it.copy(generatingOg = false, transientError = UiText(R.string.error_ai_failed))
                    }
                } else {
                    _state.update {
                        it.copy(
                            generatingOg = false,
                            title = title.ifEmpty { it.title },
                            description = description.ifEmpty { it.description },
                        )
                    }
                }
            } catch (error: SinkException) {
                _state.update { it.copy(generatingOg = false, transientError = error.text) }
            }
        }
    }

    fun uploadImage(bytes: ByteArray, mimeType: String?) {
        val snapshot = _state.value
        if (snapshot.uploadingImage) return
        if (snapshot.slug.isBlank()) {
            _state.update { it.copy(transientError = UiText(R.string.error_image_slug_required)) }
            return
        }
        if (mimeType == null || mimeType !in IMAGE_MIME_TYPES) {
            _state.update { it.copy(transientError = UiText(R.string.error_image_type)) }
            return
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            _state.update { it.copy(transientError = UiText(R.string.error_image_size)) }
            return
        }
        _state.update { it.copy(uploadingImage = true) }
        viewModelScope.launch {
            try {
                val url = repository.uploadImage(snapshot.slug, bytes, mimeType)
                _state.update { it.copy(uploadingImage = false, image = url) }
            } catch (error: SinkException) {
                _state.update { it.copy(uploadingImage = false, transientError = error.text) }
            }
        }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving) return
        if (snapshot.isEdit && original == null) return
        val resolvedUrl = resolvedUrl(snapshot)
        if (resolvedUrl == null) {
            _state.update { it.copy(urlError = UiText(R.string.error_url_invalid)) }
            return
        }
        val slug = snapshot.slug.trim()
        if (!snapshot.isEdit && slug.isNotEmpty() && (!slugPattern.matches(slug) || slug.equals("dashboard", true))) {
            _state.update { it.copy(slugError = UiText(R.string.error_slug_invalid)) }
            return
        }
        val apple = snapshot.apple.trim()
        if (apple.isNotEmpty() && !isValidHttpUrl(apple)) {
            _state.update { it.copy(appleError = UiText(R.string.error_optional_url_invalid)) }
            return
        }
        val google = snapshot.google.trim()
        if (google.isNotEmpty() && !isValidHttpUrl(google)) {
            _state.update { it.copy(googleError = UiText(R.string.error_optional_url_invalid)) }
            return
        }
        val geo = snapshot.geo.mapNotNull { rule ->
            val country = rule.country.trim().uppercase(Locale.ROOT)
            val url = rule.url.trim()
            if (country.isEmpty() || url.isEmpty()) null else country to url
        }
        val invalidGeo = geo.firstOrNull { !isValidHttpUrl(it.second) }
        if (invalidGeo != null) {
            _state.update {
                it.copy(geoError = UiText(R.string.error_geo_url_invalid, listOf(invalidGeo.first)))
            }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = original
                val comment = snapshot.comment.trim().ifEmpty { null }
                val title = snapshot.title.trim().ifEmpty { null }
                val description = snapshot.description.trim().ifEmpty { null }
                val image = snapshot.image.trim().ifEmpty { null }
                val password = snapshot.passwordInput?.trim()
                val geoMap = geo.toMap().ifEmpty { null }
                if (existing == null) {
                    repository.createLink(
                        CreateLinkPayload(
                            url = resolvedUrl,
                            slug = slug.ifEmpty { null },
                            comment = comment,
                            expiration = snapshot.expiration,
                            title = title,
                            description = description,
                            image = image,
                            apple = apple.ifEmpty { null },
                            google = google.ifEmpty { null },
                            cloaking = snapshot.cloaking,
                            redirectWithQuery = snapshot.redirectWithQuery,
                            proxy = snapshot.proxy,
                            unsafe = snapshot.unsafe.takeIf { it },
                            password = password?.ifEmpty { null },
                            geo = geoMap,
                            tags = snapshot.tags,
                        ),
                    )
                } else {
                    repository.editLink(
                        EditLinkPayload.from(
                            original = existing,
                            url = resolvedUrl,
                            comment = comment,
                            expiration = snapshot.expiration,
                            title = title,
                            description = description,
                            image = image,
                            apple = apple.ifEmpty { null },
                            google = google.ifEmpty { null },
                            cloaking = snapshot.cloaking,
                            redirectWithQuery = snapshot.redirectWithQuery,
                            proxy = snapshot.proxy,
                            unsafe = snapshot.unsafe,
                            password = password,
                            geo = geoMap,
                            tags = snapshot.tags,
                        ),
                    )
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (error: SinkException) {
                _state.update { it.copy(saving = false, error = error.text) }
            }
        }
    }

    private fun resolvedUrl(snapshot: UiState): String? {
        val entered = snapshot.url.trim()
        // The editor shows a decoded URL; keep the stored encoding when it was not edited.
        val resolved = originalUrl
            ?.takeIf { entered == decodeUrlForDisplay(it) }
            ?: entered
        return resolved.takeIf { isValidHttpUrl(it) }
    }

    private fun loadLink(slug: String) {
        viewModelScope.launch {
            try {
                val link = repository.link(slug)
                original = link
                originalUrl = link.url
                _state.update {
                    it.copy(
                        loading = false,
                        slug = link.slug,
                        url = decodeUrlForDisplay(link.url),
                        comment = link.comment.orEmpty(),
                        expiration = link.expiration,
                        tags = link.tags,
                        title = link.title.orEmpty(),
                        description = link.description.orEmpty(),
                        image = link.image.orEmpty(),
                        apple = link.apple.orEmpty(),
                        google = link.google.orEmpty(),
                        cloaking = link.cloaking == true,
                        redirectWithQuery = link.redirectWithQuery == true,
                        proxy = link.proxy == true,
                        unsafe = link.unsafe == true,
                        hasStoredPassword = !link.password.isNullOrBlank(),
                        geo = link.geo.orEmpty()
                            .map { (country, url) -> GeoRule(country, url) }
                            .sortedBy { rule -> rule.country },
                    )
                }
            } catch (error: SinkException) {
                _state.update { it.copy(loading = false, error = error.text) }
            }
        }
    }

    private fun loadSlugLength() {
        viewModelScope.launch {
            val length = runCatching { repository.slugDefaultLength() }.getOrNull() ?: return@launch
            _state.update { it.copy(slugLength = length, slugLengthLoaded = true) }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.tags() }
                .onSuccess { tags -> _state.update { it.copy(knownTags = tags.map { tag -> tag.name }) } }
        }
    }

    private fun loadBaseUrl() {
        viewModelScope.launch {
            runCatching { repository.currentSettings().baseUrl }
                .onSuccess { baseUrl -> _state.update { it.copy(baseUrl = baseUrl) } }
        }
    }

    companion object {
        fun factory(repository: SinkRepository, slug: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { LinkEditViewModel(repository, slug) }
        }
    }
}
