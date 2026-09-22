package com.example.chatlocalllm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.chatlocalllm.model.SessionDriver
import com.example.chatlocalllm.ui.chat.ChatRoute
import com.example.chatlocalllm.ui.theme.AppTheme
import org.koin.compose.koinInject

@Composable
fun ChatLocalLlmApp(session: SessionDriver = koinInject()) {
    LaunchedEffect(Unit) { session.screenOpened() }
    DisposableEffect(Unit) { onDispose { session.screenClosed() } }
    AppShell { ChatRoute() }
}

@Composable
fun AppShell(content: @Composable () -> Unit) {
    Scaffold { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26)
@Composable
private fun AppShellPreview() {
    AppTheme { AppShell { Text("content") } }
}
