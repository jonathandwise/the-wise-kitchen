package com.wisekitchen.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisekitchen.app.data.Chapter
import com.wisekitchen.app.data.Cookbook
import com.wisekitchen.app.data.searchText

@Composable
fun HomeScreen(
    book: Cookbook,
    favorites: Set<String>,
    listScale: Float,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onChapter: (Chapter) -> Unit,
    onItem: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            AppBar(book.title, actions = {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            })
        },
    ) { pad ->
        ScaledText(listScale) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
                item {
                    Surface(
                        onClick = onSearch,
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Search recipes or ingredients", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                val favs = book.allItems.filter { it.id in favorites }
                if (favs.isNotEmpty()) {
                    item { SectionLabel("Favorites", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)) }
                    items(favs, key = { "fav-" + it.id }) { item ->
                        ItemRow(item, onClick = { onItem(item.id) }, favorite = false, chapterTitle = book.chapterFor(item.id)?.title)
                    }
                }
                item { SectionLabel("Chapters", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
                items(book.chapters, key = { "ch-" + it.id }) { ch -> ChapterCard(ch) { onChapter(ch) } }
                item {
                    Text(
                        "${book.recipeCount} recipes · ${book.guideCount} guides" + (book.updated?.let { " · updated ${prettyDate(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(ch: Chapter, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                "%02d".format(ch.number),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = MaterialTheme.typography.titleLarge)
                val n = ch.items.size
                val guides = ch.items.all { it is com.wisekitchen.app.data.Guide }
                Text(
                    "$n " + if (guides) (if (n == 1) "guide" else "guides") else (if (n == 1) "recipe" else "recipes"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (ch.blurb.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(ch.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
        }
    }
}

@Composable
fun ChapterScreen(
    chapter: Chapter,
    favorites: Set<String>,
    listScale: Float,
    onBack: () -> Unit,
    onItem: (String) -> Unit,
) {
    Scaffold(topBar = { AppBar(chapter.title, onBack = onBack) }) { pad ->
        ScaledText(listScale) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
                if (chapter.blurb.isNotBlank()) {
                    item {
                        Text(
                            chapter.blurb,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                        )
                    }
                }
                items(chapter.items, key = { it.id }) { item ->
                    ItemRow(item, onClick = { onItem(item.id) }, favorite = item.id in favorites)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    book: Cookbook,
    query: String,
    onQuery: (String) -> Unit,
    favorites: Set<String>,
    listScale: Float,
    onBack: () -> Unit,
    onItem: (String) -> Unit,
) {
    val index = remember(book) { book.allItems.map { it to it.searchText() } }
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val results = remember(index, query) {
        if (words.isEmpty()) emptyList()
        else index.filter { (_, text) -> words.all { it in text } }
            .map { it.first }
            .sortedByDescending { item -> words.count { it in item.title.lowercase() } }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(topBar = { AppBar("Search", onBack = onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Try “chicken”, “pressure cooker” or “miso”") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            ScaledText(listScale) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
                    if (words.isNotEmpty()) {
                        item {
                            Text(
                                when (results.size) { 0 -> "Nothing found"; 1 -> "1 match"; else -> "${results.size} matches" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(results, key = { it.id }) { item ->
                        ItemRow(item, onClick = { onItem(item.id) }, favorite = item.id in favorites, chapterTitle = book.chapterFor(item.id)?.title)
                    }
                }
            }
        }
    }
}
