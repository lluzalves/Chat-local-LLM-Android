package com.example.chatlocalllm.model

object SessionPolicy {
    const val IDLE_RELEASE_MS = 60_000L

    data class State(val loaded: Boolean = false, val foreground: Boolean = true, val backgroundSince: Long? = null)
    sealed interface Event {
        data object Foreground : Event
        data class Background(val at: Long) : Event
        data object ScreenOpened : Event
        data object TrimMemory : Event
        data class Tick(val now: Long) : Event
    }
    enum class Action { LOAD, RELEASE, KEEP }

    fun next(s: State, e: Event): Pair<State, Action> = when (e) {
        Event.Foreground -> s.copy(foreground = true, backgroundSince = null) to Action.KEEP
        is Event.Background -> s.copy(foreground = false, backgroundSince = e.at) to Action.KEEP
        Event.ScreenOpened -> s to if (s.foreground && !s.loaded) Action.LOAD else Action.KEEP
        Event.TrimMemory -> s to if (s.loaded) Action.RELEASE else Action.KEEP
        is Event.Tick -> {
            val idle = !s.foreground && s.backgroundSince != null && e.now - s.backgroundSince >= IDLE_RELEASE_MS
            s to if (idle && s.loaded) Action.RELEASE else Action.KEEP
        }
    }
}
