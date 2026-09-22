package com.example.chatlocalllm

import com.example.chatlocalllm.model.FakeModel
import com.example.chatlocalllm.model.ModelGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SlowModel(private val prefix: String = "echo", private val msPerWord: Long = 10L) : ModelGateway by FakeModel(prefix) {

    override fun ask(systemPrompt: String, message: String): Flow<String> = slow("$prefix $message")

    private fun slow(text: String): Flow<String> = flow {
        var soFar = ""
        for (word in text.split(' ')) {
            delay(msPerWord)
            soFar = if (soFar.isEmpty()) word else "$soFar $word"
            emit(soFar)
        }
    }
}
