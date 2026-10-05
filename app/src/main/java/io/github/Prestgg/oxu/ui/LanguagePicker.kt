// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.Prestgg.oxu.data.Langs

@Composable
fun LanguagePicker(
    title: String,
    subtitle: String,
    confirmLabel: String,
    initial: String?,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onDone: (String) -> Unit
) {
    var picked by remember { mutableStateOf(initial ?: Langs.all.first().code) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(48.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LanguageGrid(picked, Modifier.weight(1f)) { picked = it }
        Button(
            onClick = { onDone(picked) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).height(52.dp)
        ) { Text(confirmLabel, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun LanguagePickerDialog(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(current ?: Langs.all.first().code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Target language") },
        text = {
            Column {
                Text(
                    "PDF text will be translated into this language.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LanguageGrid(picked, Modifier.height(420.dp)) { picked = it }
            }
        },
        confirmButton = { TextButton({ onPick(picked) }) { Text("Select") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LanguageGrid(selected: String, modifier: Modifier, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(Langs.all) { lang ->
            val isSel = lang.code == selected
            Card(
                onClick = { onSelect(lang.code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.height(72.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        lang.name,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        lang.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}