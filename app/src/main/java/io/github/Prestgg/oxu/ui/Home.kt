// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.doc.SUPPORTED_MIME_TYPES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppState) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    var showLang by remember { mutableStateOf(false) }
    var showWeb by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.open(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oxu", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton({ app.go(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                onClick = { showLang = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Target language", style = MaterialTheme.typography.labelLarge)
                        Text(
                            Langs.name(prefs.targetLang),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { picker.launch(SUPPORTED_MIME_TYPES) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Open a file", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "PDF, EPUB, DOCX, ODT, FB2, HTML, TXT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showWeb = true },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Read a web page", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("How to read", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Open a file or a web page — only its text is shown\n" +
                            "2. Long-press to select the sentence you want\n" +
                            "3. \"Translate\" — instantly into " + Langs.name(prefs.targetLang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (prefs.recents.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Recent", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                prefs.recents.forEach { doc ->
                    Card(
                        onClick = { app.open(Uri.parse(doc.uri)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    DateUtils.getRelativeTimeSpanString(
                                        doc.at, System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton({ prefs.forget(doc.uri) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove from list",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWeb) {
        WebAddressDialog(
            onOpen = { url ->
                showWeb = false
                app.openUrl(url)
            },
            onDismiss = { showWeb = false }
        )
    }

    if (showLang) {
        LanguagePickerDialog(prefs.targetLang, { code ->
            prefs.targetLang = code
            showLang = false
        }) { showLang = false }
    }
}
/** Asks for a link; the page then opens as plain text in the reader. */
@Composable
private fun WebAddressDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val valid = url.isNotBlank() && !url.trim().contains(' ')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read a web page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Only the article text is kept: no menus, ads or pictures. " +
                        "You can also share a link to Oxu from your browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    placeholder = { Text("example.com/article") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (valid) onOpen(url) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton({ onOpen(url) }, enabled = valid) { Text("Open") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
