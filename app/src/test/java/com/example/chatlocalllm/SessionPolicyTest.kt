package com.example.chatlocalllm

import com.example.chatlocalllm.model.SessionPolicy
import com.example.chatlocalllm.model.SessionPolicy.Action
import com.example.chatlocalllm.model.SessionPolicy.Event
import com.example.chatlocalllm.model.SessionPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {

    private val loaded = State(loaded = true)
    private val unloaded = State(loaded = false)

    @Test
    fun `foreground keeps and clears the background mark`() {
        val (state, action) = SessionPolicy.next(State(loaded = true, foreground = false, backgroundSince = 10L), Event.Foreground)
        assertEquals(State(loaded = true, foreground = true, backgroundSince = null), state)
        assertEquals(Action.KEEP, action)
    }

    @Test
    fun `background keeps and records the moment`() {
        val (state, action) = SessionPolicy.next(loaded, Event.Background(at = 1_000L))
        assertEquals(State(loaded = true, foreground = false, backgroundSince = 1_000L), state)
        assertEquals(Action.KEEP, action)
    }

    @Test
    fun `screen opened loads when in the foreground and not loaded`() {
        assertEquals(Action.LOAD, SessionPolicy.next(unloaded, Event.ScreenOpened).second)
    }

    @Test
    fun `screen opened keeps when already loaded`() {
        assertEquals(Action.KEEP, SessionPolicy.next(loaded, Event.ScreenOpened).second)
    }

    @Test
    fun `screen opened in the background does not load`() {
        val background = State(loaded = false, foreground = false, backgroundSince = 0L)
        assertEquals(Action.KEEP, SessionPolicy.next(background, Event.ScreenOpened).second)
    }

    @Test
    fun `trim releases when loaded, keeps when not`() {
        assertEquals(Action.RELEASE, SessionPolicy.next(loaded, Event.TrimMemory).second)
        assertEquals(Action.KEEP, SessionPolicy.next(unloaded, Event.TrimMemory).second)
    }

    @Test
    fun `tick before sixty seconds in the background keeps`() {
        val background = State(loaded = true, foreground = false, backgroundSince = 1_000L)
        assertEquals(Action.KEEP, SessionPolicy.next(background, Event.Tick(now = 1_000L + SessionPolicy.IDLE_RELEASE_MS - 1)).second)
    }

    @Test
    fun `tick at exactly sixty seconds releases`() {
        val background = State(loaded = true, foreground = false, backgroundSince = 1_000L)
        assertEquals(Action.RELEASE, SessionPolicy.next(background, Event.Tick(now = 1_000L + SessionPolicy.IDLE_RELEASE_MS)).second)
    }

    @Test
    fun `tick after sixty seconds releases, and only once it is loaded`() {
        val background = State(loaded = true, foreground = false, backgroundSince = 1_000L)
        assertEquals(Action.RELEASE, SessionPolicy.next(background, Event.Tick(now = 1_000L + 2 * SessionPolicy.IDLE_RELEASE_MS)).second)
        assertEquals(Action.KEEP, SessionPolicy.next(background.copy(loaded = false), Event.Tick(now = 1_000L + 2 * SessionPolicy.IDLE_RELEASE_MS)).second)
    }

    @Test
    fun `tick in the foreground never releases`() {
        assertEquals(Action.KEEP, SessionPolicy.next(loaded, Event.Tick(now = Long.MAX_VALUE)).second)
    }

    @Test
    fun `tick without a background mark never releases`() {
        val odd = State(loaded = true, foreground = false, backgroundSince = null)
        assertEquals(Action.KEEP, SessionPolicy.next(odd, Event.Tick(now = Long.MAX_VALUE)).second)
    }

    @Test
    fun `the policy never sets loaded by itself`() {

        val (state, action) = SessionPolicy.next(unloaded, Event.ScreenOpened)
        assertEquals(Action.LOAD, action)
        assertEquals(false, state.loaded)
    }
}
