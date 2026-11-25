package com.wisekitchen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wisekitchen.app.BuildConfig
import com.wisekitchen.app.data.Cookbook
import com.wisekitchen.app.data.Settings
import com.wisekitchen.app.data.ThemeMode
import com.wisekitchen.app.update.Updater

@Composable
fun SettingsScreen(
    book: Cookbook,
    textScale: Float,
    theme: ThemeMode,
    autoUpdate: Boolean,
    recipeStatus: String?,
    checkingRecipes: Boolean,
    appUpdate: Updater.State,
    onTextScale: (Float) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onCheckRecipes: () -> Unit,
    onCheckApp: () -> Unit,
    onInstallApp: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { AppBar("Settings", onBack = onBack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp),
        ) {
            Group("Recipe text size") {
                Text(
                    "Use A− and A+ on any recipe to change it there, too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = textScale,
                    onValueChange = onTextScale,
                    valueRange = Settings.MIN_TEXT_SCALE..Settings.MAX_TEXT_SCALE,
                )
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    ScaledText(textScale) {
                        Text(
                            "Heat the oven to 375°F (190°C) and grease a baking dish.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Group("Appearance") {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = theme == mode, onClick = { onTheme(mode) }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == mode, onClick = null)
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            Group("Updates") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates automatically", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "New recipes and new versions of the app, each time the app opens.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoUpdate, onCheckedChange = onAutoUpdate)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Text("Recipes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${book.recipeCount} recipes and ${book.guideCount} guides" + (book.updated?.let { ", updated $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recipeStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                OutlinedButton(onClick = onCheckRecipes, enabled = !checkingRecipes, modifier = Modifier.padding(top = 6.dp)) {
                    Text(if (checkingRecipes) "Checking…" else "Check for new recipes")
                }

                Spacer(Modifier.height(16.dp))
                Text("App", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (status, busy) = when (appUpdate) {
                    Updater.State.Idle -> null to false
                    Updater.State.Checking -> "Checking…" to true
                    Updater.State.UpToDate -> "You have the newest version." to false
                    is Updater.State.Downloading -> "Downloading version ${appUpdate.info.versionName}… ${appUpdate.percent}%" to true
                    is Updater.State.Ready -> "Version ${appUpdate.info.versionName} is ready to install." to false
                    is Updater.State.Failed -> appUpdate.message to false
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                if (appUpdate is Updater.State.Ready) {
                    OutlinedButton(onClick = onInstallApp, modifier = Modifier.padding(top = 6.dp)) { Text("Install update") }
                } else {
                    OutlinedButton(onClick = onCheckApp, enabled = !busy, modifier = Modifier.padding(top = 6.dp)) { Text("Check for app update") }
                }
            }

            if (book.about.isNotEmpty()) {
                Group("About this book") {
                    book.about.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(title, Modifier.padding(bottom = 6.dp))
        content()
    }
}
