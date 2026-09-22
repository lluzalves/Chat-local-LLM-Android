package com.example.chatlocalllm.model

import java.io.File

class ModelPresence(val file: File?) {
    val isHere: Boolean get() = file != null

    companion object {

        const val MODEL_FILE_NAME = "gemma-4-E2B-it-q4.litertlm"
        const val MODELS_DIR = "models"

        fun lookIn(filesDir: File): ModelPresence {
            val file = File(File(filesDir, MODELS_DIR), MODEL_FILE_NAME)
            return ModelPresence(file.takeIf { it.isFile && it.length() > 0L })
        }
    }
}
