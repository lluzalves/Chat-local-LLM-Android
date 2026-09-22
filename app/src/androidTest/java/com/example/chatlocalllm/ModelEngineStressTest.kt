package com.example.chatlocalllm

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatlocalllm.model.LiteRtGateway
import com.example.chatlocalllm.model.ModelEngine
import com.example.chatlocalllm.model.ModelPresence
import com.example.chatlocalllm.ui.chat.ChatViewModel
import com.example.chatlocalllm.ui.chat.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelEngineStressTest {

    private lateinit var modelFile: File

    @Before
    fun theFileIsOnThePhone() {
        val filesDir = ApplicationProvider.getApplicationContext<Context>().filesDir
        val presence = ModelPresence.lookIn(filesDir)
        assumeTrue("no model; push it first", presence.isHere)
        modelFile = presence.file!!
    }

    @Test
    fun twentyAnswersInARow_memoryDoesNotGrow() = runBlocking {
        val engine = ModelEngine(modelFile)
        engine.load()
        val pss = mutableListOf<Long>()
        repeat(20) { i ->
            var last = ""
            engine.ask(SYSTEM, "Give me one word for the number ${i + 1}.").collect { last = it }
            assertTrue("turn $i said nothing", last.isNotBlank())
            pss += pssMb()
        }
        Log.i(TAG, "PSS after each of 20 answers (MB): $pss")
        val early = pss.take(3).average()
        val late = pss.takeLast(3).average()
        assertTrue("PSS grew from ${early.toInt()} MB to ${late.toInt()} MB over 20 answers", late - early < 150)
        engine.release()
    }

    @Test
    fun tenReleasesMidAnswer_eachFastAndTheEngineComesBack() = runBlocking {
        val engine = ModelEngine(modelFile)
        engine.load()

        val fullMs = timed { engine.ask(SYSTEM, LONG_QUESTION).collect { } }
        val releases = mutableListOf<Long>()
        repeat(10) { i ->
            val emissions = mutableListOf<String>()
            var failure: Throwable? = null
            val answer = launch(Dispatchers.IO) {
                try { engine.ask(SYSTEM, LONG_QUESTION).collect { emissions += it } } catch (t: Throwable) { failure = t }
            }
            withTimeout(60_000) { while (emissions.isEmpty()) delay(20) }
            val ms = timed { engine.release() }
            answer.join()
            assertTrue("cycle $i: release() took $ms ms", ms < 2_000)
            assertTrue("cycle $i: the cut was reported as a failure: $failure", failure == null)
            assertFalse(engine.isLoaded)
            releases += ms
            engine.load()
            var last = ""
            engine.ask(SYSTEM, "Say hi.").collect { last = it }
            assertTrue("cycle $i: no answer after the reload", last.isNotBlank())
        }
        Log.i(TAG, "uncut answer: $fullMs ms; release() mid-answer x10 (ms): $releases")
        assertTrue("a cut release should be far faster than the answer it cuts", releases.max() < fullMs / 2)
        engine.release()
    }

    @Test
    fun fiveSendsInABurst_throughTheViewModel_endInOneCleanAnswer() = runBlocking {
        val engine = ModelEngine(modelFile)
        engine.load()
        val vm = ChatViewModel(LiteRtGateway(engine))
        withContext(Dispatchers.Main) {
            repeat(5) { i ->
                vm.send("Question number ${i + 1}: name a colour.")
                delay(150)
            }
        }
        withTimeout(120_000) { while (vm.busy.value || vm.messages.value.any { it.streaming }) delay(50) }
        val messages = vm.messages.value
        Log.i(TAG, "after the burst: ${messages.map { "${it.speaker}:${it.text.take(30)}" }}")
        assertEquals("five questions, five model bubbles", 10, messages.size)
        assertTrue("every model bubble is settled", messages.none { it.streaming })
        val answers = messages.filter { it.speaker == Speaker.MODEL }
        assertTrue("the last answer is real", answers.last().text.isNotBlank() && answers.last().text != ChatViewModel.NO_ANSWER_LINE)
        assertTrue("no cancelled turn ended on the no-answer line", answers.none { it.text.contains(ChatViewModel.NO_ANSWER_LINE) })
        engine.release()
    }

    @Test
    fun footprint_freshLoaded_afterAnAnswer_released() = runBlocking {
        val before = pssMb()
        val engine = ModelEngine(modelFile)
        engine.load()
        delay(500)
        val loaded = pssMb()
        var peak = loaded
        val answer = launch(Dispatchers.IO) { engine.ask(SYSTEM, LONG_QUESTION).collect { } }
        while (answer.isActive) { peak = maxOf(peak, pssMb()); delay(200) }
        delay(1_000)
        val afterAnswer = pssMb()
        val keptStats = stats()
        engine.release()
        val samples = mutableListOf<Long>()
        repeat(5) { System.gc(); delay(1_000); samples += pssMb() }
        val released = samples.last()
        Log.i(TAG, "PSS MB: before load $before, loaded $loaded, peak while streaming $peak, after the answer (still loaded) $afterAnswer, after release (1..5 s) $samples")
        Log.i(TAG, "kept after the answer: $keptStats")
        Log.i(TAG, "after release:        ${stats()}")
        assertTrue("loading should cost at least 100 MB of PSS, got ${loaded - before}", loaded - before > 100)
        assertTrue("the engine keeps most of the answer's memory until released: loaded $loaded, after the answer $afterAnswer", afterAnswer - loaded > 500)
        assertTrue("release should return most of what the answer took: after the answer $afterAnswer, released $released", released < loaded + 100)
    }

    private fun stats(): String {
        val m = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val keys = listOf("summary.java-heap", "summary.native-heap", "summary.code", "summary.stack", "summary.graphics", "summary.private-other", "summary.system", "summary.total-pss")
        return keys.joinToString(", ") { "${it.removePrefix("summary.")}=${(m.getMemoryStat(it)?.toLongOrNull() ?: 0L) / 1024}" }
    }

    private fun pssMb(): Long = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024L

    private inline fun timed(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }

    companion object {
        private const val TAG = "ModelEngineStressTest"
        private const val SYSTEM = "You are a plain, brief assistant. Answer in one short sentence."
        private const val LONG_QUESTION = "Write eight sentences about the sea, each on a new topic."
    }
}
