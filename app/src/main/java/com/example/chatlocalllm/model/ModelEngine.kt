package com.example.chatlocalllm.model

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ModelEngine(private val modelFile: File) {

    @Volatile private var engine: Engine? = null
    @Volatile private var streaming: Conversation? = null
    @Volatile private var cutShort = false
    private val work = Mutex()
    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)

    val status: StateFlow<ModelStatus> = _status
    val isLoaded: Boolean get() = engine != null

    suspend fun load() { work.withLock { loaded() } }

    fun ask(systemPrompt: String, message: String): Flow<String> = flow {
        work.withLock {
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
            )
            loaded().createConversation(config).use { conversation ->
                val text = StringBuilder()
                cutShort = false
                streaming = conversation
                try {
                    conversation.sendMessageAsync(message).collect { chunk ->
                        emit(text.append(chunk.toString()).toString())
                    }
                } catch (e: CancellationException) {

                    if (!cutShort) {
                        conversation.cancelProcess()
                        throw e
                    }
                } finally {
                    streaming = null
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun release() {
        streaming?.let { cutShort = true; it.cancelProcess() }
        work.withLock {
            withContext(Dispatchers.IO) { engine?.close() }
            engine = null
            _status.value = ModelStatus.NOT_LOADED
        }
    }

    private suspend fun loaded(): Engine = engine ?: withContext(Dispatchers.IO) {
        _status.value = ModelStatus.LOADING
        try {
            Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), maxNumTokens = 2_048))
                .also { it.initialize() }
                .also { engine = it; _status.value = ModelStatus.READY }
        } catch (t: Throwable) {
            _status.value = ModelStatus.NOT_LOADED
            throw t
        }
    }
}
