package com.example.chatlocalllm

import com.example.chatlocalllm.model.FakeModel
import com.example.chatlocalllm.ui.chat.ChatMessage
import com.example.chatlocalllm.ui.chat.ChatViewModel
import com.example.chatlocalllm.ui.chat.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import com.example.chatlocalllm.model.ModelGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `with a model, the last bubble is the answer`() = runTest {
        val vm = ChatViewModel(FakeModel("Two runs this week."))
        vm.send("How many runs this week?")
        advanceUntilIdle()
        assertEquals("Two runs this week.", vm.messages.value.last().text)
    }

    @Test
    fun `without a model, the last bubble is the no-model line`() = runTest {
        val vm = ChatViewModel(FakeModel(null))
        vm.send("How many runs this week?")
        advanceUntilIdle()
        assertEquals(ChatViewModel.NO_MODEL_LINE, vm.messages.value.last().text)
    }

    @Test
    fun `a turn streams into the model's bubble`() = runTest {
        val vm = ChatViewModel(FakeModel("Two runs this week."))
        val seen = mutableListOf<List<ChatMessage>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.toList(seen) }

        vm.send("How many runs this week?")
        advanceUntilIdle()

        assertEquals(
            listOf(ChatMessage(Speaker.PERSON, "How many runs this week?"), ChatMessage(Speaker.MODEL, "Two runs this week.")),
            vm.messages.value,
        )
        val modelTexts = seen.mapNotNull { it.getOrNull(1)?.text }.distinct()
        assertEquals(listOf("", "Two", "Two runs", "Two runs this", "Two runs this week."), modelTexts)
        assertTrue(seen.dropLast(1).mapNotNull { it.getOrNull(1) }.all { it.streaming })
        assertFalse(vm.messages.value.last().streaming)
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a second turn appends, and is its own ask`() = runTest {
        val fake = FakeModel("An answer.")
        val vm = ChatViewModel(fake)

        vm.send("first")
        advanceUntilIdle()
        vm.send("second")
        advanceUntilIdle()

        assertEquals(listOf("first", "An answer.", "second", "An answer."), vm.messages.value.map { it.text })
        assertEquals(listOf(Speaker.PERSON, Speaker.MODEL, Speaker.PERSON, Speaker.MODEL), vm.messages.value.map { it.speaker })
        assertEquals("each question is one ask; nothing carries over", listOf("first", "second"), fake.asked)
    }

    @Test
    fun `without a model the line is the bubble, the turn is over and nothing streamed`() = runTest {
        val vm = ChatViewModel(FakeModel(null))
        vm.send("How many runs this week?")
        advanceUntilIdle()

        assertEquals(
            listOf(
                ChatMessage(Speaker.PERSON, "How many runs this week?"),
                ChatMessage(Speaker.MODEL, ChatViewModel.NO_MODEL_LINE),
            ),
            vm.messages.value,
        )
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a model that says nothing gets the same line as no model`() = runTest {
        val vm = ChatViewModel(FakeModel(""))
        vm.send("Say nothing.")
        advanceUntilIdle()
        assertEquals(ChatViewModel.NO_MODEL_LINE, vm.messages.value.last().text)
        assertFalse(vm.messages.value.last().streaming)
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a runtime that throws ends on the no-answer line, not a crash, and the next turn is asked again`() = runTest {
        val fake = FakeModel("An answer.").apply { failWith = IllegalStateException("Conversation is not alive.") }
        val vm = ChatViewModel(fake)
        vm.send("first")
        advanceUntilIdle()
        assertEquals(ChatViewModel.NO_ANSWER_LINE, vm.messages.value.last().text)
        assertFalse(vm.messages.value.last().streaming)
        assertFalse(vm.busy.value)

        fake.failWith = null
        vm.send("second")
        advanceUntilIdle()
        assertEquals("An answer.", vm.messages.value.last().text)
        assertEquals(listOf("first", "second"), fake.asked)
    }

    @Test
    fun `a runtime that throws mid-answer keeps what was read and adds the line under it`() = runTest {
        val vm = ChatViewModel(FailingMidway("Two runs this", IllegalStateException("Conversation is not alive.")))
        vm.send("How many runs this week?")
        advanceUntilIdle()
        assertEquals("Two runs this\n\n" + ChatViewModel.NO_ANSWER_LINE, vm.messages.value.last().text)
        assertFalse(vm.messages.value.last().streaming)
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a send while a turn streams replaces it - the first bubble keeps what it had`() = runTest {
        val vm = ChatViewModel(SlowModel())
        vm.send("first question")
        advanceTimeBy(15)
        assertTrue(vm.busy.value)

        vm.send("second question")
        advanceUntilIdle()

        assertEquals(
            listOf("first question", "echo", "second question", "echo second question"),
            vm.messages.value.map { it.text },
        )
        assertTrue("the cancelled bubble is not streaming any more", vm.messages.value.none { it.streaming })
        assertFalse(vm.busy.value)
    }

    @Test
    fun `new chat clears the bubbles and the next turn starts fresh`() = runTest {
        val fake = FakeModel("An answer.")
        val vm = ChatViewModel(fake)
        vm.send("first")
        advanceUntilIdle()

        vm.newChat()
        assertEquals(emptyList<ChatMessage>(), vm.messages.value)
        assertFalse(vm.busy.value)

        vm.send("again")
        advanceUntilIdle()
        assertEquals(listOf("again", "An answer."), vm.messages.value.map { it.text })
    }

    @Test
    fun `new chat mid-stream cancels the turn`() = runTest {
        val vm = ChatViewModel(SlowModel())
        vm.send("first question")
        advanceTimeBy(15)
        vm.newChat()
        advanceUntilIdle()
        assertTrue(vm.messages.value.isEmpty())
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a blank message is ignored`() = runTest {
        val fake = FakeModel("An answer.")
        val vm = ChatViewModel(fake)
        vm.send("  ")
        advanceUntilIdle()
        assertTrue(vm.messages.value.isEmpty())
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun `one token impulse per chunk the model streamed, none on failure`() = runTest {
        val vm = ChatViewModel(FakeModel("Two runs this week."))
        val tokens = mutableListOf<Unit>()
        val failures = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tokens.toList(tokens) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.failures.toList(failures) }

        vm.send("How many runs this week?")
        advanceUntilIdle()

        assertEquals("four words, four chunks, four pulses", 4, tokens.size)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a failed turn is one failure impulse and no token`() = runTest {
        val fake = FakeModel("An answer.").apply { failWith = IllegalStateException("Conversation is not alive.") }
        val vm = ChatViewModel(fake)
        val tokens = mutableListOf<Unit>()
        val failures = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tokens.toList(tokens) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.failures.toList(failures) }

        vm.send("first")
        advanceUntilIdle()

        assertEquals(1, failures.size)
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `without a model there is neither a token nor a failure - the line is a normal bubble`() = runTest {
        val vm = ChatViewModel(FakeModel(null))
        val tokens = mutableListOf<Unit>()
        val failures = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tokens.toList(tokens) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.failures.toList(failures) }

        vm.send("How many runs this week?")
        advanceUntilIdle()

        assertTrue(tokens.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a cancelled turn is not a failure`() = runTest {
        val vm = ChatViewModel(SlowModel())
        val failures = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.failures.toList(failures) }
        vm.send("first question")
        advanceTimeBy(15)
        vm.newChat()
        advanceUntilIdle()
        assertTrue(failures.isEmpty())
    }
}

private class FailingMidway(private val partial: String, private val error: Exception) : ModelGateway by FakeModel(partial) {
    override fun ask(systemPrompt: String, message: String): Flow<String> = flow {
        var soFar = ""
        for (word in partial.split(' ')) {
            soFar = if (soFar.isEmpty()) word else "$soFar $word"
            emit(soFar)
        }
        throw error
    }
}
