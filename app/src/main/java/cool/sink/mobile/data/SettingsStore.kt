package cool.sink.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "sink_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val baseUrl: String = "",
    val token: String = "",
    val email: String = "",
    val authMethod: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
) {
    val isConnected: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

/** Small persistent store for the instance credentials and UI preferences. */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.sinkDataStore

    val settings: Flow<Settings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            Settings(
                baseUrl = preferences[Keys.BASE_URL].orEmpty(),
                token = preferences[Keys.TOKEN].orEmpty(),
                email = preferences[Keys.EMAIL].orEmpty(),
                authMethod = preferences[Keys.AUTH_METHOD].orEmpty(),
                themeMode = preferences[Keys.THEME_MODE]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: false,
            )
        }

    suspend fun current(): Settings = settings.first()

    suspend fun saveConnection(baseUrl: String, token: String, email: String, authMethod: String) {
        dataStore.edit { preferences ->
            preferences[Keys.BASE_URL] = baseUrl
            preferences[Keys.TOKEN] = token
            preferences[Keys.EMAIL] = email
            preferences[Keys.AUTH_METHOD] = authMethod
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun clearConnection() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.BASE_URL)
            preferences.remove(Keys.TOKEN)
            preferences.remove(Keys.EMAIL)
            preferences.remove(Keys.AUTH_METHOD)
        }
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val EMAIL = stringPreferencesKey("email")
        val AUTH_METHOD = stringPreferencesKey("auth_method")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
