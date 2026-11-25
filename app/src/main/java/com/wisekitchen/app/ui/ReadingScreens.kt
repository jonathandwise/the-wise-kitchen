package com.wisekitchen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisekitchen.app.data.Block
import com.wisekitchen.app.data.Guide
import com.wisekitchen.app.data.Item
import com.wisekitchen.app.data.Recipe
import com.wisekitchen.app.data.Source
import kotlinx.coroutines.launch

/** Recipe or guide page, with text-size buttons and a favorite star in the top bar. */
@Composable
fun ItemScreen(
    item: Item,
    chapterTitle: String?,
    favorite: Boolean,
    textScale: Float,
    onToggleFavorite: () -> Unit,
    onSmaller: () -> Unit,
    onBigger: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            AppBar(item.title, onBack = onBack, actions = {
                TextSizeButtons(onSmaller, onBigger)
                IconButton(onClick = onToggleFavorite) {
                    if (favorite) Icon(Icons.Filled.Star, contentDescription = "Remove from favorites", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Filled.StarBorder, contentDescription = "Add to favorites")
                }
            })
        },
    ) { pad ->
        ScaledText(textScale) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 48.dp),
            ) {
                header(item, chapterTitle)
                when (item) {
                    is Recipe -> recipeBody(item) { index -> scope.launch { listState.animateScrollToItem(index) } }
                    is Guide -> guideBody(item)
                }
            }
        }
    }
}

private fun LazyListScope.header(item: Item, chapterTitle: String?) {
    item(key = "title") {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
            if (chapterTitle != null) SectionLabel(chapterTitle)
            Spacer(Modifier.height(4.dp))
            Text(item.title, style = MaterialTheme.typography.headlineMedium)
            item.source?.let { SourceLine(it, if (item is Guide) "From" else "Adapted from") }
        }
    }
    if (item is Recipe) {
        val facts = listOfNotNull(
            item.serves?.let { "Serves" to it },
            item.makes?.let { "Makes" to it },
            item.time?.let { "Time" to it },
            item.temp?.let { "Temp" to it },
        )
        if (facts.isNotEmpty()) {
            item(key = "facts") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.forEach { (label, value) ->
                            Row {
                                Text(
                                    label.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.widthIn(min = 64.dp).padding(top = 3.dp, end = 10.dp),
                                )
                                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    item.note?.let { note ->
        item(key = "note") {
            Text(
                rich(note),
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SourceLine(source: Source, verb: String) {
    Text(
        "$verb ${source.name}" + (source.site?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun LazyListScope.recipeBody(r: Recipe, jumpTo: (Int) -> Unit) {
    // Index of each part's heading in the list, for the jump chips. Counted as items are added:
    // title, facts, note, jump row come first.
    var index = 1 + (if (listOfNotNull(r.serves, r.makes, r.time, r.temp).isNotEmpty()) 1 else 0) + (if (r.note != null) 1 else 0) + 1
    val ingredientsAt = index
    index += 1 + r.ingredients.sumOf { (if (it.title != null) 1 else 0) + it.items.size }
    val methodAt = index
    index += 1 + r.method.sumOf { (if (it.title != null) 1 else 0) + it.steps.size }
    val tipsAt = if (r.tips.isNotEmpty()) index else null

    item(key = "jump") {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = { jumpTo(ingredientsAt) }, label = { Text("Ingredients") })
            FilterChip(selected = false, onClick = { jumpTo(methodAt) }, label = { Text("Method") })
            if (tipsAt != null) FilterChip(selected = false, onClick = { jumpTo(tipsAt) }, label = { Text("Tips") })
        }
    }

    item(key = "ingredients") { PartHeading("Ingredients") }
    r.ingredients.forEachIndexed { gi, g ->
        g.title?.let { t -> item(key = "ig$gi") { GroupHeading(t) } }
        g.items.forEachIndexed { ii, ing ->
            item(key = "i$gi-$ii") { BulletLine(rich(ing), modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) }
        }
    }

    item(key = "method") { PartHeading("Method") }
    r.method.forEachIndexed { gi, g ->
        g.title?.let { t -> item(key = "mg$gi") { GroupHeading(t) } }
        g.steps.forEachIndexed { si, step ->
            item(key = "s$gi-$si") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
                    StepNumber(si + 1)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rich(step.text), style = MaterialTheme.typography.bodyLarge)
                        step.sub.forEach { BulletLine(rich(it), bullet = "–", modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        }
    }

    if (r.tips.isNotEmpty()) {
        item(key = "tips") { PartHeading("Tips") }
        r.tips.forEachIndexed { ti, tip ->
            item(key = "t$ti") { BulletLine(rich(tip), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        }
    }
}

@Composable
private fun StepNumber(n: Int) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .widthIn(min = 30.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PartHeading(text: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        rich(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

private fun LazyListScope.guideBody(g: Guide) {
    g.sections.forEachIndexed { si, s ->
        s.title?.let { t -> item(key = "sec$si") { PartHeading(t) } }
        s.blocks.forEachIndexed { bi, b ->
            item(key = "b$si-$bi") {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { GuideBlock(b) }
            }
        }
    }
}

@Composable
private fun GuideBlock(b: Block) {
    when (b) {
        is Block.Paragraph -> Text(rich(b.text), style = MaterialTheme.typography.bodyLarge)
        is Block.Heading -> Text(
            rich(b.text),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.items.forEach { BulletLine(rich(it)) }
        }
        is Block.Steps -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            b.items.forEachIndexed { i, text ->
                Row {
                    StepNumber(i + 1)
                    Spacer(Modifier.width(14.dp))
                    Text(rich(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        is Block.Table -> GuideTable(b)
    }
}

/**
 * Tables are shown so they stay readable with big text on a narrow phone: two columns as
 * label/value rows, wider tables as one card per row with each value under its heading.
 */
@Composable
private fun GuideTable(t: Block.Table) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (t.header.size == 2) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    t.header.forEachIndexed { i, h ->
                        Text(
                            h.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(if (i == 0) 0.42f else 0.58f),
                        )
                    }
                }
                t.rows.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(row[0], style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.42f).padding(end = 10.dp))
                        Text(row.getOrElse(1) { "" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.58f))
                    }
                }
            } else {
                t.rows.forEachIndexed { ri, row ->
                    if (ri > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row[0], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        row.drop(1).forEachIndexed { ci, cell ->
                            Row {
                                Text(
                                    t.header.getOrElse(ci + 1) { "" } + ":",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                                Text(cell, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
