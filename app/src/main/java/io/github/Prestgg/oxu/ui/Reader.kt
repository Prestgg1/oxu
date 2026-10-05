// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.net.Translate
import io.github.Prestgg.oxu.pdf.PdfSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private sealed interface TState {
    data object Idle : TState
    data object Loading : TState
    data class Done(val text: String, val partial: Boolean = false) : TState
    data class Error(val message: String) : TState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(app: AppState, onBack: () -> Unit) {
    val source = app.source
    val prefs = app.prefs
    if (source == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(app.initialPage.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))) }
    var fontSize by remember { mutableFloatStateOf(prefs.fontSize) }
    var showSheet by remember { mutableStateOf(false) }
    var sourceText by remember { mutableStateOf("") }
    val tFlow = remember { MutableStateFlow<TState>(TState.Idle) }
    val tState by tFlow.collectAsState()

    val text by produceState(initialValue = "", page) {
        value = source.text(page)
    }

    val fieldValue = remember(text) {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange.Zero))
    }
    val selection = fieldValue.value.selection
    val selectedText = if (!selection.collapsed && selection.end <= text.length) {
        text.substring(selection.min, selection.max)
    } else {
        ""
    }

    LaunchedEffect(page) { app.savePage(page) }

    suspend fun translate(value: String) {
        if (value.isBlank()) return
        sourceText = value
        tFlow.value = TState.Loading
        try {
            val translator = Translate.fromPrefs(prefs)
            val res = translator.translate(value, prefs.targetLang) { partial ->
                tFlow.value = TState.Done(partial, partial = true)
            }
            tFlow.value = TState.Done(res)
        } catch (e: Throwable) {
            tFlow.value = TState.Error(e.message ?: "Translation failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("${page + 1} / ${source.pageCount}", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = {
                        if (fontSize > 13f) {
                            fontSize -= 2f
                            prefs.fontSize = fontSize
                        }
                    }) { Text("A-") }
                    TextButton(onClick = {
                        if (fontSize < 29f) {
                            fontSize += 2f
                            prefs.fontSize = fontSize
                        }
                    }) { Text("A+") }
                    IconButton(onClick = { if (page > 0) page-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                    }
                    IconButton(onClick = { if (page < source.pageCount - 1) page++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                    }
                }
            )
        }
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (text.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No text on this page.\nThis PDF may be a scan or an image.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = fieldValue.value,
                            onValueChange = { fieldValue.value = it },
                            readOnly = true,
                            cursorBrush = SolidColor(Color.Transparent),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.55f).sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        )
                        Spacer(Modifier.height(120.dp))
                    }
                }
            }

            if (selectedText.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        Modifier.padding(start = 20.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(wordCountLabel(selectedText), style = MaterialTheme.typography.labelLarge)
                        TextButton({
                            scope.launch { translate(selectedText) }
                            showSheet = true
                        }) { Text("Translate") }
                        TextButton({ fieldValue.value = fieldValue.value.copy(selection = TextRange.Zero) }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    Langs.name(prefs.targetLang),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Original",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                SelectionContainer {
                    Text(
                        sourceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                when (val s = tState) {
                    TState.Idle -> Unit
                    TState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Translating...", style = MaterialTheme.typography.bodyMedium)
                    }

                    is TState.Done -> SelectionContainer {
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }

                    is TState.Error -> Column {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton({ scope.launch { translate(sourceText) } }) { Text("Try again") }
                    }
                }
            }
        }
    }
}

private fun wordCountLabel(value: String): String {
    val words = value.count { it == ' ' } + 1
    return if (words == 1) "1 word" else "$words words"
}