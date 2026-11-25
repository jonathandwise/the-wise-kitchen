package com.wisekitchen.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wisekitchen.app.App
import com.wisekitchen.app.L
import com.wisekitchen.app.data.CookbookRepository
import com.wisekitchen.app.data.Settings
import com.wisekitchen.app.data.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Where an incoming intent asked to go (used by the emulator test, and safe to ignore). */
    private var pending by mutableStateOf<List<String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pending = routesFrom(intent)
        val g = (application as App).graph
        setContent {
            val theme by g.settings.theme.collectAsState()
            WiseKitchenTheme(theme) {
                val dark = LocalIsDark.current
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    AppContent(this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routesFrom(intent)?.let { pending = it }
    }

    private fun routesFrom(intent: Intent?): List<String>? {
        intent ?: return null
        // Display settings can be passed in too (the emulator test uses these to take screenshots).
        val settings = (application as App).graph.settings
        intent.getStringExtra("theme")?.let { t -> runCatching { ThemeMode.valueOf(t) }.getOrNull()?.let(settings::setTheme) }
        if (intent.hasExtra("textScale")) settings.setTextScale(intent.getFloatExtra("textScale", Settings.DEFAULT_TEXT_SCALE))
        intent.getStringExtra("open")?.let { return listOf("home", "item/$it") }
        return when (val s = intent.getStringExtra("screen")) {
            null -> null
            "search" -> listOf("home", "search")
            "settings" -> listOf("home", "settings")
            else -> if (s.startsWith("chapter/")) listOf("home", s) else null
        }?.also { if (intent.hasExtra("query")) initialQuery = intent.getStringExtra("query").orEmpty() }
    }

    var initialQuery = ""

    val pendingRoutes: List<String>? get() = pending
    fun clearPending() { pending = null }
}

private val routeSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)

@Composable
private fun AppContent(activity: MainActivity) {
    val g = (activity.application as App).graph
    val book by g.cookbook.book.collectAsState()
    val textScale by g.settings.textScale.collectAsState()
    val theme by g.settings.theme.collectAsState()
    val favorites by g.settings.favorites.collectAsState()
    val autoUpdate by g.settings.autoUpdate.collectAsState()
    val appUpdate by g.updater.state.collectAsState()

    val stack = rememberSaveable(saver = routeSaver) { mutableStateListOf("home") }
    var query by rememberSaveable { mutableStateOf("") }
    var recipeStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var checkingRecipes by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf<com.wisekitchen.app.update.Updater.State.Ready?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun go(route: String) { stack.add(route) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    val pendingRoutes = activity.pendingRoutes
    LaunchedEffect(pendingRoutes) {
        if (pendingRoutes != null) {
            stack.clear(); stack.addAll(pendingRoutes)
            if (activity.initialQuery.isNotEmpty()) query = activity.initialQuery
            activity.clearPending()
        }
    }

    suspend fun checkRecipes(userAsked: Boolean) {
        checkingRecipes = true
        val r = g.cookbook.refresh()
        checkingRecipes = false
        recipeStatus = when (r) {
            is CookbookRepository.RefreshResult.Updated ->
                if (r.added.isEmpty()) "Recipes updated." else "Added: ${r.added.joinToString(", ")}"
            CookbookRepository.RefreshResult.UpToDate -> if (userAsked) "You have all the recipes." else null
            CookbookRepository.RefreshResult.NeedsAppUpdate -> "New recipes need a newer version of the app."
            is CookbookRepository.RefreshResult.Failed -> if (userAsked) "Couldn't check: ${r.message}" else null
        }
        if (!userAsked && r is CookbookRepository.RefreshResult.Updated) {
            val msg = when (r.added.size) {
                0 -> "Recipes updated"
                1 -> "New recipe: ${r.added[0]}"
                else -> "${r.added.size} new recipes: ${r.added.take(3).joinToString(", ")}" + if (r.added.size > 3) "…" else ""
            }
            snackbar.showSnackbar(msg)
        }
    }

    // Check for new recipes and app updates each time the app starts.
    LaunchedEffect(Unit) {
        if (g.settings.autoUpdate.value) {
            launch { checkRecipes(userAsked = false) }
            launch { g.updater.checkAndDownload() }
        }
    }
    LaunchedEffect(appUpdate) {
        val u = appUpdate as? com.wisekitchen.app.update.Updater.State.Ready ?: return@LaunchedEffect
        if (g.settings.autoUpdate.value && g.updater.shouldOffer(u)) offer = u
    }

    BackHandler(enabled = stack.size > 1) { back() }

    val listScale = (1f + (textScale - 1f) * 0.5f).coerceIn(0.9f, 1.45f)
    val route = stack.last()

    Box(Modifier.fillMaxSize()) {
        when {
            route == "home" -> HomeScreen(
                book = book,
                favorites = favorites,
                listScale = listScale,
                onSearch = { go("search") },
                onSettings = { go("settings") },
                onChapter = { go("chapter/${it.id}") },
                onItem = { go("item/$it") },
            )
            route.startsWith("chapter/") -> {
                val ch = book.chapter(route.removePrefix("chapter/"))
                if (ch == null) LaunchedEffect(route) { back() }
                else ChapterScreen(ch, favorites, listScale, onBack = ::back, onItem = { go("item/$it") })
            }
            route.startsWith("item/") -> {
                val id = route.removePrefix("item/")
                val item = book.item(id)
                if (item == null) LaunchedEffect(route) { back() }
                else {
                    LaunchedEffect(id) { L.i("opened $id") }
                    ItemScreen(
                        item = item,
                        chapterTitle = book.chapterFor(id)?.title,
                        favorite = id in favorites,
                        textScale = textScale,
                        onToggleFavorite = { g.settings.toggleFavorite(id) },
                        onSmaller = g.settings::smallerText,
                        onBigger = g.settings::biggerText,
                        onBack = ::back,
                    )
                }
            }
            route == "search" -> SearchScreen(
                book = book,
                query = query,
                onQuery = { query = it },
                favorites = favorites,
                listScale = listScale,
                onBack = ::back,
                onItem = { go("item/$it") },
            )
            route == "settings" -> SettingsScreen(
                book = book,
                textScale = textScale,
                theme = theme,
                autoUpdate = autoUpdate,
                recipeStatus = recipeStatus,
                checkingRecipes = checkingRecipes,
                appUpdate = appUpdate,
                onTextScale = g.settings::setTextScale,
                onTheme = g.settings::setTheme,
                onAutoUpdate = g.settings::setAutoUpdate,
                onCheckRecipes = { scope.launch { checkRecipes(userAsked = true) } },
                onCheckApp = { scope.launch { g.updater.checkAndDownload(userAsked = true) } },
                onInstallApp = {
                    (appUpdate as? com.wisekitchen.app.update.Updater.State.Ready)?.let { g.updater.install(activity, it) }
                },
                onBack = ::back,
            )
            else -> LaunchedEffect(route) { stack.clear(); stack.add("home") }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) { Snackbar(it) }
    }

    offer?.let { u ->
        val dismiss = { g.updater.snooze(u); offer = null }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Update available") },
            text = {
                Text(
                    "Version ${u.info.versionName} of Wise Kitchen is ready." +
                        (u.info.notes?.let { "\n\n$it" } ?: "") + "\n\n" +
                        if (g.updater.hasInstallPermission(activity)) "Install it now?"
                        else "To install updates, Android needs you to allow Wise Kitchen to install apps (asked only once). Turn it on, then come back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    offer = null
                    g.updater.install(activity, u)
                }) { Text(if (g.updater.hasInstallPermission(activity)) "Install" else "Allow") }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Later") } },
        )
    }
}
