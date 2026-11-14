package dev.chanflow.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultTest {
    @Test fun privateMediaAndResumePositionSurviveReopeningTheDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val post = MediaPost("w", 777, 888, 123456789, ".jpg", "offline-fixture", "Offline fixture", "", bytes = 4)
        var store = VaultStore(context)
        try {
            store.file(post).writeBytes(byteArrayOf(1, 2, 3, 4))
            store.record(post); store.watched(post, 12000, 60000)
            store.close(); store = VaultStore(context)
            assertTrue(store.contains(post))
            assertEquals(12000L, store.history().first { it.post.key == post.key }.position)
            store.removeHistory(setOf(post.key))
            assertTrue(store.contains(post))
            store.delete(post)
            assertFalse(store.contains(post))
        } finally { store.removeHistory(setOf(post.key)); store.file(post).delete(); store.close() }
    }
}
