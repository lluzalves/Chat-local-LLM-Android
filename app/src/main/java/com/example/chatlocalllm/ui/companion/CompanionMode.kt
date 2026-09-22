package com.example.chatlocalllm.ui.companion

import com.example.chatlocalllm.model.ModelStatus
import com.example.chatlocalllm.ui.chat.ChatMessage

enum class CompanionMode(val targets: CompanionTargets) {

    NONE(CompanionTargets(open = 0.0, glow = 0.12, energy = 0.0, dim = 1.0, innerSpin = 0.0, outerSpin = 0.0, assembly = 1.0)),

    ASLEEP(CompanionTargets(open = 0.0, glow = 0.30, energy = 0.0, dim = 0.45, innerSpin = 0.05, outerSpin = -0.04, assembly = 1.0)),

    LOADING(CompanionTargets(open = 0.15, glow = 0.50, energy = 0.3, dim = 0.15, innerSpin = 0.9, outerSpin = -0.7, assembly = 0.88)),

    IDLE(CompanionTargets(open = 0.0, glow = 0.55, energy = 0.0, dim = 0.0, innerSpin = 0.12, outerSpin = -0.09, assembly = 1.0)),

    THINKING(CompanionTargets(open = 1.0, glow = 0.85, energy = 1.0, dim = 0.0, innerSpin = 2.6, outerSpin = -1.9, assembly = 1.0)),

    STREAMING(CompanionTargets(open = 0.45, glow = 0.70, energy = 0.6, dim = 0.0, innerSpin = 0.9, outerSpin = -0.65, assembly = 1.0));

    companion object {

        fun from(status: ModelStatus, busy: Boolean, messages: List<ChatMessage>): CompanionMode = when (status) {
            ModelStatus.NONE -> NONE
            ModelStatus.NOT_LOADED -> ASLEEP
            ModelStatus.LOADING -> LOADING
            ModelStatus.READY -> {
                val last = messages.lastOrNull()
                when {
                    busy && last != null && last.streaming && last.text.isEmpty() -> THINKING
                    busy -> STREAMING
                    else -> IDLE
                }
            }
        }
    }
}

data class CompanionTargets(
    val open: Double,
    val glow: Double,
    val energy: Double,
    val dim: Double,
    val innerSpin: Double,
    val outerSpin: Double,
    val assembly: Double,
)
