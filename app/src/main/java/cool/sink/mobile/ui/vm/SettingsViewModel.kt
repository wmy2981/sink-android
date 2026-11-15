package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.R
import cool.sink.mobile.data.Settings
import cool.sink.mobile.data.SettingsStore
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.data.ThemeMode
import cool.sink.mobile.util.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SinkRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    private val _disconnected = MutableStateFlow(false)
    val disconnected: StateFlow<Boolean> = _disconnected.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var verifyJob: Job? = null

    fun verify() {
        if (_verifying.value) return
        _verifying.value = true
        verifyJob = viewModelScope.launch {
            try {
                val current = repository.currentSettings()
                if (!current.isConnected) throw SinkException(UiText(R.string.error_not_connected))
                val info = repository.verify(current.baseUrl, current.token)
                repository.saveConnection(current.baseUrl, current.token, info)
                _messages.send(UiText(R.string.snackbar_verified, listOf(info.userEmail)))
            } catch (error: SinkException) {
                _messages.send(error.text)
            } finally {
                _verifying.value = false
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    fun disconnect() {
        // A verify that finishes after disconnecting must not write the old credentials back.
        verifyJob?.cancel()
        verifyJob = null
        _verifying.value = false
        viewModelScope.launch {
            settingsStore.clearConnection()
            _disconnected.value = true
        }
    }

    companion object {
        fun factory(repository: SinkRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(repository, settingsStore) }
            }
    }
}
