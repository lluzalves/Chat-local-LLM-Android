package com.example.chatlocalllm.ui.chat

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatlocalllm.R
import com.example.chatlocalllm.model.FakeModel
import com.example.chatlocalllm.model.ModelStatus
import com.example.chatlocalllm.ui.companion.Companion
import com.example.chatlocalllm.ui.companion.CompanionMode
import com.example.chatlocalllm.ui.modelStatusLabel
import com.example.chatlocalllm.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel

data class ChatUiState(
    val status: ModelStatus,
    val messages: List<ChatMessage>,
    val busy: Boolean,
)

@Composable
fun ChatRoute(viewModel: ChatViewModel = koinViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    ChatScreen(
        state = ChatUiState(status = status, messages = messages, busy = busy),
        text = text,
        onTextChange = { text = it },
        onSend = {
            viewModel.send(text)
            text = ""
        },
        onNewChat = viewModel::newChat,
        tokens = viewModel.tokens,
        failures = viewModel.failures,
        reducedMotion = reducedMotion,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    tokens: Flow<Unit> = emptyFlow(),
    failures: Flow<Unit> = emptyFlow(),
    reducedMotion: Boolean = false,
) {
    val mode = CompanionMode.from(state.status, state.busy, state.messages)
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val canSend = text.isNotBlank() && !state.busy

    val send = {
        if (canSend) {
            focus.clearFocus()
            keyboard?.hide()
            onSend()
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        CompanionHero(
            mode = mode,
            status = state.status,
            compact = imeVisible,
            canReset = state.messages.isNotEmpty(),
            onNewChat = onNewChat,
            tokens = tokens,
            failures = failures,
            reducedMotion = reducedMotion,
        )
        val listState = rememberLazyListState()

        LaunchedEffect(state.messages.size, imeVisible) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {

                    val last = state.messages.lastIndex
                    itemsIndexed(state.messages.asReversed(), key = { i, _ -> last - i }) { _, message -> Bubble(message, reducedMotion) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Spacer(Modifier.width(8.dp))

            FilledIconButton(onClick = send, enabled = canSend) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun CompanionHero(
    mode: CompanionMode,
    status: ModelStatus,
    compact: Boolean,
    canReset: Boolean,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    tokens: Flow<Unit> = emptyFlow(),
    failures: Flow<Unit> = emptyFlow(),
    reducedMotion: Boolean = false,
) {
    val companion = remember(tokens, failures, reducedMotion) {
        movableContentOf { current: CompanionMode, companionModifier: Modifier ->
            Companion(mode = current, modifier = companionModifier, tokens = tokens, failures = failures, reducedMotion = reducedMotion)
        }
    }
    val companionSize by animateDpAsState(
        targetValue = if (compact) COMPACT_COMPANION else HERO_COMPANION,
        animationSpec = tween(300),
        label = "companion size",
    )

    val thinking = mode == CompanionMode.THINKING
    val stillThinking by produceState(initialValue = false, thinking) {
        value = false
        if (thinking) {
            delay(STILL_THINKING_AFTER_MS)
            value = true
        }
    }
    val subLabel = when {
        !thinking -> null
        stillThinking -> R.string.chat_still_thinking
        else -> R.string.chat_thinking
    }
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            companion(mode, Modifier.size(companionSize))
            Spacer(Modifier.width(12.dp))
            StatusTexts(status = status, subLabel = subLabel, centered = false, modifier = Modifier.weight(1f))
            NewChatButton(enabled = canReset, onClick = onNewChat)
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                companion(mode, Modifier.fillMaxWidth().height(companionSize))
                StatusTexts(status = status, subLabel = subLabel, centered = true, modifier = Modifier.heightIn(min = 38.dp))
                Spacer(Modifier.height(8.dp))
            }
            NewChatButton(
                enabled = canReset,
                onClick = onNewChat,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusTexts(status: ModelStatus, subLabel: Int?, centered: Boolean, modifier: Modifier = Modifier) {
    val subAlpha by animateFloatAsState(if (subLabel != null) 0.85f else 0f, tween(250), label = "sub alpha")
    Column(modifier = modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = stringResource(modelStatusLabel(status)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.let { if (status == ModelStatus.NONE) it.copy(alpha = 0.7f) else it },
        )
        Text(
            text = subLabel?.let { stringResource(it) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.height(16.dp).graphicsLayer { alpha = subAlpha },
        )
    }
}

@Composable
private fun NewChatButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.chat_new))
    }
}

@Composable
private fun Bubble(message: ChatMessage, reducedMotion: Boolean) {
    val person = message.speaker == Speaker.PERSON
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (person) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (person) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(if (person) R.string.chat_you else R.string.chat_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    message.streaming && message.text.isEmpty() -> ThinkingDots(reducedMotion)
                    message.streaming -> Text(
                        text = buildAnnotatedString {
                            append(message.text)
                            appendInlineContent(CARET, "|")
                        },
                        inlineContent = mapOf(
                            CARET to InlineTextContent(
                                Placeholder(width = 0.4.em, height = 1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
                            ) { Caret(reducedMotion) },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val progress: Float? = if (reducedMotion) null else {
        rememberInfiniteTransition(label = "dots").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(DOTS_PERIOD_MS, easing = LinearEasing)),
            label = "dots",
        ).value
    }
    Canvas(modifier.padding(top = 8.dp, bottom = 4.dp).size(width = 26.dp, height = 8.dp)) {
        val r = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val lift = 2.dp.toPx()
        for (i in 0 until 3) {
            val u = progress?.let { ((it - i * DOTS_DELAY_MS / DOTS_PERIOD_MS.toFloat()) + 1f) % 1f }
            val p = when {
                u == null -> 0f
                u < 0.4f -> u / 0.4f
                u < 0.8f -> 1f - (u - 0.4f) / 0.4f
                else -> 0f
            }
            drawCircle(
                color = color,
                radius = r,
                center = Offset(r + i * (2 * r + gap), size.height - r - lift * p),
                alpha = if (u == null) 1f else 0.25f + 0.75f * p,
            )
        }
    }
}

@Composable
private fun Caret(reducedMotion: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val progress: Float? = if (reducedMotion) null else {
        rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "caret",
        ).value
    }
    Canvas(Modifier.fillMaxHeight().width(4.dp)) {
        val on = progress == null || progress < 0.5f
        if (on) drawRect(color, topLeft = Offset(2.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
    }
}

private val HERO_COMPANION = 172.dp
private val COMPACT_COMPANION = 56.dp
private const val STILL_THINKING_AFTER_MS = 8_000L
private const val DOTS_PERIOD_MS = 1200
private const val DOTS_DELAY_MS = 200
private const val CARET = "caret"

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "a conversation, streaming")
@Composable
private fun ChatStreamingPreview() {
    val fake = FakeModel("Two runs this week, both in the morning.")
    AppTheme {
        ChatScreen(
            state = ChatUiState(
                status = fake.status.value,
                messages = listOf(
                    ChatMessage(Speaker.PERSON, "How many runs this week?"),
                    ChatMessage(Speaker.MODEL, "Two runs this week, both in the morning."),
                    ChatMessage(Speaker.PERSON, "And swims?"),
                    ChatMessage(Speaker.MODEL, "One sw", streaming = true),
                ),
                busy = true,
            ),
            text = "", onTextChange = {}, onSend = {}, onNewChat = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "thinking")
@Composable
private fun ChatThinkingPreview() {
    AppTheme {
        ChatScreen(
            state = ChatUiState(
                status = ModelStatus.READY,
                messages = listOf(
                    ChatMessage(Speaker.PERSON, "What can you do while offline?"),
                    ChatMessage(Speaker.MODEL, "", streaming = true),
                ),
                busy = true,
            ),
            text = "", onTextChange = {}, onSend = {}, onNewChat = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "no model")
@Composable
private fun ChatNoModelPreview() {
    val fake = FakeModel(null)
    AppTheme {
        ChatScreen(
            state = ChatUiState(
                status = fake.status.value,
                messages = listOf(
                    ChatMessage(Speaker.PERSON, "How many runs this week?"),
                    ChatMessage(Speaker.MODEL, ChatViewModel.NO_MODEL_LINE),
                ),
                busy = false,
            ),
            text = "", onTextChange = {}, onSend = {}, onNewChat = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "empty, idle")
@Composable
private fun ChatEmptyPreview() {
    AppTheme {
        ChatScreen(
            state = ChatUiState(status = ModelStatus.READY, messages = emptyList(), busy = false),
            text = "", onTextChange = {}, onSend = {}, onNewChat = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "hero with the keyboard up (compact row)")
@Composable
private fun CompanionHeroCompactPreview() {
    AppTheme {
        CompanionHero(
            mode = CompanionMode.THINKING,
            status = ModelStatus.READY,
            compact = true,
            canReset = true,
            onNewChat = {},
        )
    }
}
