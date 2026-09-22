package com.example.chatlocalllm.ui

import com.example.chatlocalllm.R
import com.example.chatlocalllm.model.ModelStatus

fun modelStatusLabel(status: ModelStatus): Int = when (status) {
    ModelStatus.NONE -> R.string.status_no_model
    ModelStatus.NOT_LOADED -> R.string.status_not_loaded
    ModelStatus.LOADING -> R.string.status_loading
    ModelStatus.READY -> R.string.status_ready
}
