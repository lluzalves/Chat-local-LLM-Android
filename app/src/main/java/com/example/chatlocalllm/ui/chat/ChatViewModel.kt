package com.example.chatlocalllm.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatlocalllm.model.ModelGateway
import com.example.chatlocalllm.model.ModelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class Speaker { PERSON, MODEL }

data class ChatMessage(val speaker: Speaker, val text: String, val streaming: Boolean = false)

class ChatViewModel(private val model: ModelGateway) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val status: StateFlow<ModelStatus> = model.status

    private val _tokens = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val tokens: SharedFlow<Unit> = _tokens

    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val failures: SharedFlow<Unit> = _failures

    private var current: Job? = null

    fun send(text: String) {
        if (text.isBlank()) return

        current?.cancel()
        val index = _messages.updateAndGet {
            it + ChatMessage(Speaker.PERSON, text) + ChatMessage(Speaker.MODEL, "", streaming = true)
        }.lastIndex
        _busy.value = true
        var soFar = ""
        current = viewModelScope.launch {
            val me = coroutineContext[Job]
            model.ask(SYSTEM, text)
                .onEach { soFar = it; _tokens.tryEmit(Unit) }
                .onEmpty { emit(NO_MODEL_LINE) }
                .catch { e ->
                    Log.w(TAG, "The model failed to answer", e)
                    _failures.tryEmit(Unit)

                    emit(if (soFar.isEmpty()) NO_ANSWER_LINE else "$soFar\n\n$NO_ANSWER_LINE")
                }
                .onCompletion {
                    replaceAt(index) { it.copy(streaming = false) }
                    if (current === me) _busy.value = false
                }
                .collect { latest -> replaceAt(index) { it.copy(text = latest) } }
        }
    }

    fun newChat() {
        current?.cancel()
        _busy.value = false
        _messages.value = emptyList()
    }

    private fun replaceAt(index: Int, change: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> if (index in list.indices) list.toMutableList().also { it[index] = change(it[index]) } else list }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        const val SYSTEM = "You are a plain, brief assistant running on this phone. Answer in a few sentences."
        const val NO_MODEL_LINE = "I cannot answer without a model on this phone."
        const val NO_ANSWER_LINE = "The model did not answer this time."
    }
}
