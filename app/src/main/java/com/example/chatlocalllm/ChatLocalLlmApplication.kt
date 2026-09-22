package com.example.chatlocalllm

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chatlocalllm.di.appModule
import com.example.chatlocalllm.model.GatewayManager
import com.example.chatlocalllm.model.LiteRtGateway
import com.example.chatlocalllm.model.ModelEngine
import com.example.chatlocalllm.model.ModelPresence
import com.example.chatlocalllm.model.SessionDriver
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ChatLocalLlmApplication : Application() {

    private lateinit var session: SessionDriver

    override fun onCreate() {
        super.onCreate()
        val presence = ModelPresence.lookIn(filesDir)
        startKoin {
            androidLogger(Level.WARNING)
            androidContext(this@ChatLocalLlmApplication)
            modules(appModule(presence))
        }

        presence.file?.let { get<GatewayManager>().swap(LiteRtGateway(ModelEngine(it))) }

        session = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(session)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val pressure = level == TRIM_MEMORY_RUNNING_MODERATE ||
                level == TRIM_MEMORY_RUNNING_LOW ||
                level == TRIM_MEMORY_RUNNING_CRITICAL ||
                level == TRIM_MEMORY_MODERATE ||
                level == TRIM_MEMORY_COMPLETE
        if (pressure) session.onTrimMemory()
    }
}
