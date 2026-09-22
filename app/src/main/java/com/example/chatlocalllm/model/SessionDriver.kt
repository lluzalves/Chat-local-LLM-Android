package com.example.chatlocalllm.model

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class SessionDriver(
    private val gateway: StateFlow<ModelGateway>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {

    private var state = SessionPolicy.State()
    private var idle: Job? = null
    private var screenOpen = false
    private val oneAtATime = Mutex()

    val current: SessionPolicy.State get() = state

    fun onEvent(event: SessionPolicy.Event) {
        scope.launch { apply(event) }
    }

    fun screenOpened() {
        screenOpen = true
        onEvent(SessionPolicy.Event.ScreenOpened)
    }

    fun screenClosed() {
        screenOpen = false
    }

    override fun onStart(owner: LifecycleOwner) {
        onEvent(SessionPolicy.Event.Foreground)

        if (screenOpen) onEvent(SessionPolicy.Event.ScreenOpened)
    }

    override fun onStop(owner: LifecycleOwner) {
        onEvent(SessionPolicy.Event.Background(clock()))
    }

    fun onTrimMemory() {
        onEvent(SessionPolicy.Event.TrimMemory)
    }

    private suspend fun apply(event: SessionPolicy.Event) = oneAtATime.withLock {
        val model = gateway.value
        val before = state.copy(loaded = model.isReady())
        val (next, action) = SessionPolicy.next(before, event)
        state = next
        when (action) {
            SessionPolicy.Action.LOAD -> withoutFailing { model.load() }
            SessionPolicy.Action.RELEASE -> withoutFailing { model.release() }
            SessionPolicy.Action.KEEP -> Unit
        }
        state = state.copy(loaded = model.isReady())

        if (!state.foreground && state.loaded) armIdle() else disarmIdle()
    }

    private suspend fun withoutFailing(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {

        }
    }

    private fun armIdle() {
        if (idle?.isActive == true) return
        idle = scope.launch {
            delay(SessionPolicy.IDLE_RELEASE_MS.milliseconds)

            idle = null
            apply(SessionPolicy.Event.Tick(clock()))
        }
    }

    private fun disarmIdle() {
        idle?.cancel()
        idle = null
    }
}
