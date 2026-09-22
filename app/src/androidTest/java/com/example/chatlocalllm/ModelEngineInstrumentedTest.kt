package com.example.chatlocalllm

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatlocalllm.model.ModelEngine
import com.example.chatlocalllm.model.ModelPresence
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelEngineInstrumentedTest {

    private lateinit var modelFile: File

    @Before
    fun theFileIsOnThePhone() {
        val filesDir = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        val presence = ModelPresence.lookIn(filesDir)
        assumeTrue("no model at ${File(filesDir, ModelPresence.MODELS_DIR)}; push it first", presence.isHere)
        modelFile = presence.file!!
    }

    @Test
    fun initializeWorks_andAskStreamsTheTextSoFar_andReleaseGivesItBack() = runBlocking {
        val engine = ModelEngine(modelFile)
        assertFalse(engine.isLoaded)

        val loadMs = timed { engine.load() }
        Log.i(TAG, "load(): $loadMs ms")
        assertTrue(engine.isLoaded)

        val emissions = mutableListOf<String>()
        val askMs = timed {
            engine.ask("You are a plain, brief assistant. Answer in one short sentence.", "Say hello.")
                .collect { emissions += it }
        }
        Log.i(TAG, "ask(): $askMs ms, ${emissions.size} emissions, last = ${emissions.lastOrNull()}")

        assertTrue("the runtime said nothing", emissions.isNotEmpty())
        assertTrue("the answer is blank", emissions.last().isNotBlank())

        emissions.zipWithNext().forEachIndexed { i, (before, after) ->
            assertTrue("emission ${i + 1} does not extend emission $i:\n<$before>\n<$after>", after.startsWith(before))
        }

        engine.release()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun theRuntimeStreamsDeltas_notTheTextSoFar() {

        val chunks = mutableListOf<String>()
        Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), maxNumTokens = 2_048)).use { engine ->
            engine.initialize()
            engine.createConversation().use { conversation ->
                runBlocking {
                    conversation.sendMessageAsync("Count from one to ten in words, separated by commas.")
                        .collect { chunks += it.toString() }
                }
            }
        }
        Log.i(TAG, "${chunks.size} chunks: ${chunks.joinToString("|")}")

        assumeTrue("one chunk only; nothing to tell delta from cumulative", chunks.size > 1)

        val extending = chunks.zipWithNext().count { (a, b) -> a.isNotEmpty() && b.startsWith(a) }
        assertTrue(
            "$extending of ${chunks.size - 1} chunks extend the previous one: the runtime streams the text so far, not deltas",
            extending < (chunks.size - 1) / 2,
        )

        val text = chunks.joinToString("")
        assertTrue("no 'ten' in <$text>", text.contains("ten", ignoreCase = true))
    }

    @Test
    fun aContextWindowSmallerThanThePrefillChunkFailsAtTheFirstMessage() {

        Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), maxNumTokens = 512)).use { engine ->
            engine.initialize()
            engine.createConversation().use { conversation ->
                try {
                    runBlocking { conversation.sendMessageAsync("Say hello.").collect { } }
                    throw AssertionError("maxNumTokens = 512 worked; the floor moved, update the article")
                } catch (e: com.google.ai.edge.litertlm.LiteRtLmJniException) {
                    Log.i(TAG, "512 tokens: ${e.message?.lineSequence()?.firstOrNull()}")
                }
            }
        }
    }

    @Test
    fun releaseDuringAnAnswerCutsItShort_andTheCollectorSeesANormalEnd() = runBlocking {

        val engine = ModelEngine(modelFile)
        engine.load()
        val emissions = mutableListOf<String>()
        var releaseMs = -1L
        var failure: Throwable? = null
        val answer = launch(Dispatchers.IO) {
            try {
                engine.ask("You are a verbose assistant.", "Write ten sentences about the sea.").collect { emissions += it }
            } catch (t: Throwable) {
                failure = t
            }
        }

        withTimeout(60_000) { while (emissions.isEmpty()) delay(20) }
        releaseMs = timed { engine.release() }
        answer.join()
        Log.i(TAG, "release() during an answer: $releaseMs ms, answer ended after ${emissions.size} emissions: ${emissions.lastOrNull()}; failure = $failure")

        assertFalse(engine.isLoaded)
        assertTrue("a cut asked for by release() is a normal end, not a failure: $failure", failure == null)
        assertTrue("release() waited for the whole answer", releaseMs < 5_000)
        assertTrue("ten sentences would be far longer", emissions.last().length < 600)
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }

    companion object {
        private const val TAG = "ModelEngineInstrumentedTest"
    }
}
