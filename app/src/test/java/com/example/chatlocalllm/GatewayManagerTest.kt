package com.example.chatlocalllm

import com.example.chatlocalllm.di.appModule
import com.example.chatlocalllm.model.FakeModel
import com.example.chatlocalllm.model.GatewayManager
import com.example.chatlocalllm.model.ModelGateway
import com.example.chatlocalllm.model.ModelPresence
import com.example.chatlocalllm.model.ModelStatus
import com.example.chatlocalllm.model.NoModel
import com.example.chatlocalllm.ui.chat.ChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayManagerTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `starts as NoModel and a swap is observed in order`() = runTest {
        val manager = GatewayManager()
        val seen = mutableListOf<ModelGateway>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.gateway.toList(seen) }

        val real = FakeModel("x")
        manager.swap(real)

        assertEquals(listOf<ModelGateway>(NoModel, real), seen)
        assertSame(real, manager.current)
    }

    private val koin get() = koinApplication { modules(appModule(ModelPresence(file = null))) }.koin

    @Test
    fun `a ViewModel built before the swap keeps NoModel, one built after gets the real gateway`() {
        val koin = koin
        val before = ChatViewModel(koin.get())
        koin.get<GatewayManager>().swap(FakeModel("x"))
        val after = ChatViewModel(koin.get())

        assertEquals(ModelStatus.NONE, before.status.value)
        assertEquals(ModelStatus.READY, after.status.value)
    }

    @Test
    fun `the factory answers with the current gateway every time`() {
        val koin = koin
        assertSame(NoModel, koin.get<ModelGateway>())
        val real = FakeModel("x")
        koin.get<GatewayManager>().swap(real)
        assertSame(real, koin.get<ModelGateway>())
        assertSame("the manager is one object", koin.get<GatewayManager>(), koin.get<GatewayManager>())
    }
}
