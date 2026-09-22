package com.example.chatlocalllm

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatlocalllm.model.LiteRtGateway
import com.example.chatlocalllm.model.ModelEngine
import com.example.chatlocalllm.model.ModelPresence
import com.example.chatlocalllm.model.SessionDriver
import com.example.chatlocalllm.model.SessionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionDriverInstrumentedTest {

    private lateinit var modelFile: File
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("the driver never reads the owner")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun theFileIsOnThePhone() {
        val presence = ModelPresence.lookIn(ApplicationProvider.getApplicationContext<Context>().filesDir)
        assumeTrue("no model; push it first", presence.isHere)
        modelFile = presence.file!!
    }

    @After
    fun stop() { scope.cancel() }

    @Test
    fun sixtySecondsInTheBackground_releaseTheRealEngine() = runBlocking<Unit> {
        val engine = ModelEngine(modelFile)
        val driver = SessionDriver(gateway = kotlinx.coroutines.flow.MutableStateFlow(LiteRtGateway(engine)), scope = scope)
        engine.load()
        assertTrue(engine.isLoaded)
        withContext(Dispatchers.Main) { driver.onStop(owner) }
        delay(SessionPolicy.IDLE_RELEASE_MS - 5_000)
        assertTrue("55 s in: still loaded", engine.isLoaded)
        delay(7_000)
        assertFalse("62 s in: released", engine.isLoaded)
        Log.i(TAG, "background release at ~60 s: ok")
    }

    @Test
    fun aClockThatStepsBack_reArmsTheMinute_andReleasesOnTheSecond() = runBlocking<Unit> {
        var offset = 0L
        val engine = ModelEngine(modelFile)
        val driver = SessionDriver(
            gateway = kotlinx.coroutines.flow.MutableStateFlow(LiteRtGateway(engine)),
            scope = scope,
            clock = { System.currentTimeMillis() + offset },
        )
        engine.load()
        withContext(Dispatchers.Main) { driver.onStop(owner) }
        offset = -1_000
        delay(SessionPolicy.IDLE_RELEASE_MS + 2_000)
        assertTrue("the first tick read 59 s: must keep", engine.isLoaded)
        delay(SessionPolicy.IDLE_RELEASE_MS + 2_000)
        assertFalse("the re-armed minute released", engine.isLoaded)
        Log.i(TAG, "early tick kept, second minute released: ok")
    }

    companion object { private const val TAG = "SessionDriverInstrumentedTest" }
}
