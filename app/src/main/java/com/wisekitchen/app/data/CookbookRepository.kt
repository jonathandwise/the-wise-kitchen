package com.wisekitchen.app.data

import android.content.Context
import com.wisekitchen.app.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Where the recipes come from. The app ships with the recipes it was built with, and checks the
 * GitHub repo for a newer data/recipes.json; a newer copy is saved and used from then on, so new
 * recipes show up without installing a new version of the app.
 */
class CookbookRepository(private val ctx: Context, private val http: OkHttpClient) {

    sealed interface RefreshResult {
        /** [added] are the titles that weren't in the previous copy. */
        data class Updated(val book: Cookbook, val added: List<String>) : RefreshResult
        data object UpToDate : RefreshResult
        /** The published recipes need a newer version of the app. */
        data object NeedsAppUpdate : RefreshResult
        data class Failed(val message: String) : RefreshResult
    }

    private val saved = File(ctx.filesDir, "recipes.json")
    private val mutex = Mutex()

    private val _book = MutableStateFlow(loadInitial())
    val book: StateFlow<Cookbook> = _book

    /** The saved download if it's newer than the recipes built into the app, otherwise the built-in ones. */
    private fun loadInitial(): Cookbook {
        val bundled = Cookbook.parse(ctx.assets.open(ASSET).bufferedReader().use { it.readText() })
        val downloaded = try {
            if (saved.exists()) Cookbook.parse(saved.readText()) else null
        } catch (e: Exception) {
            L.i("saved recipes unreadable, using built-in: ${e.message}")
            saved.delete()
            null
        }
        val book = if (downloaded != null && downloaded.version > bundled.version && downloaded.schema <= Cookbook.SUPPORTED_SCHEMA) {
            downloaded
        } else {
            if (downloaded != null) saved.delete() // the app update brought newer recipes
            bundled
        }
        L.i("recipes loaded version=${book.version} recipes=${book.recipeCount} guides=${book.guideCount}")
        return book
    }

    /** Downloads the published recipes and switches to them if they're newer. */
    suspend fun refresh(): RefreshResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val text = download()
                val remote = Cookbook.parse(text)
                val current = _book.value
                when {
                    remote.schema > Cookbook.SUPPORTED_SCHEMA -> {
                        L.i("recipes need a newer app (schema ${remote.schema})")
                        RefreshResult.NeedsAppUpdate
                    }
                    remote.version <= current.version -> {
                        L.i("recipes up to date version=${current.version}")
                        RefreshResult.UpToDate
                    }
                    else -> {
                        val tmp = File(saved.path + ".part")
                        tmp.writeText(text)
                        if (!tmp.renameTo(saved)) throw IOException("couldn't save the recipes")
                        val oldIds = current.allItems.map { it.id }.toSet()
                        val added = remote.allItems.filter { it.id !in oldIds }.map { it.title }
                        _book.value = remote
                        L.i("recipes updated version=${remote.version} added=${added.size}")
                        RefreshResult.Updated(remote, added)
                    }
                }
            } catch (e: Exception) {
                L.i("recipes check failed: ${e.message}")
                RefreshResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun download(): String {
        val url = FEED + "?t=" + System.currentTimeMillis() / 60_000
        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("empty response")
        }
    }

    companion object {
        const val ASSET = "recipes.json"
        const val FEED = "https://raw.githubusercontent.com/jonathandwise/the-wise-kitchen/main/data/recipes.json"
    }
}
