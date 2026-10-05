// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.Prestgg.oxu.ui.AppState
import io.github.Prestgg.oxu.ui.HomeScreen
import io.github.Prestgg.oxu.ui.LanguagePicker
import io.github.Prestgg.oxu.ui.OxuTheme
import io.github.Prestgg.oxu.ui.ReaderScreen
import io.github.Prestgg.oxu.ui.Screen
import io.github.Prestgg.oxu.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = remember { AppState(applicationContext) }
            val incoming = remember { intent.pdfUri() }
            LaunchedEffect(incoming) { incoming?.let { app.open(it) } }
            DisposableEffect(app) { onDispose { app.dispose() } }
            OxuTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        when (app.screen) {
                            Screen.FIRST_RUN -> LanguagePicker(
                                title = "Choose your target language",
                                subtitle = "Which language should PDF text be translated into?",
                                confirmLabel = "Continue",
                                initial = null
                            ) { code ->
                                app.prefs.targetLang = code
                                app.go(Screen.HOME)
                            }

                            Screen.HOME -> HomeScreen(app)

                            Screen.SETTINGS -> SettingsScreen(app) { app.go(Screen.HOME) }

                            Screen.READER -> ReaderScreen(app) {
                                app.closeDoc()
                                app.go(Screen.HOME)
                            }
                        }

                        if (app.loading) {
                            Surface(
                                Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                                        Text("Opening PDF...")
                                    }
                                }
                            }
                        }

                        app.error?.let { msg ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(
                                    Modifier.padding(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        msg,
                                        Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton({ app.error = null }) { Text("OK") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Intent.pdfUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
    else -> null
}
