package com.example.chatlocalllm

import com.example.chatlocalllm.model.ModelPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelPresenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `no models directory, no model`() {
        val presence = ModelPresence.lookIn(folder.root)
        assertFalse(presence.isHere)
        assertNull(presence.file)
    }

    @Test
    fun `an empty file is not a model`() {
        File(folder.newFolder(ModelPresence.MODELS_DIR), ModelPresence.MODEL_FILE_NAME).createNewFile()
        assertFalse(ModelPresence.lookIn(folder.root).isHere)
    }

    @Test
    fun `a file with bytes under the exact name is the model`() {
        val file = File(folder.newFolder(ModelPresence.MODELS_DIR), ModelPresence.MODEL_FILE_NAME).apply { writeText("weights") }
        val presence = ModelPresence.lookIn(folder.root)
        assertTrue(presence.isHere)
        assertEquals(file, presence.file)
    }

    @Test
    fun `another name in the same folder does not count`() {
        File(folder.newFolder(ModelPresence.MODELS_DIR), "gemma-4-E2B-it.litertlm").writeText("weights")
        assertFalse(ModelPresence.lookIn(folder.root).isHere)
    }

    @Test
    fun `the constant is the article's name with the quantisation suffix`() {
        assertEquals("gemma-4-E2B-it-q4.litertlm", ModelPresence.MODEL_FILE_NAME)
    }
}
