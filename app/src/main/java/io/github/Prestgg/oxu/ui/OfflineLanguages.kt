// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.Prestgg.oxu.data.Lang
import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.offline.OfflineModels.PackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineLanguagesScreen(app: AppState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val models = app.offline
    var confirmDelete by remember { mutableStateOf<Lang?>(null) }

    LaunchedEffect(Unit) { models.refreshCatalog() }

    // The target language first: it is the one the user needs most.
    val langs = remember(app.prefs.targetLang) {
        Langs.all.filter { it.code != "en" }.sortedByDescending { it.code == app.prefs.targetLang }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline languages") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton({ models.refreshCatalog(force = true) }, enabled = !models.catalogLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh list")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    "Downloaded languages translate on your phone: nothing leaves the device and no internet " +
                        "is needed. English is built in and connects any two languages, e.g. Russian → Azerbaijani. " +
                        "Models by Mozilla (Firefox Translations).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (models.catalogLoading && !models.hasCatalog()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("Loading the language list...")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                models.catalogState?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(langs, key = { it.code }) { lang ->
                LanguageRow(
                    lang = lang,
                    isTarget = lang.code == app.prefs.targetLang,
                    state = models.states[lang.code] ?: PackState.NotInstalled,
                    size = models.packSize(lang.code),
                    available = !models.hasCatalog() || models.isAvailable(lang.code),
                    onDownload = { models.download(lang.code) },
                    onCancel = { models.cancel(lang.code) },
                    onDelete = { confirmDelete = lang }
                )
            }
        }
    }

    confirmDelete?.let { lang ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${lang.name}?") },
            text = { Text("The offline ${lang.name} language will be removed from this device. You can download it again later.") },
            confirmButton = {
                TextButton({
                    models.delete(lang.code)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LanguageRow(
    lang: Lang,
    isTarget: Boolean,
    state: PackState,
    size: Long?,
    available: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    fun fmt(bytes: Long) = Formatter.formatShortFileSize(ctx, bytes)

    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isTarget) "${lang.name} · your language" else lang.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    val sub = when (state) {
                        PackState.Installed -> "Downloaded"
                        is PackState.Downloading ->
                            "Downloading ${fmt(state.done)} / ${fmt(state.total)}"
                        is PackState.Failed -> state.message
                        PackState.NotInstalled -> when {
                            !available -> "Not available offline"
                            size != null -> fmt(size)
                            else -> ""
                        }
                    }
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state is PackState.Failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                when (state) {
                    PackState.Installed -> {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        IconButton(onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${lang.name}") }
                    }
                    is PackState.Downloading ->
                        IconButton(onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel download") }
                    else -> FilledTonalButton(onDownload, enabled = available && size != null) {
                        Text(if (state is PackState.Failed) "Retry" else "Download")
                    }
                }
            }
            if (state is PackState.Downloading) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                )
            }
        }
    }
}
