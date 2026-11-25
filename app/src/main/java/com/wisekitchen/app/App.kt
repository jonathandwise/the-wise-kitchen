package com.wisekitchen.app

import android.app.Application
import android.util.Log
import com.wisekitchen.app.data.CookbookRepository
import com.wisekitchen.app.data.Settings
import com.wisekitchen.app.update.Updater
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The app's long-lived parts, created once. */
class AppGraph(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val settings = Settings(app)
    val cookbook = CookbookRepository(app, http)
    val updater = Updater(app, http)
}

/** App log lines (tag "WiseKitchen"); the emulator test reads these. */
object L {
    fun i(msg: String) {
        Log.i("WiseKitchen", msg)
    }
}
