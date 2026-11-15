package cool.sink.mobile.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cool.sink.mobile.data.Settings
import cool.sink.mobile.data.SettingsStore
import cool.sink.mobile.data.SinkException
import cool.sink.mobile.data.SinkRepository
import cool.sink.mobile.data.ThemeMode
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

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun verify() {
        if (_verifying.value) return
        _verifying.value = true
        viewModelScope.launch {
            try {
                val current = repository.currentSettings()
                val info = repository.verify(current.baseUrl, current.token)
                repository.saveConnection(current.baseUrl, current.token, info)
                _messages.send("验证通过，当前身份：${info.userEmail}")
            } catch (error: SinkException) {
                _messages.send(error.message ?: "验证失败")
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
