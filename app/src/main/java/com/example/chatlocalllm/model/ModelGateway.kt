package com.example.chatlocalllm.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

interface ModelGateway {

    suspend fun isReady(): Boolean

    val status: StateFlow<ModelStatus>

    fun ask(systemPrompt: String, message: String): Flow<String>

    suspend fun load() {}
    suspend fun release() {}
}

enum class ModelStatus { NONE, NOT_LOADED, LOADING, READY }

object NoModel : ModelGateway {
    override val status: StateFlow<ModelStatus> = MutableStateFlow(ModelStatus.NONE)
    override suspend fun isReady() = false
    override fun ask(systemPrompt: String, message: String): Flow<String> = emptyFlow()
}

class LiteRtGateway(private val engine: ModelEngine) : ModelGateway {
    override val status: StateFlow<ModelStatus> get() = engine.status
    override suspend fun isReady() = engine.isLoaded
    override fun ask(systemPrompt: String, message: String) = engine.ask(systemPrompt, message)
    override suspend fun load() { engine.load() }
    override suspend fun release() = engine.release()
}
