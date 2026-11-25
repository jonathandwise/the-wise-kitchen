package com.wisekitchen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisekitchen.app.data.Guide
import com.wisekitchen.app.data.Item
import com.wisekitchen.app.data.Recipe

/** Turns **bold** markers in cookbook text into bold spans. */
fun rich(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val start = rest.indexOf("**")
        if (start < 0) break
        val end = rest.indexOf("**", start + 2)
        if (end < 0) break
        append(rest.substring(0, start))
        val boldStart = length
        append(rest.substring(start + 2, end))
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart, length)
        rest = rest.substring(end + 2)
    }
    append(rest)
}

@Composable
fun AppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** The A− / A+ buttons that change recipe text size. */
@Composable
fun TextSizeButtons(onSmaller: () -> Unit, onBigger: () -> Unit) {
    TextButton(onClick = onSmaller) { Text("A−", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    TextButton(onClick = onBigger) { Text("A+", fontSize = 21.sp, fontWeight = FontWeight.SemiBold) }
}

/** A short second line for a recipe in a list: how long it takes, or what it serves. */
fun Item.summary(): String = when (this) {
    is Recipe -> listOfNotNull(
        time,
        serves?.let { "Serves ${it.substringBefore(" (")}" } ?: makes?.let { "Makes ${it.substringBefore(" (")}" },
    ).joinToString(" · ").ifBlank { source?.name.orEmpty() }
    is Guide -> "Guide" + (source?.name?.let { " · ${it.substringBefore(" ·")}" } ?: "")
}

@Composable
fun ItemRow(item: Item, onClick: () -> Unit, favorite: Boolean, chapterTitle: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (chapterTitle != null) {
                    Text(
                        chapterTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sub = item.summary()
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (favorite) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Small spaced-out capitals, used for section labels. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
fun BulletLine(text: AnnotatedString, bullet: String = "•", modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.width(22.dp)) {
            Text(bullet, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
