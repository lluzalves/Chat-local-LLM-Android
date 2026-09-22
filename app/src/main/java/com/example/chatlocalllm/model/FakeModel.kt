package com.example.chatlocalllm.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

open class FakeModel(private val answer: String?) : ModelGateway {

    override val status: StateFlow<ModelStatus> =
        MutableStateFlow(if (answer == null) ModelStatus.NONE else ModelStatus.READY)

    val loads = mutableListOf<Unit>()
    val releases = mutableListOf<Unit>()
    val asked = mutableListOf<String>()
    var failWith: Exception? = null

    override suspend fun isReady() = answer != null && status.value == ModelStatus.READY

    override fun ask(systemPrompt: String, message: String): Flow<String> {
        asked += message
        return if (answer == null) emptyFlow() else streamed(answer)
    }

    override suspend fun load() = doLoad()

    protected open suspend fun doLoad() {
        loads += Unit
        (status as MutableStateFlow).value = ModelStatus.READY
    }

    override suspend fun release() {
        releases += Unit
        (status as MutableStateFlow).value = ModelStatus.NOT_LOADED
    }

    private fun streamed(text: String): Flow<String> = flow {
        failWith?.let { throw it }
        if (text.isEmpty()) return@flow
        var soFar = ""
        for (word in text.split(' ')) {
            soFar = if (soFar.isEmpty()) word else "$soFar $word"
            emit(soFar)
        }
    }
}
