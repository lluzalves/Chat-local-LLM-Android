package com.example.chatlocalllm.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GatewayManager(initial: ModelGateway = NoModel) {
    private val _gateway = MutableStateFlow(initial)

    val gateway: StateFlow<ModelGateway> = _gateway

    val current: ModelGateway get() = _gateway.value

    fun swap(gateway: ModelGateway) {
        _gateway.value = gateway
    }
}
