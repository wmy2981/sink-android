package cool.sink.mobile

import android.app.Application
import android.content.Context
import cool.sink.mobile.data.SettingsStore
import cool.sink.mobile.data.SinkApiFactory
import cool.sink.mobile.data.SinkRepository

class AppContainer(context: Context) {
    val settingsStore = SettingsStore(context)
    val repository = SinkRepository(settingsStore, SinkApiFactory())
}

class SinkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
