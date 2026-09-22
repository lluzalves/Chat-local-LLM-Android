package com.example.chatlocalllm

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.chatlocalllm.model.FakeModel
import com.example.chatlocalllm.model.GatewayManager
import com.example.chatlocalllm.model.ModelGateway
import com.example.chatlocalllm.model.ModelStatus
import com.example.chatlocalllm.model.NoModel
import com.example.chatlocalllm.model.SessionDriver
import com.example.chatlocalllm.model.SessionPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDriverTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("the driver never reads the owner")
    }

    private fun TestScope.driverFor(gateway: ModelGateway) =
        SessionDriver(gateway = MutableStateFlow(gateway), scope = backgroundScope, clock = { currentTime })

    @Test
    fun `a screen opening loads the model`() = runTest {
        val fake = FakeModel("x").apply { release() }
        val driver = driverFor(fake)
        driver.screenOpened()
        runCurrent()
        assertEquals(1, fake.loads.size)
        assertTrue(driver.current.loaded)
    }

    @Test
    fun `a screen opening when loaded does nothing`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.screenOpened()
        runCurrent()
        assertTrue(fake.loads.isEmpty())
    }

    @Test
    fun `sixty seconds in the background release, not before`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.onStop(owner)
        runCurrent()
        advanceTimeBy(SessionPolicy.IDLE_RELEASE_MS - 1)
        assertTrue("nothing before the minute", fake.releases.isEmpty())
        advanceTimeBy(2)
        assertEquals(1, fake.releases.size)
        assertEquals(ModelStatus.NOT_LOADED, fake.status.value)
        advanceTimeBy(10 * SessionPolicy.IDLE_RELEASE_MS)
        assertEquals("released once; the job is done", 1, fake.releases.size)
    }

    @Test
    fun `coming back to the foreground disarms the idle job`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.onStop(owner)
        advanceTimeBy(30_000)
        driver.onStart(owner)
        advanceTimeBy(10 * SessionPolicy.IDLE_RELEASE_MS)
        assertTrue(fake.releases.isEmpty())
        assertTrue(driver.current.foreground)
    }

    @Test
    fun `background, foreground, background again - the minute starts over`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.onStop(owner)
        advanceTimeBy(50_000)
        driver.onStart(owner)
        runCurrent()
        driver.onStop(owner)
        advanceTimeBy(50_000)
        assertTrue("fifty seconds into the second stay: not yet", fake.releases.isEmpty())
        advanceTimeBy(10_001)
        assertEquals(1, fake.releases.size)
    }

    @Test
    fun `coming back with a screen open reloads what the background released`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.screenOpened()
        driver.onStop(owner)
        advanceTimeBy(SessionPolicy.IDLE_RELEASE_MS + 1)
        assertEquals(1, fake.releases.size)

        driver.onStart(owner)
        runCurrent()
        assertEquals("the screen was still open: ScreenOpened again", 1, fake.loads.size)
        assertEquals(ModelStatus.READY, fake.status.value)
    }

    @Test
    fun `coming back without a screen open loads nothing`() = runTest {
        val fake = FakeModel("x").apply { release() }
        val driver = driverFor(fake)
        driver.onStop(owner)
        driver.onStart(owner)
        runCurrent()
        assertTrue(fake.loads.isEmpty())
    }

    @Test
    fun `a trim releases a loaded model and ignores an unloaded one`() = runTest {
        val fake = FakeModel("x")
        val driver = driverFor(fake)
        driver.onTrimMemory()
        runCurrent()
        assertEquals(1, fake.releases.size)
        driver.onTrimMemory()
        runCurrent()
        assertEquals(1, fake.releases.size)
    }

    @Test
    fun `loaded is read from the gateway, not remembered`() = runTest {

        val fake = FakeModel("x").apply { release() }
        val driver = driverFor(fake)
        driver.onEvent(SessionPolicy.Event.Foreground)
        runCurrent()
        assertFalse(driver.current.loaded)
        fake.load()
        driver.onTrimMemory()
        runCurrent()
        assertEquals("the fixture's release plus the trim's", 2, fake.releases.size)
    }

    @Test
    fun `no model on the phone, the driver runs and nothing changes`() = runTest {
        val driver = driverFor(NoModel)
        driver.screenOpened()
        driver.onStop(owner)
        advanceTimeBy(2 * SessionPolicy.IDLE_RELEASE_MS)
        driver.onTrimMemory()
        driver.onStart(owner)
        runCurrent()
        assertFalse(driver.current.loaded)
        assertEquals(ModelStatus.NONE, NoModel.status.value)
    }

    @Test
    fun `with nothing loaded the background arms nothing`() = runTest {
        val fake = FakeModel("x").apply { release() }
        val driver = driverFor(fake)
        driver.onStop(owner)
        advanceTimeBy(10 * SessionPolicy.IDLE_RELEASE_MS)
        assertEquals("only the fixture's release", 1, fake.releases.size)
    }

    @Test
    fun `a load that fails leaves the state unloaded and the driver alive`() = runTest {
        val fake = object : FakeModel("x") {
            override suspend fun doLoad() = throw IllegalStateException("Engine is not initialized.")
        }.apply { release() }
        val driver = driverFor(fake)
        driver.screenOpened()
        runCurrent()
        assertFalse(driver.current.loaded)
        driver.onTrimMemory()
        runCurrent()
        assertEquals("only the fixture's release: nothing was loaded to release", 1, fake.releases.size)
    }

    @Test
    fun `a load that dies with an Error, not an Exception, still leaves the driver alive`() = runTest {
        val fake = object : FakeModel("x") {
            override suspend fun doLoad() = throw OutOfMemoryError("native allocation failed")
        }.apply { release() }
        val driver = driverFor(fake)
        driver.screenOpened()
        runCurrent()
        assertFalse(driver.current.loaded)
        driver.onStop(owner)
        driver.onStart(owner)
        runCurrent()
        assertTrue("the driver still takes events", driver.current.foreground)
    }

    @Test
    fun `a tick that reads the clock early re-arms the minute instead of dying`() = runTest {

        var offset = 0L
        val fake = FakeModel("x")
        val driver = SessionDriver(gateway = MutableStateFlow(fake), scope = backgroundScope, clock = { currentTime + offset })
        driver.onStop(owner)
        runCurrent()
        offset = -1
        advanceTimeBy(SessionPolicy.IDLE_RELEASE_MS + 1)
        assertTrue("an early tick must not release", fake.releases.isEmpty())
        advanceTimeBy(SessionPolicy.IDLE_RELEASE_MS + 1)
        assertEquals("the re-armed minute released", 1, fake.releases.size)
    }

    @Test
    fun `a gateway swapped after the driver was built is the one the next event sees`() = runTest {
        val manager = GatewayManager(NoModel)
        val driver = SessionDriver(gateway = manager.gateway, scope = backgroundScope, clock = { currentTime })
        driver.screenOpened()
        runCurrent()
        assertFalse("NoModel never loads", driver.current.loaded)

        val real = FakeModel("x").apply { release() }
        manager.swap(real)
        driver.screenOpened()
        runCurrent()
        assertEquals("the swapped-in gateway got the LOAD", 1, real.loads.size)
        assertTrue(driver.current.loaded)
    }
}
