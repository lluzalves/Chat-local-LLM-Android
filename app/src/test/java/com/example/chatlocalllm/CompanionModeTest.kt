package com.example.chatlocalllm

import com.example.chatlocalllm.model.ModelStatus
import com.example.chatlocalllm.ui.chat.Speaker
import com.example.chatlocalllm.ui.chat.ChatMessage
import com.example.chatlocalllm.ui.chat.ChatViewModel
import com.example.chatlocalllm.ui.companion.CompanionMode
import com.example.chatlocalllm.ui.companion.CompanionTargets
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionModeTest {

    private val question = ChatMessage(Speaker.PERSON, "How many runs this week?")
    private val emptyStreaming = ChatMessage(Speaker.MODEL, "", streaming = true)
    private val partial = ChatMessage(Speaker.MODEL, "Two runs", streaming = true)
    private val answer = ChatMessage(Speaker.MODEL, "Two runs this week.")

    @Test
    fun `NONE is none, whatever the turn looks like`() {
        assertEquals(CompanionMode.NONE, CompanionMode.from(ModelStatus.NONE, busy = false, messages = emptyList()))
        assertEquals(CompanionMode.NONE, CompanionMode.from(ModelStatus.NONE, busy = true, messages = listOf(question, emptyStreaming)))
        assertEquals(
            CompanionMode.NONE,
            CompanionMode.from(ModelStatus.NONE, busy = false, messages = listOf(question, ChatMessage(Speaker.MODEL, ChatViewModel.NO_MODEL_LINE))),
        )
    }

    @Test
    fun `NOT_LOADED is asleep`() {
        assertEquals(CompanionMode.ASLEEP, CompanionMode.from(ModelStatus.NOT_LOADED, busy = false, messages = emptyList()))
        assertEquals(CompanionMode.ASLEEP, CompanionMode.from(ModelStatus.NOT_LOADED, busy = false, messages = listOf(question, answer)))
    }

    @Test
    fun `LOADING is loading, even with a send waiting on it`() {
        assertEquals(CompanionMode.LOADING, CompanionMode.from(ModelStatus.LOADING, busy = false, messages = emptyList()))
        assertEquals(CompanionMode.LOADING, CompanionMode.from(ModelStatus.LOADING, busy = true, messages = listOf(question, emptyStreaming)))
    }

    @Test
    fun `READY and not busy is idle`() {
        assertEquals(CompanionMode.IDLE, CompanionMode.from(ModelStatus.READY, busy = false, messages = emptyList()))
        assertEquals(CompanionMode.IDLE, CompanionMode.from(ModelStatus.READY, busy = false, messages = listOf(question, answer)))
    }

    @Test
    fun `busy with an empty streaming bubble is thinking`() {
        assertEquals(CompanionMode.THINKING, CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(question, emptyStreaming)))
    }

    @Test
    fun `busy with text in the streaming bubble is streaming`() {
        assertEquals(CompanionMode.STREAMING, CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(question, partial)))
    }

    @Test
    fun `busy with no bubble at all is streaming, not thinking - the rule reads the last bubble only`() {
        assertEquals(CompanionMode.STREAMING, CompanionMode.from(ModelStatus.READY, busy = true, messages = emptyList()))
    }

    @Test
    fun `busy with a last bubble that is not streaming is streaming`() {
        assertEquals(CompanionMode.STREAMING, CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(question)))
        assertEquals(CompanionMode.STREAMING, CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(question, answer)))
    }

    @Test
    fun `an empty bubble that is no longer streaming does not think`() {
        val cancelled = ChatMessage(Speaker.MODEL, "", streaming = false)
        assertEquals(CompanionMode.STREAMING, CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(question, cancelled)))
        assertEquals(CompanionMode.IDLE, CompanionMode.from(ModelStatus.READY, busy = false, messages = listOf(question, cancelled)))
    }

    @Test
    fun `the no-answer line after a failed turn is idle - the shiver is an impulse, not a mode`() {
        val failed = ChatMessage(Speaker.MODEL, ChatViewModel.NO_ANSWER_LINE)
        assertEquals(CompanionMode.IDLE, CompanionMode.from(ModelStatus.READY, busy = false, messages = listOf(question, failed)))
    }

    @Test
    fun `only the last bubble counts`() {

        assertEquals(
            CompanionMode.STREAMING,
            CompanionMode.from(ModelStatus.READY, busy = true, messages = listOf(emptyStreaming, question, partial)),
        )
    }

    @Test
    fun `the targets are the README's table`() {
        assertEquals(CompanionTargets(0.0, 0.12, 0.0, 1.0, 0.0, 0.0, 1.0), CompanionMode.NONE.targets)
        assertEquals(CompanionTargets(0.0, 0.30, 0.0, 0.45, 0.05, -0.04, 1.0), CompanionMode.ASLEEP.targets)
        assertEquals(CompanionTargets(0.15, 0.50, 0.3, 0.15, 0.9, -0.7, 0.88), CompanionMode.LOADING.targets)
        assertEquals(CompanionTargets(0.0, 0.55, 0.0, 0.0, 0.12, -0.09, 1.0), CompanionMode.IDLE.targets)
        assertEquals(CompanionTargets(1.0, 0.85, 1.0, 0.0, 2.6, -1.9, 1.0), CompanionMode.THINKING.targets)
        assertEquals(CompanionTargets(0.45, 0.70, 0.6, 0.0, 0.9, -0.65, 1.0), CompanionMode.STREAMING.targets)
    }
}
