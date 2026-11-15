package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.R
import cool.sink.mobile.data.SinkApiFactory
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectViewModel(private val repository: SinkRepository) : ViewModel() {

    data class UiState(
        val url: String = "",
        val token: String = "",
        val loading: Boolean = false,
        val error: UiText? = null,
        val connected: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.currentSettings()
            _state.update { it.copy(url = settings.baseUrl, token = settings.token) }
        }
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    fun connect() {
        val snapshot = _state.value
        val baseUrl = SinkApiFactory.normalizeBaseUrl(snapshot.url)
        if (baseUrl == null) {
            _state.update { it.copy(error = UiText(R.string.error_invalid_instance_url)) }
            return
        }
        val token = snapshot.token.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(error = UiText(R.string.error_token_required)) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val info = repository.verify(baseUrl, token)
                repository.saveConnection(baseUrl, token, info)
                _state.update { it.copy(loading = false, url = baseUrl, token = token, connected = true) }
            } catch (error: SinkException) {
                _state.update { it.copy(loading = false, error = error.text) }
            }
        }
    }

    companion object {
        fun factory(repository: SinkRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectViewModel(repository) }
        }
    }
}
