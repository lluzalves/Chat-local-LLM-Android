package com.example.chatlocalllm.di

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.chatlocalllm.model.GatewayManager
import com.example.chatlocalllm.model.ModelGateway
import com.example.chatlocalllm.model.ModelPresence
import com.example.chatlocalllm.model.NoModel
import com.example.chatlocalllm.model.SessionDriver
import com.example.chatlocalllm.ui.chat.ChatViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun appModule(presence: ModelPresence) = module {
    single { presence }
    single { GatewayManager(NoModel) }
    factory<ModelGateway> { get<GatewayManager>().current }
    single {
        SessionDriver(
            gateway = get<GatewayManager>().gateway,
            scope = ProcessLifecycleOwner.get().lifecycleScope,
        )
    }
    viewModelOf(::ChatViewModel)
}
